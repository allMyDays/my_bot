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
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"chat_id", "conversation_message_id"})
        },
        indexes = {
                @Index(name = "idx_created_at", columnList = "created_at"),
                @Index(name = "idx_from_id", columnList = "from_id")

        }
)
public class MessageLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "my_seq")
    @SequenceGenerator(name = "my_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, name = "chat_id")
    private long chatId;

    @Column(nullable = false, name = "from_id")
    private long fromId;

    @Column(nullable = false, name = "conversation_message_id")
    private int conversationMessageId;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    @Column(nullable = false)
    private int symbolsQuantity;

    @Column(nullable = false)
    private boolean isDeleted;

    public MessageLogEntity(long chatId, long fromId, int conversationMessageId, @NonNull Instant createdAt, int symbolsQuantity, boolean isDeleted) {
        this.chatId = chatId;
        this.fromId = fromId;
        this.conversationMessageId = conversationMessageId;
        this.createdAt = createdAt;
        this.symbolsQuantity = symbolsQuantity;
        this.isDeleted = isDeleted;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        MessageLogEntity that = (MessageLogEntity) object;
        return chatId == that.chatId && conversationMessageId == that.conversationMessageId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId, conversationMessageId);
    }
}