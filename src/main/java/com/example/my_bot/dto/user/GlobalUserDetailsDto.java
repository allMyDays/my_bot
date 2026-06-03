package com.example.my_bot.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Optional;


@Setter
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GlobalUserDetailsDto {

    private long userId;

    private boolean isBanned;

    private Long boundChat;

    @JsonIgnore
    public Optional<Long> getOptionalBoundChat() {
        return Optional.ofNullable(boundChat);
    }

}
