package com.example.my_bot.entity;

import com.example.my_bot.enumeration.event.ChatEventType;
import com.example.my_bot.enumeration.event.MyEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "event", indexes = @Index(name = "events_idx_chat_id", columnList = "chatId"))
@Check(constraints = "(max_usage IS NULL AND period_in_seconds IS NULL) OR (max_usage IS NOT NULL AND period_in_seconds IS NOT NULL)")
public class EventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MyEventType type;

    @Column(nullable = false)
    private int rolePriority;

    @Column(nullable = true)
    private String argument;

    @Column(nullable = false)
    private long creatorId;

    @Column(nullable = false)
    private String fullCommand;

    @Column(name = "max_usage", nullable = true)
    @Check(constraints = "max_usage >= 1")
    private Integer maxUsage;

    @Column(name = "period_in_seconds", nullable = true)
    private Integer periodInSeconds;

    public EventEntity(Long chatId, MyEventType type, int rolePriority, String argument, long creatorId, String fullCommand) {
        this.chatId = chatId;
        this.type = type;
        this.rolePriority = rolePriority;
        this.argument = argument;
        this.creatorId = creatorId;
        this.fullCommand = fullCommand;
    }
}