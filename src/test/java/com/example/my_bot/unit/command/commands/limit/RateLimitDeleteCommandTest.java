package com.example.my_bot.unit.command.commands.limit;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.limit.AllRateLimitsShowCommand;
import com.example.my_bot.command.commands.limit.RateLimitDeleteCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.limit.RateLimitException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleRateLimitService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitDeleteCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long ENTITY_ID = 1L;
    private static final String COMMAND_NAME = "!ban";
    private static final int ROLE_PRIORITY = 5;
    private static final boolean IS_PERSONAL = false;
    private static final int MAX_USAGE = 10;
    private static final int TIME_PERIOD_SEC = 60;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private RoleRateLimitService roleRateLimitService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    @InjectMocks
    private RateLimitDeleteCommand rateLimitDeleteCommand;

    @BeforeEach
    void setUp() {
        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }


    @Test
    void shouldDeleteRateLimitSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        RoleRateLimitDto limit = new RoleRateLimitDto(
                ENTITY_ID, COMMAND_NAME, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);

        doNothing().when(roleRateLimitService).deleteLimit(limit, CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = rateLimitDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(roleRateLimitService).getRoleLimitsSortedByEntityId(CHAT_ID);
        verify(roleRateLimitService).deleteLimit(limit, CHAT_ID, FROM_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("✅Лимит с ID 1 был успешно удалён.", captor.getValue().getText());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = rateLimitDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(roleRateLimitService, never()).getRoleLimitsSortedByEntityId(anyLong());
        verify(roleRateLimitService, never()).deleteLimit(any(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidInteger() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"abc"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = rateLimitDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным числовым значением."));
        verify(roleRateLimitService, never()).getRoleLimitsSortedByEntityId(anyLong());
        verify(roleRateLimitService, never()).deleteLimit(any(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenIdOutOfRange() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"2"});

        RoleRateLimitDto limit = new RoleRateLimitDto(
                ENTITY_ID, COMMAND_NAME, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = rateLimitDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("Не найдено лимита с таким ID.", captor.getValue().getText());
        verify(roleRateLimitService, never()).deleteLimit(any(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRateLimitException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        RoleRateLimitDto limit = new RoleRateLimitDto(
                ENTITY_ID, COMMAND_NAME, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);

        RateLimitException exception = new RateLimitException("Лимит не найден") {};
        doThrow(exception).when(roleRateLimitService).deleteLimit(limit, CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = rateLimitDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        RoleRateLimitDto limit = new RoleRateLimitDto(
                ENTITY_ID, COMMAND_NAME, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);

        RoleException exception = new RoleException("Роль не найдена") {};
        doThrow(exception).when(roleRateLimitService).deleteLimit(limit, CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = rateLimitDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        RoleRateLimitDto limit = new RoleRateLimitDto(
                ENTITY_ID, COMMAND_NAME, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);

        CommandException exception = new CommandException("Ошибка команды") {};
        doThrow(exception).when(roleRateLimitService).deleteLimit(limit, CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = rateLimitDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        RoleRateLimitDto limit = new RoleRateLimitDto(
                ENTITY_ID, COMMAND_NAME, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);
        doNothing().when(roleRateLimitService).deleteLimit(limit, CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> rateLimitDeleteCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        RoleRateLimitDto limit = new RoleRateLimitDto(
                ENTITY_ID, COMMAND_NAME, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);
        doNothing().when(roleRateLimitService).deleteLimit(limit, CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> rateLimitDeleteCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromGetRoleLimits() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> rateLimitDeleteCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(roleRateLimitService, never()).deleteLimit(any(), anyLong(), anyLong());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromDeleteLimit() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        RoleRateLimitDto limit = new RoleRateLimitDto(
                ENTITY_ID, COMMAND_NAME, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);

        RuntimeException runtimeException = new RuntimeException("Unexpected error");
        doThrow(runtimeException).when(roleRateLimitService).deleteLimit(limit, CHAT_ID, FROM_ID);

        assertThrows(RuntimeException.class, () -> rateLimitDeleteCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }
}
