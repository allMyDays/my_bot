package com.example.my_bot.dto.member.stat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Optional;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class MemberStatisticDto{

    private long userId;
    private long totalMessages;
    private long totalSymbols;


}
