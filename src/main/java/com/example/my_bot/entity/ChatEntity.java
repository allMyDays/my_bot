package com.example.my_bot.entity;

import com.example.my_bot.enumeration.TimeZoneType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Optional;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat"
        ,uniqueConstraints = @UniqueConstraint(name = "uk_chat_chat_code", columnNames = "chat_code")
        ,indexes = @Index(name = "idx_bound_log_chat", columnList = "bound_log_chat")
)
public class ChatEntity {

    @Id
    private Long chatId;

    @Column(nullable = true)
    private Character prefix;

    @Column(nullable = true)
    private Instant lastSyncTime;

    @Column(nullable = false)
    private boolean silentRestriction;

    @Column(nullable = false)
    private boolean messageReplying;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TimeZoneType timeZoneType;

    @Column(nullable = true)
    private Long banPeriodSeconds;

    @Column(nullable = false)
    private boolean autoUnban;

    @Column(name = "chat_code", nullable = false)
    private String chatCode;

    @Column(name = "bound_log_chat", nullable = true)
    Long boundLogChat;


}
