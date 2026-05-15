package com.example.my_bot.entity;

import com.example.my_bot.enumeration.event.MyEventType;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
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
@Check(constraints = "(role_priority IS NULL AND member_to_trigger IS NOT NULL) OR (role_priority IS NOT NULL AND member_to_trigger IS NULL)")
public class EventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MyEventType type;

    @Column(name = "role_priority",nullable = true)
    private Integer rolePriority;

    @Column(name = "member_to_trigger", nullable = true)
    private Long memberToTrigger;

    @Column(nullable = true)
    private String argument;

    @Column(nullable = false)
    private long creatorId;

    @Column(nullable = true)
    private String fullCommand;

    @Column(name = "ae_max_usage", nullable = true)
    @Check(constraints = "ae_max_usage >= 1")
    private Integer AEMaxUsage;

    @Column(name = "ae_period_in_seconds", nullable = true)
    private Integer AEPeriodSec;

    @Column(name = "start_day_time", nullable = true)
    private LocalTime startDayTime;

    @Column(name = "end_day_time", nullable = true)
    private LocalTime endDayTime;

    @Column(name = "cd_period_in_seconds", nullable = true)
    private Integer CDPeriodSec;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Long> exceptionalMembers = new HashSet<>();

    @Column(name = "new_members_period_in_seconds", nullable = true)
    private Integer newMembersPeriodSec;

    @Column(nullable = false)
    private boolean delete;

    @Column(nullable = false)
    private boolean reply;

    @Column(nullable = false)
    private boolean silent;



    public EventEntity(long chatId,
                       @NonNull MyEventType type,
                       @Nullable Integer rolePriority,
                       @Nullable Long memberToTrigger,
                       @Nullable String argument,
                       long creatorId,
                       @Nullable String fullCommand,
                       boolean delete,
                       boolean reply,
                       boolean silent) {
        this.chatId = chatId;
        this.type = type;
        this.rolePriority = rolePriority;
        this.memberToTrigger = memberToTrigger;
        this.argument = argument;
        this.creatorId = creatorId;
        this.fullCommand = fullCommand;
        this.delete = delete;
        this.reply = reply;
        this.silent = silent;
    }
}