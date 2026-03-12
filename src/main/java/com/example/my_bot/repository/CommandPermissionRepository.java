package com.example.my_bot.repository;

import com.example.my_bot.entity.CommandPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface CommandPermissionRepository extends JpaRepository<CommandPermissionEntity, Long> {

    List<CommandPermissionEntity> findByChatIdAndUserIdIsNull(Long chatId);

    @Modifying
    @Query("UPDATE CommandPermissionEntity c SET c.rolePriority = :newPriority " +
            "WHERE c.chatId = :chatId AND c.userId IS NULL AND c.commandName IN :commandNames")
    int updateRolePermissionForRequiredCommands(@Param("chatId") Long chatId,
                                                @Param("commandNames") Set<String> commandNames,
                                                @Param("newPriority") Integer newPriority);


    @Modifying
    @Query("DELETE CommandPermissionEntity c " +
            "WHERE c.chatId = :chatId AND c.userId IS NULL AND c.commandName = :commandName")
    int deleteRolePermissionForOneCommand(@Param("chatId") Long chatId,
                                          @Param("commandName") String commandName);



}
