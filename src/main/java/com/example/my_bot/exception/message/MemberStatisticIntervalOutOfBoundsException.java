package com.example.my_bot.exception.message;

import com.example.my_bot.exception.member.MemberException;
import com.vk.api.sdk.objects.messages.Message;
import lombok.NonNull;

import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

public class MemberStatisticIntervalOutOfBoundsException extends MessageException {


    public MemberStatisticIntervalOutOfBoundsException(int min, int max){
        super("Минимальный период для просмотра статистики участников %s, максимальный — %s"
                .formatted(formatDurationFromSeconds(min, true), formatDurationFromSeconds(max, true))
        );
    }

    public MemberStatisticIntervalOutOfBoundsException(@NonNull String message){
        super(message);
    }

}
