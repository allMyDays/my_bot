package com.example.my_bot.entity;

import com.example.my_bot.enumeration.event.MyEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(indexes = @Index(name = "event_idx_chat_id", columnList = "chatId"))
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

    public EventEntity(Long chatId, MyEventType type, int rolePriority, String argument, long creatorId, String fullCommand) {
        this.chatId = chatId;
        this.type = type;
        this.rolePriority = rolePriority;
        this.argument = argument;
        this.creatorId = creatorId;
        this.fullCommand = fullCommand;
    }
}