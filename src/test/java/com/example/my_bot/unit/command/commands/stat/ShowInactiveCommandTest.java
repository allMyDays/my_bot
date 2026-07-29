package com.example.my_bot.unit.command.commands.stat;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.stat.ShowInactiveCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ShowInactiveCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final String USER_NAME_1 = "Иван Иванов";
    private static final String USER_NAME_2 = "Петр Петров";
    private static final String MENTION_1 = "@id200";
    private static final String MENTION_2 = "@id201";
    private static final long DEFAULT_PERIOD = 86_400L;
    private static final long CUSTOM_PERIOD = 3600L;
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;
    private static final String FORMATTED_THRESHOLD = "01 января 2025, 12:00 GMT+3";
    private static final String FORMATTED_LAST_MSG_1 = "01 января 2025, 11:59 GMT+3";
    private static final String FORMATTED_DURATION = "1 минута";
    private static final String FORMATTED_PERIOD = "1 день";

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

    private ShowInactiveCommand showInactiveCommand;

    @BeforeEach
    void setUp() {
        showInactiveCommand = new ShowInactiveCommand(
                globalUserService,
                chatService,
                messageLogService,
                messageMapper
        );
        showInactiveCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }


    @Test
    void shouldShowInactiveWithDefaultPeriod() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(DEFAULT_PERIOD);
        Instant lastMsg1 = now.minusSeconds(100);
        Instant lastMsg2 = null;

        InactiveMemberDto member1 = new InactiveMemberDto(USER_ID_1, lastMsg1);
        InactiveMemberDto member2 = new InactiveMemberDto(USER_ID_2, lastMsg2);
        List<InactiveMemberDto> inactiveMembers = List.of(member1, member2);

        InactiveMembersResult result = new InactiveMembersResult();
        result.setThresholdDate(threshold);
        result.setInactiveMembers(inactiveMembers);
        result.setTotalInactiveQuantity(2);

        when(messageLogService.findCurrentInactiveChatMembers(
                eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(true), isNull(), isNull()))
                .thenReturn(result);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1, USER_ID_2, USER_NAME_2);
        when(globalUserService.getUserFullNamesInRequiredCase(
                Set.of(USER_ID_1, USER_ID_2), NameCase.NOMINATIVE))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class);
             MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {

            textUtilsMock.when(() -> TextUtils.createMention(USER_ID_1)).thenReturn(MENTION_1);
            textUtilsMock.when(() -> TextUtils.createMention(USER_ID_2)).thenReturn(MENTION_2);

            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(threshold, TIME_ZONE))
                    .thenReturn(FORMATTED_THRESHOLD);
            timeUtilsMock.when(() -> TimeUtils.formatDurationFromSeconds(DEFAULT_PERIOD, false))
                    .thenReturn(FORMATTED_PERIOD);
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTime(lastMsg1, TIME_ZONE))
                    .thenReturn(FORMATTED_LAST_MSG_1);
            timeUtilsMock.when(() -> TimeUtils.formatDurationFromSeconds(Duration.between(lastMsg1, now).getSeconds(), false))
                    .thenReturn(FORMATTED_DURATION);

            // when
            CommandExecutionStatus status = showInactiveCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(messageLogService).findCurrentInactiveChatMembers(CHAT_ID, DEFAULT_PERIOD, true, null, null);
            verify(globalUserService).getUserFullNamesInRequiredCase(Set.of(USER_ID_1, USER_ID_2), NameCase.NOMINATIVE);
            verify(chatService).getChatTimeZone(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();

            assertTrue(actual.contains("&#128270; Было найдено 2 участников, которые не писали сообщения 1 день и более (то есть после " + FORMATTED_THRESHOLD + ")"));
            assertTrue(actual.contains("Участники и время их последнего сообщения по " + TIME_ZONE.getStringType() + ":"));
            assertTrue(actual.contains("2. " + MENTION_2 + "(Петр Петров) — ни одного сообщения за срок."));
        }
    }

    @Test
    void shouldShowInactiveWithCustomPeriod() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "hour"});

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {
            timeUtilsMock.when(() -> TimeUtils.toSecondsFromString("1", "hour"))
                    .thenReturn(Optional.of(CUSTOM_PERIOD));

            Instant now = Instant.now();
            Instant threshold = now.minusSeconds(CUSTOM_PERIOD);
            Instant lastMsg = now.minusSeconds(30);

            InactiveMemberDto member = new InactiveMemberDto(USER_ID_1, lastMsg);
            InactiveMembersResult result = new InactiveMembersResult();
            result.setThresholdDate(threshold);
            result.setInactiveMembers(List.of(member));
            result.setTotalInactiveQuantity(1);

            when(messageLogService.findCurrentInactiveChatMembers(
                    eq(CHAT_ID), eq(CUSTOM_PERIOD), eq(true), isNull(), isNull()))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
            when(globalUserService.getUserFullNamesInRequiredCase(Set.of(USER_ID_1), NameCase.NOMINATIVE))
                    .thenReturn(namesMap);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(threshold, TIME_ZONE))
                    .thenReturn(FORMATTED_THRESHOLD);
            timeUtilsMock.when(() -> TimeUtils.formatDurationFromSeconds(CUSTOM_PERIOD, false))
                    .thenReturn("1 час");
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTime(lastMsg, TIME_ZONE))
                    .thenReturn(FORMATTED_LAST_MSG_1);
            timeUtilsMock.when(() -> TimeUtils.formatDurationFromSeconds(Duration.between(lastMsg, now).getSeconds(), false))
                    .thenReturn("30 секунд");

            try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
                textUtilsMock.when(() -> TextUtils.createMention(USER_ID_1)).thenReturn(MENTION_1);

                CommandExecutionStatus status = showInactiveCommand.execute(commandMessage);

                assertEquals(CommandExecutionStatus.SUCCESS, status);
                verify(messageLogService).findCurrentInactiveChatMembers(CHAT_ID, CUSTOM_PERIOD, true, null, null);
                verify(vkChatClient).sendText(sendMessageDto);

                ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
                verify(vkChatClient).sendText(captor.capture());
                String actual = captor.getValue().getText();
                System.out.println(actual);
                assertTrue(actual.contains("Было найдено 1 участников, которые не писали сообщения 1 час и более"));
            }
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimePeriod() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"invalid", "period"});

        try (MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {
            timeUtilsMock.when(() -> TimeUtils.toSecondsFromString("invalid", "period"))
                    .thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = showInactiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным временным периодом. Правильный пример: 2 минуты / 3 часа / 1 день"));
            verify(messageLogService, never()).findCurrentInactiveChatMembers(anyLong(), anyLong(), anyBoolean(), any(), any());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        MemberException exception = new MemberException("Ошибка поиска участников") {};
        when(messageLogService.findCurrentInactiveChatMembers(
                eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(true), isNull(), isNull()))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = showInactiveCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
        verify(globalUserService, never()).getUserFullNamesInRequiredCase(anySet(), any());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMessageException() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        MessageException exception = new MessageException("Ошибка логов") {};
        when(messageLogService.findCurrentInactiveChatMembers(
                eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(true), isNull(), isNull()))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = showInactiveCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(DEFAULT_PERIOD);
        InactiveMembersResult result = new InactiveMembersResult();
        result.setThresholdDate(threshold);
        result.setInactiveMembers(List.of());
        result.setTotalInactiveQuantity(0);

        when(messageLogService.findCurrentInactiveChatMembers(
                eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(true), isNull(), isNull()))
                .thenReturn(result);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ClientException.class, () -> showInactiveCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(DEFAULT_PERIOD);
        InactiveMembersResult result = new InactiveMembersResult();
        result.setThresholdDate(threshold);
        result.setInactiveMembers(List.of());
        result.setTotalInactiveQuantity(0);

        when(messageLogService.findCurrentInactiveChatMembers(
                eq(CHAT_ID), eq(DEFAULT_PERIOD), eq(true), isNull(), isNull()))
                .thenReturn(result);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ApiException.class, () -> showInactiveCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}