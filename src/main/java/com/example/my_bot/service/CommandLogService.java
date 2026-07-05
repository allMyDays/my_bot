package com.example.my_bot.service;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.entity.CommandLogEntity;
import com.example.my_bot.repository.CommandLogRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandLogService {

    private final CommandLogRepository commandLogRepository;

    @Async
    public CompletableFuture<CommandLogEntity> saveNewCommandLog(long chatId, @NonNull Command cmdAnnotation, long fromId){
        return CompletableFuture.completedFuture(
                commandLogRepository.save(
                        new CommandLogEntity(chatId, fromId, cmdAnnotation.mainCommandName(), Instant.now())
        ));
    }

    public List<CommandLogEntity> getLastNCommandLogs(long chatId, int quantity){
        return commandLogRepository.findLastNCommandLogs(chatId, PageRequest.of(0, quantity));
    }

    @Transactional
    @Scheduled(fixedRate = 86_400_000)
    protected void deleteLogsOlderThanOneWeek(){
        try {
            int deletedRaws = commandLogRepository.deleteOldRecords(Instant.now().minus(7, ChronoUnit.DAYS));
            log.info("Deleted {} command log records older than 7 days", deletedRaws);
        }catch (Exception e) {
            log.error("Failed to delete old command logs", e);
        }
    }

}
