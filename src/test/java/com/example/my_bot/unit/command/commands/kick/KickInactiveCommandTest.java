package com.example.my_bot.unit.command.commands.kick;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.kick.KickCommand;
import com.example.my_bot.command.commands.kick.KickCommunitiesCommand;
import com.example.my_bot.command.commands.kick.KickFromCommand;
import com.example.my_bot.command.commands.kick.KickInactiveCommand;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KickInactiveCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final int MODERATOR_PRIORITY = DefaultRole.MODERATOR.getRolePriority();
    private static final int LIMIT = 100;
    private static final String PERIOD_STRING = "7";
    private static final String PERIOD_UNIT = "дней";
    private static final long PERIOD_SECONDS = 604800L;
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private MessageLogService messageLogService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private KickInactiveCommand kickInactiveCommand;

    @BeforeEach
    void setUp() {
        kickInactiveCommand = new KickInactiveCommand(
                chatService,
                messageMapper,
                messageLogService
        );
        kickInactiveCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldKickInactiveMembersSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            Instant thresholdDate = Instant.now().minusSeconds(PERIOD_SECONDS);
            InactiveMembersResult result = new InactiveMembersResult();
            result.setThresholdDate(thresholdDate);
            result.setTotalInactiveQuantity(2);
            result.addNewInactiveMember(USER_ID_1, thresholdDate.minusSeconds(100));
            result.addNewInactiveMember(USER_ID_2, thresholdDate.minusSeconds(200));

            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            String formattedDate = "07 мая 2025, 12:00 GMT+3";
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(thresholdDate, TIME_ZONE))
                    .thenReturn(formattedDate);

            Set<Long> kickedIds = Set.of(USER_ID_1, USER_ID_2);
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1, USER_ID_2))))
                    .thenReturn(kickedIds);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = kickInactiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(messageLogService).findCurrentInactiveChatMembers(CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT);
            verify(chatService).getChatTimeZone(CHAT_ID);
            verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of(USER_ID_1, USER_ID_2));
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            String expected = "✅Было исключено 2 из 2 участников с ролью ниже чем «Модератор», которые не писали сообщения после " + formattedDate;
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldKickInactiveMembersWhenSomeNotKicked() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            Instant thresholdDate = Instant.now().minusSeconds(PERIOD_SECONDS);
            InactiveMembersResult result = new InactiveMembersResult();
            result.setThresholdDate(thresholdDate);
            result.setTotalInactiveQuantity(2);
            result.addNewInactiveMember(USER_ID_1, thresholdDate.minusSeconds(100));
            result.addNewInactiveMember(USER_ID_2, thresholdDate.minusSeconds(200));

            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

            String formattedDate = "07 мая 2025, 12:00 GMT+3";
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(thresholdDate, TIME_ZONE))
                    .thenReturn(formattedDate);

            Set<Long> kickedIds = Set.of(USER_ID_1);
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1, USER_ID_2))))
                    .thenReturn(kickedIds);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = kickInactiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅Было исключено 1 из 2 участников с ролью ниже чем «Модератор», которые не писали сообщения после " + formattedDate, actual);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"7"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickInactiveCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(messageLogService, never()).findCurrentInactiveChatMembers(anyLong(), anyLong(), anyBoolean(), anyInt(), anyInt());
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

            CommandExecutionStatus status = kickInactiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            verify(messageLogService, never()).findCurrentInactiveChatMembers(anyLong(), anyLong(), anyBoolean(), anyInt(), anyInt());
            verify(vkChatClient, never()).kickManyChatMembers(any(), any());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            MemberException exception = new MemberException("Ошибка поиска участников") {};
            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = kickInactiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
            verify(vkChatClient, never()).kickManyChatMembers(any(), any());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMessageException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            MessageException exception = new MessageException("Ошибка логов") {};
            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = kickInactiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
            verify(vkChatClient, never()).kickManyChatMembers(any(), any());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{PERIOD_STRING, PERIOD_UNIT});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString(PERIOD_STRING, PERIOD_UNIT))
                    .thenReturn(Optional.of(PERIOD_SECONDS));

            Instant thresholdDate = Instant.now().minusSeconds(PERIOD_SECONDS);
            InactiveMembersResult result = new InactiveMembersResult();
            result.setThresholdDate(thresholdDate);
            result.setTotalInactiveQuantity(1);
            result.addNewInactiveMember(USER_ID_1, thresholdDate.minusSeconds(100));

            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenThrow(new RuntimeException("Chat service error"));

            assertThrows(RuntimeException.class, () -> kickInactiveCommand.execute(commandMessage));
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

            Instant thresholdDate = Instant.now().minusSeconds(PERIOD_SECONDS);
            InactiveMembersResult result = new InactiveMembersResult();
            result.setThresholdDate(thresholdDate);
            result.setTotalInactiveQuantity(1);
            result.addNewInactiveMember(USER_ID_1, thresholdDate.minusSeconds(100));

            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(thresholdDate, TIME_ZONE))
                    .thenReturn("07 мая 2025, 12:00 GMT+3");

            ClientException clientException = new ClientException("VK client error");
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                    .thenThrow(clientException);

            assertThrows(ClientException.class, () -> kickInactiveCommand.execute(commandMessage));
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

            Instant thresholdDate = Instant.now().minusSeconds(PERIOD_SECONDS);
            InactiveMembersResult result = new InactiveMembersResult();
            result.setThresholdDate(thresholdDate);
            result.setTotalInactiveQuantity(1);
            result.addNewInactiveMember(USER_ID_1, thresholdDate.minusSeconds(100));

            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(thresholdDate, TIME_ZONE))
                    .thenReturn("07 мая 2025, 12:00 GMT+3");

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                    .thenThrow(apiException);

            assertThrows(ApiException.class, () -> kickInactiveCommand.execute(commandMessage));
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

            Instant thresholdDate = Instant.now().minusSeconds(PERIOD_SECONDS);
            InactiveMembersResult result = new InactiveMembersResult();
            result.setThresholdDate(thresholdDate);
            result.setTotalInactiveQuantity(1);
            result.addNewInactiveMember(USER_ID_1, thresholdDate.minusSeconds(100));

            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(thresholdDate, TIME_ZONE))
                    .thenReturn("07 мая 2025, 12:00 GMT+3");

            Set<Long> kickedIds = Set.of(USER_ID_1);
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                    .thenReturn(kickedIds);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK send error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ClientException.class, () -> kickInactiveCommand.execute(commandMessage));
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

            Instant thresholdDate = Instant.now().minusSeconds(PERIOD_SECONDS);
            InactiveMembersResult result = new InactiveMembersResult();
            result.setThresholdDate(thresholdDate);
            result.setTotalInactiveQuantity(1);
            result.addNewInactiveMember(USER_ID_1, thresholdDate.minusSeconds(100));

            when(messageLogService.findCurrentInactiveChatMembers(
                    CHAT_ID, PERIOD_SECONDS, false, MODERATOR_PRIORITY, LIMIT))
                    .thenReturn(result);

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
            timeUtils.when(() -> TimeUtils.getFormattedStringDateTimeWithTimeZone(thresholdDate, TIME_ZONE))
                    .thenReturn("07 мая 2025, 12:00 GMT+3");

            Set<Long> kickedIds = Set.of(USER_ID_1);
            when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                    .thenReturn(kickedIds);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> kickInactiveCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }
}