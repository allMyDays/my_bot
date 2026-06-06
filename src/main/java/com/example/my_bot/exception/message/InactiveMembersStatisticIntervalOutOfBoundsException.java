package com.example.my_bot.exception.message;

import com.example.my_bot.exception.member.MemberException;
import com.vk.api.sdk.objects.messages.Message;

import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

public class InactiveMembersStatisticIntervalOutOfBoundsException extends MessageException {


    public InactiveMembersStatisticIntervalOutOfBoundsException(int min, int max) {

        super("Минимальный период для просмотра списка неактивных участников %s, максимальный — %s"
                .formatted(formatDurationFromSeconds(min, true), formatDurationFromSeconds(max, true))
        );
    }
}
