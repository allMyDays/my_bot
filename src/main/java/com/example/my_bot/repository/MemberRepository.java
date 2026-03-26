package com.example.my_bot.repository;

import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

    @Query("SELECT c FROM MemberEntity c WHERE c.chatId = :chatId")
    List<MemberEntity> findByChatId(@Param("chatId") Long chatId);

    @Query("SELECT c FROM MemberEntity c WHERE c.chatId = :chatId AND c.rolePriority!=0")
    List<MemberEntity> findMembersWithNotZeroRole(@Param("chatId") Long chatId);

    @Query("SELECT c FROM MemberEntity c WHERE c.chatId = :chatId AND c.rolePriority=:rolePriority")
    List<MemberEntity> findMembersWithRequiredRole(@Param("chatId") Long chatId,@Param("rolePriority") Long rolePriority);



        @Modifying
        @Transactional
        @Query("UPDATE MemberEntity m SET m.rolePriority = :newPriority " +
                "WHERE m.chatId = :chatId AND m.rolePriority = :oldPriority")
        int updateRolePriorityForMembers(@Param("chatId") long chatId,
                                         @Param("oldPriority") int oldPriority,
                                         @Param("newPriority") int newPriority);


    @Modifying
    @Query("UPDATE MemberEntity m " +
            "SET m.presenceType = CASE WHEN m.presenceType = 'IN_CHAT' THEN 'UNKNOWN_LEAVE' ELSE m.presenceType END, " +
            "m.isChatAdmin = false " +
            "WHERE m.chatId = :chatId AND m.userId NOT IN :userIds")
    int setUnknownLeaveAndChatAdminFalseForMembersNotInList(@Param("chatId") long chatId,
                                                            @Param("userIds") Set<Long> userIds);

   /* @Modifying
    @Query("UPDATE MemberEntity m SET m.presenceType = :presenceType, m.isChatAdmin = false " +
            "WHERE m.chatId = :chatId AND m.userId = :userId")
    int setPresenceTypeAndChatAdminFalseForMember(@Param("chatId") long chatId,
                                                  @Param("userId") long userId,
                                                  @Param("presenceType") MemberPresenceType presenceType);*/

    List<MemberEntity> findByChatIdAndUserIdIn(long chatId, Set<Long> userIds);

    Optional<MemberEntity> findByChatIdAndUserId(long chatId, long userId);




}
