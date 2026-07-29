package com.example.my_bot.unit.command.commands.submanager;

import com.example.my_bot.cache.value.callback.GroupIdAndChatId;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.client.VkCommunityClient;
import com.example.my_bot.command.commands.submanager.SubmanagerCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.submanager.SubmanagerActionService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.GroupUtils;
import com.example.my_bot.utils.SubmanagerUtils;
import com.example.my_bot.utils.TextUtils;
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

import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.utils.GroupUtils.createPrivateMessagesLink;
import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmanagerCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long VK_API_CHAT_ID = 12345L;
    private static final long FROM_ID = 200L;
    private static final long GROUP_ID = -300L;
    private static final long MAIN_BOT_ID = 400L;
    private static final String BINDING_CODE = "ABC123";
    private static final String TOKEN = "token123";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private VkCommunityClient vkCommunityClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private SubmanagerService submanagerService;

    @Mock
    private SubmanagerActionService submanagerActionService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private MemberService memberService;

    @Mock
    private GroupActor theMainBotGroupActor;

    @Mock
    private com.github.benmanes.caffeine.cache.Cache<String, GroupIdAndChatId> bindingCache;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor executorBot;

    private SubmanagerCommand submanagerCommand;

    @BeforeEach
    void setUp() {
        submanagerCommand = new SubmanagerCommand(
                vkChatClient,
                vkCommunityClient,
                messageMapper,
                cacheManager,
                submanagerService,
                submanagerActionService,
                MAIN_BOT_ID,
                theMainBotGroupActor,
                userInputResolver,
                memberService
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getVkApiChatId()).thenReturn(VK_API_CHAT_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(executorBot);
        when(executorBot.getGroupId()).thenReturn(GROUP_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
        when(cacheManager.getBindingSubmanagerDataCache()).thenReturn(bindingCache);
    }


    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(submanagerService, never()).isSubmanager(any());
        verify(memberService, never()).synchronizeChatMembers(any());
    }

    @Test
    void shouldRemoveSubmanagerSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить"});

        when(submanagerService.isSubmanager(executorBot)).thenReturn(true);
        doNothing().when(memberService).synchronizeChatMembers(commandRoutingData);
        when(memberService.isChatAdmin(CHAT_ID, -MAIN_BOT_ID)).thenReturn(true);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(-MAIN_BOT_ID)).thenReturn("@id" + (-MAIN_BOT_ID));

            CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(submanagerService).isSubmanager(executorBot);
            verify(memberService).synchronizeChatMembers(commandRoutingData);
            verify(memberService).isChatAdmin(CHAT_ID, -MAIN_BOT_ID);
            verify(submanagerActionService).handleSubmanagerUnBinding(CHAT_ID, executorBot, VK_API_CHAT_ID);
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenRemoveButNoSubmanager() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить"});

        when(submanagerService.isSubmanager(executorBot)).thenReturn(false);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("К текущему чату не привязан субменеджер.", captor.getValue().getText());
        verify(memberService, never()).synchronizeChatMembers(any());
        verify(submanagerActionService, never()).handleSubmanagerUnBinding(anyLong(), any(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenRemoveButMainBotNotAdmin() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить"});

        when(submanagerService.isSubmanager(executorBot)).thenReturn(true);
        doNothing().when(memberService).synchronizeChatMembers(commandRoutingData);
        when(memberService.isChatAdmin(CHAT_ID, -MAIN_BOT_ID)).thenReturn(false);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(-MAIN_BOT_ID)).thenReturn("@id" + (-MAIN_BOT_ID));

            CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Чат-менеджер") && captor.getValue().getText().contains("администратора"));
            verify(submanagerActionService, never()).handleSubmanagerUnBinding(anyLong(), any(), anyLong());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenFirstArgIsNotGroup() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(FROM_ID));

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isGroupId(FROM_ID)).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Если хотите установить субменеджера, первым аргументом должна быть ссылка/упоминание сообщества."));
            verify(submanagerService, never()).getOptionalSubmanager(anyLong());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenGroupIsNotSubmanager() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@club"});

        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@club")).thenReturn(Optional.of(GROUP_ID));

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isGroupId(GROUP_ID)).thenReturn(true);

            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            try (MockedStatic<GroupUtils> groupUtilsMock = mockStatic(GroupUtils.class)) {
                groupUtilsMock.when(() -> GroupUtils.createPrivateMessagesLink(-MAIN_BOT_ID))
                        .thenReturn("https://vk.me/club" + (MAIN_BOT_ID));

                CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

                assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
                verify(vkChatClient).sendText(sendMessageDto);
                ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
                verify(vkChatClient).sendText(captor.capture());
                assertTrue(captor.getValue().getText().contains("не является субменеджером"));
                verify(submanagerService, never()).isSubmanager(any());
            }
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenGroupIsNotChatAdmin() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@club"});

        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@club")).thenReturn(Optional.of(GROUP_ID));

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isGroupId(GROUP_ID)).thenReturn(true);

            SubmanagerDto subInfo = new SubmanagerDto(GROUP_ID, TOKEN, 1, "secret");
            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.of(subInfo));

            when(submanagerService.isSubmanager(executorBot)).thenReturn(false);
            doNothing().when(memberService).synchronizeChatMembers(commandRoutingData);
            when(memberService.isChatAdmin(CHAT_ID, GROUP_ID)).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
                textUtilsMock.when(() -> TextUtils.createMention(GROUP_ID)).thenReturn("@id" + GROUP_ID);

                CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

                assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
                verify(vkChatClient).sendText(sendMessageDto);
                ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
                verify(vkChatClient).sendText(captor.capture());
                assertTrue(captor.getValue().getText().contains("администратора"));
                verify(vkCommunityClient, never()).getAllCommunityAdministrators(anyLong(), anyString());
            }
        }
    }

    @Test
    void shouldReturnVkApiErrorWhenGetCommunityAdminsFails() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@club"});

        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@club")).thenReturn(Optional.of(GROUP_ID));

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isGroupId(GROUP_ID)).thenReturn(true);

            SubmanagerDto subInfo = new SubmanagerDto(GROUP_ID, TOKEN, 1, "secret");
            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.of(subInfo));

            when(submanagerService.isSubmanager(executorBot)).thenReturn(false);
            doNothing().when(memberService).synchronizeChatMembers(commandRoutingData);
            when(memberService.isChatAdmin(CHAT_ID, GROUP_ID)).thenReturn(true);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            when(vkCommunityClient.getAllCommunityAdministrators(GROUP_ID, TOKEN)).thenThrow(apiException);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Не удалось получить информацию"));
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenUserIsNotCommunityAdmin() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@club"});

        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@club")).thenReturn(Optional.of(GROUP_ID));

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isGroupId(GROUP_ID)).thenReturn(true);

            SubmanagerDto subInfo = new SubmanagerDto(GROUP_ID, TOKEN, 1, "secret");
            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.of(subInfo));

            when(submanagerService.isSubmanager(executorBot)).thenReturn(false);
            doNothing().when(memberService).synchronizeChatMembers(commandRoutingData);
            when(memberService.isChatAdmin(CHAT_ID, GROUP_ID)).thenReturn(true);

            Set<Long> admins = Set.of(111L, 222L);
            when(vkCommunityClient.getAllCommunityAdministrators(GROUP_ID, TOKEN)).thenReturn(admins);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("не являетесь администратором"));
            verify(cacheManager, never()).getBindingSubmanagerDataCache();
        }
    }

    @Test
    void shouldSetSubmanagerSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@club"});

        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@club")).thenReturn(Optional.of(GROUP_ID));

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class);
             MockedStatic<SubmanagerUtils> submanagerUtilsMock = mockStatic(SubmanagerUtils.class)) {

            chatUtilsMock.when(() -> ChatUtils.isGroupId(GROUP_ID)).thenReturn(true);
            chatUtilsMock.when(() -> ChatUtils.convertToPeerId(CHAT_ID)).thenReturn(VK_API_CHAT_ID);

            SubmanagerDto subInfo = new SubmanagerDto(GROUP_ID, TOKEN, 1, "secret");
            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.of(subInfo));

            when(submanagerService.isSubmanager(executorBot)).thenReturn(false);
            doNothing().when(memberService).synchronizeChatMembers(commandRoutingData);
            when(memberService.isChatAdmin(CHAT_ID, GROUP_ID)).thenReturn(true);

            Set<Long> admins = Set.of(FROM_ID, 222L);
            when(vkCommunityClient.getAllCommunityAdministrators(GROUP_ID, TOKEN)).thenReturn(admins);

            submanagerUtilsMock.when(() -> SubmanagerUtils.generateNewBindingCode(anyLong())).thenReturn(BINDING_CODE);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = submanagerCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(cacheManager).getBindingSubmanagerDataCache();
            verify(bindingCache).put(eq(BINDING_CODE), any(GroupIdAndChatId.class));
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            SendMessageDto sent = captor.getValue();
            assertEquals(BINDING_CODE, sent.getText());
            assertEquals(theMainBotGroupActor, sent.getResponderBot());
            assertEquals(VK_API_CHAT_ID, sent.getResponsePeerId());
            assertFalse(sent.isReplyToMessageId());
            assertFalse(sent.isDoNotSendTheMessage());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> submanagerCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> submanagerCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}
