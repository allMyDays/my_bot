package com.example.my_bot.unit.command.commands.member;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.member.AssignMemberCommand;
import com.example.my_bot.command.commands.member.DemoteMemberCommand;
import com.example.my_bot.command.commands.member.MemberRoleShowCommand;
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

import java.util.Optional;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberRoleShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long TARGET_USER_ID = 300L;
    private static final long MAIN_BOT_ID = 400L;
    private static final int ROLE_PRIORITY = 5;
    private static final String ROLE_NAME = "Модератор";
    private static final String USER_NAME_GENITIVE = "Ивана Иванова";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MemberService memberService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private RoleService roleService;

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

    private MemberRoleShowCommand memberRoleShowCommand;

    @BeforeEach
    void setUp() {
        memberRoleShowCommand = new MemberRoleShowCommand(
                memberService,
                userInputResolver,
                roleService,
                userService,
                MAIN_BOT_ID,
                messageMapper
        );
        memberRoleShowCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldShowOwnRoleWhenNoMemberArgument() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(ROLE_PRIORITY);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                .thenReturn(Optional.of(ROLE_NAME));

        when(userService.getUserFullNameInRequiredCase(FROM_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = memberRoleShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getMemberRolePriority(CHAT_ID, FROM_ID);
        verify(roleService).getRoleName(CHAT_ID, ROLE_PRIORITY);
        verify(userService).getUserFullNameInRequiredCase(FROM_ID, NameCase.GENITIVE);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        assertTrue(actual.contains("Роль @id" + FROM_ID + "(" + USER_NAME_GENITIVE + ") в чате — «Модератор». Приоритет роли: 5"));
    }

    @Test
    void shouldShowRoleOfAnotherMember() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(TARGET_USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, TARGET_USER_ID))
                .thenReturn(ROLE_PRIORITY);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                .thenReturn(Optional.of(ROLE_NAME));

        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = memberRoleShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getMemberRolePriority(CHAT_ID, TARGET_USER_ID);
        verify(roleService).getRoleName(CHAT_ID, ROLE_PRIORITY);
        verify(userService).getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.GENITIVE);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        assertTrue(actual.contains("Роль @id" + TARGET_USER_ID + "(" + USER_NAME_GENITIVE + ") в чате — «Модератор». Приоритет роли: 5"));
    }

    @Test
    void shouldShowChatManagerRoleWhenMemberIsMainBot() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@bot"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(-MAIN_BOT_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, -MAIN_BOT_ID)).thenReturn(0);

        when(userService.getUserFullNameInRequiredCase(-MAIN_BOT_ID, NameCase.GENITIVE))
                .thenReturn("Чат-менеджера");

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = memberRoleShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getMemberRolePriority(CHAT_ID, -MAIN_BOT_ID);
        verify(roleService, never()).getRoleName(anyLong(), anyInt());

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        System.out.println(actual);
        assertTrue(actual.contains("Роль @club" + (MAIN_BOT_ID) + "(Чат-менеджера) в чате — «Чат-менеджер». Приоритет роли: 0"));
    }

    @Test
    void shouldReturnSuccessWithFallbackRoleName() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(ROLE_PRIORITY);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                .thenReturn(Optional.empty());

        when(userService.getUserFullNameInRequiredCase(FROM_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = memberRoleShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        assertTrue(actual.contains("Роль @id" + FROM_ID + "(" + USER_NAME_GENITIVE + ") в чате — «Unknown role». Приоритет роли: 5"));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> memberRoleShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromRoleService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(ROLE_PRIORITY);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                .thenThrow(new RuntimeException("Role service error"));

        assertThrows(RuntimeException.class, () -> memberRoleShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromUserService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(ROLE_PRIORITY);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                .thenReturn(Optional.of(ROLE_NAME));

        when(userService.getUserFullNameInRequiredCase(FROM_ID, NameCase.GENITIVE))
                .thenThrow(new RuntimeException("User service error"));

        assertThrows(RuntimeException.class, () -> memberRoleShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(ROLE_PRIORITY);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                .thenReturn(Optional.of(ROLE_NAME));

        when(userService.getUserFullNameInRequiredCase(FROM_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> memberRoleShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(ROLE_PRIORITY);

        when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                .thenReturn(Optional.of(ROLE_NAME));

        when(userService.getUserFullNameInRequiredCase(FROM_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> memberRoleShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}
