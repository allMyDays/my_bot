package com.example.my_bot.dto.member.inactive;

import lombok.*;

import java.time.Instant;
import java.util.Optional;

@NoArgsConstructor
@AllArgsConstructor
@Setter
public class InactiveMemberDto {

    @Getter
    long userId;

    Instant lastMessageAt;

    public Optional<Instant> getLastMessageAt() {
        return Optional.ofNullable(lastMessageAt);
    }
}
