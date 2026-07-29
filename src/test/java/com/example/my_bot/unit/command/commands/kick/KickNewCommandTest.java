package com.example.my_bot.unit.command.commands.kick;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.kick.*;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.message.MessageException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.MessageLogService;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KickNewCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final int MODERATOR_PRIORITY = DefaultRole.MODERATOR.getRolePriority();
    private static final int LIMIT = 100;
    private static final int MAX_PERIOD = 86_400;
    private static final String PERIOD_STRING = "12";
    private static final String PERIOD_UNIT = "часов";
    private static final long PERIOD_SECONDS = 43200L;
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MemberService memberService;

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

    private KickNewCommand kickNewCommand;

    @BeforeEach
    void setUp() {
        kickNewCommand = new KickNewCommand(memberService, chatService, messageMapper);
        kickNewCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"12"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickNewCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Недостаточно аргументов") || actual.contains("аргумент"));
        verify(memberService, never()).getNotKickedNewMembersWithRoleLessThan(anyLong(), any(), anyInt(), anyInt());
        verify(vkChatClient, never()).kickManyChatMembers(any(), any());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimePeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"invalid", PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("invalid", PERIOD_UNIT))
                    .thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = kickNewCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            verify(memberService, never()).getNotKickedNewMembersWithRoleLessThan(anyLong(), any(), anyInt(), anyInt());
            verify(vkChatClient, never()).kickManyChatMembers(any(), any());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenPeriodExceedsMax() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"100", "дней"}); // 100 дней > 86 400 секунд (1 день)

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            long period = 8_640_000L; // 100 дней
            timeUtils.when(() -> TimeUtils.toSecondsFromString("100", "дней"))
                    .thenReturn(Optional.of(period));
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(MAX_PERIOD, false))
                    .thenReturn("1 день");

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = kickNewCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertTrue(actual.contains("Максимальный период, за который можно исключить новичков — 1 день"));
            verify(memberService, never()).getNotKickedNewMembersWithRoleLessThan(anyLong(), any(), anyInt(), anyInt());
            verify(vkChatClient, never()).kickManyChatMembers(any(), any());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            when(memberService.getNotKickedNewMembersWithRoleLessThan(
                    eq(CHAT_ID), any(Instant.class), eq(MODERATOR_PRIORITY), eq(LIMIT)))
                    .thenThrow(new RuntimeException("DB error"));

            assertThrows(RuntimeException.class, () -> kickNewCommand.execute(commandMessage));
            verify(vkChatClient, never()).kickManyChatMembers(any(), any());
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromKickMany() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            Instant kickAfter = Instant.now().minusSeconds(PERIOD_SECONDS);
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(kickAfter, TIME_ZONE))
                    .thenReturn("01 января 2025, 00:00 GMT+3");

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            MemberEntity member = createMember(USER_ID_1, false);
            Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
            when(memberService.getNotKickedNewMembersWithRoleLessThan(
                    eq(CHAT_ID), any(Instant.class), eq(MODERATOR_PRIORITY), eq(LIMIT)))
                    .thenReturn(page);

            ClientException clientException = new ClientException("VK client error");
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                    .thenThrow(clientException);

            assertThrows(ClientException.class, () -> kickNewCommand.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateApiExceptionFromKickMany() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            Instant kickAfter = Instant.now().minusSeconds(PERIOD_SECONDS);
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(kickAfter, TIME_ZONE))
                    .thenReturn("01 января 2025, 00:00 GMT+3");

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            MemberEntity member = createMember(USER_ID_1, false);
            Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
            when(memberService.getNotKickedNewMembersWithRoleLessThan(
                    eq(CHAT_ID), any(Instant.class), eq(MODERATOR_PRIORITY), eq(LIMIT)))
                    .thenReturn(page);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                    .thenThrow(apiException);

            assertThrows(ApiException.class, () -> kickNewCommand.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            Instant kickAfter = Instant.now().minusSeconds(PERIOD_SECONDS);
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(kickAfter, TIME_ZONE))
                    .thenReturn("01 января 2025, 00:00 GMT+3");

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            MemberEntity member = createMember(USER_ID_1, false);
            Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
            when(memberService.getNotKickedNewMembersWithRoleLessThan(
                    eq(CHAT_ID), any(Instant.class), eq(MODERATOR_PRIORITY), eq(LIMIT)))
                    .thenReturn(page);

            Set<Long> kickedIds = Set.of(USER_ID_1);
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                    .thenReturn(kickedIds);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK send error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ClientException.class, () -> kickNewCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            Instant kickAfter = Instant.now().minusSeconds(PERIOD_SECONDS);
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(kickAfter, TIME_ZONE))
                    .thenReturn("01 января 2025, 00:00 GMT+3");

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            MemberEntity member = createMember(USER_ID_1, false);
            Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
            when(memberService.getNotKickedNewMembersWithRoleLessThan(
                    eq(CHAT_ID), any(Instant.class), eq(MODERATOR_PRIORITY), eq(LIMIT)))
                    .thenReturn(page);

            Set<Long> kickedIds = Set.of(USER_ID_1);
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                    .thenReturn(kickedIds);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> kickNewCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }


    private MemberEntity createMember(long userId, boolean isChatAdmin) {
        MemberEntity member = new MemberEntity();
        member.setUserId(userId);
        member.setChatAdmin(isChatAdmin);
        return member;
    }
}
