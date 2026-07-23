package com.example.my_bot.dto.member.stat;

import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import jakarta.annotation.Nullable;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ChatMembersStatisticResult{

    @Setter
    private long totalMessageQuantity=0;

    @Setter
    private long totalSymbolsQuantity=0;

    @Setter
    private int totalMembersQuantity=0;

    @Setter
    private Instant start;

    @Setter
    private Instant end;

    private List<MemberStatisticDto> memberStatisticDtoList = new ArrayList<>();


    public void addMemberStat(@NonNull MemberStatisticDto memberStatisticDto){
        memberStatisticDtoList.add(memberStatisticDto);
    }
}
