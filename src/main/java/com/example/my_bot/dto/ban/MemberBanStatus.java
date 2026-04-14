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

public class MemberBanStatus {

    @Getter
    private Long memberId;

    @Getter
    private boolean isBanned;

    private Instant bannedUntil;

    public Optional<Instant> getBannedUntil() {
        return Optional.ofNullable(bannedUntil);
    }
}
