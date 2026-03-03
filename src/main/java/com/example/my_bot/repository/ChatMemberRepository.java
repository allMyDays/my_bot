package com.example.my_bot.repository;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.ChatMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMemberRepository extends JpaRepository<ChatMemberEntity, Long> {

    @Query("SELECT c FROM ChatMemberEntity c WHERE c.chatId = :chatId")
    List<ChatMemberEntity> findByChatId(@Param("chatId") Long chatId);
}
