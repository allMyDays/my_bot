package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.MessageLogEntity;
import com.example.my_bot.repository.MessageLogRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.time.temporal.ChronoUnit;

@DataJpaTest
@ActiveProfiles("test")
class MessageLogRepositoryIntegrationTest {

    @Autowired
    private MessageLogRepository messageLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final long chatId1 = 1L;
    private final long chatId2 = 2L;
    private final long fromId1 = 101L;
    private final long fromId2 = 102L;
    private final long fromId3 = 103L;

    private Instant now;

    @BeforeAll
    static void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @BeforeEach
    void setUp() {
        now = Instant.now();

        MessageLogEntity msg1 = new MessageLogEntity();
        msg1.setChatId(chatId1);
        msg1.setConversationMessageId(1);
        msg1.setFromId(fromId1);
        msg1.setCreatedAt(now.minusSeconds(60));
        msg1.setDeleted(false);
        msg1.setForwardedToLogChat(true);
        msg1.setSymbolsQuantity(10);

        MessageLogEntity msg2 = new MessageLogEntity();
        msg2.setChatId(chatId1);
        msg2.setConversationMessageId(2);
        msg2.setFromId(fromId2);
        msg2.setCreatedAt(now.minusSeconds(30));
        msg2.setDeleted(false);
        msg2.setForwardedToLogChat(true);
        msg2.setSymbolsQuantity(5);

        MessageLogEntity msg3 = new MessageLogEntity();
        msg3.setChatId(chatId1);
        msg3.setConversationMessageId(3);
        msg3.setFromId(fromId1);
        msg3.setCreatedAt(now.minusSeconds(10));
        msg3.setDeleted(false);
        msg3.setForwardedToLogChat(true);
        msg3.setSymbolsQuantity(20);

        MessageLogEntity msg4 = new MessageLogEntity();
        msg4.setChatId(chatId2);
        msg4.setConversationMessageId(10);
        msg4.setFromId(fromId3);
        msg4.setCreatedAt(now.minusSeconds(15));
        msg4.setDeleted(false);
        msg4.setForwardedToLogChat(false);
        msg4.setSymbolsQuantity(30);

        MessageLogEntity msg5 = new MessageLogEntity();
        msg5.setChatId(chatId1);
        msg5.setConversationMessageId(4);
        msg5.setFromId(fromId3);
        msg5.setCreatedAt(now.minusSeconds(5));
        msg5.setDeleted(false);
        msg5.setForwardedToLogChat(true);
        msg5.setSymbolsQuantity(15);

        MessageLogEntity msg6 = new MessageLogEntity();
        msg6.setChatId(chatId1);
        msg6.setConversationMessageId(5);
        msg6.setFromId(fromId1);
        msg6.setCreatedAt(now.minusSeconds(2));
        msg6.setDeleted(true);
        msg6.setForwardedToLogChat(true);
        msg6.setSymbolsQuantity(100);

        entityManager.persistAndFlush(msg1);
        entityManager.persistAndFlush(msg2);
        entityManager.persistAndFlush(msg3);
        entityManager.persistAndFlush(msg4);
        entityManager.persistAndFlush(msg5);
        entityManager.persistAndFlush(msg6);
        entityManager.clear();
    }

