package com.example.my_bot.repository;

import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.entity.ChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface BanRepository extends JpaRepository<BanEntity, Long> {

    Optional<BanEntity> findByChatIdAndMemberId(Long chatId, Long memberId);

    @Modifying
    @Transactional
    @Query("DELETE FROM BanEntity b WHERE b.unbanAt IS NOT NULL AND b.unbanAt <= :now")
    void deleteExpiredBans(@Param("now") Instant now);

}
