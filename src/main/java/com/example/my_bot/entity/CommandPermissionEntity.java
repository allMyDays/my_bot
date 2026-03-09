package com.example.my_bot.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "command_permission",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"chat_id", "command_name", "user_id"}),
                @UniqueConstraint(columnNames = {"chat_id", "command_name", "role_priority"})
        },
        check = {
                @CheckConstraint(constraint = "(user_id IS NOT NULL AND role_priority IS NULL) OR (user_id IS NULL AND role_priority IS NOT NULL)"),
                @CheckConstraint(constraint = "role_priority IS NULL OR allowed = true") // для ролей запрет невозможен
        })
public class CommandPermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "command_name", nullable = false)
    private String commandName;

    @Column(name = "user_id", nullable = true)
    private Integer userId;          // null если это настройка для роли чата

    @Column(name = "role_priority", nullable = true)
    private Integer rolePriority;           // null если это настройка для конкретного участника чата

    @Column(name = "allowed", nullable = false)
    private boolean allowed;          // true всегда для роли, для участников true или false


    public CommandPermissionEntity(Long chatId, String commandName, Integer userId, Integer rolePriority, boolean allowed) {
        this.chatId = chatId;
        this.commandName = commandName;
        this.userId = userId;
        this.rolePriority = rolePriority;
        this.allowed = allowed;
    }
}

