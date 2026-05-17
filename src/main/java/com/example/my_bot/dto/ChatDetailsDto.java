package com.example.my_bot.dto;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.Instant;
import java.util.Optional;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatDetailsDto {

    private Long chatId;

    private Character prefix;

    private Instant lastSyncTime;

    private boolean silentRestriction;

    private boolean messageReplying;

    private TimeZoneType timeZoneType;

    private Long banPeriodSeconds;

    private boolean autoUnban;

    private String chatCode;

    Long boundLogChat;

    @JsonIgnore
    public Optional<Character> getOptionalPrefix() {
        return Optional.ofNullable(prefix);
    }

    @JsonIgnore
    public Optional<Instant> getOptionalLastSyncTime() {
        return Optional.ofNullable(lastSyncTime);
    }

    public Optional<Long> getOptionalBanPeriod() {
        return Optional.ofNullable(banPeriodSeconds);
    }
}
