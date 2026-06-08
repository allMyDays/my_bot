package com.example.my_bot.repository;

import com.example.my_bot.entity.MemberEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

    @Query("SELECT c FROM MemberEntity c WHERE c.chatId = :chatId")
    List<MemberEntity> findByChatId(@Param("chatId") Long chatId);

    @Query("SELECT c FROM MemberEntity c WHERE c.chatId = :chatId AND c.rolePriority>0")
    List<MemberEntity> findMembersWithPositiveRole(@Param("chatId") Long chatId);

    @Query("SELECT c FROM MemberEntity c WHERE c.chatId = :chatId AND c.rolePriority=:rolePriority")
    List<MemberEntity> findMembersWithRequiredRole(@Param("chatId") Long chatId,@Param("rolePriority") Long rolePriority);



        @Modifying
        @Transactional
        @Query("UPDATE MemberEntity m SET m.rolePriority = :newPriority " +
                "WHERE m.chatId = :chatId AND m.rolePriority = :oldPriority")
        int updateMembersRole(@Param("chatId") long chatId,
                              @Param("oldPriority") int oldPriority,
                              @Param("newPriority") int newPriority);

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE chat_member
    SET role_priority = :newPriority
    WHERE chat_id = :chatId AND role_priority = :oldPriority
    RETURNING user_id;
    """, nativeQuery = true)
    List<Long> updateMembersRoleAndReturnIds(long chatId, int oldPriority, int newPriority);


    @Modifying
    @Query("UPDATE MemberEntity m " +
            "SET m.presenceType = CASE WHEN m.presenceType = 'IN_CHAT' THEN 'UNKNOWN_LEAVE' ELSE m.presenceType END, " +
            "m.isChatAdmin = false " +
            "WHERE m.chatId = :chatId AND m.userId NOT IN :userIds")
    int setUnknownLeaveAndChatAdminFalseForMembersNotInList(@Param("chatId") long chatId,
                                                            @Param("userIds") Set<Long> userIds);



    List<MemberEntity> findByChatIdAndUserIdIn(long chatId, Set<Long> userIds);

    Optional<MemberEntity> findByChatIdAndUserId(long chatId, long userId);

    @Modifying
    @Query("UPDATE MemberEntity m SET m.rolePriority = 0 " +
            "WHERE m.chatId = :chatId " +
            "AND m.presenceType != 'IN_CHAT' " +
            "AND m.rolePriority > 0 " +
            "AND m.rolePriority < :thresholdRolePriority")
    int removePositiveRoleFromExitedMembers(@Param("chatId") long chatId,
                                            @Param("thresholdRolePriority") int thresholdRolePriority);

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE chat_member
    SET role_priority = 0
    WHERE chat_id = :chatId
      AND presence_type <> 'IN_CHAT'
      AND role_priority > 0
      AND role_priority < :thresholdRolePriority
    RETURNING user_id;
""", nativeQuery = true)
    List<Long> removePositiveRoleFromExitedMembersAndReturnIds(long chatId, int thresholdRolePriority);


    @Query("SELECT m FROM MemberEntity m WHERE m.chatId = :chatId AND m.rolePriority < :rolePriority AND (m.presenceType = 'SELF_LEAVE' OR m.presenceType = 'UNKNOWN_LEAVE')")
    Page<MemberEntity> findLeftButNotKickedMembersWithRoleLessThan(
            @Param("chatId") Long chatId,
            @Param("rolePriority") int rolePriority,
            Pageable pageable);

    @Query("SELECT m FROM MemberEntity m WHERE m.chatId = :chatId AND m.rolePriority < :rolePriority AND m.userId < 0 AND m.presenceType != 'KICKED'")
    Page<MemberEntity> findNotKickedCommunitiesWithRoleLessThan(
            @Param("chatId") Long chatId,
            @Param("rolePriority") int rolePriority,
            Pageable pageable);

    @Query("SELECT m FROM MemberEntity m WHERE m.chatId = :chatId AND m.rolePriority < :rolePriority AND m.invitedById = :inviterId AND m.presenceType != 'KICKED'")
    Page<MemberEntity> findNotKickedMembersInvitedByAndWithRoleLessThan(
            @Param("chatId") Long chatId,
            @Param("inviterId") Long inviterId,
            @Param("rolePriority") int rolePriority,
            Pageable pageable);

    @Query("SELECT m FROM MemberEntity m WHERE m.chatId = :chatId AND m.rolePriority < :rolePriority AND m.firstAppearance >= :after AND m.presenceType != 'KICKED'")
    Page<MemberEntity> findNotKickedNewMembersWithRoleLessThan(
            @Param("chatId") Long chatId,
            @Param("after") Instant after,
            @Param("rolePriority") int rolePriority,
            Pageable pageable);

    @Query("SELECT m.userId FROM MemberEntity m WHERE m.chatId = :chatId AND m.presenceType = 'IN_CHAT'")
    List<Long> findAllCurrentMembersByChatId(long chatId);


    @Query("SELECT m.userId FROM MemberEntity m WHERE m.chatId = :chatId AND m.presenceType = 'IN_CHAT' AND m.firstAppearance < :thresholdDate")
    List<Long> findAllCurrentMemberWithFirstAppearanceBeforeThan(
            @Param("chatId") long chatId,
            @Param("thresholdDate") Instant thresholdDate
    );

    @Query("SELECT m.userId FROM MemberEntity m WHERE m.chatId = :chatId AND m.rolePriority < :rolePriority AND m.presenceType = 'IN_CHAT' AND m.firstAppearance < :thresholdDate")
    List<Long> findAllCurrentMemberWithRoleLessThanAndFirstAppearanceBeforeThan(
            @Param("chatId") long chatId,
            @Param("rolePriority") int rolePriority,
            @Param("thresholdDate") Instant thresholdDate
    );

    @Query("SELECT m.userId FROM MemberEntity m WHERE m.chatId = :chatId AND m.isChatAdmin = true")
    List<Long> findAllChatAdmins(@Param("chatId") long chatId);

    @Query("SELECT m.userId AS userId, u.fullNameInNom AS fullName " +
            "FROM MemberEntity m JOIN GlobalUserEntity u ON m.userId = u.userId " +
            "WHERE m.chatId = :chatId AND m.presenceType = 'IN_CHAT' " +
            "AND u.fullNameInNom LIKE CONCAT('%', :name, '%')")
    Optional<MemberIdAndNameProjection> findCurrentMemberByFullName(@Param("chatId") Long chatId,
                                                                    @Param("name") String fullName);



    interface MemberIdAndNameProjection {
        Long getUserId();
        String getFullName();
    }

}




