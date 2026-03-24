package com.example.my_bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(nullable = true)
    private boolean silentRestriction;


}
