package com.example.my_bot.entity;

import com.example.my_bot.enumeration.timer.TimerType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_chat_id", columnList = "chatId"),
        @Index(name = "idx_next_execution", columnList = "nextExecution")
})
@Check(constraints = "(type != 'EACH') OR (type = 'EACH' AND interval_seconds IS NOT NULL)")
public class TimerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "chat_id")
    private Long chatId;

    @Column(nullable = false)
    private Long creatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimerType type;

    @Column(nullable = false)
    private String fullCommand;

    @Column(nullable = false)
    private Instant nextExecution;

    @Column(nullable = true)
    private long intervalSeconds;  // период в секундах для типа "каждые"


    public TimerEntity(long chatId, long creatorId, TimerType type, String fullCommand, Instant nextExecution) {
        this.chatId = chatId;
        this.creatorId = creatorId;
        this.type = type;
        this.fullCommand = fullCommand;
        this.nextExecution = nextExecution;
    }

    public TimerEntity(Long chatId, Long creatorId, TimerType type, String fullCommand, long intervalSeconds, Instant nextExecution) {
        this.chatId = chatId;
        this.creatorId = creatorId;
        this.type = type;
        this.fullCommand = fullCommand;
        this.intervalSeconds = intervalSeconds;
        this.nextExecution = nextExecution;
    }
}
