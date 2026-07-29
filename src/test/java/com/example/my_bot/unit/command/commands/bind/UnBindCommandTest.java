package com.example.my_bot.unit.command.commands.bind;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.bind.UnBindCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.member.MemberNotFoundException;
import com.example.my_bot.exception.user.GlobalGlobalUserNotFoundException;
import com.example.my_bot.exception.user.GlobalUserException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
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

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnBindCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long TARGET_USER_ID = 300L;
    private static final String CHAT_TITLE = "Тестовый чат";
    private static final String CHAT_CODE = "ABC123";
    private static final String USER_NAME_DATIVE = "Ивану Иванову";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private GlobalUserService userService;

    @Mock
    private UserInputResolver userInputResolver;

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

    private UnBindCommand unBindCommand;

    @BeforeEach
    void setUp() {
        unBindCommand = new UnBindCommand(
                userService,
                userInputResolver,
                messageMapper,
                chatService
        );
        unBindCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldUnbindExplicitUserSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(TARGET_USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);

        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setChatTitle(CHAT_TITLE);
        chatDetails.setChatCode(CHAT_CODE);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(chatDetails);

        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.DATIVE))
                .thenReturn(USER_NAME_DATIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = unBindCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);
        verify(chatService).getCachedChatDetails(CHAT_ID, false);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("Вы успешно сняли с"));
        assertTrue(actual.contains("@id" + TARGET_USER_ID + "(" + USER_NAME_DATIVE + ")"));
        assertTrue(actual.contains("привязку чата «" + CHAT_TITLE + "»"));
        assertTrue(actual.contains("UID «" + CHAT_CODE + "»"));
    }

    @Test
    void shouldUnbindSelfWhenNoUserArgument() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(userService).unBindChatFromUser(CHAT_ID, FROM_ID, FROM_ID);

        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setChatTitle(CHAT_TITLE);
        chatDetails.setChatCode(CHAT_CODE);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(chatDetails);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = unBindCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(userService).unBindChatFromUser(CHAT_ID, FROM_ID, FROM_ID);
        verify(chatService).getCachedChatDetails(CHAT_ID, false);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("Вы успешно сняли с"));
        assertTrue(actual.contains("@id" + FROM_ID + "(себя)"));
        assertTrue(actual.contains("привязку чата «" + CHAT_TITLE + "»"));
        assertTrue(actual.contains("UID «" + CHAT_CODE + "»"));
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(TARGET_USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        MemberException exception = new MemberNotFoundException(TARGET_USER_ID, CHAT_ID);
        doThrow(exception).when(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = unBindCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
        verify(chatService, never()).getCachedChatDetails(anyLong(), anyBoolean());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnGlobalUserException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(TARGET_USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        GlobalUserException exception = new GlobalGlobalUserNotFoundException(TARGET_USER_ID);
        doThrow(exception).when(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = unBindCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
        verify(chatService, never()).getCachedChatDetails(anyLong(), anyBoolean());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromUserService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(TARGET_USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RuntimeException exception = new RuntimeException("Unexpected error");
        doThrow(exception).when(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);

        assertThrows(RuntimeException.class, () -> unBindCommand.execute(commandMessage));

        verify(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);
        verify(vkChatClient, never()).sendText(any());
        verify(chatService, never()).getCachedChatDetails(anyLong(), anyBoolean());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(TARGET_USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);

        RuntimeException exception = new RuntimeException("Chat service error");
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        assertThrows(RuntimeException.class, () -> unBindCommand.execute(commandMessage));

        verify(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);
        verify(chatService).getCachedChatDetails(CHAT_ID, false);
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(TARGET_USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);

        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setChatTitle(CHAT_TITLE);
        chatDetails.setChatCode(CHAT_CODE);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(chatDetails);

        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.DATIVE))
                .thenReturn(USER_NAME_DATIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> unBindCommand.execute(commandMessage));

        verify(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);
        verify(chatService).getCachedChatDetails(CHAT_ID, false);
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(TARGET_USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);

        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setChatTitle(CHAT_TITLE);
        chatDetails.setChatCode(CHAT_CODE);
        when(chatService.getCachedChatDetails(CHAT_ID, false)).thenReturn(chatDetails);

        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.DATIVE))
                .thenReturn(USER_NAME_DATIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> unBindCommand.execute(commandMessage));

        verify(userService).unBindChatFromUser(CHAT_ID, FROM_ID, TARGET_USER_ID);
        verify(chatService).getCachedChatDetails(CHAT_ID, false);
        verify(vkChatClient).sendText(sendMessageDto);
    }
}


