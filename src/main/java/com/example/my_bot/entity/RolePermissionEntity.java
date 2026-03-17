package com.example.my_bot.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "role_permission", uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "command_name"}))
public class RolePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "command_name", nullable = false)
    private String commandName;

    @Column(name = "role_priority", nullable = false)
    private Integer rolePriority;


    public RolePermissionEntity(long chatId, String commandName, int rolePriority) {
        this.chatId = chatId;
        this.commandName = commandName;
        this.rolePriority = rolePriority;
    }
}

