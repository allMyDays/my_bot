package com.example.my_bot.dto.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


@AllArgsConstructor
@Setter
@Getter
public class MemberWithRoleDto {

    private long userId;

    private int rolePriority;

    @JsonProperty("chatAdmin")
    private boolean isChatAdmin;

    @JsonProperty("inChat")
    private boolean isInChat;

}
