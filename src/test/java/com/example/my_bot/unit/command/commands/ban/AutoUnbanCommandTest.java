package com.example.my_bot.unit.command.commands.ban;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.ban.AutoUnbanCommand;
import com.example.my_bot.command.commands.ban.UnbanCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import com.vk.api.sdk.objects.base.ErrorInnerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoUnbanCommandTest {

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    @InjectMocks
    private AutoUnbanCommand autoUnbanCommand;

    @BeforeEach
    void setUp() {
        autoUnbanCommand = new AutoUnbanCommand(messageMapper);
        autoUnbanCommand.setChatService(chatService, vkChatClient);
    }

    @Test
    void shouldSwitchAutoUnbanToOnAndSendCorrectMessage() throws ClientException, ApiException {
        long databaseChatId = 123L;
        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(databaseChatId);

        when(chatService.switchAutoUnban(databaseChatId)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto(
                "some text", 1L, groupActor, null, false, false, null
        );

        when(messageMapper.toSendMessageDto(any(String.class), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = autoUnbanCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);

        verify(chatService).switchAutoUnban(databaseChatId);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), eq(commandMessage));
        String expectedText = "✅Теперь мне можно автоматически снимать бан с пользователей, которые были приглашены участниками, которым хватает прав на команду «"
                + UnbanCommand.class.getAnnotation(Command.class).mainCommandName() + "».";
        assertEquals(expectedText, textCaptor.getValue());

        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldSwitchAutoUnbanToOffAndSendCorrectMessage() throws ClientException, ApiException {
        long databaseChatId = 456L;
        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(databaseChatId);

        when(chatService.switchAutoUnban(databaseChatId)).thenReturn(SwitchChatSettingResult.OFF);

        SendMessageDto sendMessageDto = new SendMessageDto(
                "some text", 1L, groupActor, null, false, false, null
        );
        when(messageMapper.toSendMessageDto(any(String.class), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = autoUnbanCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);

        verify(chatService).switchAutoUnban(databaseChatId);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), eq(commandMessage));
        String expectedText = "✅Теперь мне нельзя автоматически снимать бан с пользователей, которые были приглашены участниками, которым хватает прав на команду «"
                + UnbanCommand.class.getAnnotation(Command.class).mainCommandName() + "».";
        assertEquals(expectedText, textCaptor.getValue());

        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateClientExceptionFromVkClient() throws ClientException, ApiException {
        long databaseChatId = 999L;
        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(databaseChatId);

        when(chatService.switchAutoUnban(databaseChatId)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto(
                "some text", 1L, groupActor, null, false, false, null
        );
        when(messageMapper.toSendMessageDto(any(String.class), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(sendMessageDto);

        assertThrows(ClientException.class, () -> autoUnbanCommand.execute(commandMessage));

        verify(chatService).switchAutoUnban(databaseChatId);
        verify(messageMapper).toSendMessageDto(any(String.class), any(CommandMessageDto.class));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkClient() throws ClientException, ApiException {
        long databaseChatId = 777L;
        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(databaseChatId);

        when(chatService.switchAutoUnban(databaseChatId)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto(
                "some text", 1L, groupActor, null, false, false, null
        );
        when(messageMapper.toSendMessageDto(any(String.class), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        Error error = new Error()
                .setErrorCode(2)
                .setErrorMsg("VK API error")
                .setErrorText("VK API error text");

        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(vkChatClient).sendText(sendMessageDto);

        assertThrows(ApiException.class, () -> autoUnbanCommand.execute(commandMessage));

        verify(chatService).switchAutoUnban(databaseChatId);
        verify(messageMapper).toSendMessageDto(any(String.class), any(CommandMessageDto.class));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldAlwaysReturnSuccess() throws ClientException, ApiException {
        long databaseChatId = 111L;
        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(databaseChatId);

        when(chatService.switchAutoUnban(databaseChatId)).thenReturn(SwitchChatSettingResult.ON);

        SendMessageDto sendMessageDto = new SendMessageDto(
                "some text", 1L, groupActor, null, false, false, null
        );
        when(messageMapper.toSendMessageDto(any(String.class), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = autoUnbanCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
    }
}