package com.example.my_bot.unit.command.commands.member;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.member.AssignMemberCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.AssignMemberResult;
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
import com.vk.api.sdk.objects.base.Error;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class AssignMemberCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long USER_TO_ASSIGN = 300L;
    private static final long BOT_GROUP_ID = 400L;
    private static final String ROLE_NAME = "Модератор";
    private static final int ROLE_PRIORITY = 5;
    private static final String OLD_ROLE_NAME = "Участник";
    private static final String USER_NAME_GENITIVE = "Ивана Иванова";

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

    private AssignMemberCommand assignMemberCommand;

    @BeforeEach
    void setUp() {
        assignMemberCommand = new AssignMemberCommand(
                vkChatClient,
                memberService,
                userInputResolver,
                userService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(groupActor.getGroupId()).thenReturn(BOT_GROUP_ID);
    }

    @Test
    void shouldAssignRoleByNameSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleDto oldRole = new RoleDto(OLD_ROLE_NAME, 0);
        RoleDto newRole = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_ASSIGN, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).assignNewRoleToMember(CHAT_ID, USER_TO_ASSIGN, ROLE_NAME, FROM_ID);
        verify(userService).getUserFullNameInRequiredCase(USER_TO_ASSIGN, NameCase.GENITIVE);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains(USER_NAME_GENITIVE));
        assertTrue(actual.contains(OLD_ROLE_NAME));
        assertTrue(actual.contains(ROLE_NAME));
        assertTrue(actual.contains("@id" + USER_TO_ASSIGN) || actual.contains("[id" + USER_TO_ASSIGN));
    }

    @Test
    void shouldAssignRoleByPrioritySuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", "5"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleDto oldRole = new RoleDto(OLD_ROLE_NAME, 0);
        RoleDto newRole = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_PRIORITY), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_ASSIGN, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).assignNewRoleToMember(CHAT_ID, USER_TO_ASSIGN, ROLE_PRIORITY, FROM_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains(ROLE_NAME));
        assertTrue(actual.contains(OLD_ROLE_NAME));
    }

    @Test
    void shouldAssignRoleWithFwdMessageSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, true);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleDto oldRole = new RoleDto(OLD_ROLE_NAME, 0);
        RoleDto newRole = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_ASSIGN, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).assignNewRoleToMember(CHAT_ID, USER_TO_ASSIGN, ROLE_NAME, FROM_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains(ROLE_NAME));
        assertTrue(actual.contains(OLD_ROLE_NAME));
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenAssigningToBot() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@bot"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(-BOT_GROUP_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        CommandExecutionStatus status = assignMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient, never()).sendText(any());
        verify(memberService, never()).assignNewRoleToMember(anyLong(), anyLong(), any(), anyLong());
    }


    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        MemberException exception = new MemberException("Участник не найден") {};
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleException exception = new RoleException("Роль не найдена") {};
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        CommandException exception = new CommandException("Ошибка команды") {};
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = assignMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleDto oldRole = new RoleDto(OLD_ROLE_NAME, 0);
        RoleDto newRole = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_ASSIGN, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> assignMemberCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleDto oldRole = new RoleDto(OLD_ROLE_NAME, 0);
        RoleDto newRole = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_ASSIGN, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> assignMemberCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenThrow(new RuntimeException("Unexpected DB error"));

        assertThrows(RuntimeException.class, () -> assignMemberCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromUserService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user", ROLE_NAME});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_ASSIGN, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RoleDto oldRole = new RoleDto(OLD_ROLE_NAME, 0);
        RoleDto newRole = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_ASSIGN), eq(ROLE_NAME), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_ASSIGN, NameCase.GENITIVE))
                .thenThrow(new RuntimeException("User service error"));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        assertThrows(RuntimeException.class, () -> assignMemberCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }
}

