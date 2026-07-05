package com.example.my_bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(
        indexes = {
                @Index(name = "idx_command_log_created_at", columnList = "created_at"),
                @Index(name = "idx_command_log_chat_id", columnList = "chat_id"),
        }
)
public class CommandLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "my_seq")
    @SequenceGenerator(name = "my_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, name = "chat_id")
    private long chatId;

    @Column(nullable = false, name = "from_id")
    private long fromId;

    @Column(nullable = false, name = "command_name")
    private String commandName;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    public CommandLogEntity(long chatId, long fromId, @NonNull String commandName, @NonNull Instant createdAt) {
        this.chatId = chatId;
        this.fromId = fromId;
        this.commandName = commandName;
        this.createdAt = createdAt;
    }
}