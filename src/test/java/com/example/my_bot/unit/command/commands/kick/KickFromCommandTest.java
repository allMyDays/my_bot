package com.example.my_bot.unit.command.commands.kick;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.kick.KickCommand;
import com.example.my_bot.command.commands.kick.KickCommunitiesCommand;
import com.example.my_bot.command.commands.kick.KickFromCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.exception.member.MemberException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KickFromCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long INVITER_ID = 300L;
    private static final long MEMBER_ID_1 = 400L;
    private static final long MEMBER_ID_2 = 401L;
    private static final long ADMIN_MEMBER_ID = 500L;
    private static final String INVITER_NAME_INSTRUMENTAL = "Иваном Ивановым";
    private static final int MODERATOR_PRIORITY = DefaultRole.MODERATOR.getRolePriority();
    private static final int LIMIT = 100;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MemberService memberService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private KickFromCommand kickFromCommand;

    @BeforeEach
    void setUp() {
        kickFromCommand = new KickFromCommand(
                memberService,
                userInputResolver,
                globalUserService,
                messageMapper
        );
        kickFromCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldKickMembersFromInviterSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        MemberEntity member1 = createMember(MEMBER_ID_1, false);
        MemberEntity member2 = createMember(MEMBER_ID_2, false);
        List<MemberEntity> members = List.of(member1, member2);
        Page<MemberEntity> page = new PageImpl<>(members, PageRequest.of(0, LIMIT), 2);

        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(MEMBER_ID_1, MEMBER_ID_2);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(MEMBER_ID_1, MEMBER_ID_2))))
                .thenReturn(kickedIds);

        when(globalUserService.getUserFullNameInRequiredCase(INVITER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(INVITER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickFromCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);
        verify(memberService).getNotKickedMembersInvitedByAndWithRoleLessThan(CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of(MEMBER_ID_1, MEMBER_ID_2));
        verify(globalUserService).getUserFullNameInRequiredCase(INVITER_ID, NameCase.INSTRUMENTAL);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        String expected = "✅Было исключено 2 из 2 участников с ролью ниже чем «Модератор», которые были приглашены @id300(Иваном Ивановым).";
        assertEquals(expected, actual);
    }

    @Test
    void shouldSkipCheckWhenInvitingHimself() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@self"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(FROM_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        MemberEntity member = createMember(MEMBER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, FROM_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(MEMBER_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(MEMBER_ID_1))))
                .thenReturn(kickedIds);

        when(globalUserService.getUserFullNameInRequiredCase(FROM_ID, NameCase.INSTRUMENTAL))
                .thenReturn("собой");

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickFromCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of(MEMBER_ID_1));
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("были приглашены @id" + FROM_ID + "(собой)"));
    }

    @Test
    void shouldFilterOutAdminMembers() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        MemberEntity member = createMember(MEMBER_ID_1, false);
        MemberEntity adminMember = createMember(ADMIN_MEMBER_ID, true);
        List<MemberEntity> members = List.of(member, adminMember);
        Page<MemberEntity> page = new PageImpl<>(members, PageRequest.of(0, LIMIT), 2);

        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(MEMBER_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(MEMBER_ID_1))))
                .thenReturn(kickedIds);

        when(globalUserService.getUserFullNameInRequiredCase(INVITER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(INVITER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickFromCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of(MEMBER_ID_1));

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertEquals("✅Было исключено 1 из 2 участников с ролью ниже чем «Модератор», которые были приглашены @id300(Иваном Ивановым).", actual);
    }

    @Test
    void shouldHandleEmptyPage() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        Page<MemberEntity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, LIMIT), 0);
        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(emptyPage);

        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of())))
                .thenReturn(Set.of());

        when(globalUserService.getUserFullNameInRequiredCase(INVITER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(INVITER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickFromCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of());

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertEquals("✅Было исключено 0 из 0 участников с ролью ниже чем «Модератор», которые были приглашены @id300(Иваном Ивановым).", actual);
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberAccessDenied() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        MemberException exception = new MemberException("Недостаточно прав") {};
        doThrow(exception).when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = kickFromCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
        verify(memberService, never()).getNotKickedMembersInvitedByAndWithRoleLessThan(anyLong(), anyLong(), anyInt(), anyInt());
        verify(vkChatClient, never()).kickManyChatMembers(any(), any());
    }

    @Test
    void shouldPropagateClientExceptionFromKickMany() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        MemberEntity member = createMember(MEMBER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        ClientException clientException = new ClientException("VK client error");
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(MEMBER_ID_1))))
                .thenThrow(clientException);

        assertThrows(ClientException.class, () -> kickFromCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateApiExceptionFromKickMany() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        MemberEntity member = createMember(MEMBER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(MEMBER_ID_1))))
                .thenThrow(apiException);

        assertThrows(ApiException.class, () -> kickFromCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> kickFromCommand.execute(commandMessage));
        verify(vkChatClient, never()).kickManyChatMembers(any(), any());
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        MemberEntity member = createMember(MEMBER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(MEMBER_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(MEMBER_ID_1))))
                .thenReturn(kickedIds);

        when(globalUserService.getUserFullNameInRequiredCase(INVITER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(INVITER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> kickFromCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@inviter"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(INVITER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, INVITER_ID, true);

        MemberEntity member = createMember(MEMBER_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(member), PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(
                CHAT_ID, INVITER_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(MEMBER_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(MEMBER_ID_1))))
                .thenReturn(kickedIds);

        when(globalUserService.getUserFullNameInRequiredCase(INVITER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(INVITER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> kickFromCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    private MemberEntity createMember(long userId, boolean isChatAdmin) {
        MemberEntity member = new MemberEntity();
        member.setUserId(userId);
        member.setChatAdmin(isChatAdmin);
        return member;
    }
}