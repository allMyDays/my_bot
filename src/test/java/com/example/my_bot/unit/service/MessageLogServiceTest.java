package com.example.my_bot.unit.service;


import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
import com.example.my_bot.dto.member.stat.ChatMembersStatisticResult;
import com.example.my_bot.dto.member.stat.MemberStatisticDto;
import com.example.my_bot.entity.MessageLogEntity;
import com.example.my_bot.exception.message.FindingMessageIntervalOutOfBoundsException;
import com.example.my_bot.exception.message.InactiveMembersStatisticIntervalOutOfBoundsException;
import com.example.my_bot.exception.message.MemberStatisticIntervalOutOfBoundsException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.repository.MessageLogRepository;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.vk.mapping.action.VkAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class MessageLogServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private MessageLogRepository messageRepository;

    @Mock
    private RoleService roleService;

    private MessageLogService messageLogService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final int conversationMessageId = 123;
    private final long theMainBotId = 777L;

    private ConcurrentMap<Long, Set<MessageLogEntity>> cache;

    @BeforeEach
    void setUp() throws Exception {
        messageLogService = new MessageLogService(
                memberService,
                messageRepository,
                roleService,
                theMainBotId
        );

        Field cacheField = MessageLogService.class.getDeclaredField("temporaryMessagesCache");
        cacheField.setAccessible(true);
        cache = (ConcurrentMap<Long, Set<MessageLogEntity>>) cacheField.get(messageLogService);
        cache.clear();
    }

    private ConcurrentMap<Long, Set<MessageLogEntity>> getCache() {
        return cache;
    }


    @Test
    void shouldSaveMessageLogToCacheWhenActionIsNull() {
        String text = "Hello";
        boolean isForwarded = false;

        messageLogService.saveNewMessageLog(chatId, fromId, conversationMessageId, null, text, isForwarded);

        assertThat(getCache()).containsKey(chatId);
        Set<MessageLogEntity> messages = getCache().get(chatId);
        assertThat(messages).hasSize(1);
        MessageLogEntity entity = messages.iterator().next();
        assertThat(entity.getChatId()).isEqualTo(chatId);
        assertThat(entity.getFromId()).isEqualTo(fromId);
        assertThat(entity.getConversationMessageId()).isEqualTo(conversationMessageId);
        assertThat(entity.getSymbolsQuantity()).isEqualTo(text.length());
        assertThat(entity.isForwardedToLogChat()).isFalse();
        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldIgnoreWhenActionIsNotNull() {
        VkAction action = new VkAction();
        messageLogService.saveNewMessageLog(chatId, fromId, conversationMessageId, action, "text", false);
        assertThat(getCache()).isEmpty();
    }

    @Test
    void shouldHandleNullText() {
        messageLogService.saveNewMessageLog(chatId, fromId, conversationMessageId, null, null, true);
        Set<MessageLogEntity> messages = getCache().get(chatId);
        MessageLogEntity entity = messages.iterator().next();
        assertThat(entity.getSymbolsQuantity()).isZero();
        assertThat(entity.isForwardedToLogChat()).isTrue();
    }

    @Test
    void shouldSaveAllMessagesToDatabaseAndClearCache() throws Exception {
        messageLogService.saveNewMessageLog(chatId, fromId, 1, null, "msg1", false);
        messageLogService.saveNewMessageLog(chatId, fromId, 2, null, "msg2", true);
        long chatId2 = 2L;
        messageLogService.saveNewMessageLog(chatId2, fromId, 3, null, "msg3", false);

        Method method = MessageLogService.class.getDeclaredMethod("loadAllChatsMessagesIntoTheDatabase");
        method.setAccessible(true);
        method.invoke(messageLogService);

        ArgumentCaptor<Set<MessageLogEntity>> captor = ArgumentCaptor.forClass(Set.class);
        verify(messageRepository).saveAll(captor.capture());
        Set<MessageLogEntity> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        verify(messageRepository).flush();

        assertThat(getCache()).allSatisfy((k, v) -> assertThat(v).isEmpty());
    }

    @Test
    void shouldDoNothingWhenCacheEmpty() throws Exception {
        Method method = MessageLogService.class.getDeclaredMethod("loadAllChatsMessagesIntoTheDatabase");
        method.setAccessible(true);
        method.invoke(messageLogService);
        verify(messageRepository, never()).saveAll(anySet());
        verify(messageRepository, never()).flush();
    }

    @Test
    void shouldFindOwnerIdInCache() {
        messageLogService.saveNewMessageLog(chatId, fromId, conversationMessageId, null, "text", false);
        Optional<Long> ownerId = messageLogService.getMessageOwnerId(chatId, conversationMessageId);
        assertThat(ownerId).contains(fromId);
        verify(messageRepository, never()).findByChatIdAndConversationMessageId(anyLong(), anyInt());
    }

    @Test
    void shouldFindOwnerIdInDatabaseWhenNotInCache() {
        Long expectedOwnerId = 200L;
        MessageLogEntity entity = new MessageLogEntity();
        entity.setFromId(expectedOwnerId);
        given(messageRepository.findByChatIdAndConversationMessageId(chatId, conversationMessageId))
                .willReturn(Optional.of(entity));

        Optional<Long> ownerId = messageLogService.getMessageOwnerId(chatId, conversationMessageId);
        assertThat(ownerId).contains(expectedOwnerId);
        verify(messageRepository).findByChatIdAndConversationMessageId(chatId, conversationMessageId);
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        given(messageRepository.findByChatIdAndConversationMessageId(chatId, conversationMessageId))
                .willReturn(Optional.empty());
        Optional<Long> ownerId = messageLogService.getMessageOwnerId(chatId, conversationMessageId);
        assertThat(ownerId).isEmpty();
    }

    @Test
    void shouldLoadCacheAndQueryDatabase() {
        int msgQuantity = 5;
        List<Integer> expectedIds = List.of(1, 2, 3);

        messageLogService.saveNewMessageLog(chatId, fromId, 100, null, "text", true);

        given(messageRepository.findLastNUndeletedMessagesForwardedToLogChat(eq(chatId), any(Pageable.class)))
                .willReturn(expectedIds);

        List<Integer> result = messageLogService.findLastMessagesForwardedToLogChat(chatId, msgQuantity);

        assertThat(result).isEqualTo(expectedIds);
        ArgumentCaptor<Set<MessageLogEntity>> captor = ArgumentCaptor.forClass(Set.class);
        verify(messageRepository).saveAll(captor.capture());
        Set<MessageLogEntity> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        verify(messageRepository).flush();
        verify(messageRepository).findLastNUndeletedMessagesForwardedToLogChat(eq(chatId), any(Pageable.class));
    }

    @Test
    void shouldClampMsgQuantity() {
        given(messageRepository.findLastNUndeletedMessagesForwardedToLogChat(eq(chatId), any(Pageable.class)))
                .willReturn(List.of());
        messageLogService.findLastMessagesForwardedToLogChat(chatId, 0);
        verify(messageRepository).findLastNUndeletedMessagesForwardedToLogChat(eq(chatId), argThat(p -> p.getPageSize() == 1));
    }


    @Test
    void shouldThrowWhenTimePeriodOutOfBounds() {
        long tooShort = 30;
        assertThatThrownBy(() -> messageLogService.findCurrentInactiveChatMembers(chatId, tooShort, false, null, null))
                .isInstanceOf(InactiveMembersStatisticIntervalOutOfBoundsException.class);
        long tooLong = 315_360_001L;
        assertThatThrownBy(() -> messageLogService.findCurrentInactiveChatMembers(chatId, tooLong, false, null, null))
                .isInstanceOf(InactiveMembersStatisticIntervalOutOfBoundsException.class);
    }

    @Test
    void shouldReturnInactiveMembersWithoutRoleFilter() {
        long timePeriod = 3600;
        List<Long> allMembers = List.of(101L, 102L, 103L);
        given(memberService.getAllCurrentChatMembersWithFirstAppearanceBeforeThan(eq(chatId), any(Instant.class)))
                .willReturn(allMembers);

        MessageLogRepository.MemberLastMessageProjection proj1 = mock(MessageLogRepository.MemberLastMessageProjection.class);
        given(proj1.getFromId()).willReturn(101L);
        given(proj1.getLastMessageAt()).willReturn(Instant.now().minusSeconds(5000));

        MessageLogRepository.MemberLastMessageProjection proj2 = mock(MessageLogRepository.MemberLastMessageProjection.class);
        given(proj2.getFromId()).willReturn(102L);
        given(proj2.getLastMessageAt()).willReturn(Instant.now().minusSeconds(500));

        given(messageRepository.findLastMessageOfRequiredMembers(eq(chatId), eq(allMembers)))
                .willReturn(List.of(proj1, proj2));

        InactiveMembersResult result = messageLogService.findCurrentInactiveChatMembers(chatId, timePeriod, false, null, null);

        assertThat(result.getTotalInactiveQuantity()).isEqualTo(2);
        List<InactiveMemberDto> inactive = result.getInactiveMembers();
        assertThat(inactive).hasSize(2);
        assertThat(inactive).extracting(InactiveMemberDto::getUserId).containsExactlyInAnyOrder(101L, 103L);
        assertThat(inactive.stream().filter(d -> d.getUserId() == 103L).findFirst().get().getLastMessageAt()).isEmpty();
        assertThat(inactive.stream().filter(d -> d.getUserId() == 101L).findFirst().get().getLastMessageAt()).contains(proj1.getLastMessageAt());
    }

    @Test
    void shouldApplyMemberLimit() {
        long timePeriod = 3600;
        List<Long> allMembers = List.of(101L, 102L, 103L);
        given(memberService.getAllCurrentChatMembersWithFirstAppearanceBeforeThan(eq(chatId), any(Instant.class)))
                .willReturn(allMembers);
        given(messageRepository.findLastMessageOfRequiredMembers(eq(chatId), eq(allMembers)))
                .willReturn(List.of());

        InactiveMembersResult result = messageLogService.findCurrentInactiveChatMembers(chatId, timePeriod, false, null, 2);
        assertThat(result.getTotalInactiveQuantity()).isEqualTo(3);
        assertThat(result.getInactiveMembers()).hasSize(2);
    }

    @Test
    void shouldFilterByRoleLessThan() {
        long timePeriod = 3600;
        int role = 10;
        given(roleService.roleExistsByPriority(chatId, role)).willReturn(true);
        List<Long> membersWithRole = List.of(101L, 102L);
        given(memberService.getAllCurrentChatMembersWithRoleLessThanAndFirstAppearanceBeforeThan(eq(chatId), eq(role), any(Instant.class)))
                .willReturn(membersWithRole);
        given(messageRepository.findLastMessageOfRequiredMembers(eq(chatId), eq(membersWithRole)))
                .willReturn(List.of());

        InactiveMembersResult result = messageLogService.findCurrentInactiveChatMembers(chatId, timePeriod, false, role, null);
        assertThat(result.getTotalInactiveQuantity()).isEqualTo(2);
        verify(roleService).roleExistsByPriority(chatId, role);
        verify(memberService).getAllCurrentChatMembersWithRoleLessThanAndFirstAppearanceBeforeThan(eq(chatId), eq(role), any(Instant.class));
    }
    @Test
    void shouldThrowWhenRoleNotFound() {
        long timePeriod = 3600;
        int role = 99;
        given(roleService.roleExistsByPriority(chatId, role)).willReturn(false);
        assertThatThrownBy(() -> messageLogService.findCurrentInactiveChatMembers(chatId, timePeriod, false, role, null))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void shouldSortInactiveMembersByLastMessageDesc() {
        long timePeriod = 3600;
        Instant threshold = Instant.now().minusSeconds(timePeriod);
        List<Long> allMembers = List.of(101L, 102L);

        given(memberService.getAllCurrentChatMembersWithFirstAppearanceBeforeThan(eq(chatId), any(Instant.class)))
                .willReturn(allMembers);

        MessageLogRepository.MemberLastMessageProjection proj1 = mock(MessageLogRepository.MemberLastMessageProjection.class);
        given(proj1.getFromId()).willReturn(101L);
        given(proj1.getLastMessageAt()).willReturn(threshold.minusSeconds(5000)); // старше порога

        MessageLogRepository.MemberLastMessageProjection proj2 = mock(MessageLogRepository.MemberLastMessageProjection.class);
        given(proj2.getFromId()).willReturn(102L);
        given(proj2.getLastMessageAt()).willReturn(threshold.minusSeconds(1000)); // тоже старше порога, но свежее чем 101

        given(messageRepository.findLastMessageOfRequiredMembers(eq(chatId), eq(allMembers)))
                .willReturn(List.of(proj1, proj2));

        InactiveMembersResult result = messageLogService.findCurrentInactiveChatMembers(chatId, timePeriod, true, null, null);
        List<InactiveMemberDto> inactive = result.getInactiveMembers();

        assertThat(inactive).hasSize(2);
        assertThat(inactive.get(0).getUserId()).isEqualTo(102L);
        assertThat(inactive.get(1).getUserId()).isEqualTo(101L);
    }

    @Test
    void shouldThrowWhenStartAfterEnd() {
        Instant start = Instant.now();
        Instant end = start.minusSeconds(10);
        assertThatThrownBy(() -> messageLogService.getAllChatMembersStatForATimePeriod(chatId, start, end, null))
                .isInstanceOf(MemberStatisticIntervalOutOfBoundsException.class);
    }

    @Test
    void shouldThrowWhenPeriodOutOfBounds() {
        Instant now = Instant.now();
        Instant start = now.minusSeconds(10);
        assertThatThrownBy(() -> messageLogService.getAllChatMembersStatForATimePeriod(chatId, start, now, null))
                .isInstanceOf(MemberStatisticIntervalOutOfBoundsException.class);
        Instant tooLong = now.minusSeconds(630_720_001L);
        assertThatThrownBy(() -> messageLogService.getAllChatMembersStatForATimePeriod(chatId, tooLong, now, null))
                .isInstanceOf(MemberStatisticIntervalOutOfBoundsException.class);
    }

    @Test
    void shouldReturnStatistic() {
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        List<MessageLogRepository.MemberStatsProjection> projections = new ArrayList<>();
        MessageLogRepository.MemberStatsProjection p1 = mock(MessageLogRepository.MemberStatsProjection.class);
        given(p1.getFromId()).willReturn(101L);
        given(p1.getMessageCount()).willReturn(5L);
        given(p1.getTotalSymbols()).willReturn(100L);
        projections.add(p1);
        MessageLogRepository.MemberStatsProjection p2 = mock(MessageLogRepository.MemberStatsProjection.class);
        given(p2.getFromId()).willReturn(102L);
        given(p2.getMessageCount()).willReturn(3L);
        given(p2.getTotalSymbols()).willReturn(50L);
        projections.add(p2);

        given(messageRepository.getChatMembersStat(chatId, start, end)).willReturn(projections);

        ChatMembersStatisticResult result = messageLogService.getAllChatMembersStatForATimePeriod(chatId, start, end, null);

        assertThat(result.getTotalMessageQuantity()).isEqualTo(8);
        assertThat(result.getTotalSymbolsQuantity()).isEqualTo(150L);
        assertThat(result.getTotalMembersQuantity()).isEqualTo(2);
        assertThat(result.getStart()).isEqualTo(start);
        assertThat(result.getEnd()).isEqualTo(end);
        List<MemberStatisticDto> stats = result.getMemberStatisticDtoList();
        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).getUserId()).isEqualTo(101L);
        assertThat(stats.get(0).getTotalMessages()).isEqualTo(5);
        assertThat(stats.get(0).getTotalSymbols()).isEqualTo(100L);
    }


    private MessageLogRepository.MemberStatsProjection mockProjection(long userId, int msgCount, long symbols) {
        MessageLogRepository.MemberStatsProjection p = mock(MessageLogRepository.MemberStatsProjection.class);
        given(p.getFromId()).willReturn(userId);
        given(p.getMessageCount()).willReturn((long) msgCount);
        given(p.getTotalSymbols()).willReturn(symbols);
        return p;
    }

    @Test
    void shouldThrowWhenTimePeriodTooLarge() {
        long tooLarge = 630_720_001L;
        assertThatThrownBy(() -> messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(chatId, fromId, tooLarge, 10, 123L))
                .isInstanceOf(FindingMessageIntervalOutOfBoundsException.class);
    }

    @Test
    void shouldReturnEmptyIfMemberIsChatAdminAndNotMainBot() {
        long memberId = 101L;
        long currentBotId = 456L;
        given(memberService.isChatAdmin(chatId, memberId)).willReturn(true);
        Page<Integer> result = messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(chatId, memberId, 3600, 10, currentBotId);
        assertThat(result).isEmpty();
        verify(messageRepository, never()).findFreshNotDeletedMemberMessageIds(anyLong(), anyLong(), any(Instant.class), any(Pageable.class));
    }


    @Test
    void shouldQueryRepositoryForNonAdmin() {
        long memberId = 101L;
        long currentBotId = 456L;
        given(memberService.isChatAdmin(chatId, memberId)).willReturn(false);
        Page<Integer> expected = new PageImpl<>(List.of(5, 6));
        given(messageRepository.findFreshNotDeletedMemberMessageIds(eq(chatId), eq(memberId), any(Instant.class), any(Pageable.class)))
                .willReturn(expected);

        Page<Integer> result = messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(chatId, memberId, 3600, 10, currentBotId);
        assertThat(result).isEqualTo(expected);
        verify(messageRepository).findFreshNotDeletedMemberMessageIds(eq(chatId), eq(memberId), any(Instant.class), any(Pageable.class));
    }

    @Test
    void shouldQueryRepositoryExcludingAdmins() {
        long currentBotId = 456L;
        List<Long> admins = List.of(101L, 102L);
        given(memberService.getAllChatAdmins(chatId)).willReturn(admins);
        Page<Integer> expected = new PageImpl<>(List.of(1, 2));
        given(messageRepository.findFreshNotDeletedMessageIdsOfOwnersNotIn(eq(chatId), eq(admins), any(Instant.class), any(Pageable.class)))
                .willReturn(expected);

        Page<Integer> result = messageLogService.findNotDeletedMessageIdsOfNotChatAdminOwners(chatId, 3600, 10, currentBotId);
        assertThat(result).isEqualTo(expected);
        verify(memberService).getAllChatAdmins(chatId);
        verify(messageRepository).findFreshNotDeletedMessageIdsOfOwnersNotIn(eq(chatId), eq(admins), any(Instant.class), any(Pageable.class));
    }

    @Test
    void shouldLoadCacheAndMarkMessages() {
        Set<Integer> ids = Set.of(1, 2, 3);
        messageLogService.saveNewMessageLog(chatId, fromId, 10, null, "text", false);
        messageLogService.markMessagesAsDeleted(chatId, ids);
        verify(messageRepository).saveAll(anySet());
        verify(messageRepository).flush();
        verify(messageRepository).markMessagesAsDeleted(chatId, ids);
    }
}