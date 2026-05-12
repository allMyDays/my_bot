package com.example.my_bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(indexes = @Index(name = "idx_banned_until", columnList = "banned_until"),
        name = "bans",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "member_id"}))
public class BanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Instant bannedAt;

    @Column(nullable = true)
    private Instant bannedUntil;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private Long bannedBy;

}