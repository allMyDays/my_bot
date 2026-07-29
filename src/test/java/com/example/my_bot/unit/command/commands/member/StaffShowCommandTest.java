package com.example.my_bot.unit.command.commands.member;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.commands.member.*;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.member.MemberPresenceType;
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

import java.util.*;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long MAIN_BOT_ID = 300L;
    private static final long EXECUTOR_BOT_ID = 400L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final long USER_ID_3 = 202L;
    private static final String USER_NAME_1 = "Иван Иванов";
    private static final String USER_NAME_2 = "Петр Петров";
    private static final String USER_NAME_3 = "Сергей Сергеев";
    private static final int ROLE_PRIORITY_1 = 5;
    private static final int ROLE_PRIORITY_2 = 3;
    private static final String ROLE_NAME_1 = "Модератор";
    private static final String ROLE_NAME_2 = "Помощник";

    @Mock
    private MemberService memberService;

    @Mock
    private RoleService roleService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private StaffShowCommand staffShowCommand;

    @BeforeEach
    void setUp() {
        staffShowCommand = new StaffShowCommand(
                memberService,
                roleService,
                MAIN_BOT_ID,
                messageMapper,
                globalUserService
        );
        staffShowCommand.setVkChatService(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(groupActor.getGroupId()).thenReturn(EXECUTOR_BOT_ID);
    }

    @Test
    void shouldShowStaffListSuccess() throws ClientException, ApiException {
        MemberEntity member1 = createMember(USER_ID_1, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, false);
        MemberEntity member2 = createMember(USER_ID_2, ROLE_PRIORITY_2, MemberPresenceType.IN_CHAT, false);
        MemberEntity member3 = createMember(USER_ID_3, ROLE_PRIORITY_2, MemberPresenceType.SELF_LEAVE, false);
        MemberEntity bot = createMember(-MAIN_BOT_ID, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, false);

        List<MemberEntity> members = List.of(member1, member2, member3, bot);
        when(memberService.getMembersWithPositiveRole(CHAT_ID)).thenReturn(members);

        Map<Integer, String> roleMap = Map.of(
                ROLE_PRIORITY_1, ROLE_NAME_1,
                ROLE_PRIORITY_2, ROLE_NAME_2
        );
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(roleMap);

        Set<Long> userIds = Set.of(USER_ID_1, USER_ID_2, USER_ID_3);
        Map<Long, String> namesMap = Map.of(
                USER_ID_1, USER_NAME_1,
                USER_ID_2, USER_NAME_2,
                USER_ID_3, USER_NAME_3
        );
        when(globalUserService.getUserFullNamesInRequiredCase(eq(userIds), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = staffShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getMembersWithPositiveRole(CHAT_ID);
        verify(roleService).getAllRolesWithNoSorting(CHAT_ID);
        verify(globalUserService).getUserFullNamesInRequiredCase(userIds, NameCase.NOMINATIVE);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("В чате 3 управляющих (из них 1 сейчас отсутствует)."));
        assertTrue(actual.contains(ROLE_NAME_1 + " (" + ROLE_PRIORITY_1 + "):"));
        assertTrue(actual.contains(ROLE_NAME_2 + " (" + ROLE_PRIORITY_2 + "):"));
        assertTrue(actual.contains("@id" + USER_ID_1 + "(" + USER_NAME_1 + ")"));
        assertTrue(actual.contains("@id" + USER_ID_2 + "(" + USER_NAME_2 + ")"));
        assertTrue(actual.contains("@id" + USER_ID_3 + "(" + USER_NAME_3 + ") \uD83D\uDEAA"));
        assertFalse(actual.contains("@id" + (-MAIN_BOT_ID)));
        int idx1 = actual.indexOf(ROLE_NAME_1);
        int idx2 = actual.indexOf(ROLE_NAME_2);
        assertTrue(idx1 < idx2);
    }

    @Test
    void shouldShowEmptyStaffList() throws ClientException, ApiException {
        when(memberService.getMembersWithPositiveRole(CHAT_ID)).thenReturn(Collections.emptyList());
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Collections.emptyMap());
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenReturn(Collections.emptyMap());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = staffShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("В чате 0 управляющих (из них 0 сейчас отсутствует)."));
        assertFalse(actual.contains("):"));
        assertFalse(actual.contains("@id"));
    }

    @Test
    void shouldFilterOutBothMainBotAndExecutorBot() throws ClientException, ApiException {
        MemberEntity mainBot = createMember(-MAIN_BOT_ID, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, false);
        MemberEntity executorBot = createMember(-EXECUTOR_BOT_ID, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, false);
        List<MemberEntity> members = List.of(mainBot, executorBot);
        when(memberService.getMembersWithPositiveRole(CHAT_ID)).thenReturn(members);

        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of(ROLE_PRIORITY_1, ROLE_NAME_1));
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenReturn(Collections.emptyMap());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = staffShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("В чате 0 управляющих (из них 0 сейчас отсутствует)."));
        assertFalse(actual.contains("@id"));
    }

    @Test
    void shouldHandleMissingRoleName() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, false);
        List<MemberEntity> members = List.of(member);
        when(memberService.getMembersWithPositiveRole(CHAT_ID)).thenReturn(members);

        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Collections.emptyMap());

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(eq(Set.of(USER_ID_1)), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = staffShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("null (5):"));
        assertTrue(actual.contains("@id" + USER_ID_1 + "(" + USER_NAME_1 + ")"));
    }

    @Test
    void shouldShowAdminEmojiForChatAdmin() throws ClientException, ApiException {
        MemberEntity admin = createMember(USER_ID_1, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, true);
        List<MemberEntity> members = List.of(admin);
        when(memberService.getMembersWithPositiveRole(CHAT_ID)).thenReturn(members);

        Map<Integer, String> roleMap = Map.of(ROLE_PRIORITY_1, ROLE_NAME_1);
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(roleMap);

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(eq(Set.of(USER_ID_1)), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = staffShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("\uD83D\uDCA0 @id" + USER_ID_1 + "(" + USER_NAME_1 + ")"));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromGlobalUserService() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, false);
        when(memberService.getMembersWithPositiveRole(CHAT_ID)).thenReturn(List.of(member));
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of(ROLE_PRIORITY_1, ROLE_NAME_1));
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenThrow(new RuntimeException("User service error"));

        assertThrows(RuntimeException.class, () -> staffShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, false);
        when(memberService.getMembersWithPositiveRole(CHAT_ID)).thenReturn(List.of(member));
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of(ROLE_PRIORITY_1, ROLE_NAME_1));
        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(eq(Set.of(USER_ID_1)), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> staffShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT, false);
        when(memberService.getMembersWithPositiveRole(CHAT_ID)).thenReturn(List.of(member));
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of(ROLE_PRIORITY_1, ROLE_NAME_1));
        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(eq(Set.of(USER_ID_1)), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> staffShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    private MemberEntity createMember(long userId, int rolePriority, MemberPresenceType presenceType, boolean isChatAdmin) {
        MemberEntity member = new MemberEntity();
        member.setUserId(userId);
        member.setRolePriority(rolePriority);
        member.setPresenceType(presenceType);
        member.setChatAdmin(isChatAdmin);
        return member;
    }
}