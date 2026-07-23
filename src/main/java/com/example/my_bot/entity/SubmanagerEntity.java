package com.example.my_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "submanager")
public class SubmanagerEntity {

    @Id
    private Long groupId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedToken;

    @Column(nullable = false)
    private int serverId;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String secretKey;

}
