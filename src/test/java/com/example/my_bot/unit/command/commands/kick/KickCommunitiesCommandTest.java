package com.example.my_bot.unit.command.commands.kick;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.kick.KickCommand;
import com.example.my_bot.command.commands.kick.KickCommunitiesCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KickCommunitiesCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long COMMUNITY_ID_1 = 200L;
    private static final long COMMUNITY_ID_2 = 201L;
    private static final long COMMUNITY_ID_ADMIN = 300L;
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

    private KickCommunitiesCommand kickCommunitiesCommand;

    @BeforeEach
    void setUp() {
        kickCommunitiesCommand = new KickCommunitiesCommand(memberService, messageMapper);
        kickCommunitiesCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldKickCommunitiesSuccessfully() throws ClientException, ApiException {
        // given
        MemberEntity community1 = createMember(COMMUNITY_ID_1, false);
        MemberEntity community2 = createMember(COMMUNITY_ID_2, false);
        List<MemberEntity> communities = List.of(community1, community2);
        Page<MemberEntity> page = new PageImpl<>(communities, org.springframework.data.domain.PageRequest.of(0, LIMIT), 2);

        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(COMMUNITY_ID_1, COMMUNITY_ID_2);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(COMMUNITY_ID_1, COMMUNITY_ID_2))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = kickCommunitiesCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(memberService).getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of(COMMUNITY_ID_1, COMMUNITY_ID_2));
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        assertEquals("✅Было исключено 2 из 2 сообществ с ролью ниже чем «Модератор».", actual);
    }

    @Test
    void shouldKickOnlyNonAdminCommunities() throws ClientException, ApiException {
        // given
        MemberEntity community1 = createMember(COMMUNITY_ID_1, false);
        MemberEntity community2 = createMember(COMMUNITY_ID_2, false);
        MemberEntity adminCommunity = createMember(COMMUNITY_ID_ADMIN, true); // админ
        List<MemberEntity> communities = List.of(community1, adminCommunity, community2);
        Page<MemberEntity> page = new PageImpl<>(communities, org.springframework.data.domain.PageRequest.of(0, LIMIT), 3);

        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(COMMUNITY_ID_1, COMMUNITY_ID_2);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(COMMUNITY_ID_1, COMMUNITY_ID_2))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = kickCommunitiesCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of(COMMUNITY_ID_1, COMMUNITY_ID_2));
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        assertEquals("✅Было исключено 2 из 3 сообществ с ролью ниже чем «Модератор».", actual);
    }

    @Test
    void shouldHandleEmptyPage() throws ClientException, ApiException {
        // given
        Page<MemberEntity> emptyPage = new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, LIMIT), 0);
        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(emptyPage);

        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of())))
                .thenReturn(Set.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = kickCommunitiesCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        assertEquals("✅Было исключено 0 из 0 сообществ с ролью ниже чем «Модератор».", actual);
    }

    @Test
    void shouldHandleAllCommunitiesAreAdmins() throws ClientException, ApiException {
        // given
        MemberEntity admin1 = createMember(COMMUNITY_ID_1, true);
        MemberEntity admin2 = createMember(COMMUNITY_ID_2, true);
        List<MemberEntity> admins = List.of(admin1, admin2);
        Page<MemberEntity> page = new PageImpl<>(admins, org.springframework.data.domain.PageRequest.of(0, LIMIT), 2);

        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        // После фильтрации список для кика будет пустым
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of())))
                .thenReturn(Set.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = kickCommunitiesCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).kickManyChatMembers(commandRoutingData, List.of());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();
        assertEquals("✅Было исключено 0 из 2 сообществ с ролью ниже чем «Модератор».", actual);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberService() throws ClientException, ApiException {
        // given
        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenThrow(new RuntimeException("DB error"));

        // when / then
        assertThrows(RuntimeException.class, () -> kickCommunitiesCommand.execute(commandMessage));
        verify(vkChatClient, never()).kickManyChatMembers(any(), any());
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromKickMany() throws ClientException, ApiException {
        // given
        MemberEntity community = createMember(COMMUNITY_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(community), org.springframework.data.domain.PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        ClientException clientException = new ClientException("VK client error");
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(COMMUNITY_ID_1))))
                .thenThrow(clientException);

        // when / then
        assertThrows(ClientException.class, () -> kickCommunitiesCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateApiExceptionFromKickMany() throws ClientException, ApiException {
        // given
        MemberEntity community = createMember(COMMUNITY_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(community), org.springframework.data.domain.PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(COMMUNITY_ID_1))))
                .thenThrow(apiException);

        // when / then
        assertThrows(ApiException.class, () -> kickCommunitiesCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        MemberEntity community = createMember(COMMUNITY_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(community), org.springframework.data.domain.PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(COMMUNITY_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(COMMUNITY_ID_1))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ClientException.class, () -> kickCommunitiesCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
        verify(messageMapper).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        MemberEntity community = createMember(COMMUNITY_ID_1, false);
        Page<MemberEntity> page = new PageImpl<>(List.of(community), org.springframework.data.domain.PageRequest.of(0, LIMIT), 1);
        when(memberService.getNotKickedCommunitiesWithRoleLessThan(CHAT_ID, MODERATOR_PRIORITY, LIMIT))
                .thenReturn(page);

        Set<Long> kickedIds = Set.of(COMMUNITY_ID_1);
        when(vkChatClient.kickManyChatMembers(eq(commandRoutingData), eq(List.of(COMMUNITY_ID_1))))
                .thenReturn(kickedIds);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ApiException.class, () -> kickCommunitiesCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
        verify(messageMapper).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    private MemberEntity createMember(long userId, boolean isChatAdmin) {
        MemberEntity member = new MemberEntity();
        member.setUserId(userId);
        member.setChatAdmin(isChatAdmin);
        return member;
    }
}