package com.example.my_bot.unit.service;


import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.key.ButtonPayloadKey;
import com.example.my_bot.exception.message.VkKeyboardButtonsMaxLimitException;
import com.example.my_bot.service.VkKeyboardActionService;
import com.example.my_bot.service.chat.AdminChatActionService;
import com.example.my_bot.utils.KeyboardUtils;
import com.example.my_bot.vk.mapping.button.VkButtonConfig;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButton;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.my_bot.enumeration.key.ButtonPayloadKey.ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VkKeyboardActionServiceTest {

    @Mock
    private AdminChatActionService adminChatActionService;

    @InjectMocks
    private VkKeyboardActionService keyboardService;

    private final long botId = 123L;
    private final long fromId = 456L;
    private final long chatId = 789L;

    private CommandRoutingData routingData;
    private GroupActor actor;

    @BeforeEach
    void setUp() {
        actor = new GroupActor(botId, "token");
        routingData = new CommandRoutingData();
        routingData.setDataBaseChatId(chatId);
        routingData.setVkApiChatId(chatId);
        routingData.setExecutorBot(actor);
        routingData.setReceivedEventBot(actor);
    }

    @Test
    void shouldCreateKeyboardSuccessfully() {
        List<VkButtonConfig> buttons = List.of(
                new VkButtonConfig("Button1", KeyboardButtonColor.POSITIVE, "{\"key\":\"value1\"}"),
                new VkButtonConfig("Button2", KeyboardButtonColor.NEGATIVE, "{\"key\":\"value2\"}")
        );
        int buttonsPerRow = 2;

        Keyboard result = keyboardService.createAutoLayoutKeyboard(buttons, buttonsPerRow);

        assertThat(result).isNotNull();
        assertThat(result.getButtons()).hasSize(1); // одна строка
        assertThat(result.getButtons().get(0)).hasSize(2);

        KeyboardButton first = result.getButtons().get(0).get(0);
        assertThat(first.getColor()).isEqualTo(KeyboardButtonColor.POSITIVE);
    }

    @Test
    void shouldCreateKeyboardWithMultipleRows() {
        List<VkButtonConfig> buttons = List.of(
                new VkButtonConfig("1", KeyboardButtonColor.POSITIVE, "{}"),
                new VkButtonConfig("2", KeyboardButtonColor.POSITIVE, "{}"),
                new VkButtonConfig("3", KeyboardButtonColor.POSITIVE, "{}")
        );
        int buttonsPerRow = 2;

        Keyboard result = keyboardService.createAutoLayoutKeyboard(buttons, buttonsPerRow);

        assertThat(result.getButtons()).hasSize(2);
        assertThat(result.getButtons().get(0)).hasSize(2);
        assertThat(result.getButtons().get(1)).hasSize(1);
    }

    @Test
    void shouldThrowWhenTooManyButtons() {
        List<VkButtonConfig> buttons = mock(List.class);
        given(buttons.size()).willReturn(41);
        assertThatThrownBy(() -> keyboardService.createAutoLayoutKeyboard(buttons, 5))
                .isInstanceOf(VkKeyboardButtonsMaxLimitException.class)
                .hasMessageContaining("40");
    }

    @Test
    void shouldThrowWhenButtonsPerRowOutOfRange() {
        List<VkButtonConfig> buttons = List.of(new VkButtonConfig("1", KeyboardButtonColor.POSITIVE, "{}"));
        assertThatThrownBy(() -> keyboardService.createAutoLayoutKeyboard(buttons, 0))
                .isInstanceOf(VkKeyboardButtonsMaxLimitException.class)
                .hasMessageContaining("от 1 до 5");
        assertThatThrownBy(() -> keyboardService.createAutoLayoutKeyboard(buttons, 6))
                .isInstanceOf(VkKeyboardButtonsMaxLimitException.class)
                .hasMessageContaining("от 1 до 5");
    }

    @Test
    void shouldCreateKeyboardWithDefaultButtonsPerRow() {
        List<VkButtonConfig> buttons = List.of(
                new VkButtonConfig("1", KeyboardButtonColor.POSITIVE, "{}"),
                new VkButtonConfig("2", KeyboardButtonColor.POSITIVE, "{}"),
                new VkButtonConfig("3", KeyboardButtonColor.POSITIVE, "{}"),
                new VkButtonConfig("4", KeyboardButtonColor.POSITIVE, "{}"),
                new VkButtonConfig("5", KeyboardButtonColor.POSITIVE, "{}"),
                new VkButtonConfig("6", KeyboardButtonColor.POSITIVE, "{}"),
                new VkButtonConfig("7", KeyboardButtonColor.POSITIVE, "{}")
        );

        Keyboard result = keyboardService.createAutoLayoutKeyboard(buttons);

        assertThat(result.getButtons()).hasSize(2);
        assertThat(result.getButtons().get(0)).hasSize(5);
        assertThat(result.getButtons().get(1)).hasSize(2);
    }

    @Test
    void shouldReturnIfButtonNotBelongsToBot() throws ClientException, ApiException {
        String messageText = "some text";
        String buttonPayload = "payload";
        long wrongBotId = 999L;
        routingData.setReceivedEventBot(new GroupActor(wrongBotId, "other"));

        try (var mocked = mockStatic(KeyboardUtils.class)) {
            mocked.when(() -> KeyboardUtils.isClickedButtonBelongsToRequiredBot(messageText, wrongBotId))
                    .thenReturn(false);

            keyboardService.handleClickedButton(messageText, buttonPayload, routingData, fromId);

            verify(adminChatActionService, never()).handleClickedAdminChatButton(any(), any(), anyString(), anyLong());
        }
    }

    @Test
    void shouldReturnIfPayloadCannotBeExtracted() throws ClientException, ApiException {
        String messageText = "some text";
        String buttonPayload = "invalid";

        try (var mocked = mockStatic(KeyboardUtils.class)) {
            mocked.when(() -> KeyboardUtils.isClickedButtonBelongsToRequiredBot(messageText, botId))
                    .thenReturn(true);
            mocked.when(() -> KeyboardUtils.extractKeyAndValueFromPayload(buttonPayload))
                    .thenReturn(Optional.empty());

            keyboardService.handleClickedButton(messageText, buttonPayload, routingData, fromId);

            verify(adminChatActionService, never()).handleClickedAdminChatButton(any(), any(), anyString(), anyLong());
        }
    }

    @Test
    void shouldCallAdminChatServiceForValidPayload() throws ClientException, ApiException {
        String messageText = "some text";
        String buttonPayload = "{\"key\":\"ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT\",\"value\":\"123\"}";
        var key = ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT;
        String value = "123";
        Map.Entry<ButtonPayloadKey, String> entry = Map.entry(key, value);

        try (var mocked = mockStatic(KeyboardUtils.class)) {
            mocked.when(() -> KeyboardUtils.isClickedButtonBelongsToRequiredBot(messageText, botId))
                    .thenReturn(true);
            mocked.when(() -> KeyboardUtils.extractKeyAndValueFromPayload(buttonPayload))
                    .thenReturn(Optional.of(entry));

            keyboardService.handleClickedButton(messageText, buttonPayload, routingData, fromId);

            verify(adminChatActionService).handleClickedAdminChatButton(routingData, key, value, fromId);
        }
    }

    @Test
    void shouldHandleAllAdminChatPayloadKeys() throws ClientException, ApiException {
        String messageText = "some text";
        var keys = List.of(
                ButtonPayloadKey.ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT,
                ButtonPayloadKey.ADMIN_CHAT_EXECUTE_COMMAND_IN_ALL_BOUND_CHATS,
                ButtonPayloadKey.ADMIN_CHAT_EXECUTE_COMMAND_IN_THIS_ADMIN_CHAT
        );

        for (ButtonPayloadKey key : keys) {
            String value = "456";
            Map.Entry<ButtonPayloadKey, String> entry = Map.entry(key, value);
            String buttonPayload = "{\"key\":\"" + key.name() + "\",\"value\":\"" + value + "\"}";

            try (var mocked = mockStatic(KeyboardUtils.class)) {
                mocked.when(() -> KeyboardUtils.isClickedButtonBelongsToRequiredBot(messageText, botId))
                        .thenReturn(true);
                mocked.when(() -> KeyboardUtils.extractKeyAndValueFromPayload(buttonPayload))
                        .thenReturn(Optional.of(entry));

                keyboardService.handleClickedButton(messageText, buttonPayload, routingData, fromId);

                verify(adminChatActionService).handleClickedAdminChatButton(routingData, key, value, fromId);
            }

            reset(adminChatActionService);
        }
    }
}