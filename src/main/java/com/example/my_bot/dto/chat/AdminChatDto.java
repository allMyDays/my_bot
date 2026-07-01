package com.example.my_bot.dto.chat;

import com.example.my_bot.enumeration.TimeZoneType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminChatDto {

    private long chatId;

    private Set<Long> boundChats = new HashSet<>();

}
