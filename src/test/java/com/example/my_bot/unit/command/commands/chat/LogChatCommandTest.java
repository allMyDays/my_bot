package com.example.my_bot.unit.command.commands.chat;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.chat.LogChatCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.key.ConfirmationCacheKeyBuilder;
import com.example.my_bot.exception.chat.ChatException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MessageLogService;
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

import java.util.List;
import java.util.Optional;

import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class LogChatCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long MAIN_BOT_ID = 300L;
    private static final long SUBMANAGER_ID = 400L;
    private static final String CHAT_TITLE = "Тестовый чат";
    private static final String CHAT_CODE = "ABC123";
    private static final long LOG_CHAT_ID = 500L;
    private static final long RESPONDER_BOT_GROUP_ID = MAIN_BOT_ID;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private MessageLogService messageLogService;

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

    private LogChatCommand logChatCommand;

    @BeforeEach
    void setUp() {
        logChatCommand = new LogChatCommand(
                chatService,
                messageMapper,
                messageLogService,
                cacheManager,
                vkChatClient,
                MAIN_BOT_ID
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
        when(commandRoutingData.getResponderBot()).thenReturn(groupActor);
        when(groupActor.getGroupId()).thenReturn(RESPONDER_BOT_GROUP_ID);

        when(cacheManager.getConfirmationCache()).thenReturn(confirmationCache);

        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setChatTitle(CHAT_TITLE);
        currentChat.setChatCode(CHAT_CODE);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);
    }


    @Test
    void shouldShowBoundChatsWhenChatIsLogChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ChatEntity chat1 = new ChatEntity();
        chat1.setChatTitle("Чат 1");
        chat1.setChatCode("C1");
        ChatEntity chat2 = new ChatEntity();
        chat2.setChatTitle("Чат 2");
        chat2.setChatCode("C2");
        when(chatService.findByBoundLogChat(CHAT_ID)).thenReturn(List.of(chat1, chat2));

        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(null);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).findByBoundLogChat(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("Данный чат является логчатом для [2] бесед:"));
        assertTrue(actual.contains("«Чат 1» — C1"));
        assertTrue(actual.contains("«Чат 2» — C2"));
        assertTrue(actual.contains("1.") && actual.contains("2."));
    }

    @Test
    void shouldShowMessageWhenNoLogChatAndNotBound() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.findByBoundLogChat(CHAT_ID)).thenReturn(List.of());

        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(null);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("не привязан логчат"));
        assertTrue(actual.contains("напишите в другом желаемом чате"));
    }

    @Test
    void shouldShowBoundLogChatInfoWhenChatHasBoundLogChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.findByBoundLogChat(CHAT_ID)).thenReturn(List.of());

        long boundLogChatId = 123L;
        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(boundLogChatId);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);

        ChatDetailsDto logChatDetails = new ChatDetailsDto();
        logChatDetails.setChatTitle("Лог чат");
        logChatDetails.setChatCode("LC123");
        when(chatService.getCachedChatDetails(boundLogChatId, false)).thenReturn(logChatDetails);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("К данному чату привязан логчат с кодом «LC123»."));
        assertTrue(actual.contains("Название чата: «Лог чат»."));
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenRemoveAndNoLogChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить"});

        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(null);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);
        when(chatService.findByBoundLogChat(CHAT_ID)).thenReturn(List.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("К текущему чату не привязан логчат, а также текущий чат сам не является логчатом.", captor.getValue().getText());
    }

    @Test
    void shouldUnbindLogChatFromCurrentChat() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"удалить"});

        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(LOG_CHAT_ID);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);
        when(chatService.findByBoundLogChat(CHAT_ID)).thenReturn(List.of());

        doNothing().when(chatService).setBoundLogChatAsNull(CHAT_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).setBoundLogChatAsNull(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("✅Логчат был успешно отвязан от текущего чата.", captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenNoLogChatForShowMessages() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"10"});

        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(null);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("К текущему чату не привязан логчат.", captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenCannotForwardDueToDifferentBot() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        long logChatId = 123L;
        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(logChatId);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);

        ChatDetailsDto logChatDetails = new ChatDetailsDto();
        logChatDetails.setBoundSubmanagerId(null);
        when(chatService.getCachedChatDetails(logChatId, false)).thenReturn(logChatDetails);

        when(groupActor.getGroupId()).thenReturn(999L);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Я не могу переслать сообщения из того логчата"));
    }

    @Test
    void shouldSetLogChatSuccess() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"для", "XYZ"});

        doNothing().when(chatService).setLogChat("XYZ", CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).setLogChat("XYZ", CHAT_ID, FROM_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("✅Вы успешно сделали текущий чат логчатом для беседы с кодом «XYZ»."));
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenSetLogChatThrowsChatException() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"для", "XYZ"});

        ChatException chatException = new ChatException("Ошибка установки") {};
        doThrow(chatException).when(chatService).setLogChat("XYZ", CHAT_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(chatException.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArgumentsForFor() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"для"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Недостаточно") || actual.contains("аргументов") || actual.contains("Недостаточно аргументов"));
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidFirstArgument() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"неправильно", "что-то"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = logChatCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Структура команды должна быть такой") || actual.contains("структура команды должна быть такой"));
    }


    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        RuntimeException exception = new RuntimeException("Service error");
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenThrow(exception);

        assertThrows(RuntimeException.class, () -> logChatCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.findByBoundLogChat(CHAT_ID)).thenReturn(List.of());

        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(null);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> logChatCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws Exception {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.findByBoundLogChat(CHAT_ID)).thenReturn(List.of());

        ChatDetailsDto currentChat = new ChatDetailsDto();
        currentChat.setBoundLogChat(null);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(currentChat);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> logChatCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}
