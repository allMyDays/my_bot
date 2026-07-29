package com.example.my_bot.unit.command.commands.warn;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.warn.UnwarnCommand;
import com.example.my_bot.command.commands.warn.WarnCommand;
import com.example.my_bot.command.commands.warn.WarnLimitChangeCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.event.DataForEventExecution;
import com.example.my_bot.dto.event.ExecuteChatEventsResult;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.dto.warn.CreateWarnResult;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.warn.WarnException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.WarnService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventExecutionService;
import com.example.my_bot.utils.TextUtils;
import com.example.my_bot.utils.TimeUtils;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.vk.api.sdk.objects.base.Error;

import java.time.Instant;
import java.util.Optional;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class WarnLimitChangeCommandTest {

    private static final long CHAT_ID = 100L;
    private static final int NEW_LIMIT = 5;

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

    private WarnLimitChangeCommand warnLimitChangeCommand;

    @BeforeEach
    void setUp() {
        warnLimitChangeCommand = new WarnLimitChangeCommand(messageMapper);
        warnLimitChangeCommand.setChatService(chatService, vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldChangeWarnLimitSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            when(chatService.setMaxWarnLimit(CHAT_ID, NEW_LIMIT)).thenReturn(NEW_LIMIT);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = warnLimitChangeCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(chatService).setMaxWarnLimit(CHAT_ID, NEW_LIMIT);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅ Теперь участники получат наказание если наберут 5 предупреждений.", actual);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = warnLimitChangeCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(chatService, never()).setMaxWarnLimit(anyLong(), anyInt());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidInteger() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"abc"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("abc")).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = warnLimitChangeCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным числовым значением."));
            verify(chatService, never()).setMaxWarnLimit(anyLong(), anyInt());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            when(chatService.setMaxWarnLimit(CHAT_ID, NEW_LIMIT))
                    .thenThrow(new RuntimeException("DB error"));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            assertThrows(RuntimeException.class, () -> warnLimitChangeCommand.execute(commandMessage));
            verify(chatService).setMaxWarnLimit(CHAT_ID, NEW_LIMIT);
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            when(chatService.setMaxWarnLimit(CHAT_ID, NEW_LIMIT)).thenReturn(NEW_LIMIT);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK client error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ClientException.class, () -> warnLimitChangeCommand.execute(commandMessage));
            verify(chatService).setMaxWarnLimit(CHAT_ID, NEW_LIMIT);
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            when(chatService.setMaxWarnLimit(CHAT_ID, NEW_LIMIT)).thenReturn(NEW_LIMIT);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> warnLimitChangeCommand.execute(commandMessage));
            verify(chatService).setMaxWarnLimit(CHAT_ID, NEW_LIMIT);
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }
}