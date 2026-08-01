package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.CommandLogEntity;
import com.example.my_bot.repository.CommandLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.time.temporal.ChronoUnit;

@DataJpaTest
@ActiveProfiles("test")
class CommandLogRepositoryIntegrationTest {

    @Autowired
    private CommandLogRepository commandLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final long chatId1 = 1L;
    private final long chatId2 = 2L;
    private final long fromId1 = 100L;
    private final long fromId2 = 101L;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();

        CommandLogEntity cmd1 = new CommandLogEntity(chatId1, fromId1, "/start", now.minusSeconds(60));
        CommandLogEntity cmd2 = new CommandLogEntity(chatId1, fromId1, "/help", now.minusSeconds(30));
        CommandLogEntity cmd3 = new CommandLogEntity(chatId1, fromId2, "/settings", now.minusSeconds(10));
        CommandLogEntity cmd4 = new CommandLogEntity(chatId2, fromId1, "/echo", now.minusSeconds(20));

        entityManager.persistAndFlush(cmd1);
        entityManager.persistAndFlush(cmd2);
        entityManager.persistAndFlush(cmd3);
        entityManager.persistAndFlush(cmd4);
        entityManager.clear();
    }

    @Test
    void findLastNCommandLogs_shouldReturnLastNLogsForChatSortedDesc() {
        Pageable pageable = PageRequest.of(0, 2);

        List<CommandLogEntity> logs = commandLogRepository.findLastNCommandLogs(chatId1, pageable);

        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getCommandName()).isEqualTo("/settings");
        assertThat(logs.get(0).getCreatedAt())
                .isCloseTo(now.minusSeconds(10), within(1, ChronoUnit.SECONDS));
        assertThat(logs.get(1).getCommandName()).isEqualTo("/help");
        assertThat(logs.get(1).getCreatedAt())
                .isCloseTo(now.minusSeconds(30), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void findLastNCommandLogs_shouldReturnEmptyListWhenNoLogsForChat() {
        Pageable pageable = PageRequest.of(0, 10);
        long unknownChatId = 999L;

        List<CommandLogEntity> logs = commandLogRepository.findLastNCommandLogs(unknownChatId, pageable);

        assertThat(logs).isEmpty();
    }

    @Test
    void deleteOldRecords_shouldRemoveRecordsOlderThanThreshold() {
        Instant threshold = now.minusSeconds(40);

        int deletedCount = commandLogRepository.deleteOldRecords(threshold);

        assertThat(deletedCount).isEqualTo(1);

        List<CommandLogEntity> remaining = commandLogRepository.findAll();
        assertThat(remaining).hasSize(3);
        assertThat(remaining).extracting(CommandLogEntity::getCommandName)
                .containsExactlyInAnyOrder("/help", "/settings", "/echo");
        assertThat(remaining).extracting(CommandLogEntity::getCreatedAt)
                .allMatch(instant -> instant.isAfter(threshold) || instant.equals(threshold));
    }
}