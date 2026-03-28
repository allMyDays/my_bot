package com.example.my_bot.dto.member;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;

@AllArgsConstructor
@NoArgsConstructor
@Setter
public class ParseMemberInputResult {

   private Long memberId=null;
    @Getter
   private boolean isFwdMessage=false;

    public Optional<Long> getMemberId() {
        return Optional.ofNullable(memberId);
    }
}
