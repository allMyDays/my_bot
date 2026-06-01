package com.example.my_bot.exception.member;

import lombok.NonNull;

import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

public class MemberStatisticIntervalOutOfBoundsException extends MemberException {


    public MemberStatisticIntervalOutOfBoundsException(int min, int max){
        super("Минимальный период для просмотра статистики участников %s, максимальный — %s"
                .formatted(formatDurationFromSeconds(min, true), formatDurationFromSeconds(max, true))
        );
    }

    public MemberStatisticIntervalOutOfBoundsException(@NonNull String message){
        super(message);
    }

}
