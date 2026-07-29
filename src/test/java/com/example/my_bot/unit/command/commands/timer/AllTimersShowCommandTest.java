package com.example.my_bot.unit.command.commands.timer;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.timer.AllTimersShowCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.timer.TimerType;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class AllTimersShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long CREATOR_ID = 200L;
    private static final String COMMAND = "!ban";
    private static final int INTERVAL_SEC = 3600;
    private static final int MAX_TIMERS = 10;
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;

    @Mock
    private TimerService timerService;

    @Mock
    private VkChatClient vkChatClient;

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

    private AllTimersShowCommand allTimersShowCommand;

    @BeforeEach
    void setUp() {
        allTimersShowCommand = new AllTimersShowCommand(timerService, chatService, messageMapper);
        allTimersShowCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(timerService.getMaxTimers()).thenReturn(MAX_TIMERS);
    }

    @Test
    void shouldShowTimersWithOnceType() throws ClientException, ApiException {
        // given
        Instant now = Instant.now();
        TimerEntity timer = new TimerEntity();
        timer.setId(1L);
        timer.setCreatorId(CREATOR_ID);
        timer.setFullCommand(COMMAND);
        timer.setType(TimerType.ONCE);
        timer.setNextExecution(now.plus(10, ChronoUnit.MINUTES));
        timer.setIntervalSeconds(null);

        List<TimerEntity> timers = List.of(timer);
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.createMention(CREATOR_ID)).thenReturn("@id200");
            String formattedDate = "01 января 2025, 12:10 GMT+3";
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(timer.getNextExecution(), TIME_ZONE))
                    .thenReturn(formattedDate);

            // when
            CommandExecutionStatus status = allTimersShowCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(timerService).getChatTimersSortedByIdAsc(CHAT_ID);
            verify(chatService).getChatTimeZone(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            String expected = "В чате установлено (1/10) таймеров.\n\n" +
                    "@id200(1). Команда «!ban» одноразово 01 января 2025, 12:10 GMT+3";
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldShowTimersWithEachType() throws ClientException, ApiException {
        // given
        Instant now = Instant.now();
        TimerEntity timer = new TimerEntity();
        timer.setId(1L);
        timer.setCreatorId(CREATOR_ID);
        timer.setFullCommand(COMMAND);
        timer.setType(TimerType.EACH);
        timer.setNextExecution(now.plus(5, ChronoUnit.MINUTES));
        timer.setIntervalSeconds((long) INTERVAL_SEC);

        List<TimerEntity> timers = List.of(timer);
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.createMention(CREATOR_ID)).thenReturn("@id200");
            String formattedDate = "01 января 2025, 12:05 GMT+3";
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(timer.getNextExecution(), TIME_ZONE))
                    .thenReturn(formattedDate);
            timeUtilsMock.when(() -> TimeUtils.formatDurationFromSeconds(INTERVAL_SEC, true))
                    .thenReturn("1 час");

            // when
            CommandExecutionStatus status = allTimersShowCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            String expected = "В чате установлено (1/10) таймеров.\n\n" +
                    "@id200(1). Команда «!ban» циклично через каждые 1 час. " +
                    "Следующий вызов: " + formattedDate + "\n";
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldShowTimersWithDailyType() throws ClientException, ApiException {
        // given
        Instant now = Instant.now();
        TimerEntity timer = new TimerEntity();
        timer.setId(1L);
        timer.setCreatorId(CREATOR_ID);
        timer.setFullCommand(COMMAND);
        timer.setType(TimerType.DAILY);
        timer.setNextExecution(now.plus(1, ChronoUnit.DAYS));
        timer.setIntervalSeconds(null);

        List<TimerEntity> timers = List.of(timer);
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.createMention(CREATOR_ID)).thenReturn("@id200");
            String formattedDate = "02 января 2025, 12:00 GMT+3";
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(timer.getNextExecution(), TIME_ZONE))
                    .thenReturn(formattedDate);

            // when
            CommandExecutionStatus status = allTimersShowCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            String expected = "В чате установлено (1/10) таймеров.\n\n" +
                    "@id200(1). Команда «!ban» циклично каждый день в 12:00." +
                    " Следующий вызов: " + formattedDate + "\n";
            assertEquals(expected, actual);
        }
    }


    @Test
    void shouldShowTimersWithExecutionLimit() throws ClientException, ApiException {
        // given
        Instant now = Instant.now();
        TimerEntity timer = new TimerEntity();
        timer.setId(1L);
        timer.setCreatorId(CREATOR_ID);
        timer.setFullCommand(COMMAND);
        timer.setType(TimerType.EACH);
        timer.setNextExecution(now.plus(5, ChronoUnit.MINUTES));
        timer.setIntervalSeconds((long) INTERVAL_SEC);
        timer.setCustomExecutionLimit(3);
        timer.setExecutionCounter(1);

        List<TimerEntity> timers = List.of(timer);
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.createMention(CREATOR_ID)).thenReturn("@id200");
            String formattedDate = "01 января 2025, 12:05 GMT+3";
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(timer.getNextExecution(), TIME_ZONE))
                    .thenReturn(formattedDate);
            timeUtilsMock.when(() -> TimeUtils.formatDurationFromSeconds(INTERVAL_SEC, true))
                    .thenReturn("1 час");

            // when
            CommandExecutionStatus status = allTimersShowCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            String expected = "В чате установлено (1/10) таймеров.\n\n" +
                    "@id200(1). Команда «!ban» циклично через каждые 1 час (1/3). " +
                    "Следующий вызов: " + formattedDate + "\n";
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldShowEmptyTimers() throws ClientException, ApiException {
        // given
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(List.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = allTimersShowCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(timerService).getChatTimersSortedByIdAsc(CHAT_ID);
        verify(chatService).getChatTimeZone(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        String expected = "В чате установлено (0/10) таймеров.\n\n";
        assertEquals(expected, actual);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromTimerService() throws ClientException, ApiException {
        // given
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID))
                .thenThrow(new RuntimeException("DB error"));

        // when / then
        assertThrows(RuntimeException.class, () -> allTimersShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        // given
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(List.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenThrow(new RuntimeException("Chat service error"));

        // when / then
        assertThrows(RuntimeException.class, () -> allTimersShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(List.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ClientException.class, () -> allTimersShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(List.of());
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ApiException.class, () -> allTimersShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}