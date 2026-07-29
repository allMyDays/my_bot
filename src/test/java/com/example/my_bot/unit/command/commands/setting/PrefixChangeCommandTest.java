package com.example.my_bot.unit.command.commands.setting;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.settings.PrefixChangeCommand;
import com.example.my_bot.command.commands.settings.SilentRestrictionSwitchCommand;
import com.example.my_bot.command.commands.settings.SubPostsSwitchCommand;
import com.example.my_bot.command.commands.settings.TimeZoneChangeCommand;
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
class PrefixChangeCommandTest {

    private static final long CHAT_ID = 100L;
    private static final char DEFAULT_PREFIX = DEFAULT_CHAT_PREFIX;

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

    private PrefixChangeCommand prefixChangeCommand;

    @BeforeEach
    void setUp() {
        prefixChangeCommand = new PrefixChangeCommand(messageMapper);
        prefixChangeCommand.setChatService(chatService, vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldSetDefaultPrefixWhenNoPrefixExists() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = prefixChangeCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).setChatPrefix(CHAT_ID, DEFAULT_PREFIX);
        verify(chatService, never()).disableChatPrefix(anyLong());
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertEquals("✅Префикс чата был установлен на стандартный: " + DEFAULT_PREFIX, actual);
    }

    @Test
    void shouldDisablePrefixWhenPrefixExists() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of('@'));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = prefixChangeCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).disableChatPrefix(CHAT_ID);
        verify(chatService, never()).setChatPrefix(anyLong(), anyChar());
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        String expected = "✅Префикс чата был отключён. " +
                "Теперь команды в чате можно писать как без префикса, так и со стандартным префиксом: " + DEFAULT_PREFIX;
        assertEquals(expected, actual);
    }

    @Test
    void shouldSetCustomPrefixSuccess() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"#"});
        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.empty()); // не важно для этого теста

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = prefixChangeCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).setChatPrefix(CHAT_ID, '#');
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertEquals("✅Префикс чата был установлен на: #\n" +
                "Теперь команды в чате можно писать ТОЛЬКО с этим префиксом.", actual);
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenPrefixTooLong() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"!!"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = prefixChangeCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(chatService, never()).setChatPrefix(anyLong(), anyChar());
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("В качестве префикса можно установить только один символ.", captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenForbiddenPrefixExceptionThrown() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"&"});
        ForbiddenPrefixException exception = new ForbiddenPrefixException('&');
        doThrow(exception).when(chatService).setChatPrefix(CHAT_ID, '&');

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = prefixChangeCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(chatService).setChatPrefix(CHAT_ID, '&');
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatServiceGetPrefix() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(chatService.getChatPrefix(CHAT_ID)).thenThrow(new RuntimeException("DB error"));

        // when / then
        assertThrows(RuntimeException.class, () -> prefixChangeCommand.execute(commandMessage));
        verify(chatService, never()).setChatPrefix(anyLong(), anyChar());
        verify(chatService, never()).disableChatPrefix(anyLong());
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatServiceSetPrefix() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"$"});
        doThrow(new RuntimeException("DB error")).when(chatService).setChatPrefix(CHAT_ID, '$');

        // when / then
        assertThrows(RuntimeException.class, () -> prefixChangeCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ClientException.class, () -> prefixChangeCommand.execute(commandMessage));
        verify(chatService).setChatPrefix(CHAT_ID, DEFAULT_PREFIX);
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ApiException.class, () -> prefixChangeCommand.execute(commandMessage));
        verify(chatService).setChatPrefix(CHAT_ID, DEFAULT_PREFIX);
        verify(vkChatClient).sendText(sendMessageDto);
    }
}