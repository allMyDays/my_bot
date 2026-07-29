package com.example.my_bot.unit.command.commands.immunity;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.immunity.AssignImmunityCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignImmunityCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long USER_TO_ALTER = 300L;
    private static final String ROLE_NAME = "Модератор";
    private static final int ROLE_PRIORITY = 5;
    private static final String USER_NAME_ACCUSATIVE = "Ивана Иванова";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MemberService memberService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private GlobalUserService userService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private AssignImmunityCommand assignImmunityCommand;

    @BeforeEach
    void setUp() {
        assignImmunityCommand = new AssignImmunityCommand(
                vkChatClient,
                memberService,
                userInputResolver,
                userService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignImmunityCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Недостаточно") || actual.contains("аргумент"));
        verify(memberService, never()).assignImmunityToMember(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoMemberFound() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"неверный"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignImmunityCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Укажите участника") || actual.contains("участник"));
        verify(memberService, never()).assignImmunityToMember(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArgumentsForNonFwd() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@durov"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ALTER, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignImmunityCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Недостаточно") || actual.contains("аргумент"));
        verify(memberService, never()).assignImmunityToMember(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@durov", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ALTER, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        MemberException exception = new MemberException("Участник не найден") {};
        when(memberService.assignImmunityToMember(eq(CHAT_ID), eq(USER_TO_ALTER), eq(ROLE_NAME), eq(FROM_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignImmunityCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@durov", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ALTER, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleException exception = new RoleException("Роль не найдена") {};
        when(memberService.assignImmunityToMember(eq(CHAT_ID), eq(USER_TO_ALTER), eq(ROLE_NAME), eq(FROM_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignImmunityCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@durov", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ALTER, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        CommandException exception = new CommandException("Ошибка команды") {};
        when(memberService.assignImmunityToMember(eq(CHAT_ID), eq(USER_TO_ALTER), eq(ROLE_NAME), eq(FROM_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignImmunityCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@durov", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ALTER, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleDto roleDto = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        when(memberService.assignImmunityToMember(eq(CHAT_ID), eq(USER_TO_ALTER), eq(ROLE_NAME), eq(FROM_ID)))
                .thenReturn(roleDto);
        when(userService.getUserFullNameInRequiredCase(USER_TO_ALTER, NameCase.ACCUSATIVE))
                .thenReturn(USER_NAME_ACCUSATIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> assignImmunityCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@durov", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ALTER, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleDto roleDto = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        when(memberService.assignImmunityToMember(eq(CHAT_ID), eq(USER_TO_ALTER), eq(ROLE_NAME), eq(FROM_ID)))
                .thenReturn(roleDto);
        when(userService.getUserFullNameInRequiredCase(USER_TO_ALTER, NameCase.ACCUSATIVE))
                .thenReturn(USER_NAME_ACCUSATIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> assignImmunityCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}