package com.example.my_bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.Instant;
import java.util.Optional;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatDetailsDto {

    private Character prefix;

    private Instant lastSyncTime;

    @JsonIgnore
    public Optional<Character> getOptionalPrefix() {
        return Optional.ofNullable(prefix);
    }

    @JsonIgnore
    public Optional<Instant> getOptionalLastSyncTime() {
        return Optional.ofNullable(lastSyncTime);
    }

}
