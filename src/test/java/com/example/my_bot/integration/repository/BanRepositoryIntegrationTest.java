package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.repository.BanRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BanRepositoryIntegrationTest {

    @Autowired
    private BanRepository banRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final Long chatId = 1L;
    private final Long memberId1 = 101L;
    private final Long memberId2 = 102L;
    private final Long memberId3 = 103L;
    private final Long bannedBy = 100L; // кто забанил
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();

        // Вечный бан (bannedUntil = null)
        BanEntity ban1 = new BanEntity();
        ban1.setChatId(chatId);
        ban1.setMemberId(memberId1);
        ban1.setBannedUntil(null);
        ban1.setBannedAt(now.minusSeconds(600));
        ban1.setBannedBy(bannedBy);

        // Временный бан, срок не истек
        BanEntity ban2 = new BanEntity();
        ban2.setChatId(chatId);
        ban2.setMemberId(memberId2);
        ban2.setBannedUntil(now.plusSeconds(3600));
        ban2.setBannedAt(now.minusSeconds(600));
        ban2.setBannedBy(bannedBy);

        // Временный бан, срок истек
        BanEntity ban3 = new BanEntity();
        ban3.setChatId(chatId);
        ban3.setMemberId(memberId3);
        ban3.setBannedUntil(now.minusSeconds(3600));
        ban3.setBannedAt(now.minusSeconds(600));
        ban3.setBannedBy(bannedBy);

        entityManager.persistAndFlush(ban1);
        entityManager.persistAndFlush(ban2);
        entityManager.persistAndFlush(ban3);
        entityManager.clear();
    }

    @Test
    void findByChatIdAndMemberId_shouldReturnBanWhenExists() {
        Optional<BanEntity> found = banRepository.findByChatIdAndMemberId(chatId, memberId1);
        Optional<BanEntity> notFound = banRepository.findByChatIdAndMemberId(chatId, 999L);

        assertThat(found).isPresent();
        assertThat(found.get().getMemberId()).isEqualTo(memberId1);
        assertThat(found.get().getBannedUntil()).isNull();
        assertThat(notFound).isEmpty();
    }

    @Test
    void deleteExpiredBans_shouldRemoveBansWithBannedUntilBeforeNow() {
        Instant nowBeforeDelete = Instant.now();

        banRepository.deleteExpiredBans(nowBeforeDelete);

        Optional<BanEntity> ban1 = banRepository.findByChatIdAndMemberId(chatId, memberId1);
        Optional<BanEntity> ban2 = banRepository.findByChatIdAndMemberId(chatId, memberId2);
        Optional<BanEntity> ban3 = banRepository.findByChatIdAndMemberId(chatId, memberId3);

        assertThat(ban1).isPresent();   // вечный не удаляется
        assertThat(ban2).isPresent();   // не истек
        assertThat(ban3).isEmpty();     // истек
    }

    @Test
    void deleteByChatIdAndMemberId_shouldDeleteBan() {
        assertThat(banRepository.findByChatIdAndMemberId(chatId, memberId1)).isPresent();

        banRepository.deleteByChatIdAndMemberId(chatId, memberId1);

        assertThat(banRepository.findByChatIdAndMemberId(chatId, memberId1)).isEmpty();
    }

    @Test
    void getAllChatPermanentBans_shouldReturnOnlyPermanentBans() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<BanEntity> page = banRepository.getAllChatPermanentBans(chatId, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getMemberId()).isEqualTo(memberId1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getAllChatTemporaryBans_shouldReturnOnlyActiveTemporaryBans() {
        Pageable pageable = PageRequest.of(0, 10);
        Instant nowForQuery = Instant.now();

        Page<BanEntity> page = banRepository.getAllChatTemporaryBans(chatId, nowForQuery, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getMemberId()).isEqualTo(memberId2);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}