package com.example.my_bot.integration.repository;


import com.example.my_bot.entity.WarnEntity;
import com.example.my_bot.repository.WarnRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class WarnRepositoryTest {

    @Autowired
    private WarnRepository warnRepository;

    @Autowired
    private TestEntityManager entityManager;


    @Test
    void countActiveMemberWarns_shouldCountOnlyActiveWarns() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        Instant now = Instant.now();

        WarnEntity activeWarn = createWarn(chatId, memberId, now.minusSeconds(600), null);
        WarnEntity expiredWarn = createWarn(chatId, memberId, now.minusSeconds(7200), now.minusSeconds(3600));
        WarnEntity anotherMemberWarn = createWarn(chatId, 200L, now.minusSeconds(300), null);
        WarnEntity anotherChatWarn = createWarn(2L, memberId, now.minusSeconds(100), null);

        entityManager.persist(activeWarn);
        entityManager.persist(expiredWarn);
        entityManager.persist(anotherMemberWarn);
        entityManager.persist(anotherChatWarn);
        entityManager.flush();

        // when
        int count = warnRepository.countActiveMemberWarns(chatId, memberId, now);

        // then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countActiveMemberWarns_shouldReturnZeroIfNoActiveWarns() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        Instant now = Instant.now();

        WarnEntity expiredWarn = createWarn(chatId, memberId, now.minusSeconds(7200), now.minusSeconds(3600));
        entityManager.persist(expiredWarn);
        entityManager.flush();

        // when
        int count = warnRepository.countActiveMemberWarns(chatId, memberId, now);

        // then
        assertThat(count).isZero();
    }

    @Test
    void deleteExpiredWarns_shouldDeleteExpiredWarns() {
        // given
        Instant now = Instant.now();
        WarnEntity expiredWarn = createWarn(1L, 100L, now.minusSeconds(7200), now.minusSeconds(3600));
        WarnEntity activeWarn = createWarn(1L, 200L, now.minusSeconds(600), null);
        entityManager.persist(expiredWarn);
        entityManager.persist(activeWarn);
        entityManager.flush();

        // when
        warnRepository.deleteExpiredWarns(now);

        // then
        List<WarnEntity> all = warnRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getMemberId()).isEqualTo(200L);
    }

    @Test
    void deleteExpiredWarns_shouldDeleteMultipleExpiredWarns() {
        // given
        Instant now = Instant.now();
        WarnEntity expired1 = createWarn(1L, 100L, now.minusSeconds(7200), now.minusSeconds(3600));
        WarnEntity expired2 = createWarn(1L, 200L, now.minusSeconds(10000), now.minusSeconds(5000));
        WarnEntity active = createWarn(1L, 300L, now.minusSeconds(60), null);
        entityManager.persist(expired1);
        entityManager.persist(expired2);
        entityManager.persist(active);
        entityManager.flush();

        // when
        warnRepository.deleteExpiredWarns(now);

        // then
        List<WarnEntity> all = warnRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getMemberId()).isEqualTo(300L);
    }

    @Test
    void deleteExpiredWarns_shouldDoNothingIfNoExpiredWarns() {
        // given
        Instant now = Instant.now();
        WarnEntity active1 = createWarn(1L, 100L, now.minusSeconds(600), null);
        WarnEntity active2 = createWarn(1L, 200L, now.minusSeconds(300), now.plusSeconds(1000));
        entityManager.persist(active1);
        entityManager.persist(active2);
        entityManager.flush();

        // when
        warnRepository.deleteExpiredWarns(now);

        // then
        List<WarnEntity> all = warnRepository.findAll();
        assertThat(all).hasSize(2);
    }

    @Test
    void deleteLastActiveMemberWarn_shouldDeleteTheLastActiveWarn() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        Instant now = Instant.now();

        WarnEntity olderWarn = createWarn(chatId, memberId, now.minusSeconds(1200), null);
        WarnEntity newerWarn = createWarn(chatId, memberId, now.minusSeconds(600), null);
        WarnEntity expiredWarn = createWarn(chatId, memberId, now.minusSeconds(7200), now.minusSeconds(3600));
        entityManager.persist(olderWarn);
        entityManager.persist(newerWarn);
        entityManager.persist(expiredWarn);
        entityManager.flush();

        // when
        int deleted = warnRepository.deleteLastActiveMemberWarn(chatId, memberId, now);

        // then
        assertThat(deleted).isEqualTo(1);
        List<WarnEntity> remaining = warnRepository.findActiveMemberWarningsSortedInDesc(chatId, memberId, now);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(olderWarn.getId());
    }

    @Test
    void deleteLastActiveMemberWarn_shouldReturnZeroIfNoActiveWarns() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        Instant now = Instant.now();

        WarnEntity expiredWarn = createWarn(chatId, memberId, now.minusSeconds(7200), now.minusSeconds(3600));
        entityManager.persist(expiredWarn);
        entityManager.flush();

        // when
        int deleted = warnRepository.deleteLastActiveMemberWarn(chatId, memberId, now);

        // then
        assertThat(deleted).isZero();
    }

    @Test
    void deleteLastActiveMemberWarn_shouldDeleteOnlyLastWarnEvenIfMultipleActive() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        Instant now = Instant.now();

        WarnEntity w1 = createWarn(chatId, memberId, now.minusSeconds(1800), null);
        WarnEntity w2 = createWarn(chatId, memberId, now.minusSeconds(1200), null);
        WarnEntity w3 = createWarn(chatId, memberId, now.minusSeconds(600), null);
        entityManager.persist(w1);
        entityManager.persist(w2);
        entityManager.persist(w3);
        entityManager.flush();

        // when
        int deleted = warnRepository.deleteLastActiveMemberWarn(chatId, memberId, now);

        // then
        assertThat(deleted).isEqualTo(1);
        List<WarnEntity> remaining = warnRepository.findActiveMemberWarningsSortedInDesc(chatId, memberId, now);
        assertThat(remaining).hasSize(2);
        assertThat(remaining).extracting("id").containsExactly(w2.getId(), w1.getId());
    }

    @Test
    void deleteAllMemberWarns_shouldDeleteAllWarnsForMember() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        WarnEntity w1 = createWarn(chatId, memberId, Instant.now().minusSeconds(100), null);
        WarnEntity w2 = createWarn(chatId, memberId, Instant.now().minusSeconds(200), Instant.now().minusSeconds(50));
        WarnEntity other = createWarn(chatId, 200L, Instant.now(), null);
        entityManager.persist(w1);
        entityManager.persist(w2);
        entityManager.persist(other);
        entityManager.flush();

        // when
        int deleted = warnRepository.deleteAllMemberWarns(chatId, memberId);

        // then
        assertThat(deleted).isEqualTo(2);
        List<WarnEntity> all = warnRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getMemberId()).isEqualTo(200L);
    }

    @Test
    void deleteAllMemberWarns_shouldReturnZeroIfNoWarnsForMember() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        WarnEntity other = createWarn(chatId, 200L, Instant.now(), null);
        entityManager.persist(other);
        entityManager.flush();

        // when
        int deleted = warnRepository.deleteAllMemberWarns(chatId, memberId);

        // then
        assertThat(deleted).isZero();
    }

    @Test
    void findActiveMemberWarningsSortedInDesc_shouldReturnActiveWarnsSortedByIdDesc() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        Instant now = Instant.now();

        WarnEntity oldest = createWarn(chatId, memberId, now.minusSeconds(1800), null);
        WarnEntity middle = createWarn(chatId, memberId, now.minusSeconds(1200), null);
        WarnEntity newest = createWarn(chatId, memberId, now.minusSeconds(600), null);
        WarnEntity expired = createWarn(chatId, memberId, now.minusSeconds(7200), now.minusSeconds(3600));

        entityManager.persist(oldest);
        entityManager.persist(middle);
        entityManager.persist(newest);
        entityManager.persist(expired);
        entityManager.flush();

        // when
        List<WarnEntity> result = warnRepository.findActiveMemberWarningsSortedInDesc(chatId, memberId, now);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isGreaterThan(result.get(1).getId());
        assertThat(result.get(1).getId()).isGreaterThan(result.get(2).getId());
        assertThat(result).extracting("id").containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    @Test
    void findActiveMemberWarningsSortedInDesc_shouldReturnEmptyListIfNoActiveWarns() {
        // given
        long chatId = 1L;
        long memberId = 100L;
        Instant now = Instant.now();

        WarnEntity expired = createWarn(chatId, memberId, now.minusSeconds(7200), now.minusSeconds(3600));
        entityManager.persist(expired);
        entityManager.flush();

        // when
        List<WarnEntity> result = warnRepository.findActiveMemberWarningsSortedInDesc(chatId, memberId, now);

        // then
        assertThat(result).isEmpty();
    }

    private WarnEntity createWarn(long chatId, long memberId, Instant createdAt, Instant expiresAt) {
        WarnEntity warn = new WarnEntity();
        warn.setChatId(chatId);
        warn.setMemberId(memberId);
        warn.setCreatedAt(createdAt != null ? createdAt : Instant.now());
        warn.setExpiresAt(expiresAt);
        warn.setReason("Test reason");
        warn.setGivenBy(200L);
        return warn;
    }
}