package com.example.my_bot.repository;

import com.example.my_bot.entity.RoleRateLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleRateLimitRepository extends JpaRepository<RoleRateLimitEntity, Long> {

    List<RoleRateLimitEntity> findByChatId(Long chatId);

}
