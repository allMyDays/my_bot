package com.example.my_bot.unit.command.commands.kick;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.kick.*;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.message.MessageException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class KickSelfLeftMembersCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final long ADMIN_ID = 300L;
    private static final int MODERATOR_PRIORITY = DefaultRole.MODERATOR.getRolePriority();
    private static final int LIMIT = 100;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MemberService memberService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private KickSelfLeftMembersCommand kickSelfLeftMembersCommand;

    @BeforeEach
    void setUp() {
        kickSelfLeftMembersCommand = new KickSelfLeftMembersCommand(
                memberService,
                messageMapper
        );
        kickSelfLeftMembersCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldKickAllLeftMembersSuccess() throws ClientException, ApiException {
        MemberEntity member1 = createMember(USER_ID_1, false);
        MemberEntity member2 = createMember(USER_ID_2, false);
        List<MemberEntity> members = List.of(member1, member2);
        Page<MemberEntity> page = new PageImpl<>(members, PageRequest.of(0, LIMIT), 2);

        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(USER_ID_1, USER_ID_2);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1, USER_ID_2))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickSelfLeftMembersCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of(USER_ID_1, USER_ID_2));
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        assertEquals("✅Было исключено 2 из 2 вышедших, но не исключённых участников с ролью ниже чем «Модератор».", textCaptor.getValue());
    }

    @Test
    void shouldKickOnlyNonAdminLeftMembers() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, false);
        MemberEntity admin = createMember(ADMIN_ID, true);
        List<MemberEntity> members = List.of(member, admin);
        Page<MemberEntity> page = new PageImpl<>(members, PageRequest.of(0, LIMIT), 2);

        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(USER_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickSelfLeftMembersCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of(USER_ID_1));
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        assertEquals("✅Было исключено 1 из 2 вышедших, но не исключённых участников с ролью ниже чем «Модератор».", textCaptor.getValue());
    }

    @Test
    void shouldHandleEmptyPage() throws ClientException, ApiException {
        Page<MemberEntity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, LIMIT), 0);
        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(emptyPage);

        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of())))
                .thenReturn(Set.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickSelfLeftMembersCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        assertEquals("✅Было исключено 0 из 0 вышедших, но не исключённых участников с ролью ниже чем «Модератор».", textCaptor.getValue());
    }

    @Test
    void shouldHandlePartialKick() throws ClientException, ApiException {
        MemberEntity member1 = createMember(USER_ID_1, false);
        MemberEntity member2 = createMember(USER_ID_2, false);
        List<MemberEntity> members = List.of(member1, member2);
        Page<MemberEntity> page = new PageImpl<>(members, PageRequest.of(0, LIMIT), 2);

        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(USER_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1, USER_ID_2))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickSelfLeftMembersCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        assertEquals("✅Было исключено 1 из 2 вышедших, но не исключённых участников с ролью ниже чем «Модератор».", textCaptor.getValue());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberService() throws ClientException, ApiException {
        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> kickSelfLeftMembersCommand.execute(commandMessage));
        verify(vkChatClient, never()).kickManyChatMembers(any(), any());
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromKickMany() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        ClientException clientException = new ClientException("VK client error");
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                .thenThrow(clientException);

        assertThrows(ClientException.class, () -> kickSelfLeftMembersCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateApiExceptionFromKickMany() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Error error = new Error().setErrorCode(1).setErrorMsg("API error");
        ApiException apiException = new ApiException(error);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                .thenThrow(apiException);

        assertThrows(ApiException.class, () -> kickSelfLeftMembersCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(USER_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> kickSelfLeftMembersCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        MemberEntity member = createMember(USER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getLeftButNotKickedMembersWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(USER_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(USER_ID_1))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        Error error = new Error().setErrorCode(1).setErrorMsg("API error");
        ApiException apiException = new ApiException(error);
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> kickSelfLeftMembersCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    private MemberEntity createMember(long userId, boolean isChatAdmin) {
        MemberEntity member = new MemberEntity();
        member.setUserId(userId);
        member.setChatAdmin(isChatAdmin);
        return member;
    }
}