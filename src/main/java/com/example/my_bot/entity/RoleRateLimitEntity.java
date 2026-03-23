package com.example.my_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "role_rate_limit", uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "command_name","role_priority"}))
public class RoleRateLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "command_name", nullable = false)
    private String commandName;

    @Column(name = "role_priority", nullable = false)
    private Integer rolePriority;

    @Column(name = "personal", nullable = false)
    private boolean isPersonal;

    @Column(name = "max_usage", nullable = false)
    @Check(constraints = "max_usage >= 1")
    private int maxUsage;

    @Column(nullable = false)
    private int periodInSeconds;

    public RoleRateLimitEntity(Long chatId, String commandName, Integer rolePriority, boolean isPersonal, int maxUsage, int periodInSeconds) {
        this.chatId = chatId;
        this.commandName = commandName;
        this.rolePriority = rolePriority;
        this.isPersonal = isPersonal;
        this.maxUsage = maxUsage;
        this.periodInSeconds = periodInSeconds;
    }
}
