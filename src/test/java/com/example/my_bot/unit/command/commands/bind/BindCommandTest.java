package com.example.my_bot.unit.command.commands.bind;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.client.VkCommunityClient;
import com.example.my_bot.command.commands.bind.BindCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;

@ExtendWith(MockitoExtension.class)
class BindCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long PEER_ID = 200L;
    private static final long PERSONAL_PEER_ID = 300L;
    private static final long USER_ID = 400L;
    private static final long MAIN_BOT_ID = 500L;
    private static final String CHAT_TITLE = "Тестовый чат";
    private static final String CHAT_CODE = "ABC123";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private VkCommunityClient vkCommunityClient;

    @Mock
    private GlobalUserService userService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private GroupActor theMainBotGroupActor;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    private BindCommand bindCommand;

    @BeforeEach
    void setUp() {
        bindCommand = new BindCommand(
                vkChatClient,
                vkCommunityClient,
                userService,
                messageMapper,
                chatService,
                MAIN_BOT_ID,
                theMainBotGroupActor
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(PEER_ID);
        when(commandMessage.getFromId()).thenReturn(USER_ID);
    }

    @Test
    void shouldShowChatInfoWhenPersonalChat() throws ClientException, ApiException {
        try (MockedStatic<ChatUtils> chatUtils = mockStatic(ChatUtils.class)) {
            chatUtils.when(() -> ChatUtils.isPersonalChat(PERSONAL_PEER_ID)).thenReturn(true);

            when(commandRoutingData.getOriginalEventPeerId()).thenReturn(PERSONAL_PEER_ID);

            ChatDetailsDto chatDetails = new ChatDetailsDto();
            chatDetails.setChatTitle(CHAT_TITLE);
            chatDetails.setChatCode(CHAT_CODE);
            when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(chatDetails);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = bindCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(chatService).getCachedChatDetails(CHAT_ID, false);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();

            assertTrue(actual.contains("Информация о привязанном чате"));
            assertTrue(actual.contains(CHAT_TITLE));
            assertTrue(actual.contains(CHAT_CODE));
            verify(vkCommunityClient, never()).canTheMainBotWriteToUser(anyLong());
            verify(userService, never()).bindChatToUser(anyLong(), anyLong());
        }
    }

    @Test
    void shouldBindChatAndSendPersonalMessageSuccess() throws ClientException, ApiException {
        try (MockedStatic<ChatUtils> chatUtils = mockStatic(ChatUtils.class)) {
            chatUtils.when(() -> ChatUtils.isPersonalChat(PEER_ID)).thenReturn(false);

            when(vkCommunityClient.canTheMainBotWriteToUser(USER_ID)).thenReturn(true);
            doNothing().when(userService).bindChatToUser(CHAT_ID, USER_ID);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            when(vkChatClient.sendText(any(SendMessageDto.class))).thenReturn(null);

            CommandExecutionStatus status = bindCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(userService).bindChatToUser(CHAT_ID, USER_ID);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient, times(1)).sendText(captor.capture());
            SendMessageDto sent = captor.getValue();
            assertEquals(USER_ID, sent.getResponsePeerId());
            assertEquals(theMainBotGroupActor, sent.getResponderBot());
            assertFalse(sent.isReplyToMessageId());
            assertTrue(sent.getText().contains("Вы успешно привязали чат к своим личным сообщениям."));
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromVkCommunityClient() throws ClientException, ApiException {
        try (MockedStatic<ChatUtils> chatUtils = mockStatic(ChatUtils.class)) {
            chatUtils.when(() -> ChatUtils.isPersonalChat(PEER_ID)).thenReturn(false);

            RuntimeException runtimeException = new RuntimeException("VK client error");
            when(vkCommunityClient.canTheMainBotWriteToUser(USER_ID)).thenThrow(runtimeException);

            assertThrows(RuntimeException.class, () -> bindCommand.execute(commandMessage));
            verify(vkCommunityClient).canTheMainBotWriteToUser(USER_ID);
            verify(vkChatClient, never()).sendText(any());
            verify(userService, never()).bindChatToUser(anyLong(), anyLong());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromUserServiceBind() throws ClientException, ApiException {
        try (MockedStatic<ChatUtils> chatUtils = mockStatic(ChatUtils.class)) {
            chatUtils.when(() -> ChatUtils.isPersonalChat(PEER_ID)).thenReturn(false);

            when(vkCommunityClient.canTheMainBotWriteToUser(USER_ID)).thenReturn(true);
            RuntimeException runtimeException = new RuntimeException("Bind error");
            doThrow(runtimeException).when(userService).bindChatToUser(CHAT_ID, USER_ID);

            assertThrows(RuntimeException.class, () -> bindCommand.execute(commandMessage));
            verify(userService).bindChatToUser(CHAT_ID, USER_ID);
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        try (MockedStatic<ChatUtils> chatUtils = mockStatic(ChatUtils.class)) {
            chatUtils.when(() -> ChatUtils.isPersonalChat(PERSONAL_PEER_ID)).thenReturn(true);

            when(commandRoutingData.getOriginalEventPeerId()).thenReturn(PERSONAL_PEER_ID);
            when(chatService.getCachedChatDetails(CHAT_ID, false)).thenThrow(new RuntimeException("Service error"));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, theMainBotGroupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            assertThrows(RuntimeException.class, () -> bindCommand.execute(commandMessage));
            verify(chatService).getCachedChatDetails(CHAT_ID, false);
            verify(vkChatClient, never()).sendText(any());
        }
    }



}
