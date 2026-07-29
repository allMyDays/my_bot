package com.example.my_bot.unit.command.commands.chat;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.chat.AdminChatCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.chat.AdminChatDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.key.ConfirmationCacheKeyBuilder;
import com.example.my_bot.exception.chat.ChatException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.AdminChatService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import com.vk.api.sdk.objects.base.Error;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminChatCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final String CHAT_TITLE = "Тестовый чат";
    private static final String CHAT_CODE = "ABC123";
    private static final long ADMIN_CHAT_ID = 300L;
    private static final String ADMIN_CHAT_CODE = "XYZ789";
    private static final String ADMIN_CHAT_TITLE = "Админ чат";

    @Mock
    private AdminChatService adminChatService;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    @Mock
    private com.github.benmanes.caffeine.cache.Cache<String, String> confirmationCache;

    private AdminChatCommand adminChatCommand;

    @BeforeEach
    void setUp() {
        adminChatCommand = new AdminChatCommand(
                adminChatService,
                chatService,
                messageMapper,
                cacheManager
        );
        adminChatCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);

        when(cacheManager.getConfirmationCache()).thenReturn(confirmationCache);

        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setChatTitle(CHAT_TITLE);
        chatDetails.setChatCode(CHAT_CODE);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(chatDetails);
    }

    @Test
    void shouldShowAdminChatInfoWhenChatIsAdminChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        AdminChatDto adminChatDto = new AdminChatDto();
        adminChatDto.setChatId(ADMIN_CHAT_ID);
        adminChatDto.setBoundChats(Set.of(101L, 102L));
        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.of(adminChatDto));

        ChatDetailsDto boundChat1 = new ChatDetailsDto();
        boundChat1.setChatTitle("Чат 1");
        boundChat1.setChatCode("C1");
        ChatDetailsDto boundChat2 = new ChatDetailsDto();
        boundChat2.setChatTitle("Чат 2");
        boundChat2.setChatCode("C2");
        when(chatService.getCachedChatDetails(101L, false)).thenReturn(boundChat1);
        when(chatService.getCachedChatDetails(102L, false)).thenReturn(boundChat2);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(adminChatService).getAdminChatData(CHAT_ID);
        verify(chatService, times(3)).getCachedChatDetails(anyLong(), eq(false));
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("Данный чат является админ-чатом для [2] бесед:"));
        // Проверяем наличие обоих чатов без привязки к порядку
        assertTrue(actual.contains("«Чат 1» — C1"));
        assertTrue(actual.contains("«Чат 2» — C2"));
        // Проверяем наличие номеров (1. и 2. могут быть в любом порядке)
        assertTrue(actual.contains("1.") && actual.contains("2."));
    }

    @Test
    void shouldShowLastBoundAdminChatInfoWhenNotAdminChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.empty());
        when(adminChatService.findLatestAdminChatIdByBoundChatId(CHAT_ID))
                .thenReturn(Optional.of(ADMIN_CHAT_ID));

        ChatDetailsDto adminChatDetails = new ChatDetailsDto();
        adminChatDetails.setChatTitle(ADMIN_CHAT_TITLE);
        adminChatDetails.setChatCode(ADMIN_CHAT_CODE);
        when(chatService.getCachedChatDetails(ADMIN_CHAT_ID, false)).thenReturn(adminChatDetails);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(adminChatService).getAdminChatData(CHAT_ID);
        verify(adminChatService).findLatestAdminChatIdByBoundChatId(CHAT_ID);
        verify(chatService).getCachedChatDetails(ADMIN_CHAT_ID, false);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("Информация о последнем привязанном админ-чате к текущей беседе:"));
        assertTrue(actual.contains("Название: «" + ADMIN_CHAT_TITLE + "»."));
        assertTrue(actual.contains("UID: " + ADMIN_CHAT_CODE));
        assertTrue(actual.contains("админчат для " + CHAT_CODE));
    }

    @Test
    void shouldUnbindSpecificChatFromAdminChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить", "C2"});

        AdminChatDto adminChatDto = new AdminChatDto();
        adminChatDto.setChatId(ADMIN_CHAT_ID);
        adminChatDto.setBoundChats(Set.of(CHAT_ID, 101L));
        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.of(adminChatDto));

        doNothing().when(adminChatService).unBindChatFromAdminChat(ADMIN_CHAT_ID, "C2");

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(adminChatService).unBindChatFromAdminChat(ADMIN_CHAT_ID, "C2");
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("Вы успешно отвязали от текущего админ-чата беседу с кодом «C2».", captor.getValue().getText());
    }

    @Test
    void shouldRemoveAdminChatAfterConfirmation() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить"});

        AdminChatDto adminChatDto = new AdminChatDto();
        adminChatDto.setChatId(ADMIN_CHAT_ID);
        adminChatDto.setBoundChats(Set.of(CHAT_ID, 101L, 102L));
        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.of(adminChatDto));

        when(confirmationCache.getIfPresent(anyString())).thenReturn("");

        doNothing().when(adminChatService).removeAdminChat(CHAT_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(adminChatService).removeAdminChat(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("✅Все чаты были успешно отвязаны от текущего админ-чата, и теперь это обычная беседа.", captor.getValue().getText());
    }

    @Test
    void shouldRequestConfirmationForRemoveAdminChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить"});

        AdminChatDto adminChatDto = new AdminChatDto();
        adminChatDto.setChatId(ADMIN_CHAT_ID);
        adminChatDto.setBoundChats(Set.of(CHAT_ID, 101L, 102L));
        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.of(adminChatDto));

        when(confirmationCache.getIfPresent(anyString())).thenReturn(null);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ACTION_CONFIRMATION_IS_REQUIRED, status);
        verify(confirmationCache).put(anyString(), eq(""));
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Внимание: к данному админ-чату привязано [3] бесед."));
        verify(adminChatService, never()).removeAdminChat(anyLong());
    }

    @Test
    void shouldSetAdminChatSuccess() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"для", "XYZ789"});

        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.empty());

        when(confirmationCache.getIfPresent(anyString())).thenReturn("");

        doNothing().when(adminChatService).setAdminChat("XYZ789", CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(adminChatService).setAdminChat("XYZ789", CHAT_ID, FROM_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("✅Вы успешно сделали текущий чат админ-чатом для беседы с кодом «XYZ789»."));
    }

    @Test
    void shouldRequestConfirmationForSetAdminChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"для", "XYZ789"});

        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.empty());

        when(confirmationCache.getIfPresent(anyString())).thenReturn(null);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ACTION_CONFIRMATION_IS_REQUIRED, status);
        verify(confirmationCache).put(anyString(), eq(""));
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("Если вы уверены что хотите установить этот чат как админ-чат, введите команду ещё раз.", captor.getValue().getText());
        verify(adminChatService, never()).setAdminChat(anyString(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidFirstArgument() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"неправильно", "что-то"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Если хотите установить админ-чат, структура команды должна быть такой"));
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenRemoveAdminChatButNotAdminChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить"});

        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("Текущий чат не является админ-чатом.", captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenUnbindThrowsChatException() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить", "C2"});

        AdminChatDto adminChatDto = new AdminChatDto();
        adminChatDto.setChatId(ADMIN_CHAT_ID);
        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.of(adminChatDto));

        ChatException chatException = new ChatException("Ошибка отвязки") {};
        doThrow(chatException).when(adminChatService).unBindChatFromAdminChat(ADMIN_CHAT_ID, "C2");

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(chatException.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenSetAdminChatThrowsChatException() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"для", "XYZ789"});

        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.empty());

        // Кэш уже содержит ключ
        when(confirmationCache.getIfPresent(anyString())).thenReturn("");

        ChatException chatException = new ChatException("Ошибка установки") {};
        doThrow(chatException).when(adminChatService).setAdminChat("XYZ789", CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = adminChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(chatException.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatServiceGetCachedChatDetails() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        RuntimeException runtimeException = new RuntimeException("Service error");
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenThrow(runtimeException);

        assertThrows(RuntimeException.class, () -> adminChatCommand.execute(commandMessage));
        verify(chatService).getCachedChatDetails(CHAT_ID, false);
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        AdminChatDto adminChatDto = new AdminChatDto();
        adminChatDto.setChatId(ADMIN_CHAT_ID);
        adminChatDto.setBoundChats(Set.of());
        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.of(adminChatDto));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> adminChatCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromAdminChatService() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить", "C2"});

        AdminChatDto adminChatDto = new AdminChatDto();
        adminChatDto.setChatId(ADMIN_CHAT_ID);
        when(adminChatService.getAdminChatData(CHAT_ID)).thenReturn(Optional.of(adminChatDto));

        RuntimeException runtimeException = new RuntimeException("Unexpected");
        doThrow(runtimeException).when(adminChatService).unBindChatFromAdminChat(ADMIN_CHAT_ID, "C2");

        assertThrows(RuntimeException.class, () -> adminChatCommand.execute(commandMessage));
    }
}