package com.example.my_bot.exception.limit;

import static com.example.my_bot.utils.TimeUtils.formatDuration;

public class RateLimitPeriodOutOfBoundsException extends RateLimitException {

    public RateLimitPeriodOutOfBoundsException(int minInSeconds, int maxInSeconds) {
        super("Минимальный период для лимита — %s, максимальный — %s"
                .formatted(formatDuration(minInSeconds, true), formatDuration(maxInSeconds, true)));
    }
}
