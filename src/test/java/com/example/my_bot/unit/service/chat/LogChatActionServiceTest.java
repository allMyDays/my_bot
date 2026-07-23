package com.example.my_bot.unit.service.chat;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.chat.LogChatActionService;
import com.example.my_bot.vk.mapping.action.VkAction;
import com.example.my_bot.vk.mapping.message.VkMessage;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogChatActionServiceTest {

    @Mock
    private ChatService chatService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private LogChatActionService logChatActionService;

    private final long chatId = 1L;
    private final long logChatId = 2L;
    private final long submanagerId = 100L;
    private final long submanagerChatId = 101L;
    private final int convMessageId = 123;

    private CommandRoutingData routingData;
    private ChatDetailsDto currentChat;
    private VkMessage message;
    private final GroupActor actor = new GroupActor(111L, "token");

    @BeforeEach
    void setUp() {
        routingData = new CommandRoutingData();
        routingData.setDataBaseChatId(chatId);
        routingData.setVkApiChatId(chatId);
        routingData.setExecutorBot(new GroupActor(111L, "token"));

        currentChat = new ChatDetailsDto();
        currentChat.setChatId(chatId);

        message = new VkMessage();
        message.setConversationMessageId(convMessageId);
        message.setAction(null);

        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(new SendMessageDto("", 0L, new GroupActor(111L, "token"), null, false, false, null));
        given(messageMapper.toSendMessageDto(anyString(), any(CommandRoutingData.class)))
                .willReturn(new SendMessageDto("", 0L, new GroupActor(111L, "token"), null, false, false, null));
    }

    @Test
    void shouldReturnWhenCurrentChatIsNull() throws Exception {
        logChatActionService.forwardMessageToLogChat(routingData, null, message);
        verify(chatService, never()).getCachedChatDetails(anyLong(), anyBoolean());
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldReturnWhenNoBoundLogChat() throws Exception {
        currentChat.setBoundLogChat(null);
        logChatActionService.forwardMessageToLogChat(routingData, currentChat, message);
        verify(chatService, never()).getCachedChatDetails(anyLong(), anyBoolean());
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldReturnWhenMessageHasAction() throws Exception {
        currentChat.setBoundLogChat(logChatId);
        message.setAction(new VkAction());
        logChatActionService.forwardMessageToLogChat(routingData, currentChat, message);
        verify(chatService, never()).getCachedChatDetails(anyLong(), anyBoolean());
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldUnbindWhenSubmanagerDiffers() throws Exception {
        currentChat.setBoundLogChat(logChatId);
        currentChat.setBoundSubmanagerId(100L);

        ChatDetailsDto logChat = new ChatDetailsDto();
        logChat.setBoundSubmanagerId(200L);
        given(chatService.getCachedChatDetails(logChatId, false)).willReturn(logChat);

        logChatActionService.forwardMessageToLogChat(routingData, currentChat, message);

        verify(chatService).setBoundLogChatAsNull(chatId);
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldForwardSuccessfullyWithoutSubmanager() throws Exception {
        currentChat.setBoundLogChat(logChatId);
        currentChat.setBoundSubmanagerId(null);

        ChatDetailsDto logChat = new ChatDetailsDto();
        logChat.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(logChatId, false)).willReturn(logChat);

        SendMessageDto sendMessage = new SendMessageDto("", 0L, new GroupActor(111L, "token"), null, false, false, null);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(sendMessage);

        logChatActionService.forwardMessageToLogChat(routingData, currentChat, message);

        verify(vkChatClient).sendText(sendMessage);
        assertThat(sendMessage.getForward()).isNotNull();
        assertThat(sendMessage.getForward().getConversationMessageIds()).contains(convMessageId);
        assertThat(sendMessage.isLogChatForward()).isTrue();
        verify(chatService, never()).setBoundLogChatAsNull(anyLong());
    }

    @Test
    void shouldForwardSuccessfullyWithSubmanager() throws Exception {
        currentChat.setBoundLogChat(logChatId);
        currentChat.setBoundSubmanagerId(submanagerId);

        ChatDetailsDto logChat = new ChatDetailsDto();
        logChat.setBoundSubmanagerId(submanagerId);
        given(chatService.getCachedChatDetails(logChatId, false)).willReturn(logChat);
        given(chatService.getSubmanagerChatIdByMainChatId(submanagerId, logChatId))
                .willReturn(submanagerChatId);

        SendMessageDto sendMessage = new SendMessageDto("", 0L, new GroupActor(111L, "token"), null, false, false, null);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(sendMessage);

        logChatActionService.forwardMessageToLogChat(routingData, currentChat, message);

        verify(vkChatClient).sendText(sendMessage);
        assertThat(sendMessage.getResponsePeerId()).isEqualTo(convertToPeerId(submanagerChatId));
        verify(chatService, never()).setBoundLogChatAsNull(anyLong());
    }

    @Test
    void shouldSendErrorOnCannotForward() throws Exception {
        currentChat.setBoundLogChat(logChatId);
        ChatDetailsDto logChat = new ChatDetailsDto();
        logChat.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(logChatId, false)).willReturn(logChat);

        ApiException apiEx = mock(ApiException.class);
        given(apiEx.getCode()).willReturn(999);
        given(apiEx.getMessage()).willReturn("Some error");
        doThrow(apiEx).when(vkChatClient).sendText(any(SendMessageDto.class));

        logChatActionService.forwardMessageToLogChat(routingData, currentChat, message);

        verify(vkChatClient, times(2)).sendText(any(SendMessageDto.class));
        verify(chatService, never()).setBoundLogChatAsNull(anyLong());

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient, times(2)).sendText(captor.capture());
        List<SendMessageDto> messages = captor.getAllValues();
        assertThat(messages.get(1).getText()).contains("Произошла ошибка");
        assertThat(messages.get(1).getText()).contains("Some error");
        assertThat(messages.get(1).getForward()).isNull();
        assertThat(messages.get(1).getResponsePeerId()).isEqualTo(convertToPeerId(chatId));
    }

    @Test
    void shouldUnbindAndNotifyOnAccessError() throws Exception {
        currentChat.setBoundLogChat(logChatId);
        ChatDetailsDto logChat = new ChatDetailsDto();
        logChat.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(logChatId, false)).willReturn(logChat);

        ApiException apiEx = mock(ApiException.class);
        given(apiEx.getCode()).willReturn(15);
        doThrow(apiEx).when(vkChatClient).sendText(any(SendMessageDto.class));

        logChatActionService.forwardMessageToLogChat(routingData, currentChat, message);

        verify(chatService).setBoundLogChatAsNull(chatId);
        verify(vkChatClient, times(2)).sendText(any(SendMessageDto.class));

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient, times(2)).sendText(captor.capture());
        List<SendMessageDto> messages = captor.getAllValues();
        assertThat(messages.get(1).getText()).contains("исключили из привязанного логчата");
        assertThat(messages.get(1).getForward()).isNull();
        assertThat(messages.get(1).getResponsePeerId()).isEqualTo(convertToPeerId(chatId));
        assertThat(messages.get(1).isLogChatForward()).isFalse();
    }

    @Test
    void shouldNotThrowIfErrorNotificationFails() throws Exception {
        currentChat.setBoundLogChat(logChatId);
        ChatDetailsDto logChat = new ChatDetailsDto();
        logChat.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(logChatId, false)).willReturn(logChat);

        ApiException apiEx = mock(ApiException.class);
        given(apiEx.getCode()).willReturn(15);
        doThrow(apiEx).when(vkChatClient).sendText(any(SendMessageDto.class));

        logChatActionService.forwardMessageToLogChat(routingData, currentChat, message);

        verify(chatService).setBoundLogChatAsNull(chatId);
        verify(vkChatClient, times(2)).sendText(any(SendMessageDto.class));
    }

    private long convertToPeerId(long chatId) {
        return chatId + 2000000000L;
    }
}