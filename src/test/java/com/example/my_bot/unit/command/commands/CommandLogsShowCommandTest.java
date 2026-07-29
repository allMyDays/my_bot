package com.example.my_bot.unit.command.commands;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.CommandLogsShowCommand;
import com.example.my_bot.command.commands.WriteCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.CommandLogEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.command.CommandLogService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.utils.ChatUtils;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandLogsShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final String USER_NAME_1 = "Иван Иванов";
    private static final String USER_NAME_2 = "Петр Петров";
    private static final String COMMAND_NAME_1 = "!ban";
    private static final String COMMAND_NAME_2 = "!kick";
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;
    private static final int MAX_LOGS = 100;
    private static final String MENTION_1 = "@id200";
    private static final String MENTION_2 = "@id201";

    @Mock
    private CommandLogService commandLogService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private CommandLogsShowCommand commandLogsShowCommand;

    @BeforeEach
    void setUp() {
        commandLogsShowCommand = new CommandLogsShowCommand(
                commandLogService,
                messageMapper,
                chatService,
                globalUserService
        );
        commandLogsShowCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldCapLimitToMax() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"150"});

        when(commandLogService.getLastNCommandLogs(CHAT_ID, MAX_LOGS)).thenReturn(List.of());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(Map.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = commandLogsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(commandLogService).getLastNCommandLogs(CHAT_ID, MAX_LOGS);
    }

    @Test
    void shouldShowEmptyLogs() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(commandLogService.getLastNCommandLogs(CHAT_ID, MAX_LOGS)).thenReturn(List.of());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(Map.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = commandLogsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(commandLogService).getLastNCommandLogs(CHAT_ID, MAX_LOGS);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertEquals("Последние 0 команд, используемых в данном чате:\n", actual);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromCommandLogService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(commandLogService.getLastNCommandLogs(CHAT_ID, MAX_LOGS))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> commandLogsShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromGlobalUserService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(commandLogService.getLastNCommandLogs(CHAT_ID, MAX_LOGS)).thenReturn(List.of());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenThrow(new RuntimeException("User service error"));

        assertThrows(RuntimeException.class, () -> commandLogsShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(commandLogService.getLastNCommandLogs(CHAT_ID, MAX_LOGS)).thenReturn(List.of());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(Map.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenThrow(new RuntimeException("Chat service error"));

        assertThrows(RuntimeException.class, () -> commandLogsShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(commandLogService.getLastNCommandLogs(CHAT_ID, MAX_LOGS)).thenReturn(List.of());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(Map.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> commandLogsShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(commandLogService.getLastNCommandLogs(CHAT_ID, MAX_LOGS)).thenReturn(List.of());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(Map.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> commandLogsShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    private CommandLogEntity createLog(long id, long fromId, String commandName, Instant createdAt) {
        CommandLogEntity log = new CommandLogEntity();
        log.setId(id);
        log.setFromId(fromId);
        log.setCommandName(commandName);
        log.setCreatedAt(createdAt);
        return log;
    }
}