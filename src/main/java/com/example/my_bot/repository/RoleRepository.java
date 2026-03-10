package com.example.my_bot.repository;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    List<RoleEntity> findByChatId(Long chatId);

    Optional<RoleEntity> findByChatIdAndRolePriority(Long chatId, int rolePriority);

    Optional<RoleEntity> findByChatIdAndRoleName(Long chatId, String roleName);

    @Modifying
    @Query("UPDATE RoleEntity r SET r.roleName = :newName " +
            "WHERE r.chatId = :chatId AND r.rolePriority = :rolePriority")
    int updateRoleName(@Param("chatId") long chatId, @Param("rolePriority") long rolePriority, @Param("newName") String newName);

    Optional<RoleEntity> findByChatIdAndRoleNameIgnoreCase(Long chatId, String roleName);

    List<RoleEntity> findByChatIdAndRolePriorityIn(Long chatId, Set<Integer> rolePriority);

}
