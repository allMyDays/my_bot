package com.example.my_bot.unit.command.commands.event;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.event.EventCreateCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.EventEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.event.EventService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventCreateCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final String ROLE_NAME = "Модератор";
    private static final int ROLE_PRIORITY = 5;
    private static final String FULL_COMMAND = "!ban";
    private static final String EVENT_ARGUMENT = "10";
    private static final long EVENT_ID = 1L;
    private static final int CHAT_MANAGER_ROLE_PRIORITY = 100;

    @Mock
    private EventService eventService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private RoleService roleService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private EventCreateCommand eventCreateCommand;

    @BeforeEach
    void setUp() {
        eventCreateCommand = new EventCreateCommand(
                eventService,
                messageMapper
        );
        eventCreateCommand.setVkChatClient(vkChatClient);
        eventCreateCommand.setRoleService(roleService);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldCreateEventWithoutArgumentSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", ROLE_NAME, FULL_COMMAND});

        EventEntity createdEvent = createEventEntity(null, ROLE_PRIORITY, FULL_COMMAND, false, false, false);
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false)))
                .thenReturn(createdEvent);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY)).thenReturn(java.util.Optional.of(ROLE_NAME));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(eventService).createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false));
        verify(roleService).getRoleName(CHAT_ID, ROLE_PRIORITY);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("успешно создали новое событие."));
        assertTrue(actual.contains("Тип: приглашение (Приглашение в чат)."));
        assertTrue(actual.contains("Воздействует на роль «Модератор» и ниже."));
        assertTrue(actual.contains("Применяется команда: !ban"));
        assertTrue(actual.contains("@id" + FROM_ID));
    }


    @Test
    void shouldCreateEventWithRoleByPriorityNumber() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", "5", FULL_COMMAND});

        EventEntity createdEvent = createEventEntity(null, ROLE_PRIORITY, FULL_COMMAND, false, false, false);
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(5), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false)))
                .thenReturn(createdEvent);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY)).thenReturn(java.util.Optional.of(ROLE_NAME));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(eventService).createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(5), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false));
    }

    @Test
    void shouldCreateEventWithParametersDeleteReplySilent() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", ROLE_NAME, "!ban &delete &reply &silent"});

        EventEntity createdEvent = createEventEntity(null, ROLE_PRIORITY, "!ban", true, true, true);
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq("!ban"), eq(FROM_ID), eq(true), eq(true), eq(true)))
                .thenReturn(createdEvent);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY)).thenReturn(java.util.Optional.of(ROLE_NAME));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(eventService).createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq("!ban"), eq(FROM_ID), eq(true), eq(true), eq(true));
        assertTrue(true);
    }


    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"приглашение", "Модератор"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Недостаточно") || captor.getValue().getText().contains("аргумент"));
        verify(eventService, never()).createNewEvent(anyLong(), any(), anyString(), any(), any(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidEventType() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"несуществующий", ROLE_NAME, FULL_COMMAND});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("несуществующий тип события"));
        verify(eventService, never()).createNewEvent(anyLong(), any(), anyString(), any(), any(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArgumentsForEventWithArg() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"короткое_сообщение", "10", ROLE_NAME}); // нет команды

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(eventService, never()).createNewEvent(anyLong(), any(), anyString(), any(), any(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", ROLE_NAME, FULL_COMMAND});

        RoleException roleException = new RoleException("Роль не найдена") {};
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false)))
                .thenThrow(roleException);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(roleException.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnEventException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", ROLE_NAME, FULL_COMMAND});

        EventException eventException = new EventException("Ошибка события") {};
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false)))
                .thenThrow(eventException);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(eventException.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", ROLE_NAME, FULL_COMMAND});

        CommandException commandException = new CommandException("Ошибка команды") {};
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false)))
                .thenThrow(commandException);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(commandException.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromEventService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", ROLE_NAME, FULL_COMMAND});

        RuntimeException runtimeException = new RuntimeException("Unexpected error");
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false)))
                .thenThrow(runtimeException);

        assertThrows(RuntimeException.class, () -> eventCreateCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", ROLE_NAME, FULL_COMMAND});

        EventEntity createdEvent = createEventEntity(null, ROLE_PRIORITY, FULL_COMMAND, false, false, false);
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false)))
                .thenReturn(createdEvent);
        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY)).thenReturn(java.util.Optional.of(ROLE_NAME));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> eventCreateCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"приглашение", ROLE_NAME, FULL_COMMAND});

        EventEntity createdEvent = createEventEntity(null, ROLE_PRIORITY, FULL_COMMAND, false, false, false);
        when(eventService.createNewEvent(eq(CHAT_ID), eq(MyEventType.INVITE_ANOTHER), eq(ROLE_NAME), isNull(), eq(FULL_COMMAND), eq(FROM_ID), eq(false), eq(false), eq(false)))
                .thenReturn(createdEvent);
        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY)).thenReturn(java.util.Optional.of(ROLE_NAME));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> eventCreateCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    private EventEntity createEventEntity(String argument, int rolePriority, String fullCommand, boolean delete, boolean reply, boolean silent) {
        EventEntity event = new EventEntity();
        event.setId(EVENT_ID);
        event.setType(MyEventType.INVITE_ANOTHER);
        event.setRolePriority(rolePriority);
        event.setArgument(argument);
        event.setFullCommand(fullCommand);
        event.setDelete(delete);
        event.setReply(reply);
        event.setSilent(silent);
        return event;
    }
}