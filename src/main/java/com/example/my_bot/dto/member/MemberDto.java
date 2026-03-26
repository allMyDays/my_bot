package com.example.my_bot.dto.member;

import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


@AllArgsConstructor
@Setter
@Getter
public class MemberDto {

    private long userId;

    private int rolePriority;

    private boolean isChatAdmin;

    private MemberPresenceType presenceType;

}
