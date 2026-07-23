package com.example.my_bot.entity;

import com.example.my_bot.enumeration.TimeZoneType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat"
        ,uniqueConstraints = {
              @UniqueConstraint(name = "uk_chat_chat_code", columnNames = "chat_code"),
              @UniqueConstraint(columnNames = {"bound_submanager_id", "submanager_chat_id"})
        }
        ,indexes = {
              @Index(name = "idx_bound_log_chat", columnList = "bound_log_chat"),
        }
)
@Check(constraints = "(bound_submanager_id IS NULL AND submanager_chat_id IS NULL) OR (bound_submanager_id IS NOT NULL AND submanager_chat_id IS NOT NULL)")
public class ChatEntity {

    @Id
    private Long chatId;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String chatTitle;

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
    private Long banTimePeriodSec;

    @Column(nullable = true)
    private Long warnTimePeriodSec;

    @Column(nullable = false)
    private int warnMaxQuantity;

    @Column(nullable = false)
    private boolean autoUnban;

    @Column(name = "chat_code", nullable = false)
    private String chatCode;

    @Column(name = "bound_log_chat", nullable = true)
    private Long boundLogChat;

    @Column(name = "bound_submanager_id", nullable = true)
    private Long boundSubmanagerId;

    @Column(name = "submanager_chat_id", nullable = true)
    private Long submanagerChatId;

    private boolean isSubPostsEnabled;

    public ChatEntity(Long chatId) {
        this.chatId = chatId;
    }
}
