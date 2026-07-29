package com.example.my_bot.unit.command.commands.bind;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.bind.BindListShowCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.vk.api.sdk.objects.base.Error;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BindListShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final String USER_NAME_1 = "Иван Иванов";
    private static final String USER_NAME_2 = "Петр Петров";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private GlobalUserService userService;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private BindListShowCommand bindListShowCommand;

    @BeforeEach
    void setUp() {
        bindListShowCommand = new BindListShowCommand(
                userService,
                messageMapper,
                globalUserService
        );
        bindListShowCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldReturnSuccessWithListOfBoundUsers() throws ClientException, ApiException {
        Set<Long> userIds = Set.of(USER_ID_1, USER_ID_2);
        when(userService.findUserIdsByBoundChat(CHAT_ID)).thenReturn(userIds);

        Map<Long, String> namesMap = Map.of(
                USER_ID_1, USER_NAME_1,
                USER_ID_2, USER_NAME_2
        );
        when(globalUserService.getUserFullNamesInRequiredCase(eq(userIds), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        when(vkChatClient.sendText(any(SendMessageDto.class))).thenReturn(null);

        CommandExecutionStatus status = bindListShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);

        verify(userService).findUserIdsByBoundChat(CHAT_ID);
        verify(globalUserService).getUserFullNamesInRequiredCase(userIds, NameCase.NOMINATIVE);
        verify(messageMapper).toSendMessageDto(anyString(), any(CommandMessageDto.class));
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("Этот чат привязан у 2 участников:"));
        assertTrue(actual.contains("@id" + USER_ID_1 + "(" + USER_NAME_1 + ")"));
        assertTrue(actual.contains("@id" + USER_ID_2 + "(" + USER_NAME_2 + ")"));
    }

    @Test
    void shouldReturnSuccessWithEmptyList() throws ClientException, ApiException {
        Set<Long> emptySet = Set.of();
        when(userService.findUserIdsByBoundChat(CHAT_ID)).thenReturn(emptySet);

        when(globalUserService.getUserFullNamesInRequiredCase(eq(emptySet), eq(NameCase.NOMINATIVE)))
                .thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        when(vkChatClient.sendText(any(SendMessageDto.class))).thenReturn(null);

        CommandExecutionStatus status = bindListShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        assertTrue(actual.contains("Этот чат привязан у 0 участников:"));
        assertFalse(actual.contains("1."));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromUserServiceFindUserIds() throws ClientException, ApiException {
        RuntimeException exception = new RuntimeException("DB error");
        when(userService.findUserIdsByBoundChat(CHAT_ID)).thenThrow(exception);

        assertThrows(RuntimeException.class, () -> bindListShowCommand.execute(commandMessage));

        verify(userService).findUserIdsByBoundChat(CHAT_ID);
        verify(globalUserService, never()).getUserFullNamesInRequiredCase(anySet(), any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromGlobalUserService() throws ClientException, ApiException {
        Set<Long> userIds = Set.of(USER_ID_1);
        when(userService.findUserIdsByBoundChat(CHAT_ID)).thenReturn(userIds);

        RuntimeException exception = new RuntimeException("User service error");
        when(globalUserService.getUserFullNamesInRequiredCase(eq(userIds), eq(NameCase.NOMINATIVE)))
                .thenThrow(exception);

        assertThrows(RuntimeException.class, () -> bindListShowCommand.execute(commandMessage));

        verify(userService).findUserIdsByBoundChat(CHAT_ID);
        verify(globalUserService).getUserFullNamesInRequiredCase(userIds, NameCase.NOMINATIVE);
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        Set<Long> userIds = Set.of(USER_ID_1);
        when(userService.findUserIdsByBoundChat(CHAT_ID)).thenReturn(userIds);

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(eq(userIds), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> bindListShowCommand.execute(commandMessage));

        verify(userService).findUserIdsByBoundChat(CHAT_ID);
        verify(globalUserService).getUserFullNamesInRequiredCase(userIds, NameCase.NOMINATIVE);
        verify(messageMapper).toSendMessageDto(anyString(), any(CommandMessageDto.class));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        Set<Long> userIds = Set.of(USER_ID_1);
        when(userService.findUserIdsByBoundChat(CHAT_ID)).thenReturn(userIds);

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(eq(userIds), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> bindListShowCommand.execute(commandMessage));

        verify(userService).findUserIdsByBoundChat(CHAT_ID);
        verify(globalUserService).getUserFullNamesInRequiredCase(userIds, NameCase.NOMINATIVE);
        verify(messageMapper).toSendMessageDto(anyString(), any(CommandMessageDto.class));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}

