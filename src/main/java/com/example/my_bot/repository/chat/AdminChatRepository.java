package com.example.my_bot.repository.chat;

import com.example.my_bot.entity.AdminChatEntity;
import com.example.my_bot.entity.ChatEntity;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminChatRepository extends JpaRepository<AdminChatEntity, Long> {

    @Transactional
    int deleteByChatId(long chatId);

    Optional<AdminChatEntity> findTopByBoundChatsContainingOrderByChatIdDesc(long chatId);

}
