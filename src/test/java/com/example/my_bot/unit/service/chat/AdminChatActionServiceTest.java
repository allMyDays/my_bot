package com.example.my_bot.unit.service.chat;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.dto.*;
import com.example.my_bot.dto.chat.AdminChatDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.cooldown.CooldownResult;
import com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.command.HandleAdminChatCommandStatus;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.VkKeyboardActionService;
import com.example.my_bot.service.chat.AdminChatActionService;
import com.example.my_bot.service.chat.AdminChatService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.github.benmanes.caffeine.cache.Cache;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.messages.Keyboard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.enumeration.command.HandleAdminChatCommandStatus.*;
import static com.example.my_bot.enumeration.key.ButtonPayloadKey.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminChatActionServiceTest {

    @Mock
    private ChatService chatService;

    @Mock
    private AdminChatService adminChatService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private VkKeyboardActionService keyboardService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private CommandRegistry commandRegistry;

    @Mock
    private CommandAccessService commandAccessService;

    @Mock
    private MemberService memberService;

    @Mock
    private GroupActor theMainBotGroupActor;

    @Mock
    private SubmanagerService submanagerService;

    @Mock
    private GlobalUserService globalUserService;

    private AdminChatActionService adminChatActionService;

    private final long chatId = 1L;
    private final long adminChatId = 2L;
    private final long boundChatId = 3L;
    private final long fromId = 100L;
    private final String fullCommand = "test arg";
    private final String fullCommandWithNoPrefix = "test arg";
    private final String commandName = "test";
    private final CommandRoutingData routingData = new CommandRoutingData();

    @BeforeEach
    void setUp() {
        routingData.setDataBaseChatId(adminChatId);
        routingData.setVkApiChatId(adminChatId);
        routingData.setExecutorBot(theMainBotGroupActor);

        adminChatActionService = new AdminChatActionService(
                chatService,
                adminChatService,
                messageMapper,
                keyboardService,
                vkChatClient,
                commandRegistry,
                commandAccessService,
                memberService,
                theMainBotGroupActor,
                submanagerService,
                globalUserService
        );
    }

    @Test
    void shouldReturnNotAdminChatWhenDataBaseChatIdNull() throws Exception {
        CommandRoutingData invalid = new CommandRoutingData();
        Command cmd = mock(Command.class);
        HandleAdminChatCommandStatus result = adminChatActionService.handleAdminChatCommand(cmd, fullCommandWithNoPrefix, invalid, false);
        assertThat(result).isEqualTo(NOT_ADMIN_CHAT);
        verifyNoInteractions(adminChatService);
    }

    @Test
    void shouldReturnNotAdminChatWhenNotAdminChat() throws Exception {
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.empty());
        Command cmd = mock(Command.class);
        HandleAdminChatCommandStatus result = adminChatActionService.handleAdminChatCommand(cmd, fullCommandWithNoPrefix, routingData, false);
        assertThat(result).isEqualTo(NOT_ADMIN_CHAT);
        verify(adminChatService).getAdminChatData(adminChatId);
    }

    @Test
    void shouldReturnMustBeExecutedInAdminChatWhenModeOnlyInAdminChat() throws Exception {
        AdminChatDto adminChat = new AdminChatDto();
        adminChat.setChatId(adminChatId);
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.of(adminChat));
        Command cmd = mock(Command.class);
        given(cmd.adminChatCommandExecutionMode()).willReturn(AdminChatCommandExecutionMode.ONLY_IN_ADMIN_CHAT);

        HandleAdminChatCommandStatus result = adminChatActionService.handleAdminChatCommand(cmd, fullCommandWithNoPrefix, routingData, false);
        assertThat(result).isEqualTo(MUST_BE_EXECUTED_IN_ADMIN_CHAT);
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldReturnMustBeExecutedInAdminChatWhenEventOrTimerMode() throws Exception {
        AdminChatDto adminChat = new AdminChatDto();
        adminChat.setChatId(adminChatId);
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.of(adminChat));
        Command cmd = mock(Command.class);
        given(cmd.adminChatCommandExecutionMode()).willReturn(AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE);

        HandleAdminChatCommandStatus result = adminChatActionService.handleAdminChatCommand(cmd, fullCommandWithNoPrefix, routingData, true);
        assertThat(result).isEqualTo(MUST_BE_EXECUTED_IN_ADMIN_CHAT);
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldSendKeyboardForAdminChatCommand() throws Exception {
        AdminChatDto adminChat = new AdminChatDto();
        adminChat.setChatId(adminChatId);
        adminChat.setBoundChats(Set.of(boundChatId));
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.of(adminChat));

        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setChatId(boundChatId);
        chatDetails.setChatTitle("Bound Chat");
        given(chatService.getCachedChatDetails(boundChatId, false)).willReturn(chatDetails);

        Command cmd = mock(Command.class);
        given(cmd.adminChatCommandExecutionMode()).willReturn(AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE);

        SendMessageDto sendMessage = new SendMessageDto(
                "Выберите чат, в котором нужно применить эту команду.",
                0L,
                theMainBotGroupActor,
                null,
                false,
                false,
                null
        );
        given(messageMapper.toSendMessageDto(anyString(), any(CommandRoutingData.class))).willReturn(sendMessage);

        Keyboard keyboard = new Keyboard();
        given(keyboardService.createAutoLayoutKeyboard(anyList(), anyInt())).willReturn(keyboard);

        HandleAdminChatCommandStatus result = adminChatActionService.handleAdminChatCommand(cmd, fullCommandWithNoPrefix, routingData, false);
        assertThat(result).isEqualTo(VK_KEYBOARD_IS_SENT);

        verify(messageMapper).toSendMessageDto(eq("Выберите чат, в котором нужно применить эту команду."), any(CommandRoutingData.class));
        verify(keyboardService).createAutoLayoutKeyboard(anyList(), eq(3));
        verify(vkChatClient).sendText(sendMessage);
    }

    @Test
    void shouldReturnWhenAdminChatNotFound() throws Exception {
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.empty());
        adminChatActionService.handleClickedAdminChatButton(routingData, ADMIN_CHAT_EXECUTE_COMMAND_IN_THIS_ADMIN_CHAT, "", fromId);
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldReturnWhenCommandNotInCache() throws Exception {
        AdminChatDto adminChat = new AdminChatDto();
        adminChat.setChatId(adminChatId);
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.of(adminChat));
        adminChatActionService.handleClickedAdminChatButton(routingData, ADMIN_CHAT_EXECUTE_COMMAND_IN_THIS_ADMIN_CHAT, "", fromId);
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldExecuteInThisAdminChat() throws Exception {
        AdminChatDto adminChat = new AdminChatDto();
        adminChat.setChatId(adminChatId);
        adminChat.setBoundChats(Set.of(boundChatId));
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.of(adminChat));

        Cache<Long, String> sentCommandCache = getSentCommandCache();
        sentCommandCache.put(adminChatId, fullCommandWithNoPrefix);

        try (MockedStatic<UserInputResolver> userInputResolverMock = mockStatic(UserInputResolver.class)) {
            userInputResolverMock.when(() -> UserInputResolver.splitFullCommandIntoTwoElements(fullCommandWithNoPrefix))
                    .thenReturn(new String[]{commandName, "arg"});

            Command command = mock(Command.class);
            given(command.adminChatCommandExecutionMode()).willReturn(AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE);
            given(command.mainCommandName()).willReturn(commandName);
            ChatCommand chatCommand = mock(ChatCommand.class);
            given(commandRegistry.getCommandWithTheAnnotation(commandName)).willReturn(Optional.of(Map.entry(chatCommand, command)));

            given(memberService.getMemberRolePriority(adminChatId, fromId)).willReturn(50);
            given(commandAccessService.checkCommandAuthorization(adminChatId, commandName, 50, fromId)).willReturn(true);
            CooldownResult cooldownResult = new CooldownResult();
            cooldownResult.setCanExecuteCommand(true);
            given(commandAccessService.checkCommandRateLimit(adminChatId, commandName, 50, fromId)).willReturn(cooldownResult);

            ChatDetailsDto chatDetails = new ChatDetailsDto();
            chatDetails.setChatId(adminChatId);
            chatDetails.setChatTitle("Admin Chat");
            given(chatService.getCachedChatDetails(adminChatId, false)).willReturn(chatDetails);

            CommandExecutionStatus executionStatus = CommandExecutionStatus.SUCCESS;
            given(chatCommand.execute(any())).willReturn(executionStatus);

            SendMessageDto resultMessage = new SendMessageDto(
                    "📋 Результат выполнения команды:",
                    0L,
                    theMainBotGroupActor,
                    null,
                    false,
                    false,
                    null
            );
            given(messageMapper.toSendMessageDto(anyString(), any(CommandRoutingData.class))).willReturn(resultMessage);

            adminChatActionService.handleClickedAdminChatButton(routingData, ADMIN_CHAT_EXECUTE_COMMAND_IN_THIS_ADMIN_CHAT, "", fromId);

            verify(vkChatClient).sendText(resultMessage);
            verify(chatCommand).execute(any());
        }
    }

    @Test
    void shouldExecuteInOneBoundChat() throws Exception {
        AdminChatDto adminChat = new AdminChatDto();
        adminChat.setChatId(adminChatId);
        adminChat.setBoundChats(Set.of(boundChatId));
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.of(adminChat));

        Cache<Long, String> sentCommandCache = getSentCommandCache();
        sentCommandCache.put(adminChatId, fullCommandWithNoPrefix);

        try (MockedStatic<UserInputResolver> userInputResolverMock = mockStatic(UserInputResolver.class)) {
            userInputResolverMock.when(() -> UserInputResolver.splitFullCommandIntoTwoElements(fullCommandWithNoPrefix))
                    .thenReturn(new String[]{commandName, "arg"});

            Command command = mock(Command.class);
            given(command.adminChatCommandExecutionMode()).willReturn(AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE);
            given(command.mainCommandName()).willReturn(commandName);
            ChatCommand chatCommand = mock(ChatCommand.class);
            given(commandRegistry.getCommandWithTheAnnotation(commandName)).willReturn(Optional.of(Map.entry(chatCommand, command)));

            given(memberService.getMemberRolePriority(boundChatId, fromId)).willReturn(50);
            given(commandAccessService.checkCommandAuthorization(boundChatId, commandName, 50, fromId)).willReturn(true);
            CooldownResult cooldownResult = new CooldownResult();
            cooldownResult.setCanExecuteCommand(true);
            given(commandAccessService.checkCommandRateLimit(boundChatId, commandName, 50, fromId)).willReturn(cooldownResult);

            ChatDetailsDto boundChatDetails = new ChatDetailsDto();
            boundChatDetails.setChatId(boundChatId);
            boundChatDetails.setChatTitle("Bound Chat");
            given(chatService.getCachedChatDetails(boundChatId, false)).willReturn(boundChatDetails);

            CommandExecutionStatus executionStatus = CommandExecutionStatus.SUCCESS;
            given(chatCommand.execute(any())).willReturn(executionStatus);

            SendMessageDto resultMessage = new SendMessageDto(
                    "📋 Результат выполнения команды:",
                    0L,
                    theMainBotGroupActor,
                    null,
                    false,
                    false,
                    null
            );
            given(messageMapper.toSendMessageDto(anyString(), any(CommandRoutingData.class))).willReturn(resultMessage);

            adminChatActionService.handleClickedAdminChatButton(routingData, ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT, String.valueOf(boundChatId), fromId);

            verify(vkChatClient).sendText(resultMessage);
            verify(chatCommand).execute(any());
        }
    }

    @Test
    void shouldExecuteInAllBoundChats() throws Exception {
        AdminChatDto adminChat = new AdminChatDto();
        adminChat.setChatId(adminChatId);
        adminChat.setBoundChats(Set.of(boundChatId, 4L));
        given(adminChatService.getAdminChatData(adminChatId)).willReturn(Optional.of(adminChat));

        Cache<Long, String> sentCommandCache = getSentCommandCache();
        sentCommandCache.put(adminChatId, fullCommandWithNoPrefix);

        try (MockedStatic<UserInputResolver> userInputResolverMock = mockStatic(UserInputResolver.class)) {
            userInputResolverMock.when(() -> UserInputResolver.splitFullCommandIntoTwoElements(fullCommandWithNoPrefix))
                    .thenReturn(new String[]{commandName, "arg"});

            Command command = mock(Command.class);
            given(command.adminChatCommandExecutionMode()).willReturn(AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE);
            given(command.mainCommandName()).willReturn(commandName);
            ChatCommand chatCommand = mock(ChatCommand.class);
            given(commandRegistry.getCommandWithTheAnnotation(commandName)).willReturn(Optional.of(Map.entry(chatCommand, command)));

            for (long chat : Set.of(boundChatId, 4L)) {
                given(memberService.getMemberRolePriority(chat, fromId)).willReturn(50);
                given(commandAccessService.checkCommandAuthorization(chat, commandName, 50, fromId)).willReturn(true);
                CooldownResult cooldownResult = new CooldownResult();
                cooldownResult.setCanExecuteCommand(true);
                given(commandAccessService.checkCommandRateLimit(chat, commandName, 50, fromId)).willReturn(cooldownResult);
                ChatDetailsDto details = new ChatDetailsDto();
                details.setChatId(chat);
                details.setChatTitle("Chat " + chat);
                given(chatService.getCachedChatDetails(chat, false)).willReturn(details);
                given(chatCommand.execute(any())).willReturn(CommandExecutionStatus.SUCCESS);
            }

            SendMessageDto resultMessage = new SendMessageDto(
                    "📋 Результат выполнения команды:",
                    0L,
                    theMainBotGroupActor,
                    null,
                    false,
                    false,
                    null
            );
            given(messageMapper.toSendMessageDto(anyString(), any(CommandRoutingData.class))).willReturn(resultMessage);

            adminChatActionService.handleClickedAdminChatButton(routingData, ADMIN_CHAT_EXECUTE_COMMAND_IN_ALL_BOUND_CHATS, "", fromId);

            verify(vkChatClient).sendText(resultMessage);
            verify(chatCommand, times(2)).execute(any());
        }
    }

    @Test
    void shouldQueueMessageWhenAdminChatExists() throws Exception {
        given(adminChatService.findLatestAdminChatIdByBoundChatId(boundChatId)).willReturn(Optional.of(adminChatId));
        Command cmd = mock(Command.class);
        given(cmd.mainCommandName()).willReturn("cmd");
        given(globalUserService.getUserFullNameInRequiredCase(eq(fromId), any())).willReturn("User");
        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setChatId(boundChatId);
        chatDetails.setChatTitle("Bound Chat");
        given(chatService.getCachedChatDetails(boundChatId, false)).willReturn(chatDetails);

        adminChatActionService.sendMessageAboutAUsedCommand(boundChatId, cmd, fromId);

        Cache<Long, StringBuilder> queue = getMessageQueueCache();
        assertThat(queue.asMap()).containsKey(adminChatId);
        String message = queue.getIfPresent(adminChatId).toString();
        assertThat(message).contains("Команда «cmd» была использована");
        assertThat(message).contains("User");
    }

    @Test
    void shouldNotQueueWhenNoAdminChat() {
        given(adminChatService.findLatestAdminChatIdByBoundChatId(boundChatId)).willReturn(Optional.empty());
        Command cmd = mock(Command.class);
        adminChatActionService.sendMessageAboutAUsedCommand(boundChatId, cmd, fromId);
        verifyNoInteractions(globalUserService, chatService);
    }

    @Test
    void shouldQueueEventMessageWhenAdminChatExists() throws Exception {
        given(adminChatService.findLatestAdminChatIdByBoundChatId(boundChatId)).willReturn(Optional.of(adminChatId));
        given(globalUserService.getUserFullNameInRequiredCase(eq(fromId), any())).willReturn("User");
        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setChatId(boundChatId);
        chatDetails.setChatTitle("Bound Chat");
        given(chatService.getCachedChatDetails(boundChatId, false)).willReturn(chatDetails);

        adminChatActionService.sendMessageAboutAnExecutedEvent(boundChatId, MyEventType.WORD_FILTER, "cmd", fromId);

        Cache<Long, StringBuilder> queue = getMessageQueueCache();
        assertThat(queue.asMap()).containsKey(adminChatId);
        String message = queue.getIfPresent(adminChatId).toString();
        assertThat(message).contains("Было активировано событие «Фильтр слов»");
        assertThat(message).contains("User");
        assertThat(message).contains("Команда, которая была применена: cmd");
    }

    @Test
    void shouldNotQueueEventWhenNoAdminChat() {
        given(adminChatService.findLatestAdminChatIdByBoundChatId(boundChatId)).willReturn(Optional.empty());
        adminChatActionService.sendMessageAboutAnExecutedEvent(boundChatId, MyEventType.WORD_FILTER, "cmd", fromId);
        verifyNoInteractions(globalUserService, chatService);
    }

    private Cache<Long, String> getSentCommandCache() throws Exception {
        java.lang.reflect.Field field = AdminChatActionService.class.getDeclaredField("sentCommandCache");
        field.setAccessible(true);
        return (Cache<Long, String>) field.get(adminChatActionService);
    }

    private Cache<Long, StringBuilder> getMessageQueueCache() throws Exception {
        java.lang.reflect.Field field = AdminChatActionService.class.getDeclaredField("messagesToSendToAdminChat");
        field.setAccessible(true);
        return (Cache<Long, StringBuilder>) field.get(adminChatActionService);
    }
}