package com.example.my_bot.unit.command.commands.submanager;

import com.example.my_bot.cache.value.callback.GroupIdAndChatId;
import com.example.my_bot.cache.value.callback.SecretKeyAndConfirmationCodeAndCompletableFuture;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.client.VkCommunityClient;
import com.example.my_bot.command.commands.submanager.SubmanagerCommand;
import com.example.my_bot.command.commands.submanager.TokenGiveCommand;
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
import com.example.my_bot.vk.enumeration.GroupTokenPermissionType;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.example.my_bot.utils.GroupUtils.createPrivateMessagesLink;
import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenGiveCommandTest {

    private static final long FROM_ID = 200L;
    private static final long GROUP_ID = 123L;
    private static final long MAIN_BOT_ID = 400L;
    private static final String TOKEN = "test_token";
    private static final String CALLBACK_URL = "https://example.com/callback";
    private static final String CALLBACK_TITLE = "Test Callback";
    private static final String CALLBACK_API_VERSION = "5.131";
    private static final String CONFIRMATION_CODE = "conf_code";
    private static final String SECRET_KEY = "secret_key";
    private static final int SERVER_ID = 1;
    private static final String PRIVATE_MESSAGES_LINK = "https://vk.me/club" + (-MAIN_BOT_ID);

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
    private GroupActor theMainBotGroupActor;

    @Mock
    private com.github.benmanes.caffeine.cache.Cache<Long, SecretKeyAndConfirmationCodeAndCompletableFuture> confirmationCache;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    private TokenGiveCommand tokenGiveCommand;

    @BeforeEach
    void setUp() {
        tokenGiveCommand = new TokenGiveCommand(
                vkChatClient,
                vkCommunityClient,
                messageMapper,
                cacheManager,
                submanagerService,
                MAIN_BOT_ID,
                theMainBotGroupActor,
                CALLBACK_URL,
                CALLBACK_TITLE,
                CALLBACK_API_VERSION
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
        when(cacheManager.getConfirmationCallbackUrlCache()).thenReturn(confirmationCache);
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenNotPersonalChat() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(12345L);

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class);
             MockedStatic<GroupUtils> groupUtilsMock = mockStatic(GroupUtils.class)) {

            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(false);
            groupUtilsMock.when(() -> GroupUtils.createPrivateMessagesLink(MAIN_BOT_ID)).thenReturn(PRIVATE_MESSAGES_LINK);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("только в личных сообщениях"));
            verify(vkCommunityClient, never()).getCommunityIdByToken(anyString());
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
            verify(vkCommunityClient, never()).getCommunityIdByToken(anyString());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenInvalidToken() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkCommunityClient).getCommunityIdByToken(TOKEN);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("не является валидным"));
            verify(vkCommunityClient, never()).getTokenPermissions(anyString());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenMissingPermissions() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.of(GROUP_ID));

            Set<GroupTokenPermissionType> tokenPermissions = Set.of(GroupTokenPermissionType.MESSAGES);
            when(vkCommunityClient.getTokenPermissions(TOKEN)).thenReturn(tokenPermissions);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkCommunityClient).getTokenPermissions(TOKEN);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("не хватает следующих прав"));
            verify(vkCommunityClient, never()).deleteCallbackServerByToken(anyLong(), anyString(), anyInt());
        }
    }

    @Test
    void shouldReturnVkApiErrorWhenGetTokenPermissionsFails() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.of(GROUP_ID));

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            when(vkCommunityClient.getTokenPermissions(TOKEN)).thenThrow(apiException);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Не удалось получить список прав"));
        }
    }

    @Test
    void shouldReturnVkApiErrorWhenDeleteExistingCallbackServerFails() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.of(GROUP_ID));

            Set<GroupTokenPermissionType> tokenPermissions = Set.of(GroupTokenPermissionType.values());
            when(vkCommunityClient.getTokenPermissions(TOKEN)).thenReturn(tokenPermissions);

            SubmanagerDto existingSub = new SubmanagerDto(GROUP_ID, TOKEN, 1, "secret");
            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.of(existingSub));

            when(vkCommunityClient.deleteCallbackServerByToken(GROUP_ID, TOKEN, 1)).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
            verify(vkCommunityClient).deleteCallbackServerByToken(GROUP_ID, TOKEN, 1);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("не удалось его удалить"));
            verify(vkCommunityClient, never()).getCallbackConfirmationCodeByToken(anyLong(), anyString());
        }
    }

    @Test
    void shouldCompleteSuccessfully() throws ClientException, ApiException, InterruptedException, ExecutionException, TimeoutException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class);
             MockedStatic<GroupUtils> groupUtilsMock = mockStatic(GroupUtils.class)) {

            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);
            groupUtilsMock.when(GroupUtils::generateCBServerSecretKey).thenReturn(SECRET_KEY);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.of(GROUP_ID));

            Set<GroupTokenPermissionType> tokenPermissions = Set.of(GroupTokenPermissionType.values());
            when(vkCommunityClient.getTokenPermissions(TOKEN)).thenReturn(tokenPermissions);

            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.empty());

            when(vkCommunityClient.getCallbackConfirmationCodeByToken(GROUP_ID, TOKEN)).thenReturn(CONFIRMATION_CODE);

            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            SecretKeyAndConfirmationCodeAndCompletableFuture cacheValue =
                    new SecretKeyAndConfirmationCodeAndCompletableFuture(SECRET_KEY, CONFIRMATION_CODE, future);
            when(confirmationCache.getIfPresent(GROUP_ID)).thenReturn(cacheValue);

            when(vkCommunityClient.addCallbackServerByToken(GROUP_ID, TOKEN, CALLBACK_URL, CALLBACK_TITLE, SECRET_KEY))
                    .thenReturn(SERVER_ID);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            List<Integer> sentMessageIds = List.of(123);
            when(vkChatClient.sendText(sendMessageDto)).thenReturn(sentMessageIds);

            when(vkChatClient.deleteOneMessageInTheMainBotPrivateMessages(FROM_ID, 123)).thenReturn(true);

            when(vkCommunityClient.setStandardCallbackSettingsForSubmanager(GROUP_ID, TOKEN, SERVER_ID, CALLBACK_API_VERSION))
                    .thenReturn(true);

            doNothing().when(submanagerService).createOrUpdateSubmanagerInfo(GROUP_ID, TOKEN, SERVER_ID, SECRET_KEY);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(vkCommunityClient).getCallbackConfirmationCodeByToken(GROUP_ID, TOKEN);
            verify(vkCommunityClient).addCallbackServerByToken(GROUP_ID, TOKEN, CALLBACK_URL, CALLBACK_TITLE, SECRET_KEY);
            verify(vkChatClient).sendText(sendMessageDto);
            verify(vkChatClient).deleteOneMessageInTheMainBotPrivateMessages(FROM_ID, 123);
            verify(vkCommunityClient).setStandardCallbackSettingsForSubmanager(GROUP_ID, TOKEN, SERVER_ID, CALLBACK_API_VERSION);
            verify(submanagerService).createOrUpdateSubmanagerInfo(GROUP_ID, TOKEN, SERVER_ID, SECRET_KEY);

            ArgumentCaptor<SendMessageDto> finalCaptor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient, times(3)).sendText(finalCaptor.capture());
            List<SendMessageDto> sentMessages = finalCaptor.getAllValues();
            assertEquals("Подождите...", sentMessages.get(1).getText());
            assertTrue(sentMessages.get(2).getText().contains("Успешно!"));
        }
    }

    @Test
    void shouldReturnVkApiErrorWhenGetConfirmationCodeFails() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.of(GROUP_ID));

            Set<GroupTokenPermissionType> tokenPermissions = Set.of(GroupTokenPermissionType.values());
            when(vkCommunityClient.getTokenPermissions(TOKEN)).thenReturn(tokenPermissions);

            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.empty());

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            when(vkCommunityClient.getCallbackConfirmationCodeByToken(GROUP_ID, TOKEN)).thenThrow(apiException);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Не удалось получить строку"));
            verify(vkCommunityClient, never()).addCallbackServerByToken(anyLong(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Test
    void shouldReturnVkApiErrorWhenAddCallbackServerFails() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class);
             MockedStatic<GroupUtils> groupUtilsMock = mockStatic(GroupUtils.class)) {

            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);
            groupUtilsMock.when(GroupUtils::generateCBServerSecretKey).thenReturn(SECRET_KEY);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.of(GROUP_ID));
            Set<GroupTokenPermissionType> tokenPermissions = Set.of(GroupTokenPermissionType.values());
            when(vkCommunityClient.getTokenPermissions(TOKEN)).thenReturn(tokenPermissions);
            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.empty());
            when(vkCommunityClient.getCallbackConfirmationCodeByToken(GROUP_ID, TOKEN)).thenReturn(CONFIRMATION_CODE);

            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            SecretKeyAndConfirmationCodeAndCompletableFuture cacheValue =
                    new SecretKeyAndConfirmationCodeAndCompletableFuture(SECRET_KEY, CONFIRMATION_CODE, future);
            when(confirmationCache.getIfPresent(GROUP_ID)).thenReturn(cacheValue);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            when(vkCommunityClient.addCallbackServerByToken(GROUP_ID, TOKEN, CALLBACK_URL, CALLBACK_TITLE, SECRET_KEY))
                    .thenThrow(apiException);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Не удалось добавить Callback API сервер"));
            verify(vkCommunityClient, never()).setStandardCallbackSettingsForSubmanager(anyLong(), anyString(), anyInt(), anyString());
        }
    }

    @Test
    void shouldReturnVkApiErrorWhenConfirmationTimeout() throws ClientException, ApiException, InterruptedException, ExecutionException, TimeoutException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class);
             MockedStatic<GroupUtils> groupUtilsMock = mockStatic(GroupUtils.class)) {

            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);
            groupUtilsMock.when(GroupUtils::generateCBServerSecretKey).thenReturn(SECRET_KEY);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.of(GROUP_ID));
            Set<GroupTokenPermissionType> tokenPermissions = Set.of(GroupTokenPermissionType.values());
            when(vkCommunityClient.getTokenPermissions(TOKEN)).thenReturn(tokenPermissions);
            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.empty());
            when(vkCommunityClient.getCallbackConfirmationCodeByToken(GROUP_ID, TOKEN)).thenReturn(CONFIRMATION_CODE);

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            SecretKeyAndConfirmationCodeAndCompletableFuture cacheValue =
                    new SecretKeyAndConfirmationCodeAndCompletableFuture(SECRET_KEY, CONFIRMATION_CODE, future);
            when(confirmationCache.getIfPresent(GROUP_ID)).thenReturn(cacheValue);

            when(vkCommunityClient.addCallbackServerByToken(GROUP_ID, TOKEN, CALLBACK_URL, CALLBACK_TITLE, SECRET_KEY))
                    .thenReturn(SERVER_ID);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            List<Integer> sentMessageIds = List.of(123);
            when(vkChatClient.sendText(sendMessageDto)).thenReturn(sentMessageIds);
            when(vkChatClient.deleteOneMessageInTheMainBotPrivateMessages(FROM_ID, 123)).thenReturn(true);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient, times(2)).sendText(captor.capture());
            List<SendMessageDto> sentMessages = captor.getAllValues();
            assertEquals("Подождите...", sentMessages.get(0).getText());
            assertTrue(sentMessages.get(1).getText().contains("Не удалось подтвердить url адрес"));
            verify(vkCommunityClient, never()).setStandardCallbackSettingsForSubmanager(anyLong(), anyString(), anyInt(), anyString());
        }
    }

    @Test
    void shouldReturnVkApiErrorWhenSetStandardCallbackSettingsFails() throws ClientException, ApiException, InterruptedException, ExecutionException, TimeoutException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(FROM_ID);
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{TOKEN});

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class);
             MockedStatic<GroupUtils> groupUtilsMock = mockStatic(GroupUtils.class)) {

            chatUtilsMock.when(() -> ChatUtils.isPersonalChat(anyLong())).thenReturn(true);
            groupUtilsMock.when(GroupUtils::generateCBServerSecretKey).thenReturn(SECRET_KEY);

            when(vkCommunityClient.getCommunityIdByToken(TOKEN)).thenReturn(Optional.of(GROUP_ID));
            Set<GroupTokenPermissionType> tokenPermissions = Set.of(GroupTokenPermissionType.values());
            when(vkCommunityClient.getTokenPermissions(TOKEN)).thenReturn(tokenPermissions);
            when(submanagerService.getOptionalSubmanager(GROUP_ID)).thenReturn(Optional.empty());
            when(vkCommunityClient.getCallbackConfirmationCodeByToken(GROUP_ID, TOKEN)).thenReturn(CONFIRMATION_CODE);

            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            SecretKeyAndConfirmationCodeAndCompletableFuture cacheValue =
                    new SecretKeyAndConfirmationCodeAndCompletableFuture(SECRET_KEY, CONFIRMATION_CODE, future);
            when(confirmationCache.getIfPresent(GROUP_ID)).thenReturn(cacheValue);

            when(vkCommunityClient.addCallbackServerByToken(GROUP_ID, TOKEN, CALLBACK_URL, CALLBACK_TITLE, SECRET_KEY))
                    .thenReturn(SERVER_ID);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            List<Integer> sentMessageIds = List.of(123);
            when(vkChatClient.sendText(sendMessageDto)).thenReturn(sentMessageIds);
            when(vkChatClient.deleteOneMessageInTheMainBotPrivateMessages(FROM_ID, 123)).thenReturn(true);

            when(vkCommunityClient.setStandardCallbackSettingsForSubmanager(GROUP_ID, TOKEN, SERVER_ID, CALLBACK_API_VERSION))
                    .thenReturn(false);

            CommandExecutionStatus status = tokenGiveCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient, times(2)).sendText(captor.capture());
            List<SendMessageDto> sentMessages = captor.getAllValues();
            assertEquals("Подождите...", sentMessages.get(0).getText());
            assertTrue(sentMessages.get(1).getText().contains("не удалось настроить этот сервер"));
            verify(submanagerService, never()).createOrUpdateSubmanagerInfo(anyLong(), anyString(), anyInt(), anyString());
        }
    }
}
