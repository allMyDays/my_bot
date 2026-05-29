package com.example.my_bot.dto.member;

import jakarta.annotation.Nullable;
import lombok.*;

import java.time.Instant;
import java.util.*;

@NoArgsConstructor
@Getter
public class InactiveMembersResult {

    @Setter
    private int totalInactiveQuantity;

    private List<InactiveMemberDto> inactiveMembers = new ArrayList<>();

    @Setter
    private Instant thresholdDate;

    public void addNewInactiveMember(long fromId, @Nullable  Instant lastMessageAt){
        inactiveMembers.add(new InactiveMemberDto(fromId, lastMessageAt));
    }






}
