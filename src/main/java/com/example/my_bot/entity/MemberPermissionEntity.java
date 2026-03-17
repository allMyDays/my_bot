package com.example.my_bot.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "member_permission", uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "command_name", "user_id"}))
public class MemberPermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "command_name", nullable = false)
    private String commandName;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false)
    boolean isAllowed;


    public MemberPermissionEntity(long chatId, String commandName, int userId, boolean isAllowed) {
        this.chatId = chatId;
        this.commandName = commandName;
        this.userId = userId;
        this.isAllowed = isAllowed;
    }
}

