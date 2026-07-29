package com.example.my_bot.unit.command.commands.immunity;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.immunity.ImmunitiesShowCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ImmunitiesShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final String USER_NAME_1 = "Иван Иванов";
    private static final String USER_NAME_2 = "Петр Петров";
    private static final int IMMUNE_ROLE_PRIORITY_1 = 5;
    private static final int IMMUNE_ROLE_PRIORITY_2 = 3;
    private static final String ROLE_NAME_1 = "Модератор";
    private static final String ROLE_NAME_2 = "Старший модератор";

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

    private ImmunitiesShowCommand immunitiesShowCommand;

    @BeforeEach
    void setUp() {
        immunitiesShowCommand = new ImmunitiesShowCommand(
                memberService,
                roleService,
                messageMapper,
                globalUserService
        );
        immunitiesShowCommand.setVkChatService(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldShowImmunitiesSuccess() throws ClientException, ApiException {
        // given
        MemberEntity member1 = createMember(USER_ID_1, IMMUNE_ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT);
        MemberEntity member2 = createMember(USER_ID_2, IMMUNE_ROLE_PRIORITY_2, MemberPresenceType.IN_CHAT);
        when(memberService.getMembersWithImmunity(CHAT_ID)).thenReturn(List.of(member1, member2));

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1, USER_ID_2, USER_NAME_2);
        when(globalUserService.getUserFullNamesInRequiredCase(
                Set.of(USER_ID_1, USER_ID_2), NameCase.NOMINATIVE))
                .thenReturn(namesMap);

        Map<Integer, String> roleMap = Map.of(
                IMMUNE_ROLE_PRIORITY_1, ROLE_NAME_1,
                IMMUNE_ROLE_PRIORITY_2, ROLE_NAME_2
        );
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(roleMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = immunitiesShowCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getMembersWithImmunity(CHAT_ID);
        verify(globalUserService).getUserFullNamesInRequiredCase(Set.of(USER_ID_1, USER_ID_2), NameCase.NOMINATIVE);
        verify(roleService).getAllRolesWithNoSorting(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("В чате 2 участников имеют иммунитет от ролей."));
        assertTrue(actual.contains(USER_NAME_1));
        assertTrue(actual.contains(USER_NAME_2));
        assertTrue(actual.contains(ROLE_NAME_1));
        assertTrue(actual.contains(ROLE_NAME_2));
        assertTrue(actual.contains("❓На этих пользователей не могут воздействовать управляющие, чья роль ниже или равна роли напротив."));
    }

    @Test
    void shouldShowImmunitiesWithMemberNotInChat() throws ClientException, ApiException {
        // given
        MemberEntity member = createMember(USER_ID_1, IMMUNE_ROLE_PRIORITY_1, MemberPresenceType.SELF_LEAVE);
        when(memberService.getMembersWithImmunity(CHAT_ID)).thenReturn(List.of(member));

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(Set.of(USER_ID_1), NameCase.NOMINATIVE))
                .thenReturn(namesMap);

        Map<Integer, String> roleMap = Map.of(IMMUNE_ROLE_PRIORITY_1, ROLE_NAME_1);
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(roleMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = immunitiesShowCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("\uD83D\uDEAA"));
        assertTrue(actual.contains(USER_NAME_1));
        assertTrue(actual.contains(ROLE_NAME_1));
    }

    @Test
    void shouldShowImmunitiesWithFallbackRolePriority() throws ClientException, ApiException {
        // given
        MemberEntity member = createMember(USER_ID_1, IMMUNE_ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT);
        when(memberService.getMembersWithImmunity(CHAT_ID)).thenReturn(List.of(member));

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(Set.of(USER_ID_1), NameCase.NOMINATIVE))
                .thenReturn(namesMap);

        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = immunitiesShowCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("роль с приоритетом " + IMMUNE_ROLE_PRIORITY_1));
        assertFalse(actual.contains(ROLE_NAME_1));
    }

    @Test
    void shouldShowEmptyImmunitiesList() throws ClientException, ApiException {
        // given
        when(memberService.getMembersWithImmunity(CHAT_ID)).thenReturn(List.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = immunitiesShowCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getMembersWithImmunity(CHAT_ID);
        verify(globalUserService).getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE));
        verify(roleService).getAllRolesWithNoSorting(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("В чате 0 участников имеют иммунитет от ролей."));
        assertFalse(actual.contains("❓На этих пользователей"));
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        // given
        MemberEntity member = createMember(USER_ID_1, IMMUNE_ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT);
        when(memberService.getMembersWithImmunity(CHAT_ID)).thenReturn(List.of(member));

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(Set.of(USER_ID_1), NameCase.NOMINATIVE))
                .thenReturn(namesMap);

        Map<Integer, String> roleMap = Map.of(IMMUNE_ROLE_PRIORITY_1, ROLE_NAME_1);
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(roleMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ClientException.class, () -> immunitiesShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        // given
        MemberEntity member = createMember(USER_ID_1, IMMUNE_ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT);
        when(memberService.getMembersWithImmunity(CHAT_ID)).thenReturn(List.of(member));

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(Set.of(USER_ID_1), NameCase.NOMINATIVE))
                .thenReturn(namesMap);

        Map<Integer, String> roleMap = Map.of(IMMUNE_ROLE_PRIORITY_1, ROLE_NAME_1);
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(roleMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ApiException.class, () -> immunitiesShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberService() throws ClientException, ApiException {
        // given
        when(memberService.getMembersWithImmunity(CHAT_ID))
                .thenThrow(new RuntimeException("DB error"));

        // when / then
        assertThrows(RuntimeException.class, () -> immunitiesShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(globalUserService, never()).getUserFullNamesInRequiredCase(anySet(), any());
        verify(roleService, never()).getAllRolesWithNoSorting(anyLong());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromGlobalUserService() throws ClientException, ApiException {
        // given
        MemberEntity member = createMember(USER_ID_1, IMMUNE_ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT);
        when(memberService.getMembersWithImmunity(CHAT_ID)).thenReturn(List.of(member));

        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenThrow(new RuntimeException("User service error"));

        // when / then
        assertThrows(RuntimeException.class, () -> immunitiesShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(roleService, never()).getAllRolesWithNoSorting(anyLong());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromRoleService() throws ClientException, ApiException {
        // given
        MemberEntity member = createMember(USER_ID_1, IMMUNE_ROLE_PRIORITY_1, MemberPresenceType.IN_CHAT);
        when(memberService.getMembersWithImmunity(CHAT_ID)).thenReturn(List.of(member));

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1);
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE)))
                .thenReturn(namesMap);

        when(roleService.getAllRolesWithNoSorting(CHAT_ID))
                .thenThrow(new RuntimeException("Role service error"));

        // when / then
        assertThrows(RuntimeException.class, () -> immunitiesShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    private MemberEntity createMember(long userId, int immuneRolePriority, MemberPresenceType presenceType) {
        MemberEntity member = new MemberEntity();
        member.setUserId(userId);
        member.setImmuneRolePriority(immuneRolePriority);
        member.setPresenceType(presenceType);
        return member;
    }
}
