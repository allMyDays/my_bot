package com.example.my_bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name = "idx_warn_chat_id", columnList = "chat_id"),
        @Index(name = "idx_warn_member_id", columnList = "member_id"),
        @Index(name = "idx_warn_expires_at", columnList = "expires_at")
})
public class WarnEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "chat_id")
    private Long chatId;

    @Column(nullable = false, name = "member_id")
    private Long memberId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = true, name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private Long givenBy;

}