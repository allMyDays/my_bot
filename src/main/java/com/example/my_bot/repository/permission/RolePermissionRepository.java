package com.example.my_bot.repository.permission;

import com.example.my_bot.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {

    List<RolePermissionEntity> findByChatId(Long chatId);

    @Modifying
    @Query("UPDATE RolePermissionEntity c SET c.rolePriority = :newPriority " +
            "WHERE c.chatId = :chatId AND c.commandName IN :commandNames")
    int updateRolePermissionForRequiredCommands(@Param("chatId") Long chatId,
                                                @Param("commandNames") Set<String> commandNames,
                                                @Param("newPriority") Integer newPriority);


    @Modifying
    @Query("DELETE RolePermissionEntity c " +
            "WHERE c.chatId = :chatId AND c.commandName = :commandName")
    int deleteRolePermissionForOneCommand(@Param("chatId") Long chatId,
                                          @Param("commandName") String commandName);



}
