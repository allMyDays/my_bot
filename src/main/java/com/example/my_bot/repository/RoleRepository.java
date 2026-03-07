package com.example.my_bot.repository;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    List<RoleEntity> findByChatId(Long chatId);

    Optional<RoleEntity> findByChatIdAndRolePriority(Long chatId, int rolePriority);

    Optional<RoleEntity> findByChatIdAndRoleName(Long chatId, String roleName);

}
