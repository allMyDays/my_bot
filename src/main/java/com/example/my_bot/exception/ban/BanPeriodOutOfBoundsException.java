package com.example.my_bot.exception.ban;

import com.example.my_bot.exception.limit.RateLimitException;

import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

public class BanPeriodOutOfBoundsException extends BanException {

    public BanPeriodOutOfBoundsException(int minInSeconds) {
        super("Минимальный период для временного бана — %s"
                .formatted(formatDurationFromSeconds(minInSeconds, true)));
    }
}
