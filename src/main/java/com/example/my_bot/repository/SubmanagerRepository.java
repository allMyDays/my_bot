package com.example.my_bot.repository;

import com.example.my_bot.entity.SubmanagerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmanagerRepository extends JpaRepository<SubmanagerEntity, Long> {
}
