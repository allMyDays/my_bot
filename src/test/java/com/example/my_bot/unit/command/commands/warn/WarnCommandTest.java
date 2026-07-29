package com.example.my_bot.unit.command.commands.warn;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.warn.UnwarnCommand;
import com.example.my_bot.command.commands.warn.WarnCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.event.DataForEventExecution;
import com.example.my_bot.dto.event.ExecuteChatEventsResult;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.dto.warn.CreateWarnResult;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.warn.WarnException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.WarnService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventExecutionService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.vk.api.sdk.objects.base.Error;

import java.time.Instant;
import java.util.Optional;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class WarnCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long USER_ID = 300L;
    private static final long BOT_GROUP_ID = 400L;
    private static final int VK_API_CHAT_ID = 12345;
    private static final String USER_NAME_NOMINATIVE = "Иван Иванов";
    private static final String MENTION_USER = "@id300";
    private static final String MENTION_FROM = "@id200";
    private static final long WARN_PERIOD_SEC = 3600L;
    private static final int NEW_WARN_QUANTITY = 2;
    private static final int MAX_WARN_QUANTITY = 5;
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private WarnService warnService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private EventExecutionService eventExecutionService;

    @Mock
    private MemberService memberService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private WarnCommand warnCommand;

    @BeforeEach
    void setUp() {
        warnCommand = new WarnCommand(
                messageMapper,
                chatService,
                warnService,
                userInputResolver,
                globalUserService,
                eventExecutionService,
                memberService,
                vkChatClient
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getVkApiChatId()).thenReturn((long) VK_API_CHAT_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(groupActor.getGroupId()).thenReturn(BOT_GROUP_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
        when(commandMessage.getConversationMessageId()).thenReturn(123);
        when(commandMessage.isEventOrTimerMode()).thenReturn(false);
    }

    @Test
    void shouldKickUserWhenWarnLimitReachedAndNoEvents() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE))
                .thenReturn(USER_NAME_NOMINATIVE);

        CreateWarnResult result = new CreateWarnResult()
                .setNewWarnQuantity(NEW_WARN_QUANTITY)
                .setMaxWarnQuantity(MAX_WARN_QUANTITY)
                .setWarnLimitReached(true)
                .setExpiresAt(null);
        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID)).thenReturn(result);

        ExecuteChatEventsResult eventsResult = mock(ExecuteChatEventsResult.class);
        when(eventsResult.getExecutedEventsCounter()).thenReturn(0);
        when(eventExecutionService.executeRequiredChatEvents(any(DataForEventExecution.class)))
                .thenReturn(eventsResult);

        when(memberService.isChatAdmin(CHAT_ID, USER_ID)).thenReturn(false);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(USER_ID)).thenReturn(MENTION_USER);

            doNothing().when(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);

            CommandExecutionStatus status = warnCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(vkChatClient).kickOneChatMember(commandRoutingData, USER_ID);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertTrue(actual.contains(MENTION_USER + "(" + USER_NAME_NOMINATIVE + ") достиг лимита предупреждений."));
        }
    }

    @Test
    void shouldNotKickAdminWhenWarnLimitReachedAndNoEvents() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE))
                .thenReturn(USER_NAME_NOMINATIVE);

        CreateWarnResult result = new CreateWarnResult()
                .setNewWarnQuantity(NEW_WARN_QUANTITY)
                .setMaxWarnQuantity(MAX_WARN_QUANTITY)
                .setWarnLimitReached(true)
                .setExpiresAt(null);
        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID)).thenReturn(result);

        ExecuteChatEventsResult eventsResult = mock(ExecuteChatEventsResult.class);
        when(eventsResult.getExecutedEventsCounter()).thenReturn(0);
        when(eventExecutionService.executeRequiredChatEvents(any(DataForEventExecution.class)))
                .thenReturn(eventsResult);

        when(memberService.isChatAdmin(CHAT_ID, USER_ID)).thenReturn(true);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(USER_ID)).thenReturn(MENTION_USER);

            CommandExecutionStatus status = warnCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(vkChatClient, never()).kickOneChatMember(any(), anyLong());
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldNotKickWhenEventsExecuted() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE))
                .thenReturn(USER_NAME_NOMINATIVE);

        CreateWarnResult result = new CreateWarnResult()
                .setNewWarnQuantity(NEW_WARN_QUANTITY)
                .setMaxWarnQuantity(MAX_WARN_QUANTITY)
                .setWarnLimitReached(true)
                .setExpiresAt(null);
        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID)).thenReturn(result);

        ExecuteChatEventsResult eventsResult = mock(ExecuteChatEventsResult.class);
        when(eventsResult.getExecutedEventsCounter()).thenReturn(2);
        when(eventExecutionService.executeRequiredChatEvents(any(DataForEventExecution.class)))
                .thenReturn(eventsResult);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(USER_ID)).thenReturn(MENTION_USER);

            CommandExecutionStatus status = warnCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(vkChatClient, never()).kickOneChatMember(any(), anyLong());
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenWarnBot() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@bot"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@bot"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(-BOT_GROUP_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        CommandExecutionStatus status = warnCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient, never()).sendText(any());
        verify(warnService, never()).createNewWarn(anyLong(), anyLong(), anyString(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        CommandException exception = new CommandException("Ошибка команды") {};
        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID)).thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = warnCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        MemberException exception = new MemberException("Участник не найден") {};
        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID)).thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = warnCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnWarnException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        WarnException exception = new WarnException("Ошибка предупреждения") {};
        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID)).thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = warnCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE))
                .thenReturn(USER_NAME_NOMINATIVE);

        CreateWarnResult result = new CreateWarnResult()
                .setNewWarnQuantity(NEW_WARN_QUANTITY)
                .setMaxWarnQuantity(MAX_WARN_QUANTITY)
                .setWarnLimitReached(false)
                .setExpiresAt(null);
        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID)).thenReturn(result);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> warnCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.NOMINATIVE))
                .thenReturn(USER_NAME_NOMINATIVE);

        CreateWarnResult result = new CreateWarnResult()
                .setNewWarnQuantity(NEW_WARN_QUANTITY)
                .setMaxWarnQuantity(MAX_WARN_QUANTITY)
                .setWarnLimitReached(false)
                .setExpiresAt(null);
        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID)).thenReturn(result);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> warnCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromWarnService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user"});
        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getDefaultWarnTimePeriod(CHAT_ID)).thenReturn(Optional.empty());

        when(warnService.createNewWarn(CHAT_ID, USER_ID, null, null, FROM_ID))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThrows(RuntimeException.class, () -> warnCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }
}