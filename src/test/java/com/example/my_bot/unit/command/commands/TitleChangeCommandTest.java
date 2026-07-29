package com.example.my_bot.unit.command.commands;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.TitleChangeCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
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


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TitleChangeCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long VK_API_CHAT_ID = 12345L;
    private static final String NEW_TITLE = "Новое название";
    private static final String[] ARGS = {"Новое", "название"};

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private TitleChangeCommand titleChangeCommand;

    @BeforeEach
    void setUp() {
        titleChangeCommand = new TitleChangeCommand(messageMapper, chatService);
        titleChangeCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getVkApiChatId()).thenReturn(VK_API_CHAT_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
    }

    @Test
    void shouldChangeTitleSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        doNothing().when(vkChatClient).changeChatTitle(groupActor, VK_API_CHAT_ID, NEW_TITLE);

        CommandExecutionStatus status = titleChangeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).changeChatTitle(groupActor, VK_API_CHAT_ID, NEW_TITLE);
        verify(chatService).setChatTitle(CHAT_ID, NEW_TITLE);
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = titleChangeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(vkChatClient, never()).changeChatTitle(any(), anyLong(), anyString());
        verify(chatService, never()).setChatTitle(anyLong(), anyString());
    }

    @Test
    void shouldReturnVkApiErrorWithGenericMessage() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        // Создаём ApiException с кодом, который не входит в списки
        int otherCode = 999;
        Error error = new Error().setErrorCode(otherCode).setErrorMsg("Some API error");
        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(vkChatClient).changeChatTitle(groupActor, VK_API_CHAT_ID, NEW_TITLE);

        CommandExecutionStatus status = titleChangeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.VK_API_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Произошла ошибка: " + apiException.getMessage()));
        verify(chatService, never()).setChatTitle(anyLong(), anyString());
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientChangeTitle() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(ARGS);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).changeChatTitle(groupActor, VK_API_CHAT_ID, NEW_TITLE);

        assertThrows(ClientException.class, () -> titleChangeCommand.execute(commandMessage));
        verify(vkChatClient).changeChatTitle(groupActor, VK_API_CHAT_ID, NEW_TITLE);
        verify(vkChatClient, never()).sendText(any());
        verify(chatService, never()).setChatTitle(anyLong(), anyString());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> titleChangeCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
        verify(vkChatClient, never()).changeChatTitle(any(), anyLong(), anyString());
        verify(chatService, never()).setChatTitle(anyLong(), anyString());
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> titleChangeCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
        verify(vkChatClient, never()).changeChatTitle(any(), anyLong(), anyString());
        verify(chatService, never()).setChatTitle(anyLong(), anyString());
    }
}