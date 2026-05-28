package com.example.my_bot.exception.member;

import com.example.my_bot.exception.timer.TimerException;
import com.example.my_bot.utils.TimeUtils;
import lombok.NonNull;

import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

public class InactiveMembersIntervalOutOfBoundsException extends MemberException {


    public InactiveMembersIntervalOutOfBoundsException(int min, int max) {

        super("Минимальный период для просмотра списка неактивных участников %s, максимальный — %s"
                .formatted(formatDurationFromSeconds(min, true), formatDurationFromSeconds(max, true))
        );
    }
}
