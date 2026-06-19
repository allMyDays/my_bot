package com.example.my_bot.repository.permission;

import com.example.my_bot.entity.MemberPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface MemberPermissionRepository extends JpaRepository<MemberPermissionEntity, Long> {

    List<MemberPermissionEntity> findByChatId(Long chatId);

    @Modifying
    @Query("DELETE MemberPermissionEntity c " +
            "WHERE c.chatId = :chatId AND c.commandName = :commandName AND c.userId = :userId")
    int deleteMemberPermissionForOneCommand(@Param("chatId") Long chatId,
                                          @Param("commandName") String commandName,
                                          @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE MemberPermissionEntity c SET c.isAllowed = :isAllowed " +
            "WHERE c.chatId = :chatId AND c.userId=:userId AND c.commandName IN :commandNames")
    int updateUserPermissionsForRequiredCommands(@Param("chatId") Long chatId,
                                                @Param("commandNames") Set<String> commandNames,
                                                @Param("userId") Long userId,
                                                 @Param("isAllowed") Boolean isAllowed);
}
