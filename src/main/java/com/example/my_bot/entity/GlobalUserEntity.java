package com.example.my_bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "all_users")
public class GlobalUserEntity {
    @Id
    private Long userId;

    @Column(nullable = false)
    private boolean isBanned;

    private Long boundChat;

    private String fullNameInNom;

    private String fullNameInGen;

    private String fullNameInDat;

    private String fullNameInAcc;

    private String fullNameInIns;

    private String fullNameInAbl;

    private Instant lastFullNameUpdate;



    public GlobalUserEntity(Long userId) {
        this.userId = userId;
    }
}
