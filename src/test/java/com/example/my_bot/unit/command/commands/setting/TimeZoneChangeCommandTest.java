package com.example.my_bot.unit.command.commands.setting;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.settings.TimeZoneChangeCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeZoneChangeCommandTest {

    private static final long CHAT_ID = 100L;
    private static final TimeZoneType DEFAULT_ZONE = TimeZoneType.GMT_PLUS_3;
    private static final TimeZoneType CUSTOM_ZONE = TimeZoneType.GMT_PLUS_5;
    private static final String CUSTOM_ZONE_STRING = "GMT+5";
    private static final String INVALID_ZONE_STRING = "invalid";
    private static final String FORMATTED_TIME = "01 января 2025, 12:00 GMT+5";

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

    private TimeZoneChangeCommand timeZoneChangeCommand;

    @BeforeEach
    void setUp() {
        timeZoneChangeCommand = new TimeZoneChangeCommand(messageMapper);
        timeZoneChangeCommand.setChatService(chatService, vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldSetDefaultTimeZoneWhenNoArguments() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(any(Instant.class), eq(DEFAULT_ZONE)))
                    .thenReturn(FORMATTED_TIME);

            // when
            CommandExecutionStatus status = timeZoneChangeCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(chatService).setChatTimeZone(CHAT_ID, DEFAULT_ZONE);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            String expected = "✅Временная зона чата была успешно установлена на " + DEFAULT_ZONE.getStringType() + ".\n" +
                    "Текущее время: " + FORMATTED_TIME;
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldSetCustomTimeZoneWhenValidArgument() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{CUSTOM_ZONE_STRING});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(any(Instant.class), eq(CUSTOM_ZONE)))
                    .thenReturn(FORMATTED_TIME);

            // when
            CommandExecutionStatus status = timeZoneChangeCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(chatService).setChatTimeZone(CHAT_ID, CUSTOM_ZONE);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            String expected = "✅Временная зона чата была успешно установлена на " + CUSTOM_ZONE.getStringType() + ".\n" +
                    "Текущее время: " + FORMATTED_TIME;
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimeZone() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{INVALID_ZONE_STRING});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = timeZoneChangeCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(chatService, never()).setChatTimeZone(anyLong(), any());
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("Не найдено временной зоны по указанному аргументу.", captor.getValue().getText());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        RuntimeException exception = new RuntimeException("DB error");
        doThrow(exception).when(chatService).setChatTimeZone(CHAT_ID, DEFAULT_ZONE);

        // when / then
        assertThrows(RuntimeException.class, () -> timeZoneChangeCommand.execute(commandMessage));
        verify(chatService).setChatTimeZone(CHAT_ID, DEFAULT_ZONE);
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(any(Instant.class), eq(DEFAULT_ZONE)))
                    .thenReturn(FORMATTED_TIME);

            ClientException clientException = new ClientException("VK client error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            // when / then
            assertThrows(ClientException.class, () -> timeZoneChangeCommand.execute(commandMessage));
            verify(chatService).setChatTimeZone(CHAT_ID, DEFAULT_ZONE);
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(any(Instant.class), eq(DEFAULT_ZONE)))
                    .thenReturn(FORMATTED_TIME);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            // when / then
            assertThrows(ApiException.class, () -> timeZoneChangeCommand.execute(commandMessage));
            verify(chatService).setChatTimeZone(CHAT_ID, DEFAULT_ZONE);
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }
}