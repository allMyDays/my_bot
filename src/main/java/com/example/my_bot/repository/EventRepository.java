package com.example.my_bot.repository;

import com.example.my_bot.entity.EventEntity;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.event.ChatEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {


    long countByChatId(Long chatId);

    List<EventEntity> findByChatId(long chatId);
}
