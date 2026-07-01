package com.example.my_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AdminChatEntity {

    @Id
    private Long chatId;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Long> boundChats = new HashSet<>();

    public AdminChatEntity(long chatId) {
        this.chatId = chatId;
    }
}
