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
@Table(name = "chat")
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
    @Enumerated(EnumType.STRING)
    private TimeZoneType timeZoneType;

    @Column(nullable = true)
    private Long banPeriodSeconds;


}
