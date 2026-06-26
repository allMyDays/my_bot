package com.example.my_bot.repository;

import com.example.my_bot.entity.GlobalUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface GlobalUserRepository extends JpaRepository<GlobalUserEntity, Long> {

    @Query("SELECT u.userId FROM GlobalUserEntity u WHERE u.boundChat = :boundChat")
    Set<Long> findUserIdsByBoundChat(Long boundChat);
}
