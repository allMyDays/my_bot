package com.example.my_bot.unit.command.commands.stat;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.stat.ShowAllMembersStatisticCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.stat.ChatMembersStatisticResult;
import com.example.my_bot.dto.member.stat.MemberStatisticDto;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.message.MessageException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TextUtils;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShowAllMembersStatisticCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final String USER_NAME_1 = "Иван Иванов";
    private static final String USER_NAME_2 = "Петр Петров";
    private static final String MENTION_1 = "@id200";
    private static final String MENTION_2 = "@id201";
    private static final int MEMBER_LIMIT = 50;
    private static final long DEFAULT_PERIOD = 86_400L;
    private static final long CUSTOM_PERIOD = 7200L;
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;
    private static final String FORMATTED_START = "01 января 2025, 00:00 GMT+3";
    private static final String FORMATTED_END = "02 января 2025, 00:00 GMT+3";
    private static final String FORMATTED_DURATION = "1 день";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageLogService messageLogService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private ShowAllMembersStatisticCommand statisticCommand;

    @BeforeEach
    void setUp() {
        statisticCommand = new ShowAllMembersStatisticCommand(
                globalUserService,
                chatService,
                messageLogService,
                messageMapper
        );
        statisticCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldShowStatisticWithDefaultPeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        Instant start = Instant.now().minusSeconds(DEFAULT_PERIOD);
        Instant end = Instant.now();
        ChatMembersStatisticResult result = createStatResult(start, end, List.of(
                createMemberStat(USER_ID_1, 100, 500),
                createMemberStat(USER_ID_2, 50, 500)
        ), 2, 150, 1000);

        when(messageLogService.getAllChatMembersStatForATimePeriod(eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(MEMBER_LIMIT)))
                .thenReturn(result);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1, USER_ID_2, USER_NAME_2);
        when(globalUserService.getUserFullNamesInRequiredCase(Set.of(USER_ID_1, USER_ID_2), NameCase.NOMINATIVE))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class);
             MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {

            textUtilsMock.when(() -> TextUtils.createMention(USER_ID_1)).thenReturn(MENTION_1);
            textUtilsMock.when(() -> TextUtils.createMention(USER_ID_2)).thenReturn(MENTION_2);

            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTime(start, TIME_ZONE))
                    .thenReturn(FORMATTED_START);
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(end, TIME_ZONE))
                    .thenReturn(FORMATTED_END);
            timeUtilsMock.when(() -> TimeUtils.formatDurationFromSeconds(DEFAULT_PERIOD, false))
                    .thenReturn(FORMATTED_DURATION);

            CommandExecutionStatus status = statisticCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(messageLogService).getAllChatMembersStatForATimePeriod(CHAT_ID, DEFAULT_PERIOD, MEMBER_LIMIT);
            verify(globalUserService).getUserFullNamesInRequiredCase(Set.of(USER_ID_1, USER_ID_2), NameCase.NOMINATIVE);
            verify(chatService).getChatTimeZone(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();

            assertTrue(actual.contains("📊 Статистика чата за период 1 день"));
            assertTrue(actual.contains("с " + FORMATTED_START));
            assertTrue(actual.contains("до " + FORMATTED_END));
            assertTrue(actual.contains("[ ✉ сообщения | 🔣 символы ]:"));
            assertTrue(actual.contains("1. " + MENTION_1 + "(Иван Иванов) — 100 | 500"));
            assertTrue(actual.contains("2. " + MENTION_2 + "(Петр Петров) — 50 | 500"));
            assertTrue(actual.contains("Всего сообщений: 150"));
            assertTrue(actual.contains("Всего символов: 1000"));
        }
    }

    @Test
    void shouldShowStatisticWithCustomPeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"2", "hours"});

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {
            timeUtilsMock.when(() -> TimeUtils.toSecondsFromString("2", "hours"))
                    .thenReturn(Optional.of(CUSTOM_PERIOD));

            Instant start = Instant.now().minusSeconds(CUSTOM_PERIOD);
            Instant end = Instant.now();
            ChatMembersStatisticResult result = createStatResult(start, end, List.of(
                    createMemberStat(USER_ID_1, 10, 50)
            ), 1, 10, 50);

            when(messageLogService.getAllChatMembersStatForATimePeriod(eq(CHAT_ID), eq(CUSTOM_PERIOD), eq(MEMBER_LIMIT)))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
            when(globalUserService.getUserFullNamesInRequiredCase(Set.of(USER_ID_1), NameCase.NOMINATIVE))
                    .thenReturn(namesMap);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTime(start, TIME_ZONE))
                    .thenReturn(FORMATTED_START);
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(end, TIME_ZONE))
                    .thenReturn(FORMATTED_END);
            timeUtilsMock.when(() -> TimeUtils.formatDurationFromSeconds(CUSTOM_PERIOD, false))
                    .thenReturn("2 часа");

            try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
                textUtilsMock.when(() -> TextUtils.createMention(USER_ID_1)).thenReturn(MENTION_1);

                CommandExecutionStatus status = statisticCommand.execute(commandMessage);

                assertEquals(CommandExecutionStatus.SUCCESS, status);
                verify(messageLogService).getAllChatMembersStatForATimePeriod(CHAT_ID, CUSTOM_PERIOD, MEMBER_LIMIT);
                verify(vkChatClient).sendText(sendMessageDto);

                ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
                verify(vkChatClient).sendText(captor.capture());
                String actual = captor.getValue().getText();
                assertTrue(actual.contains("Статистика чата за период 2 часа"));
                assertTrue(actual.contains("1. " + MENTION_1 + "(Иван Иванов) — 10 | 50"));
            }
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidPeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"invalid", "period"});

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {
            timeUtilsMock.when(() -> TimeUtils.toSecondsFromString("invalid", "period"))
                    .thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = statisticCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным временным периодом. Правильный пример: 2 минуты / 3 часа / 1 день"));
            verify(messageLogService, never()).getAllChatMembersStatForATimePeriod(anyLong(), anyLong(), anyInt());
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidSingleDate() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"invalid-date"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = statisticCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Введён некорректный аргумент даты. Пример: 01.01.2027"));
        verify(messageLogService, never()).getAllChatMembersStatForATimePeriod(anyLong(), any(), any(), anyInt());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidDateRange() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"01.01.2025-"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = statisticCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("должен иметь вид 01.11.2025-09.11.2025"));
        verify(messageLogService, never()).getAllChatMembersStatForATimePeriod(anyLong(), any(), any(), anyInt());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        MemberException exception = new MemberException("Ошибка участников") {};
        when(messageLogService.getAllChatMembersStatForATimePeriod(eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(MEMBER_LIMIT)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = statisticCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
        verify(globalUserService, never()).getUserFullNamesInRequiredCase(anySet(), any());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMessageException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        MessageException exception = new MessageException("Ошибка сообщений") {};
        when(messageLogService.getAllChatMembersStatForATimePeriod(eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(MEMBER_LIMIT)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = statisticCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        Instant start = Instant.now().minusSeconds(DEFAULT_PERIOD);
        Instant end = Instant.now();
        ChatMembersStatisticResult result = createStatResult(start, end, List.of(), 0, 0, 0);

        when(messageLogService.getAllChatMembersStatForATimePeriod(eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(MEMBER_LIMIT)))
                .thenReturn(result);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> statisticCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        Instant start = Instant.now().minusSeconds(DEFAULT_PERIOD);
        Instant end = Instant.now();
        ChatMembersStatisticResult result = createStatResult(start, end, List.of(), 0, 0, 0);

        when(messageLogService.getAllChatMembersStatForATimePeriod(eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(MEMBER_LIMIT)))
                .thenReturn(result);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> statisticCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    private ChatMembersStatisticResult createStatResult(Instant start, Instant end,
                                                        List<MemberStatisticDto> members,
                                                        int totalMembers, int totalMessages, int totalSymbols) {
        ChatMembersStatisticResult result = new ChatMembersStatisticResult();
        result.setStart(start);
        result.setEnd(end);
        result.setMemberStatisticDtoList(members);
        result.setTotalMembersQuantity(totalMembers);
        result.setTotalMessageQuantity(totalMessages);
        result.setTotalSymbolsQuantity(totalSymbols);
        return result;
    }

    private MemberStatisticDto createMemberStat(long userId, int messages, int symbols) {
        MemberStatisticDto dto = new MemberStatisticDto();
        dto.setUserId(userId);
        dto.setTotalMessages(messages);
        dto.setTotalSymbols(symbols);
        return dto;
    }
}
