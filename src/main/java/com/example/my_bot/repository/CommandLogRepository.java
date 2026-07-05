package com.example.my_bot.repository;

import com.example.my_bot.entity.CommandLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;


@Repository
public interface CommandLogRepository extends JpaRepository<CommandLogEntity, Long> {

    @Query("SELECT c FROM CommandLogEntity c WHERE c.chatId = :chatId ORDER BY c.createdAt DESC")
    List<CommandLogEntity> findLastNCommandLogs(@Param("chatId") long chatId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM CommandLogEntity c WHERE c.createdAt < :threshold")
    int deleteOldRecords(@Param("threshold") Instant threshold);
}
