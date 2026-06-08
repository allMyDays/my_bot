package com.example.my_bot.dto.member;

import com.example.my_bot.dto.ban.MemberBanStatus;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Optional;

@AllArgsConstructor
@Setter
@Getter
public class MemberDto {

    private long userId;

    private int rolePriority;

    private Integer immuneRolePriority;

    private boolean isChatAdmin;

    private MemberPresenceType presenceType;

    private Instant firstAppearance;

    public Optional<Integer> getOptionalImmuneRolePriority() {
        return Optional.ofNullable(immuneRolePriority);
    }
}
