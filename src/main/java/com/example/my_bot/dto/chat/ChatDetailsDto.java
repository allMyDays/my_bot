package com.example.my_bot.dto.chat;

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

    private String chatTitle;

    private Character prefix;

    private Instant lastSyncTime;

    private boolean silentRestriction;

    private boolean messageReplying;

    private TimeZoneType timeZoneType;

    private Long banPeriodSeconds;

    private boolean autoUnban;

    private String chatCode;

    private Long boundLogChat;

    private Long boundSubmanagerId;

    private Long submanagerChatId;

    private boolean isSubPosts;



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
