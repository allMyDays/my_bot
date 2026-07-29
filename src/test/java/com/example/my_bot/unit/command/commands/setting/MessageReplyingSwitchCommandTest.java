package com.example.my_bot.unit.command.commands.setting;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.settings.*;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
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
import java.util.Optional;

import static com.example.my_bot.utils.ChatUtils.DEFAULT_CHAT_PREFIX;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageReplyingSwitchCommandTest {

    private static final long CHAT_ID = 100L;

    @Mock
    private ChatService chatService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private MessageReplyingSwitchCommand messageReplyingSwitchCommand;

    @BeforeEach
    void setUp() {
        messageReplyingSwitchCommand = new MessageReplyingSwitchCommand(messageMapper);
        messageReplyingSwitchCommand.setChatService(chatService, vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldEnableReplyingAndSetReplyToTrueWhenSwitchReturnsOn() throws ClientException, ApiException {
        // given
        when(chatService.switchMessageReplying(CHAT_ID)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = messageReplyingSwitchCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).switchMessageReplying(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), eq(commandMessage));
        String actualText = textCaptor.getValue();
        assertEquals("Теперь мне можно отвечать на ваши команды посредством пересыла вашего сообщения в чате.", actualText);

        assertTrue(sendMessageDto.isReplyToMessageId());
    }


    @Test
    void shouldDisableReplyingAndSetReplyToFalseWhenSwitchReturnsOff() throws ClientException, ApiException {
        // given
        when(chatService.switchMessageReplying(CHAT_ID)).thenReturn(SwitchChatSettingResult.OFF);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = messageReplyingSwitchCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).switchMessageReplying(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), eq(commandMessage));
        String actualText = textCaptor.getValue();
        assertEquals("Теперь мне нельзя отвечать на ваши команды посредством пересыла вашего сообщения в чате.", actualText);

        assertFalse(sendMessageDto.isReplyToMessageId());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        // given
        when(chatService.switchMessageReplying(CHAT_ID)).thenThrow(new RuntimeException("DB error"));

        // when / then
        assertThrows(RuntimeException.class, () -> messageReplyingSwitchCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(chatService.switchMessageReplying(CHAT_ID)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ClientException.class, () -> messageReplyingSwitchCommand.execute(commandMessage));
        verify(chatService).switchMessageReplying(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(chatService.switchMessageReplying(CHAT_ID)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ApiException.class, () -> messageReplyingSwitchCommand.execute(commandMessage));
        verify(chatService).switchMessageReplying(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);
    }
}