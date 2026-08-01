package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.repository.TimerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TimerRepositoryIntegrationTest {

    @Autowired
    private TimerRepository timerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final long chatId1 = 1L;
    private final long chatId2 = 2L;
    private final long creatorId = 100L;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();

        TimerEntity timer1 = new TimerEntity(chatId1, creatorId, TimerType.ONCE, "/remind", now.minusSeconds(10));
        timer1.setExecutionCounter(0);

        TimerEntity timer2 = new TimerEntity(chatId1, creatorId, TimerType.EACH, "/repeat", 30L, now.plusSeconds(60));
        timer2.setExecutionCounter(0);

        TimerEntity timer3 = new TimerEntity(chatId1, creatorId, TimerType.ONCE, "/alert", now.minusSeconds(5));
        timer3.setExecutionCounter(0);

        TimerEntity timer4 = new TimerEntity(chatId2, creatorId, TimerType.ONCE, "/info", now.minusSeconds(10));
        timer4.setExecutionCounter(0);

        TimerEntity timer5 = new TimerEntity(chatId1, creatorId, TimerType.ONCE, "/future", now.plusSeconds(30));
        timer5.setExecutionCounter(0);

        entityManager.persistAndFlush(timer1);
        entityManager.persistAndFlush(timer2);
        entityManager.persistAndFlush(timer3);
        entityManager.persistAndFlush(timer4);
        entityManager.persistAndFlush(timer5);
        entityManager.clear();
    }

    @Test
    void countByChatId_shouldReturnNumberOfTimersForChat() {
        // when
        long countChat1 = timerRepository.countByChatId(chatId1);
        long countChat2 = timerRepository.countByChatId(chatId2);

        // then
        assertThat(countChat1).isEqualTo(4);
        assertThat(countChat2).isEqualTo(1);
    }

    @Test
    void findByChatIdOrderByIdAsc_shouldReturnTimersSortedById() {
        // when
        List<TimerEntity> timers = timerRepository.findByChatIdOrderByIdAsc(chatId1);

        // then
        assertThat(timers).hasSize(4);

        List<Long> ids = timers.stream().map(TimerEntity::getId).toList();
        assertThat(ids).isSorted();
    }

    @Test
    void findAllTimersWithNextExecutionLessThan_withoutExcludedIds_shouldReturnTimersBeforeDateTime() {
        // given
        Instant threshold = now.minusSeconds(1); // порог "сейчас - 1 сек"

        // when
        List<TimerEntity> timers = timerRepository.findAllTimersWithNextExecutionLessThan(threshold);

        // then
        assertThat(timers).hasSize(3);
        assertThat(timers).extracting(TimerEntity::getChatId)
                .containsExactlyInAnyOrder(chatId1, chatId1, chatId2);
        assertThat(timers).extracting(TimerEntity::getFullCommand)
                .containsExactlyInAnyOrder("/remind", "/alert", "/info");
    }

    @Test
    void findAllTimersWithNextExecutionLessThan_withExcludedIds_shouldExcludeGivenIds() {
        // given
        Instant threshold = now.minusSeconds(1);

        List<TimerEntity> all = timerRepository.findAllTimersWithNextExecutionLessThan(threshold);
        Set<Long> excludedIds = Set.of(
                all.stream().filter(t -> "/remind".equals(t.getFullCommand())).findFirst().get().getId(),
                all.stream().filter(t -> "/alert".equals(t.getFullCommand())).findFirst().get().getId()
        );

        // when
        List<TimerEntity> result = timerRepository.findAllTimersWithNextExecutionLessThan(threshold, excludedIds);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFullCommand()).isEqualTo("/info");
        assertThat(result.get(0).getChatId()).isEqualTo(chatId2);
    }

    @Test
    void findAllTimersWithNextExecutionLessThan_shouldReturnEmptyListWhenNoTimersMatch() {
        // given
        Instant threshold = now.minusSeconds(100); // порог в прошлом, таймеры с nextExecution > threshold

        // when
        List<TimerEntity> timers = timerRepository.findAllTimersWithNextExecutionLessThan(threshold);

        // then
        assertThat(timers).isEmpty();
    }
}