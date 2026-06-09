package com.example.my_bot.dto.ban;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Optional;


@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class MemberBanStatus {

    private Long memberId;

    private boolean isBanned;

    private Instant bannedUntil;

    public Optional<Instant> getOptionalBannedUntil() {
        return Optional.ofNullable(bannedUntil);
    }

}
