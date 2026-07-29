package com.example.my_bot.unit.command.commands.ban;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.ban.BanCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.ban.BanException;
import com.example.my_bot.exception.ban.BanPeriodOutOfBoundsException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import com.vk.api.sdk.objects.base.ErrorInnerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BanCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID = 200L;
    private static final long EXECUTOR_ID = 300L;
    private static final long BOT_GROUP_ID = 400L;
    private static final String USER_NAME = "Иван Иванов";
    private static final String EXECUTOR_NAME = "Петр Петров";

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private ChatService chatService;

    @Mock
    private BanService banService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    @InjectMocks
    private BanCommand banCommand;

    @BeforeEach
    void setUp() {
        banCommand = new BanCommand(messageMapper, chatService, banService, userInputResolver, globalUserService);
        banCommand.setVkChatClient(vkChatClient);
    }

    @Test
    void shouldBanUserPermanentlySuccess() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@durov"});
        when(commandMessage.isEventOrTimerMode()).thenReturn(false);

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);
        when(chatService.getDefaultBanTimePeriod(CHAT_ID)).thenReturn(Optional.empty());
        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE)).thenReturn(USER_NAME);
        when(globalUserService.getUserFullNameInRequiredCase(EXECUTOR_ID, NameCase.NOMINATIVE)).thenReturn(EXECUTOR_NAME);
        when(banService.createMemberBan(CHAT_ID, USER_ID, null, null, EXECUTOR_ID)).thenReturn(Optional.empty());
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(banService).createMemberBan(CHAT_ID, USER_ID, null, null, EXECUTOR_ID);
        verify(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);
        verify(chatService).getChatTimeZone(CHAT_ID);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient, times(1)).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("✅ @id" + USER_ID + "(" + USER_NAME + ")"));
        assertTrue(actual.contains("навечно."));
        assertTrue(actual.contains("Модератор: @id" + EXECUTOR_ID + "(" + EXECUTOR_NAME + ")"));
    }

    @Test
    void shouldBanUserTemporarilySuccess() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov", "2", "часа"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@durov", "2", "часа"});
        when(commandMessage.isEventOrTimerMode()).thenReturn(false);

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        long banSeconds = 7200L;
        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("2", "часа")).thenReturn(Optional.of(banSeconds));

            when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE)).thenReturn(USER_NAME);
            when(globalUserService.getUserFullNameInRequiredCase(EXECUTOR_ID, NameCase.NOMINATIVE)).thenReturn(EXECUTOR_NAME);

            Instant bannedUntil = Instant.now().plusSeconds(banSeconds);
            when(banService.createMemberBan(CHAT_ID, USER_ID, "2", banSeconds, EXECUTOR_ID))
                    .thenReturn(Optional.of(bannedUntil));

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = banCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(banService).createMemberBan(CHAT_ID, USER_ID, "2", banSeconds, EXECUTOR_ID);
            verify(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient, times(1)).sendText(captor.capture());
            String actual = captor.getValue().getText();

            assertTrue(actual.contains("✅ @id" + USER_ID + "(" + USER_NAME + ")"));
            assertTrue(actual.contains("был забанен в чате до "));
            assertTrue(actual.contains("Модератор: @id" + EXECUTOR_ID + "(" + EXECUTOR_NAME + ")"));
        }
    }

    @Test
    void shouldBanWithFwdMessageAndTimeSuccess() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"2", "часа"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"2", "часа"});
        when(commandMessage.isEventOrTimerMode()).thenReturn(false);

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, true);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        long banSeconds = 7200L;
        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("2", "часа")).thenReturn(Optional.of(banSeconds));

            when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE)).thenReturn(USER_NAME);
            when(globalUserService.getUserFullNameInRequiredCase(EXECUTOR_ID, NameCase.NOMINATIVE)).thenReturn(EXECUTOR_NAME);

            Instant bannedUntil = Instant.now().plusSeconds(banSeconds);
            when(banService.createMemberBan(CHAT_ID, USER_ID, "часа", banSeconds, EXECUTOR_ID))
                    .thenReturn(Optional.of(bannedUntil));

            when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = banCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoMemberArgument() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(commandMessage.getAllRows()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        assertNotNull(sendMessageDto.getText());
        assertFalse(sendMessageDto.getText().isEmpty());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArgsForTempBan() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov", "2"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@durov", "2"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimeFormat() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov", "неправильное", "время"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@durov", "неправильное", "время"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("неправильное", "время")).thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = banCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenBanningBot() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@bot"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@bot"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(-BOT_GROUP_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(groupActor.getGroupId()).thenReturn(BOT_GROUP_ID);

        CommandExecutionStatus status = banCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient, never()).sendText(any());
        verify(banService, never()).createMemberBan(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnBanServiceException() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@durov"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);
        when(chatService.getDefaultBanTimePeriod(CHAT_ID)).thenReturn(Optional.empty());
        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE)).thenReturn(USER_NAME);

        BanException banException = new BanPeriodOutOfBoundsException(3600);
        when(banService.createMemberBan(CHAT_ID, USER_ID, null, null, EXECUTOR_ID))
                .thenThrow(banException);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        assertEquals(banException.getMessage(), sendMessageDto.getText());
        verify(vkChatClient, never()).kickOneChatMember(any(), anyLong());
    }

    @Test
    void shouldHandleApiExceptionWithUserNotFoundInChatCode() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@durov"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);
        when(chatService.getDefaultBanTimePeriod(CHAT_ID)).thenReturn(Optional.empty());
        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE)).thenReturn(USER_NAME);
        when(banService.createMemberBan(CHAT_ID, USER_ID, null, null, EXECUTOR_ID))
                .thenReturn(Optional.empty());

        int notFoundCode = 100;
        Error error = new Error().setErrorCode(notFoundCode).setErrorMsg("User not found");
        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient, times(1)).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("забанен, но его не удалось исключить из чата"));
    }

    @Test
    void shouldReturnSuccessWhenApiExceptionWithOtherCode() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@durov"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);
        when(chatService.getDefaultBanTimePeriod(CHAT_ID)).thenReturn(Optional.empty());
        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE)).thenReturn(USER_NAME);
        when(banService.createMemberBan(CHAT_ID, USER_ID, null, null, EXECUTOR_ID))
                .thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        int otherCode = 500;
        Error error = new Error().setErrorCode(otherCode).setErrorMsg("Other error");
        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);

        CommandExecutionStatus status = banCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);
        verify(banService).createMemberBan(CHAT_ID, USER_ID, null, null, EXECUTOR_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        assertTrue(sendMessageDto.getText().contains("забанен, но его не удалось исключить из чата"));
    }

    private void mockCommonCommandData() {
        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(EXECUTOR_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(groupActor.getGroupId()).thenReturn(BOT_GROUP_ID);
    }
}