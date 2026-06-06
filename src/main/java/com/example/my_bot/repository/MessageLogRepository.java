package com.example.my_bot.repository;

import com.example.my_bot.entity.MessageLogEntity;
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
import java.util.Set;

@Repository
public interface MessageLogRepository extends JpaRepository<MessageLogEntity, Long> {

    Optional<MessageLogEntity> findByChatIdAndConversationMessageId(long chatId, int conversationMessageId);

    @Query("""
    SELECT m.fromId as fromId, MAX(m.createdAt) as lastMessageAt
    FROM MessageLogEntity m
    WHERE m.chatId = :chatId
      AND m.fromId IN :requiredMembers
    GROUP BY m.fromId
""")
    List<MemberLastMessageProjection> findLastMessageOfRequiredMembers(
            @Param("chatId") long chatId,
            @Param("requiredMembers") List<Long> requiredMembers
    );

    @Query("SELECT m.fromId as fromId, COUNT(m) as messageCount, COALESCE(SUM(m.symbolsQuantity), 0) as totalSymbols " +
            "FROM MessageLogEntity m " +
            "WHERE m.chatId = :chatId AND m.createdAt BETWEEN :start AND :end " +
            "GROUP BY m.fromId "+
            "ORDER BY messageCount DESC"
    )
    List<MemberStatsProjection> getChatMembersStat(@Param("chatId") long chatId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT m.conversationMessageId FROM MessageLogEntity m " +
            "WHERE m.chatId = :chatId AND m.fromId = :fromId " +
            "AND m.isDeleted = false AND m.isForwardedToLogChat = :isForwardedToLogChat " +
            "ORDER BY m.createdAt DESC")
    List<Integer> findLastNUndeletedMemberMessageIds(@Param("chatId") long chatId,
                                                     @Param("fromId") long fromId,
                                                     @Param("isForwardedToLogChat") boolean isForwardedToLogChat,
                                                     Pageable pageable);

    @Query("SELECT m.conversationMessageId FROM MessageLogEntity m " +
            "WHERE m.chatId = :chatId AND m.fromId = :fromId " +
            "AND m.isDeleted = false AND m.createdAt > :after ")
    Page<Integer> findFreshNotDeletedMemberMessageIds(@Param("chatId") long chatId,
                                                      @Param("fromId") long fromId,
                                                      @Param("after") Instant after,
                                                      Pageable pageable);

    @Query("SELECT m.conversationMessageId FROM MessageLogEntity m " +
            "WHERE m.chatId = :chatId AND m.fromId NOT IN :fromIds " +
            "AND m.isDeleted = false AND m.createdAt > :after ")
    Page<Integer> findFreshNotDeletedMessageIdsOfOwnersNotIn(@Param("chatId") long chatId,
                                                     @Param("fromIds") List<Long> fromIds,
                                                     @Param("after") Instant after,
                                                     Pageable pageable);

    @Modifying
    @Query("UPDATE MessageLogEntity m SET m.isDeleted=true " +
            "WHERE m.chatId = :chatId AND m.conversationMessageId IN :conversationMessageIds")
    @Transactional
    int markMessagesAsDeleted(
            @Param("chatId") Long chatId,
            @Param("conversationMessageIds") Set<Integer> conversationMessageIds);
    

    interface MemberLastMessageProjection {
        Long getFromId();
        Instant getLastMessageAt();
    }

    interface MemberStatsProjection{
        Long getFromId();
        Long getMessageCount();
        Long getTotalSymbols();
    }


}
