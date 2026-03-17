package com.example.my_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "role", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"chat_id", "role_name"}),
        @UniqueConstraint(columnNames = {"chat_id", "role_priority"})
})
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "chat_id")
    private Long chatId;

    @Column(nullable = false, name = "role_priority")
    private int rolePriority;

    @Column(nullable = false)
    private String roleName;


    public RoleEntity(Long chatId, int rolePriority, String roleName) {
        this.chatId = chatId;
        this.rolePriority = rolePriority;
        this.roleName = roleName;
    }
    public RoleEntity(Long chatId, int rolePriority) {
        this.chatId = chatId;
        this.rolePriority = rolePriority;
    }
}
