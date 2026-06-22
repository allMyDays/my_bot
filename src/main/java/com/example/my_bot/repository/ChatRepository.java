package com.example.my_bot.repository;

import com.example.my_bot.entity.ChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<ChatEntity, Long> {

    Optional<ChatEntity> findByChatCode(String chatCode);

    boolean existsByBoundLogChat(Long boundLogChat);

    List<ChatEntity> findByBoundLogChat(Long boundLogChat);

    @Query("SELECT c.chatId FROM ChatEntity c WHERE c.boundSubmanagerId=:submanagerId AND c.submanagerChatId =:submanagerChatId")
    Optional<Long> findMainChatIdBySubmanagerChatId(Long submanagerId, Long submanagerChatId);

    @Query("SELECT c.submanagerChatId FROM ChatEntity c WHERE c.boundSubmanagerId=:submanagerId AND c.chatId =:mainChatId")
    Optional<Long> findSubmanagerChatIdByMainChatId(Long submanagerId, Long mainChatId);

    @Query("SELECT c FROM ChatEntity c WHERE c.boundSubmanagerId=:submanagerId AND c.isSubPosts = true ")
    List<ChatEntity> findChatsByBoundSubmanagerAndSubPostsTrue(Long submanagerId);


}
