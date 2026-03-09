package com.example.my_bot.repository;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.CommandPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommandPermissionRepository extends JpaRepository<CommandPermissionEntity, Long> {

    List<CommandPermissionEntity> findByChatIdAndUserIdIsNull(Long chatId);



}
