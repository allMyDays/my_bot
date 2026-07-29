package com.example.my_bot.unit.command.commands.timer;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.timer.AllTimersShowCommand;
import com.example.my_bot.command.commands.timer.TimerChangeExecutionLimitCommand;
import com.example.my_bot.command.commands.timer.TimerChangeNextExecutionCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.timer.TimerException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.timer.TimerService;
import com.example.my_bot.utils.TextUtils;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class TimerChangeNextExecutionCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long TIMER_ID = 1L;
    private static final int OUTER_ID = 1;
    private static final String DATE_TIME_STR = "2025-01-01 12:00";
    private static final String[] ARGS = {"1", "2025-01-01", "12:00"};
    private static final LocalDateTime NEW_DATE_TIME = LocalDateTime.of(2025, 1, 1, 12, 0);
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;
    private static final String FORMATTED_DATE = "01 января 2025, 12:00 GMT+3";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private TimerService timerService;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private TimerChangeNextExecutionCommand command;

    @BeforeEach
    void setUp() {
        command = new TimerChangeNextExecutionCommand(vkChatClient, timerService, chatService, messageMapper);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldChangeNextExecutionSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime(DATE_TIME_STR)).thenReturn(Optional.of(NEW_DATE_TIME));

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            doNothing().when(timerService).changeNextExecutionForEachTimer(TIMER_ID, NEW_DATE_TIME);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            timeUtilsMock.when(() -> TimeUtils.getStringDateTimeWithTimeZone(NEW_DATE_TIME, TIME_ZONE))
                    .thenReturn(FORMATTED_DATE);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(timerService).getChatTimersSortedByIdAsc(CHAT_ID);
            verify(timerService).changeNextExecutionForEachTimer(TIMER_ID, NEW_DATE_TIME);
            verify(chatService).getChatTimeZone(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅Теперь таймер с ID 1 в следующий раз сработает " + FORMATTED_DATE + ".", actual);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "2025-01-01"}); // только 2 аргумента

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = command.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(timerService, never()).getChatTimersSortedByIdAsc(anyLong());
        verify(timerService, never()).changeNextExecutionForEachTimer(anyLong(), any());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimerId() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"abc", "2025-01-01", "12:00"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("abc")).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным числовым значением."));
            verify(timerService, never()).getChatTimersSortedByIdAsc(anyLong());
            verify(timerService, never()).changeNextExecutionForEachTimer(anyLong(), any());
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidDateTime() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "invalid", "datetime"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime("invalid datetime")).thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Некорректная дата/время"));
            verify(timerService, never()).getChatTimersSortedByIdAsc(anyLong());
            verify(timerService, never()).changeNextExecutionForEachTimer(anyLong(), any());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenTimerIdOutOfRange() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"2", "2025-01-01", "12:00"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("2")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime(DATE_TIME_STR)).thenReturn(Optional.of(NEW_DATE_TIME));

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer); // только 1 таймер
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals("Не найдено таймера с таким ID.", captor.getValue().getText());
            verify(timerService, never()).changeNextExecutionForEachTimer(anyLong(), any());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnTimerException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime(DATE_TIME_STR)).thenReturn(Optional.of(NEW_DATE_TIME));

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            TimerException timerException = new TimerException("Ошибка изменения времени") {};
            doThrow(timerException).when(timerService).changeNextExecutionForEachTimer(TIMER_ID, NEW_DATE_TIME);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(timerException.getMessage(), captor.getValue().getText());
        }
    }


    @Test
    void shouldPropagateRuntimeExceptionFromGetTimers() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime(DATE_TIME_STR)).thenReturn(Optional.of(NEW_DATE_TIME));

            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID))
                    .thenThrow(new RuntimeException("DB error"));

            assertThrows(RuntimeException.class, () -> command.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
            verify(timerService, never()).changeNextExecutionForEachTimer(anyLong(), any());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChangeNextExecution() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime(DATE_TIME_STR)).thenReturn(Optional.of(NEW_DATE_TIME));

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            RuntimeException runtimeException = new RuntimeException("Unexpected error");
            doThrow(runtimeException).when(timerService).changeNextExecutionForEachTimer(TIMER_ID, NEW_DATE_TIME);

            assertThrows(RuntimeException.class, () -> command.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime(DATE_TIME_STR)).thenReturn(Optional.of(NEW_DATE_TIME));

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            doNothing().when(timerService).changeNextExecutionForEachTimer(TIMER_ID, NEW_DATE_TIME);

            when(chatService.getChatTimeZone(CHAT_ID)).thenThrow(new RuntimeException("Chat service error"));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            assertThrows(RuntimeException.class, () -> command.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime(DATE_TIME_STR)).thenReturn(Optional.of(NEW_DATE_TIME));

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            doNothing().when(timerService).changeNextExecutionForEachTimer(TIMER_ID, NEW_DATE_TIME);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            timeUtilsMock.when(() -> TimeUtils.getStringDateTimeWithTimeZone(NEW_DATE_TIME, TIME_ZONE))
                    .thenReturn(FORMATTED_DATE);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK client error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ClientException.class, () -> command.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            timeUtilsMock.when(() -> TimeUtils.parseDateTime(DATE_TIME_STR)).thenReturn(Optional.of(NEW_DATE_TIME));

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            doNothing().when(timerService).changeNextExecutionForEachTimer(TIMER_ID, NEW_DATE_TIME);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            timeUtilsMock.when(() -> TimeUtils.getStringDateTimeWithTimeZone(NEW_DATE_TIME, TIME_ZONE))
                    .thenReturn(FORMATTED_DATE);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> command.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }
}