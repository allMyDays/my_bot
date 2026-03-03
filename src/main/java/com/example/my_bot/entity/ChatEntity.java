package com.example.my_bot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat")
public class ChatEntity {

    @Id
    private Long id;

    private char prefix = '!';





}
