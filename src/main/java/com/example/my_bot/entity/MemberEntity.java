package com.example.my_bot.entity;

import com.example.my_bot.enumeration.member.MemberPresenceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "user_id"}))
public class MemberEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberPresenceType presenceType;

    @Column(nullable = true)
    private Long invitedById;

    @Column(nullable = false)
    private Instant firstAppearance;

    public MemberEntity(Long chatId, Long userId, int rolePriority, boolean isChatAdmin, MemberPresenceType presenceType, Long invitedById, Instant firstAppearance) {
        this.chatId = chatId;
        this.userId = userId;
        this.rolePriority = rolePriority;
        this.isChatAdmin = isChatAdmin;
        this.presenceType = presenceType;
        this.invitedById = invitedById;
        this.firstAppearance = firstAppearance;
    }
}






