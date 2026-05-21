package com.example.my_bot.repository;

import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.entity.MessageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface MessageLogRepository extends JpaRepository<MessageLogEntity, Long> {

    Optional<MessageLogEntity> findByChatIdAndConversationMessageId(long chatId, int conversationMessageId);




}
