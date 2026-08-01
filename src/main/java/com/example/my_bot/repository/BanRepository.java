package com.example.my_bot.repository;

import com.example.my_bot.entity.BanEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BanRepository extends JpaRepository<BanEntity, Long> {

    Optional<BanEntity> findByChatIdAndMemberId(Long chatId, Long memberId);

    @Modifying
    @Transactional
    @Query("DELETE FROM BanEntity b WHERE b.bannedUntil IS NOT NULL AND b.bannedUntil <= :now")
    void deleteExpiredBans(@Param("now") Instant now);

    @Modifying
    @Transactional
    void deleteByChatIdAndMemberId(Long chatId, Long memberId);

    @Query("SELECT b FROM BanEntity b WHERE b.chatId = :chatId AND b.bannedUntil IS NULL")
    Page<BanEntity> getAllChatPermanentBans(@Param("chatId") long chatId, Pageable pageable);

    @Query("SELECT b FROM BanEntity b WHERE b.chatId = :chatId AND b.bannedUntil IS NOT NULL AND b.bannedUntil > :now")
    Page<BanEntity> getAllChatTemporaryBans(@Param("chatId") long chatId, @Param("now") Instant now, Pageable pageable);

}
