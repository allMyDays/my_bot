package com.example.my_bot.unit.command.commands.warn;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.warn.UnwarnCommand;
import com.example.my_bot.command.commands.warn.WarnCommand;
import com.example.my_bot.command.commands.warn.WarnLimitChangeCommand;
import com.example.my_bot.command.commands.warn.WarnListShowCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.event.DataForEventExecution;
import com.example.my_bot.dto.event.ExecuteChatEventsResult;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.dto.warn.CreateWarnResult;
import com.example.my_bot.entity.WarnEntity;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class WarnListShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID = 200L;
    private static final long MODERATOR_ID_1 = 300L;
    private static final long MODERATOR_ID_2 = 301L;
    private static final String USER_NAME_GENITIVE = "Ивана Иванова";
    private static final String MODERATOR_NAME_1 = "Петр Петров";
    private static final String MODERATOR_NAME_2 = "Сергей Сергеев";
    private static final String MENTION_USER = "@id200";
    private static final String MENTION_MOD_1 = "@id300";
    private static final String MENTION_MOD_2 = "@id301";
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private WarnService warnService;

    @Mock
    private ChatService chatService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private WarnListShowCommand warnListShowCommand;

    @BeforeEach
    void setUp() {
        warnListShowCommand = new WarnListShowCommand(
                vkChatClient,
                messageMapper,
                globalUserService,
                warnService,
                chatService,
                userInputResolver
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldShowWarningsSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        Instant now = Instant.now();
        Instant expiresAt1 = now.plusSeconds(3600);
        Instant expiresAt2 = null;

        WarnEntity warn1 = new WarnEntity();
        warn1.setId(1L);
        warn1.setGivenBy(MODERATOR_ID_1);
        warn1.setCreatedAt(now);
        warn1.setExpiresAt(expiresAt1);
        warn1.setReason("Флуд");

        WarnEntity warn2 = new WarnEntity();
        warn2.setId(2L);
        warn2.setGivenBy(MODERATOR_ID_2);
        warn2.setCreatedAt(now.minusSeconds(60));
        warn2.setExpiresAt(expiresAt2);
        warn2.setReason(null);

        List<WarnEntity> warnings = List.of(warn1, warn2);
        when(warnService.getMemberWarningsSortedInDesc(CHAT_ID, USER_ID)).thenReturn(warnings);

        Map<Long, String> givenByMap = Map.of(
                MODERATOR_ID_1, MODERATOR_NAME_1,
                MODERATOR_ID_2, MODERATOR_NAME_2
        );
        when(globalUserService.getUserFullNamesInRequiredCase(Set.of(MODERATOR_ID_1, MODERATOR_ID_2), NameCase.NOMINATIVE))
                .thenReturn(givenByMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class);
             MockedStatic<TimeUtils> timeUtilsMock = mockStatic(TimeUtils.class)) {

            textUtilsMock.when(() -> TextUtils.createMention(USER_ID)).thenReturn(MENTION_USER);
            textUtilsMock.when(() -> TextUtils.createMention(MODERATOR_ID_1)).thenReturn(MENTION_MOD_1);
            textUtilsMock.when(() -> TextUtils.createMention(MODERATOR_ID_2)).thenReturn(MENTION_MOD_2);

            String formattedDate1 = "01 января 2025, 12:00 GMT+3";
            String formattedDate2 = "01 января 2025, 11:59 GMT+3";
            String formattedExpires1 = "01 января 2025, 13:00 GMT+3";
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTime(now, TIME_ZONE)).thenReturn(formattedDate1);
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTime(now.minusSeconds(60), TIME_ZONE)).thenReturn(formattedDate2);
            timeUtilsMock.when(() -> TimeUtils.getFormattedStringDateTime(expiresAt1, TIME_ZONE)).thenReturn(formattedExpires1);

            CommandExecutionStatus status = warnListShowCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(warnService).getMemberWarningsSortedInDesc(CHAT_ID, USER_ID);
            verify(globalUserService).getUserFullNamesInRequiredCase(Set.of(MODERATOR_ID_1, MODERATOR_ID_2), NameCase.NOMINATIVE);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            String expected = "Предупреждения @id200(Ивана Иванова):\n\n" +
                    "1. Выдан пользователем @id300(Петр Петров) " + formattedDate1 + " \n" +
                    "    ⏳Истекает " + formattedExpires1 + "\n" +
                    "    ❓Причина: Флуд\n" +
                    "2. Выдан пользователем @id301(Сергей Сергеев) " + formattedDate2 + " \n" +
                    "\n" +
                    "Время выдачи/истечения указано по GMT+3.";

            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldShowEmptyWarnings() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        when(warnService.getMemberWarningsSortedInDesc(CHAT_ID, USER_ID)).thenReturn(List.of());

        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(USER_ID)).thenReturn(MENTION_USER);

            CommandExecutionStatus status = warnListShowCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(warnService).getMemberWarningsSortedInDesc(CHAT_ID, USER_ID);

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            String expected = "Предупреждения @id200(Ивана Иванова):\n\n";
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoMemberFound() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"неверный"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = warnListShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Необходимо указать участника, к которому вы хотите применить эту команду."));
        verify(warnService, never()).getMemberWarningsSortedInDesc(anyLong(), anyLong());
        verify(globalUserService, never()).getUserFullNamesInRequiredCase(anySet(), any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromWarnService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        when(warnService.getMemberWarningsSortedInDesc(CHAT_ID, USER_ID))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> warnListShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromGlobalUserService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        when(warnService.getMemberWarningsSortedInDesc(CHAT_ID, USER_ID)).thenReturn(List.of());

        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenThrow(new RuntimeException("User service error"));

        assertThrows(RuntimeException.class, () -> warnListShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        when(warnService.getMemberWarningsSortedInDesc(CHAT_ID, USER_ID)).thenReturn(List.of());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> warnListShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        when(warnService.getMemberWarningsSortedInDesc(CHAT_ID, USER_ID)).thenReturn(List.of());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> warnListShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}
