package com.example.my_bot.entity;

import com.example.my_bot.enumeration.event.MyEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "event", indexes = @Index(name = "events_idx_chat_id", columnList = "chatId"))
@Check(constraints = "(ae_max_usage IS NULL AND ae_period_in_seconds IS NULL) OR (ae_max_usage IS NOT NULL AND ae_period_in_seconds IS NOT NULL)")
@Check(constraints = "(start_day_time IS NULL AND end_day_time IS NULL) OR (start_day_time IS NOT NULL AND end_day_time IS NOT NULL)")
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

    @Column(name = "ae_max_usage", nullable = true)
    @Check(constraints = "ae_max_usage >= 1")
    private Integer AEMaxUsage;

    @Column(name = "ae_period_in_seconds", nullable = true)
    private Integer AEPeriodInSeconds;

    @Column(name = "start_day_time", nullable = true)
    private LocalTime startDayTime;

    @Column(name = "end_day_time", nullable = true)
    private LocalTime endDayTime;

    @Column(name = "cd_period_in_seconds", nullable = true)
    private Integer CDPeriodInSeconds;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Long> exceptionalMembers = new HashSet<>();

    public EventEntity(Long chatId, MyEventType type, int rolePriority, String argument, long creatorId, String fullCommand) {
        this.chatId = chatId;
        this.type = type;
        this.rolePriority = rolePriority;
        this.argument = argument;
        this.creatorId = creatorId;
        this.fullCommand = fullCommand;
    }
}