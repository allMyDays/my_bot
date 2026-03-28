package com.example.my_bot.exception.limit;

import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

public class RateLimitPeriodOutOfBoundsException extends RateLimitException {

    public RateLimitPeriodOutOfBoundsException(int minInSeconds, int maxInSeconds) {
        super("Минимальный период для лимита — %s, максимальный — %s"
                .formatted(formatDurationFromSeconds(minInSeconds, true), formatDurationFromSeconds(maxInSeconds, true)));
    }
}
