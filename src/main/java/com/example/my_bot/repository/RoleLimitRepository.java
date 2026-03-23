package com.example.my_bot.repository;

import com.example.my_bot.entity.RoleLimitEntity;
import com.example.my_bot.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface RoleLimitRepository extends JpaRepository<RoleLimitEntity, Long> {

    List<RoleLimitEntity> findByChatId(Long chatId);


}
