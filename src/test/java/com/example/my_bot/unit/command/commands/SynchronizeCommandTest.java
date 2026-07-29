package com.example.my_bot.unit.command.commands;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.SynchronizeCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SynchronizeCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long VK_API_CHAT_ID = 12345L;
    private static final String CHAT_TITLE = "Тестовый чат";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MemberService memberService;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private SynchronizeCommand synchronizeCommand;

    @BeforeEach
    void setUp() {
        synchronizeCommand = new SynchronizeCommand(memberService, chatService, messageMapper);
        synchronizeCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getVkApiChatId()).thenReturn(VK_API_CHAT_ID);
    }

    @Test
    void shouldSynchronizeSuccess() throws ClientException, ApiException {
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(vkChatClient.getChatTitle(VK_API_CHAT_ID, groupActor)).thenReturn(Optional.of(CHAT_TITLE));
        doNothing().when(memberService).synchronizeChatMembers(commandRoutingData);

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        SendMessageDto successMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq("✅Информация чата была синхронизирована с моей базой данных."), any(CommandMessageDto.class)))
                .thenReturn(successMessage);

        CommandExecutionStatus status = synchronizeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService).setChatTitle(CHAT_ID, CHAT_TITLE);
        verify(memberService).synchronizeChatMembers(commandRoutingData);
        verify(vkChatClient).sendText(successMessage);
        verify(vkChatClient, never()).sendText(initialSendMessage);
    }

    @Test
    void shouldSynchronizeSuccessWithoutChatTitle() throws ClientException, ApiException {
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(vkChatClient.getChatTitle(VK_API_CHAT_ID, groupActor)).thenReturn(Optional.empty());
        doNothing().when(memberService).synchronizeChatMembers(commandRoutingData);

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        SendMessageDto successMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq("✅Информация чата была синхронизирована с моей базой данных."), any(CommandMessageDto.class)))
                .thenReturn(successMessage);

        CommandExecutionStatus status = synchronizeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(chatService, never()).setChatTitle(anyLong(), anyString());
        verify(memberService).synchronizeChatMembers(commandRoutingData);
        verify(vkChatClient).sendText(successMessage);
    }

    @Test
    void shouldReturnVkApiErrorWhenNoChatAccess() throws ClientException, ApiException {
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(vkChatClient.getChatTitle(VK_API_CHAT_ID, groupActor)).thenReturn(Optional.of(CHAT_TITLE));

        int noAccessCode = 15;
        Error error = new Error().setErrorCode(noAccessCode);
        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(memberService).synchronizeChatMembers(commandRoutingData);

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        CommandExecutionStatus status = synchronizeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
        verify(chatService).setChatTitle(CHAT_ID, CHAT_TITLE);
        verify(memberService).synchronizeChatMembers(commandRoutingData);
        verify(vkChatClient).sendText(initialSendMessage);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("доступа") || actual.contains("доступ"));
        verify(messageMapper, never()).toSendMessageDto(eq("✅Информация чата была синхронизирована с моей базой данных."), any(CommandMessageDto.class));
    }

    @Test
    void shouldReturnVkApiErrorWithGenericMessage() throws ClientException, ApiException {
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(vkChatClient.getChatTitle(VK_API_CHAT_ID, groupActor)).thenReturn(Optional.of(CHAT_TITLE));

        int otherCode = 999;
        Error error = new Error().setErrorCode(otherCode).setErrorMsg("Some API error");
        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(memberService).synchronizeChatMembers(commandRoutingData);

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        CommandExecutionStatus status = synchronizeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
        verify(vkChatClient).sendText(initialSendMessage);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Произошла ошибка:"));
        assertTrue(actual.contains("Some API error"));
        verify(messageMapper, never()).toSendMessageDto(eq("✅Информация чата была синхронизирована с моей базой данных."), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromGetChatTitle() throws ClientException, ApiException {
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        ClientException clientException = new ClientException("VK client error");
        when(vkChatClient.getChatTitle(VK_API_CHAT_ID, groupActor)).thenThrow(clientException);

        assertThrows(ClientException.class, () -> synchronizeCommand.execute(commandMessage));
        verify(memberService, never()).synchronizeChatMembers(any());
        verify(vkChatClient, never()).sendText(any());
        verify(chatService, never()).setChatTitle(anyLong(), anyString());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(vkChatClient.getChatTitle(VK_API_CHAT_ID, groupActor)).thenReturn(Optional.of(CHAT_TITLE));

        int noAccessCode = 15;
        Error error = new Error().setErrorCode(noAccessCode);
        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(memberService).synchronizeChatMembers(commandRoutingData);

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> synchronizeCommand.execute(commandMessage));
        verify(vkChatClient).sendText(initialSendMessage);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(vkChatClient.getChatTitle(VK_API_CHAT_ID, groupActor)).thenReturn(Optional.of(CHAT_TITLE));

        int noAccessCode = 15;
        Error error = new Error().setErrorCode(noAccessCode);
        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(memberService).synchronizeChatMembers(commandRoutingData);

        SendMessageDto initialSendMessage = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(initialSendMessage);

        ApiException sendApiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API send error"));
        doThrow(sendApiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> synchronizeCommand.execute(commandMessage));
        verify(vkChatClient).sendText(initialSendMessage);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromSynchronizeChatMembers() throws ClientException, ApiException {
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(vkChatClient.getChatTitle(VK_API_CHAT_ID, groupActor)).thenReturn(Optional.of(CHAT_TITLE));

        RuntimeException runtimeException = new RuntimeException("Unexpected error");
        doThrow(runtimeException).when(memberService).synchronizeChatMembers(commandRoutingData);

        assertThrows(RuntimeException.class, () -> synchronizeCommand.execute(commandMessage));
        verify(chatService).setChatTitle(CHAT_ID, CHAT_TITLE);
        verify(vkChatClient, never()).sendText(any());
    }
}
