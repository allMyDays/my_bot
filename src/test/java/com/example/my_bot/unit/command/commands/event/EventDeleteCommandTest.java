package com.example.my_bot.unit.command.commands.event;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.event.EventDeleteCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.event.EventService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventDeleteCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long EVENT_ID = 1L;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private EventService eventService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private EventDeleteCommand eventDeleteCommand;

    @BeforeEach
    void setUp() {
        eventDeleteCommand = new EventDeleteCommand(
                vkChatClient,
                eventService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldDeleteEventSuccessfully() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        EventDto event = new EventDto(
                EVENT_ID,
                MyEventType.INVITE_ANOTHER,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));

        doNothing().when(eventService).deleteEventById(EVENT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventDeleteCommand.execute(commandMessage);

        assertNull(status);
        verify(eventService).getEventsSortedByIdInIncreasingOrder(CHAT_ID);
        verify(eventService).deleteEventById(EVENT_ID, FROM_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("✅Событие с ID 1 было успешно удалёно.", captor.getValue().getText());
    }


    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(eventService, never()).getEventsSortedByIdInIncreasingOrder(anyLong());
        verify(eventService, never()).deleteEventById(anyLong(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidInteger() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"abc"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(eventService, never()).getEventsSortedByIdInIncreasingOrder(anyLong());
        verify(eventService, never()).deleteEventById(anyLong(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenIdOutOfRange() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        EventDto event = new EventDto(
                EVENT_ID,
                MyEventType.INVITE_ANOTHER,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("Не найдено события с таким ID.", captor.getValue().getText());
        verify(eventService, never()).deleteEventById(anyLong(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnEventException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        EventDto event = new EventDto(
                EVENT_ID,
                MyEventType.INVITE_ANOTHER,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));

        EventException exception = new EventException("Ошибка удаления события") {};
        doThrow(exception).when(eventService).deleteEventById(EVENT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        EventDto event = new EventDto(
                EVENT_ID,
                MyEventType.INVITE_ANOTHER,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));

        RoleException exception = new RoleException("Роль не найдена") {};
        doThrow(exception).when(eventService).deleteEventById(EVENT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        EventDto event = new EventDto(
                EVENT_ID,
                MyEventType.INVITE_ANOTHER,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));

        MemberException exception = new MemberException("Участник не найден") {};
        doThrow(exception).when(eventService).deleteEventById(EVENT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = eventDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromEventServiceDelete() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        EventDto event = new EventDto(
                EVENT_ID,
                MyEventType.INVITE_ANOTHER,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));

        RuntimeException runtimeException = new RuntimeException("Unexpected");
        doThrow(runtimeException).when(eventService).deleteEventById(EVENT_ID, FROM_ID);

        assertThrows(RuntimeException.class, () -> eventDeleteCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        EventDto event = new EventDto(
                EVENT_ID,
                MyEventType.INVITE_ANOTHER,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));
        doNothing().when(eventService).deleteEventById(EVENT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> eventDeleteCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        EventDto event = new EventDto(
                EVENT_ID,
                MyEventType.INVITE_ANOTHER,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));
        doNothing().when(eventService).deleteEventById(EVENT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> eventDeleteCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}
