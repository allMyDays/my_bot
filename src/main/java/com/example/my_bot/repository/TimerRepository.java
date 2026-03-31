package com.example.my_bot.repository;

import com.example.my_bot.entity.RoleRateLimitEntity;
import com.example.my_bot.entity.TimerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimerRepository extends JpaRepository<TimerEntity, Long> {

    long countByChatId(Long chatId);

    List<TimerEntity> findByChatIdOrderByIdAsc(long chatId);


}
