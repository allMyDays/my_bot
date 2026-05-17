package com.example.my_bot.repository;

import com.example.my_bot.entity.ChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<ChatEntity, Long> {

    Optional<ChatEntity> findByChatCode(String chatCode);

    boolean existsByBoundLogChat(Long boundLogChat);

    List<ChatEntity> findByBoundLogChat(Long boundLogChat);


}
