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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "conversation_message_id"}))
public class MessageLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "my_seq")
    @SequenceGenerator(name = "my_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, name = "chat_id")
    private long chatId;

    @Column(nullable = false)
    private long fromId;

    @Column(nullable = false, name = "conversation_message_id")
    private int conversationMessageId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean isDeleted;

    public MessageLogEntity(long chatId, long fromId, int conversationMessageId, @NonNull Instant createdAt, boolean isDeleted) {
        this.chatId = chatId;
        this.fromId = fromId;
        this.conversationMessageId = conversationMessageId;
        this.createdAt = createdAt;
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