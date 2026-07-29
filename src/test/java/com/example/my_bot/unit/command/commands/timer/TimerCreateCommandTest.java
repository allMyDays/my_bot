package com.example.my_bot.unit.command.commands.timer;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.timer.AllTimersShowCommand;
import com.example.my_bot.command.commands.timer.TimerChangeExecutionLimitCommand;
import com.example.my_bot.command.commands.timer.TimerCreateCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.command.CommandException;
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
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static com.example.my_bot.utils.TextUtils.collectArgumentsSinceIndex;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimerCreateCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final String COMMAND = "!ban";
    private static final String FULL_COMMAND = "!ban";
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;
    private static final String FORMATTED_DATE = "01 января 2025, 12:00 GMT+3";

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

    private TimerCreateCommand timerCreateCommand;

    @BeforeEach
    void setUp() {
        timerCreateCommand = new TimerCreateCommand(timerService, chatService, messageMapper);
        timerCreateCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenInvalidType() throws ClientException, ApiException {
        String[] args = {"неверный", "12:00", COMMAND};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.collectArgumentsSinceIndex(any(String[].class), anyInt()))
                    .thenReturn(FULL_COMMAND);

            CommandExecutionStatus status = timerCreateCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("несуществующий тип таймера"));
            verify(timerService, never()).createDailyTimer(anyLong(), any(), anyString(), anyLong());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        String[] args = {"ежедневно", "12:00", COMMAND};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);

        LocalTime time = LocalTime.of(12, 0);
        TimerEntity createdTimer = new TimerEntity();
        createdTimer.setType(TimerType.DAILY);
        createdTimer.setNextExecution(Instant.now());
        createdTimer.setFullCommand(FULL_COMMAND);

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class);
             MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {

            timeUtilsMock.when(() -> TimeUtils.parseTimeOfDay("12:00")).thenReturn(Optional.of(time));
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(createdTimer.getNextExecution(), TIME_ZONE))
                    .thenReturn(FORMATTED_DATE);

            textUtilsMock.when(() -> TextUtils.collectArgumentsSinceIndex(any(String[].class), eq(2)))
                    .thenReturn(FULL_COMMAND);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            when(timerService.createDailyTimer(CHAT_ID, time, FULL_COMMAND, FROM_ID))
                    .thenReturn(createdTimer);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK client error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ClientException.class, () -> timerCreateCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        String[] args = {"ежедневно", "12:00", COMMAND};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);

        LocalTime time = LocalTime.of(12, 0);
        TimerEntity createdTimer = new TimerEntity();
        createdTimer.setType(TimerType.DAILY);
        createdTimer.setNextExecution(Instant.now());
        createdTimer.setFullCommand(FULL_COMMAND);

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class);
             MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {

            timeUtilsMock.when(() -> TimeUtils.parseTimeOfDay("12:00")).thenReturn(Optional.of(time));
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(createdTimer.getNextExecution(), TIME_ZONE))
                    .thenReturn(FORMATTED_DATE);

            textUtilsMock.when(() -> TextUtils.collectArgumentsSinceIndex(any(String[].class), eq(2)))
                    .thenReturn(FULL_COMMAND);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            when(timerService.createDailyTimer(CHAT_ID, time, FULL_COMMAND, FROM_ID))
                    .thenReturn(createdTimer);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> timerCreateCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

}
