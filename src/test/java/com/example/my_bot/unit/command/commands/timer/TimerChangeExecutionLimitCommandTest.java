package com.example.my_bot.unit.command.commands.timer;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.timer.AllTimersShowCommand;
import com.example.my_bot.command.commands.timer.TimerChangeExecutionLimitCommand;
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
class TimerChangeExecutionLimitCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long TIMER_ID = 1L;
    private static final int OUTER_ID = 1;
    private static final int NEW_LIMIT = 5;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private TimerService timerService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private TimerChangeExecutionLimitCommand command;

    @BeforeEach
    void setUp() {
        command = new TimerChangeExecutionLimitCommand(vkChatClient, timerService, messageMapper);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldChangeExecutionLimitSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            doNothing().when(timerService).setCustomExecutionLimit(TIMER_ID, NEW_LIMIT);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(timerService).getChatTimersSortedByIdAsc(CHAT_ID);
            verify(timerService).setCustomExecutionLimit(TIMER_ID, NEW_LIMIT);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅Теперь таймер с ID 1 выполнится максимум 5 раз, после чего будет удалён.", actual);
        }
    }


    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = command.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(timerService, never()).getChatTimersSortedByIdAsc(anyLong());
        verify(timerService, never()).setCustomExecutionLimit(anyLong(), anyInt());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidInteger() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"abc", "5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("abc")).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            verify(timerService, never()).getChatTimersSortedByIdAsc(anyLong());
            verify(timerService, never()).setCustomExecutionLimit(anyLong(), anyInt());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenTimerIdOutOfRange() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"2", "5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("2")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            // Только один таймер в списке
            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals("Не найдено таймера с таким ID.", captor.getValue().getText());
            verify(timerService, never()).setCustomExecutionLimit(anyLong(), anyInt());
        }
    }

    // ==================== Исключения от TimerService ====================

    @Test
    void shouldReturnBusinessLogicErrorOnTimerException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            TimerException timerException = new TimerException("Ошибка установки лимита") {};
            doThrow(timerException).when(timerService).setCustomExecutionLimit(TIMER_ID, NEW_LIMIT);

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
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID))
                    .thenThrow(new RuntimeException("DB error"));

            assertThrows(RuntimeException.class, () -> command.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
            verify(timerService, never()).setCustomExecutionLimit(anyLong(), anyInt());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromSetLimit() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            RuntimeException runtimeException = new RuntimeException("Unexpected error");
            doThrow(runtimeException).when(timerService).setCustomExecutionLimit(TIMER_ID, NEW_LIMIT);

            assertThrows(RuntimeException.class, () -> command.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }

    // ==================== Проброс исключений от VkChatClient ====================

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            doNothing().when(timerService).setCustomExecutionLimit(TIMER_ID, NEW_LIMIT);

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
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("1")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            TimerEntity timer = new TimerEntity();
            timer.setId(TIMER_ID);
            List<TimerEntity> timers = List.of(timer);
            when(timerService.getChatTimersSortedByIdAsc(CHAT_ID)).thenReturn(timers);

            doNothing().when(timerService).setCustomExecutionLimit(TIMER_ID, NEW_LIMIT);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> command.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }
}