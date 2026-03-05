package com.example.my_bot.repository;

import com.example.my_bot.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

    @Query("SELECT c FROM MemberEntity c WHERE c.chatId = :chatId")
    List<MemberEntity> findByChatId(@Param("chatId") Long chatId);

    @Query("SELECT c FROM MemberEntity c WHERE c.chatId = :chatId AND c.rolePriority!=0")
    List<MemberEntity> findMembersWithRole(@Param("chatId") Long chatId);

}
