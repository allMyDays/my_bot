package com.example.my_bot.entity;

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
@Table(name = "all_users",
        indexes = @Index(name = "idx_bound_chat", columnList = "bound_chat")
)
public class GlobalUserEntity {

    @Id
    private Long userId;

    @Column(nullable = false)
    private boolean isBanned;

    @Column(name = "bound_chat", nullable = true)
    private Long boundChat;

    private String fullNameInNom;

    private String fullNameInGen;

    private String fullNameInDat;

    private String fullNameInAcc;

    private String fullNameInIns;

    private String fullNameInAbl;

    public GlobalUserEntity(Long userId) {
        this.userId = userId;
    }
}
