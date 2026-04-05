package com.example.my_bot.repository;

import com.example.my_bot.entity.RoleRateLimitEntity;
import com.example.my_bot.entity.TimerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface TimerRepository extends JpaRepository<TimerEntity, Long> {

    long countByChatId(Long chatId);

    List<TimerEntity> findByChatIdOrderByIdAsc(long chatId);

    @Query("SELECT t FROM TimerEntity t WHERE t.nextExecution <= :requiredDateTime AND t.id NOT IN :excludedIds")
    List<TimerEntity> findAllTimersWithNextExecutionLessThan(@Param("requiredDateTime") Instant requiredDateTime,
                                                    @Param("excludedIds") Set<Long> excludedIds);

    @Query("SELECT t FROM TimerEntity t WHERE t.nextExecution <= :requiredDateTime")
    List<TimerEntity> findAllTimersWithNextExecutionLessThan(@Param("requiredDateTime") Instant requiredDateTime);
}