    @Test
    void findByChatIdAndConversationMessageId_shouldReturnMessageWhenExists() {
        // when
        Optional<MessageLogEntity> found = messageLogRepository.findByChatIdAndConversationMessageId(chatId1, 2);
        Optional<MessageLogEntity> notFound = messageLogRepository.findByChatIdAndConversationMessageId(chatId1, 99);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getFromId()).isEqualTo(fromId2);
        assertThat(notFound).isEmpty();
    }

    @Test
    void findLastMessageOfRequiredMembers_shouldReturnLastMessageTimeForEachMember() {
        // given
        List<Long> requiredMembers = List.of(fromId1, fromId2, fromId3);

        // when
        List<MessageLogRepository.MemberLastMessageProjection> projections =
                messageLogRepository.findLastMessageOfRequiredMembers(chatId1, requiredMembers);

        // then
        assertThat(projections).hasSize(3);

        MessageLogRepository.MemberLastMessageProjection proj1 = projections.stream()
                .filter(p -> p.getFromId().equals(fromId1))
                .findFirst().orElseThrow();

        assertThat(proj1.getLastMessageAt())
                .isCloseTo(now.minusSeconds(2), within(1, ChronoUnit.SECONDS));

        MessageLogRepository.MemberLastMessageProjection proj2 = projections.stream()
                .filter(p -> p.getFromId().equals(fromId2))
                .findFirst().orElseThrow();
        assertThat(proj2.getLastMessageAt())
                .isCloseTo(now.minusSeconds(30), within(1, ChronoUnit.SECONDS));

        MessageLogRepository.MemberLastMessageProjection proj3 = projections.stream()
                .filter(p -> p.getFromId().equals(fromId3))
                .findFirst().orElseThrow();
        assertThat(proj3.getLastMessageAt())
                .isCloseTo(now.minusSeconds(5), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void getChatMembersStat_shouldReturnStatisticsWithinTimeRange() {
        // given
        Instant start = now.minusSeconds(120);
        Instant end = now.plusSeconds(1);

        // when
        List<MessageLogRepository.MemberStatsProjection> stats =
                messageLogRepository.getChatMembersStat(chatId1, start, end);

        // then
        assertThat(stats).hasSize(3);

        MessageLogRepository.MemberStatsProjection stat1 = stats.stream()
                .filter(s -> s.getFromId().equals(fromId1))
                .findFirst().orElseThrow();
        assertThat(stat1.getMessageCount()).isEqualTo(3);
        assertThat(stat1.getTotalSymbols()).isEqualTo(130);

        MessageLogRepository.MemberStatsProjection stat2 = stats.stream()
                .filter(s -> s.getFromId().equals(fromId2))
                .findFirst().orElseThrow();
        assertThat(stat2.getMessageCount()).isEqualTo(1);
        assertThat(stat2.getTotalSymbols()).isEqualTo(5);

        MessageLogRepository.MemberStatsProjection stat3 = stats.stream()
                .filter(s -> s.getFromId().equals(fromId3))
                .findFirst().orElseThrow();
        assertThat(stat3.getMessageCount()).isEqualTo(1);
        assertThat(stat3.getTotalSymbols()).isEqualTo(15);
    }

    @Test
    void findLastNUndeletedMessagesForwardedToLogChat_shouldReturnMessageIdsSortedDesc() {
        // given
        Pageable pageable = PageRequest.of(0, 3);

        // when
        List<Integer> messageIds = messageLogRepository.findLastNUndeletedMessagesForwardedToLogChat(chatId1, pageable);

        // then
        assertThat(messageIds).containsExactly(4, 3, 2);
    }

    @Test
    void findFreshNotDeletedMemberMessageIds_shouldReturnMessageIdsAfterGivenTime() {
        // given
        Instant after = now.minusSeconds(20); // сообщения после now-20
        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<Integer> page = messageLogRepository.findFreshNotDeletedMemberMessageIds(chatId1, fromId1, after, pageable);

        // then
        assertThat(page.getContent()).containsExactly(3);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findFreshNotDeletedMessageIdsOfOwnersNotIn_shouldReturnMessageIdsFromOtherUsers() {
        // given
        Instant after = now.minusSeconds(40); // после now-40
        List<Long> excludedFromIds = List.of(fromId2);
        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<Integer> page = messageLogRepository.findFreshNotDeletedMessageIdsOfOwnersNotIn(chatId1, excludedFromIds, after, pageable);

        // then
        assertThat(page.getContent()).containsExactlyInAnyOrder(3, 4);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void markMessagesAsDeleted_shouldMarkSpecifiedMessagesAsDeleted() {
        // given
        Set<Integer> messageIdsToDelete = Set.of(1, 2); // msg1 и msg2

        // when
        int updatedRows = messageLogRepository.markMessagesAsDeleted(chatId1, messageIdsToDelete);

        // then
        assertThat(updatedRows).isEqualTo(2);

        Optional<MessageLogEntity> msg1 = messageLogRepository.findByChatIdAndConversationMessageId(chatId1, 1);
        assertThat(msg1).isPresent();
        assertThat(msg1.get().isDeleted()).isTrue();

        Optional<MessageLogEntity> msg2 = messageLogRepository.findByChatIdAndConversationMessageId(chatId1, 2);
        assertThat(msg2).isPresent();
        assertThat(msg2.get().isDeleted()).isTrue();

        Optional<MessageLogEntity> msg3 = messageLogRepository.findByChatIdAndConversationMessageId(chatId1, 3);
        assertThat(msg3).isPresent();
        assertThat(msg3.get().isDeleted()).isFalse();
    }
}