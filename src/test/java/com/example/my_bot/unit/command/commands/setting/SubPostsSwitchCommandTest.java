package com.example.my_bot.unit.command.commands.setting;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.settings.SubPostsSwitchCommand;
import com.example.my_bot.command.commands.settings.TimeZoneChangeCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.utils.TextUtils;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubPostsSwitchCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long GROUP_ID = -300L;

    @Mock
    private ChatService chatService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private SubmanagerService submanagerService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor executorBot;

    private SubPostsSwitchCommand subPostsSwitchCommand;

    @BeforeEach
    void setUp() {
        subPostsSwitchCommand = new SubPostsSwitchCommand(messageMapper, submanagerService);
        subPostsSwitchCommand.setChatService(chatService, vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(executorBot);
        when(executorBot.getGroupId()).thenReturn(GROUP_ID);
    }

    @Test
    void shouldEnableSubPostsWhenSubmanagerAndSwitchToOn() throws ClientException, ApiException {
        // given
        when(submanagerService.isSubmanager(executorBot)).thenReturn(true);
        when(chatService.switchSubPosts(CHAT_ID)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, executorBot, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(anyLong())).thenReturn("@club300");

            // when
            CommandExecutionStatus status = subPostsSwitchCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(chatService).switchSubPosts(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("Теперь мне можно присылать в текущий чат все новые посты из @club300(этой группы).", actual);
        }
    }

    @Test
    void shouldDisableSubPostsWhenSubmanagerAndSwitchToOff() throws ClientException, ApiException {
        // given
        when(submanagerService.isSubmanager(executorBot)).thenReturn(true);
        when(chatService.switchSubPosts(CHAT_ID)).thenReturn(SwitchChatSettingResult.OFF);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, executorBot, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(anyLong())).thenReturn("@club300");

            // when
            CommandExecutionStatus status = subPostsSwitchCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(chatService).switchSubPosts(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("Теперь мне нельзя присылать в текущий чат все новые посты из @club300(этой группы).", actual);
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenNotSubmanager() throws ClientException, ApiException {
        // given
        when(submanagerService.isSubmanager(executorBot)).thenReturn(false);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, executorBot, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = subPostsSwitchCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(chatService, never()).switchSubPosts(anyLong());
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("субменеджер") || captor.getValue().getText().contains("сабменеджер"));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromSubmanagerService() throws ClientException, ApiException {
        // given
        when(submanagerService.isSubmanager(executorBot)).thenThrow(new RuntimeException("Submanager error"));

        // when / then
        assertThrows(RuntimeException.class, () -> subPostsSwitchCommand.execute(commandMessage));
        verify(chatService, never()).switchSubPosts(anyLong());
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        // given
        when(submanagerService.isSubmanager(executorBot)).thenReturn(true);
        when(chatService.switchSubPosts(CHAT_ID)).thenThrow(new RuntimeException("DB error"));

        // when / then
        assertThrows(RuntimeException.class, () -> subPostsSwitchCommand.execute(commandMessage));
        verify(chatService).switchSubPosts(CHAT_ID);
        verify(vkChatClient, never()).sendText(any());
    }


    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(submanagerService.isSubmanager(executorBot)).thenReturn(true);
        when(chatService.switchSubPosts(CHAT_ID)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, executorBot, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(GROUP_ID)).thenReturn("@club300");

            // when / then
            assertThrows(ClientException.class, () -> subPostsSwitchCommand.execute(commandMessage));
            verify(chatService).switchSubPosts(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(submanagerService.isSubmanager(executorBot)).thenReturn(true);
        when(chatService.switchSubPosts(CHAT_ID)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, executorBot, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(GROUP_ID)).thenReturn("@club300");

            // when / then
            assertThrows(ApiException.class, () -> subPostsSwitchCommand.execute(commandMessage));
            verify(chatService).switchSubPosts(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }
}