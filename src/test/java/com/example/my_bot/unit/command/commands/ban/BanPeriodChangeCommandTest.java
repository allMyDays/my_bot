package com.example.my_bot.unit.command.commands.ban;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.ban.BanCommand;
import com.example.my_bot.command.commands.ban.BanPeriodChangeCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.vk.api.sdk.objects.base.Error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BanPeriodChangeCommandTest {

    private static final long CHAT_ID = 100L;
    private static final String BAN_COMMAND_NAME = BanCommand.class.getAnnotation(Command.class).mainCommandName();

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    @InjectMocks
    private BanPeriodChangeCommand banPeriodChangeCommand;

    @BeforeEach
    void setUp() {
        banPeriodChangeCommand = new BanPeriodChangeCommand(messageMapper);
        banPeriodChangeCommand.setChatService(chatService, vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    // ==================== Успешные сценарии ====================

    @Test
    void shouldDisableDefaultBanPeriodSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banPeriodChangeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).disableDefaultBanTimePeriod(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        // Проверяем текст через ArgumentCaptor для переданного сообщения
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        String expected = "✅Дефолтный срок бана был отключён. Теперь, при команде «" + BAN_COMMAND_NAME + "» без аргументов времени, я буду выдавать вечный бан.";
        assertEquals(expected, actual);
    }

    @Test
    void shouldSetDefaultBanPeriodSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"2", "часа"});

        long banSeconds = 7200L;
        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("2", "часа")).thenReturn(java.util.Optional.of(banSeconds));
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(banSeconds, true)).thenReturn("2 часа");

            when(chatService.setDefaultBanTimePeriod(CHAT_ID, banSeconds)).thenReturn(banSeconds);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = banPeriodChangeCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(chatService).setDefaultBanTimePeriod(CHAT_ID, banSeconds);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            String expected = "✅Дефолтный срок бана был успешно установлен на 2 часа Теперь, при команде «" + BAN_COMMAND_NAME + "» без аргументов времени, я буду выдавать бан на этот период.";
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"2"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banPeriodChangeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("Вы ввели недостаточно аргументов для обработки этой команды.", captor.getValue().getText());
        verify(chatService, never()).disableDefaultBanTimePeriod(anyLong());
        verify(chatService, never()).setDefaultBanTimePeriod(anyLong(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimeFormat() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"invalid", "time"});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("invalid", "time")).thenReturn(java.util.Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = banPeriodChangeCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals("Указанный вами аргумент не является корректным временным периодом. Правильный пример: 2 минуты / 3 часа / 1 день", captor.getValue().getText());
            verify(chatService, never()).disableDefaultBanTimePeriod(anyLong());
            verify(chatService, never()).setDefaultBanTimePeriod(anyLong(), anyLong());
        }
    }

    // ==================== Обработка исключений ====================

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        RuntimeException runtimeException = new RuntimeException("Service error");
        doThrow(runtimeException).when(chatService).disableDefaultBanTimePeriod(CHAT_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        assertThrows(RuntimeException.class, () -> banPeriodChangeCommand.execute(commandMessage));
        verify(chatService).disableDefaultBanTimePeriod(CHAT_ID);
        verify(vkChatClient, never()).sendText(any());
    }
}
