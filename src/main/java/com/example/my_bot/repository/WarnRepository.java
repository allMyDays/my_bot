package com.example.my_bot.repository;

import com.example.my_bot.entity.WarnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface WarnRepository extends JpaRepository<WarnEntity, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM WarnEntity w WHERE w.expiresAt IS NOT NULL AND w.expiresAt <= :now")
    void deleteExpiredWarns(@Param("now") Instant now);

    @Query("SELECT COUNT(w) FROM WarnEntity w " +
            "WHERE w.chatId = :chatId AND w.memberId = :memberId " +
            "AND (w.expiresAt IS NULL OR w.expiresAt > :now)")
    int countActiveMemberWarns(@Param("chatId") Long chatId, @Param("memberId") Long memberId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM WarnEntity w " +
            "WHERE w.id = (SELECT MAX(w2.id) FROM WarnEntity w2 " +
            "              WHERE w2.chatId = :chatId AND w2.memberId = :memberId)")
    int deleteLastMemberWarn(@Param("chatId") Long chatId, @Param("memberId") Long memberId);


    @Modifying
    @Transactional
    @Query("DELETE FROM WarnEntity w " +
            "WHERE w.chatId = :chatId AND w.memberId = :memberId")
    int deleteAllMemberWarns(@Param("chatId") Long chatId, @Param("memberId") Long memberId);







}
