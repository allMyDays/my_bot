package com.example.my_bot.unit.command.commands;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.WriteCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.utils.ChatUtils;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WriteCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long VK_API_CHAT_ID = 12345L;
    private static final long PEER_ID = VK_API_CHAT_ID;
    private static final long ORIGINAL_PEER_ID = PEER_ID;
    private static final long RESPONSE_PEER_ID = PEER_ID;
    private static final int CONVERSATION_MESSAGE_ID = 42;
    private static final String USER_TEXT = "!напиши привет";
    private static final String MESSAGE_TEXT = "привет";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private SubmanagerService submanagerService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private WriteCommand writeCommand;

    @BeforeEach
    void setUp() {
        writeCommand = new WriteCommand(messageMapper, submanagerService);
        writeCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getVkApiChatId()).thenReturn(VK_API_CHAT_ID);
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(ORIGINAL_PEER_ID);
        when(commandRoutingData.getResponsePeerId()).thenReturn(RESPONSE_PEER_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(commandMessage.getConversationMessageId()).thenReturn(CONVERSATION_MESSAGE_ID);
        when(commandMessage.isReplyToMessageId()).thenReturn(true);
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenNotSubmanager() throws ClientException, ApiException {
        when(submanagerService.isSubmanager(groupActor)).thenReturn(false);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = writeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("субменеджер") || captor.getValue().getText().contains("сабменеджер"));
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandRoutingData.class), anyInt(), anyBoolean());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(submanagerService.isSubmanager(groupActor)).thenReturn(true);
        when(commandMessage.getOptionalUserText()).thenReturn(Optional.of(USER_TEXT));

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        try (MockedStatic<UserInputResolver> resolverMock = mockStatic(UserInputResolver.class)) {
            resolverMock.when(() -> UserInputResolver.splitFullCommandIntoTwoElements(USER_TEXT))
                    .thenReturn(new String[]{"напиши"});

            CommandExecutionStatus status = writeCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(initialSendMessage);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Недостаточно") || captor.getValue().getText().contains("аргумент"));
            verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandRoutingData.class), anyInt(), anyBoolean());
        }
    }

    @Test
    void shouldSetReplyToMessageIdFalseWhenOriginalPeerDiffers() throws ClientException, ApiException {
        when(commandRoutingData.getOriginalEventPeerId()).thenReturn(ORIGINAL_PEER_ID + 1);
        when(submanagerService.isSubmanager(groupActor)).thenReturn(true);
        when(commandMessage.getOptionalUserText()).thenReturn(Optional.of(USER_TEXT));

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        try (MockedStatic<UserInputResolver> resolverMock = mockStatic(UserInputResolver.class);
             MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {

            resolverMock.when(() -> UserInputResolver.splitFullCommandIntoTwoElements(USER_TEXT))
                    .thenReturn(new String[]{"напиши", MESSAGE_TEXT});
            chatUtilsMock.when(() -> ChatUtils.convertToPeerId(VK_API_CHAT_ID)).thenReturn(VK_API_CHAT_ID);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(MESSAGE_TEXT), any(CommandRoutingData.class), eq(CONVERSATION_MESSAGE_ID), eq(false)))
                    .thenReturn(sendMessageDto);

            writeCommand.execute(commandMessage);
            verify(messageMapper).toSendMessageDto(eq(MESSAGE_TEXT), any(CommandRoutingData.class), eq(CONVERSATION_MESSAGE_ID), eq(false));
        }
    }

    @Test
    void shouldKeepReplyToMessageIdTrueWhenOriginalPeerSame() throws ClientException, ApiException {
        when(submanagerService.isSubmanager(groupActor)).thenReturn(true);
        when(commandMessage.getOptionalUserText()).thenReturn(Optional.of(USER_TEXT));

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        try (MockedStatic<UserInputResolver> resolverMock = mockStatic(UserInputResolver.class);
             MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {

            resolverMock.when(() -> UserInputResolver.splitFullCommandIntoTwoElements(USER_TEXT))
                    .thenReturn(new String[]{"напиши", MESSAGE_TEXT});
            chatUtilsMock.when(() -> ChatUtils.convertToPeerId(VK_API_CHAT_ID)).thenReturn(VK_API_CHAT_ID);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(MESSAGE_TEXT), any(CommandRoutingData.class), eq(CONVERSATION_MESSAGE_ID), eq(true)))
                    .thenReturn(sendMessageDto);

            writeCommand.execute(commandMessage);
            verify(messageMapper).toSendMessageDto(eq(MESSAGE_TEXT), any(CommandRoutingData.class), eq(CONVERSATION_MESSAGE_ID), eq(true));
        }
    }


}
