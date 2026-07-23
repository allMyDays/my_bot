package com.example.my_bot.unit.service.chat;


import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.ban.MemberBanStatus;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatActionService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.example.my_bot.vk.mapping.action.VkAction;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatActionServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private ChatService chatService;

    @Mock
    private BanService banService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private CommandAccessService commandAccessService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private GroupActor theMainBotGroupActor;

    private ChatActionService chatActionService;

    private final long theMainBotId = 123L;
    private final long dataBaseChatId = 1L;
    private final long vkApiChatId = 2L;
    private final long fromId = 100L;
    private final long memberId = 200L;
    private final CommandRoutingData routingData = new CommandRoutingData();

    @BeforeEach
    void setUp() {
        routingData.setDataBaseChatId(dataBaseChatId);
        routingData.setVkApiChatId(vkApiChatId);
        routingData.setExecutorBot(theMainBotGroupActor);

        chatActionService = new ChatActionService(
                memberService,
                chatService,
                banService,
                vkChatClient,
                commandAccessService,
                messageMapper,
                globalUserService,
                theMainBotGroupActor,
                theMainBotId
        );
    }

    @Test
    void shouldIgnoreWhenDataBaseChatIdNull() {
        CommandRoutingData invalid = new CommandRoutingData();
        chatActionService.handleChatAction(invalid, fromId, new VkAction());
        verifyNoInteractions(memberService, chatService);
    }

    @Test
    void shouldIgnoreWhenActionNull() {
        chatActionService.handleChatAction(routingData, fromId, null);
        verifyNoInteractions(memberService, chatService);
    }

    @Test
    void shouldHandleChatInviteByLink() throws ClientException, ApiException {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER_BY_LINK);
        action.setMemberId(fromId);

        MemberBanStatus banStatus = new MemberBanStatus();
        banStatus.setBanned(false);
        given(banService.getMemberBanStatus(dataBaseChatId, fromId)).willReturn(banStatus);

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(memberService).createNewMemberOrMarkAsPresent(dataBaseChatId, fromId, null);
        verify(banService, never()).deleteMemberBan(anyLong(), anyLong());
        verify(vkChatClient, never()).kickOneChatMember(any(), anyLong());
    }

    @Test
    void shouldHandleChatInviteUserWhenInvitedBySelf() throws ClientException, ApiException {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(fromId);

        MemberBanStatus banStatus = new MemberBanStatus();
        banStatus.setBanned(false);
        given(banService.getMemberBanStatus(dataBaseChatId, fromId)).willReturn(banStatus);

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(memberService).createNewMemberOrMarkAsPresent(dataBaseChatId, fromId, null);
        verify(vkChatClient, never()).kickOneChatMember(any(), anyLong());
    }

    @Test
    void shouldHandleChatInviteUserWhenInvitedByOther() {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(memberId);

        MemberBanStatus banStatus = new MemberBanStatus();
        banStatus.setBanned(false);
        given(banService.getMemberBanStatus(dataBaseChatId, memberId)).willReturn(banStatus);

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(memberService).createNewMemberOrMarkAsPresent(dataBaseChatId, memberId, fromId);
    }

    @Test
    void shouldHandleChatKickUserSelfLeave() {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_KICK_USER);
        action.setMemberId(fromId);

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(memberService).setPresenceTypeToMember(dataBaseChatId, fromId, MemberPresenceType.SELF_LEAVE, true);
    }

    @Test
    void shouldHandleChatKickUserKicked() {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_KICK_USER);
        action.setMemberId(memberId);

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(memberService).setPresenceTypeToMember(dataBaseChatId, memberId, MemberPresenceType.KICKED, true);
    }

    @Test
    void shouldHandleChatTitleUpdate() {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_TITLE_UPDATE);
        action.setText("New Title");

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(chatService).setChatTitle(dataBaseChatId, "New Title");
    }


    @Test
    void shouldKickBannedMemberWhenNoAutoUnban() throws ClientException, ApiException {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(memberId);

        MemberBanStatus banStatus = new MemberBanStatus();
        banStatus.setBanned(true);
        banStatus.setBannedUntil(Instant.now().plusSeconds(60));
        given(banService.getMemberBanStatus(dataBaseChatId, memberId)).willReturn(banStatus);
        given(chatService.getChatTimeZone(dataBaseChatId)).willReturn(TimeZoneType.GMT_PLUS_3);
        given(chatService.isAutoUnban(dataBaseChatId)).willReturn(false);
        given(globalUserService.getUserFullNameInRequiredCase(eq(memberId), any())).willReturn("User Name");

        SendMessageDto mockDto = new SendMessageDto("", 0L, theMainBotGroupActor, null, false, false, null);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(mockDto);

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(vkChatClient).kickOneChatMember(routingData, memberId);
        verify(memberService, never()).createNewMemberOrMarkAsPresent(anyLong(), anyLong(), any());
        verify(banService, never()).deleteMemberBan(anyLong(), anyLong());
        verify(vkChatClient).sendText(any(SendMessageDto.class));
    }

    @Test
    void shouldAutoUnbanWhenPossible() throws ClientException, ApiException {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(memberId);

        MemberBanStatus banStatus = new MemberBanStatus();
        banStatus.setBanned(true);
        banStatus.setBannedUntil(Instant.now().plusSeconds(60));
        given(banService.getMemberBanStatus(dataBaseChatId, memberId)).willReturn(banStatus);
        given(chatService.getChatTimeZone(dataBaseChatId)).willReturn(TimeZoneType.GMT_PLUS_3);
        given(chatService.isAutoUnban(dataBaseChatId)).willReturn(true);
        given(memberService.getMemberRolePriority(dataBaseChatId, fromId)).willReturn(50);
        given(commandAccessService.checkCommandAuthorization(dataBaseChatId, "разбанить", 50, fromId)).willReturn(true);
        given(globalUserService.getUserFullNameInRequiredCase(eq(memberId), any())).willReturn("User Name");

        SendMessageDto mockDto = new SendMessageDto("", 0L, theMainBotGroupActor, null, false, false, null);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(mockDto);

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(banService).deleteMemberBan(dataBaseChatId, memberId);
        verify(vkChatClient, never()).kickOneChatMember(any(), anyLong());
        verify(memberService).createNewMemberOrMarkAsPresent(dataBaseChatId, memberId, fromId);
        verify(vkChatClient).sendText(any(SendMessageDto.class));
    }

    @Test
    void shouldSendMessageEvenIfKickFails() throws ClientException, ApiException {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(memberId);

        MemberBanStatus banStatus = new MemberBanStatus();
        banStatus.setBanned(true);
        banStatus.setBannedUntil(null);
        given(banService.getMemberBanStatus(dataBaseChatId, memberId)).willReturn(banStatus);
        given(chatService.getChatTimeZone(dataBaseChatId)).willReturn(TimeZoneType.GMT_PLUS_3);
        given(chatService.isAutoUnban(dataBaseChatId)).willReturn(false);
        given(globalUserService.getUserFullNameInRequiredCase(eq(memberId), any())).willReturn("User Name");

        SendMessageDto mockDto = new SendMessageDto("", 0L, theMainBotGroupActor, null, false, false, null);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(mockDto);

        doThrow(new ClientException("API error")).when(vkChatClient).kickOneChatMember(routingData, memberId);

        chatActionService.handleChatAction(routingData, fromId, action);

        verify(vkChatClient).kickOneChatMember(routingData, memberId);
        verify(vkChatClient).sendText(any(SendMessageDto.class));
        verify(memberService).createNewMemberOrMarkAsPresent(dataBaseChatId, memberId, fromId);
    }


    @Test
    void shouldNotSyncIfLastSyncRecent() throws ClientException, ApiException {
        ChatDetailsDto chatDto = new ChatDetailsDto();
        chatDto.setLastSyncTime(Instant.now().minus(Duration.ofMinutes(30)));
        given(chatService.getCachedChatDetails(dataBaseChatId, true)).willReturn(chatDto);

        chatActionService.checkLastChatSynchronizationAndExecute(routingData);

        verify(vkChatClient, never()).getChatTitle(anyLong(), any());
        verify(memberService, never()).synchronizeChatMembers(any());
        verify(chatService, never()).setLastSyncToNow(anyLong());
    }

    @Test
    void shouldSyncIfLastSyncOld() throws ClientException, ApiException {
        ChatDetailsDto chatDto = new ChatDetailsDto();
        chatDto.setLastSyncTime(Instant.now().minus(Duration.ofHours(2)));
        given(chatService.getCachedChatDetails(dataBaseChatId, true)).willReturn(chatDto);

        given(vkChatClient.getChatTitle(vkApiChatId, theMainBotGroupActor)).willReturn(Optional.of("New Title"));
        doNothing().when(memberService).synchronizeChatMembers(routingData);

        chatActionService.checkLastChatSynchronizationAndExecute(routingData);

        verify(chatService).setChatTitle(dataBaseChatId, "New Title");
        verify(memberService).synchronizeChatMembers(routingData);
        verify(chatService).setLastSyncToNow(dataBaseChatId);
    }

    @Test
    void shouldNotSetTitleIfNoTitle() throws ClientException, ApiException {
        ChatDetailsDto chatDto = new ChatDetailsDto();
        chatDto.setLastSyncTime(Instant.now().minus(Duration.ofHours(2)));
        given(chatService.getCachedChatDetails(dataBaseChatId, true)).willReturn(chatDto);

        given(vkChatClient.getChatTitle(vkApiChatId, theMainBotGroupActor)).willReturn(Optional.empty());
        doNothing().when(memberService).synchronizeChatMembers(routingData);

        chatActionService.checkLastChatSynchronizationAndExecute(routingData);

        verify(chatService, never()).setChatTitle(anyLong(), anyString());
        verify(memberService).synchronizeChatMembers(routingData);
        verify(chatService).setLastSyncToNow(dataBaseChatId);
    }

    @Test
    void shouldLogAndNotSetLastSyncIfSyncFails() throws ClientException, ApiException {
        ChatDetailsDto chatDto = new ChatDetailsDto();
        chatDto.setLastSyncTime(Instant.now().minus(Duration.ofHours(2)));
        given(chatService.getCachedChatDetails(dataBaseChatId, true)).willReturn(chatDto);

        given(vkChatClient.getChatTitle(vkApiChatId, theMainBotGroupActor)).willReturn(Optional.of("Title"));
        doThrow(new RuntimeException("Sync error")).when(memberService).synchronizeChatMembers(routingData);

        chatActionService.checkLastChatSynchronizationAndExecute(routingData);

        verify(chatService, never()).setLastSyncToNow(anyLong());
        verify(vkChatClient).getChatTitle(vkApiChatId, theMainBotGroupActor);
        verify(chatService).setChatTitle(dataBaseChatId, "Title");
    }

    @Test
    void shouldReturnFalseWhenActionNull() {
        boolean result = chatActionService.tryHandleTheMainBotChatAdding(null, dataBaseChatId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenActionTypeNotInvite() {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_TITLE_UPDATE);
        boolean result = chatActionService.tryHandleTheMainBotChatAdding(action, dataBaseChatId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenMemberIdNotTheMainBot() {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(999L);
        boolean result = chatActionService.tryHandleTheMainBotChatAdding(action, dataBaseChatId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldSendWelcomeMessageAndReturnTrue() throws ClientException, ApiException {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(-theMainBotId);

        SendMessageDto mockDto = new SendMessageDto("", 0L, theMainBotGroupActor, null, false, false, null);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(mockDto);

        boolean result = chatActionService.tryHandleTheMainBotChatAdding(action, dataBaseChatId);

        assertThat(result).isTrue();
        verify(vkChatClient).sendText(any(SendMessageDto.class));
    }

    @Test
    void shouldNotThrowIfSendFails() throws ClientException, ApiException {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(-theMainBotId);

        SendMessageDto mockDto = new SendMessageDto("", 0L, theMainBotGroupActor, null, false, false, null);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(mockDto);
        doThrow(new ClientException("VK error")).when(vkChatClient).sendText(any(SendMessageDto.class));

        boolean result = chatActionService.tryHandleTheMainBotChatAdding(action, dataBaseChatId);

        assertThat(result).isTrue();
        verify(vkChatClient).sendText(any(SendMessageDto.class));
    }
}