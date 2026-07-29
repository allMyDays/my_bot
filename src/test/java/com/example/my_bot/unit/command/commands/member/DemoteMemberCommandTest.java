package com.example.my_bot.unit.command.commands.member;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.member.AssignMemberCommand;
import com.example.my_bot.command.commands.member.DemoteMemberCommand;
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
import com.example.my_bot.service.RoleService;
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
class DemoteMemberCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long USER_TO_DEMOTE = 300L;
    private static final long BOT_GROUP_ID = 400L;
    private static final int CURRENT_ROLE_PRIORITY = 5;
    private static final int NEW_ROLE_PRIORITY = 3;
    private static final String CURRENT_ROLE_NAME = "Модератор";
    private static final String NEW_ROLE_NAME = "Пользователь";
    private static final String USER_NAME_GENITIVE = "Ивана Иванова";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MemberService memberService;

    @Mock
    private RoleService roleService;

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

    private DemoteMemberCommand demoteMemberCommand;

    @BeforeEach
    void setUp() {
        demoteMemberCommand = new DemoteMemberCommand(
                vkChatClient,
                memberService,
                roleService,
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
    void shouldDemoteMemberSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenReturn(CURRENT_ROLE_PRIORITY);

        RoleDto newRole = new RoleDto(NEW_ROLE_NAME, NEW_ROLE_PRIORITY);
        when(roleService.findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false))
                .thenReturn(newRole);

        RoleDto oldRole = new RoleDto(CURRENT_ROLE_NAME, CURRENT_ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_DEMOTE), eq(NEW_ROLE_PRIORITY), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_DEMOTE, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = demoteMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE);
        verify(roleService).findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false);
        verify(memberService).assignNewRoleToMember(CHAT_ID, USER_TO_DEMOTE, NEW_ROLE_PRIORITY, FROM_ID);
        verify(userService).getUserFullNameInRequiredCase(USER_TO_DEMOTE, NameCase.GENITIVE);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains(USER_NAME_GENITIVE));
        assertTrue(actual.contains(CURRENT_ROLE_NAME));
        assertTrue(actual.contains(NEW_ROLE_NAME));
        assertTrue(actual.contains("@id" + USER_TO_DEMOTE) || actual.contains("[id" + USER_TO_DEMOTE));
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenDemotingBot() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@bot"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(-BOT_GROUP_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        CommandExecutionStatus status = demoteMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient, never()).sendText(any());
        verify(memberService, never()).getMemberRolePriority(anyLong(), anyLong());
        verify(roleService, never()).findTheNearestLowestRole(anyLong(), anyInt(), anyBoolean());
        verify(memberService, never()).assignNewRoleToMember(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberExceptionFromGetRole() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        MemberException exception = new MemberException("Участник не найден") {};
        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = demoteMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleExceptionFromFindRole() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenReturn(CURRENT_ROLE_PRIORITY);

        RoleException exception = new RoleException("Нет роли ниже") {};
        when(roleService.findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = demoteMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandExceptionFromAssign() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenReturn(CURRENT_ROLE_PRIORITY);

        RoleDto newRole = new RoleDto(NEW_ROLE_NAME, NEW_ROLE_PRIORITY);
        when(roleService.findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false))
                .thenReturn(newRole);

        CommandException exception = new CommandException("Ошибка назначения") {};
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_DEMOTE), eq(NEW_ROLE_PRIORITY), eq(FROM_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = demoteMemberCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenReturn(CURRENT_ROLE_PRIORITY);

        RoleDto newRole = new RoleDto(NEW_ROLE_NAME, NEW_ROLE_PRIORITY);
        when(roleService.findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false))
                .thenReturn(newRole);

        RoleDto oldRole = new RoleDto(CURRENT_ROLE_NAME, CURRENT_ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_DEMOTE), eq(NEW_ROLE_PRIORITY), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_DEMOTE, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> demoteMemberCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenReturn(CURRENT_ROLE_PRIORITY);

        RoleDto newRole = new RoleDto(NEW_ROLE_NAME, NEW_ROLE_PRIORITY);
        when(roleService.findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false))
                .thenReturn(newRole);

        RoleDto oldRole = new RoleDto(CURRENT_ROLE_NAME, CURRENT_ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_DEMOTE), eq(NEW_ROLE_PRIORITY), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_DEMOTE, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> demoteMemberCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberServiceGetRole() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> demoteMemberCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromRoleServiceFindRole() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenReturn(CURRENT_ROLE_PRIORITY);

        when(roleService.findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false))
                .thenThrow(new RuntimeException("Role service error"));

        assertThrows(RuntimeException.class, () -> demoteMemberCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberServiceAssign() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenReturn(CURRENT_ROLE_PRIORITY);

        RoleDto newRole = new RoleDto(NEW_ROLE_NAME, NEW_ROLE_PRIORITY);
        when(roleService.findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false))
                .thenReturn(newRole);

        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_DEMOTE), eq(NEW_ROLE_PRIORITY), eq(FROM_ID)))
                .thenThrow(new RuntimeException("Assign error"));

        assertThrows(RuntimeException.class, () -> demoteMemberCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromUserService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_TO_DEMOTE, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, USER_TO_DEMOTE))
                .thenReturn(CURRENT_ROLE_PRIORITY);

        RoleDto newRole = new RoleDto(NEW_ROLE_NAME, NEW_ROLE_PRIORITY);
        when(roleService.findTheNearestLowestRole(CHAT_ID, CURRENT_ROLE_PRIORITY, false))
                .thenReturn(newRole);

        RoleDto oldRole = new RoleDto(CURRENT_ROLE_NAME, CURRENT_ROLE_PRIORITY);
        AssignMemberResult assignResult = new AssignMemberResult(oldRole, newRole);
        when(memberService.assignNewRoleToMember(eq(CHAT_ID), eq(USER_TO_DEMOTE), eq(NEW_ROLE_PRIORITY), eq(FROM_ID)))
                .thenReturn(assignResult);

        when(userService.getUserFullNameInRequiredCase(USER_TO_DEMOTE, NameCase.GENITIVE))
                .thenThrow(new RuntimeException("User service error"));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        assertThrows(RuntimeException.class, () -> demoteMemberCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }
}

