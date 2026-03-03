package com.example.my_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "user_id"}))
public class ChatMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "chat_id")
    private Long chatId;

    @Column(nullable = false, name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private int rolePriority;

    @Column(nullable = false)
    private boolean isChatAdmin;

    @Column(nullable = false)
    private boolean isInChat;

    @Column(nullable = true)
    private Long invitedById;

}






