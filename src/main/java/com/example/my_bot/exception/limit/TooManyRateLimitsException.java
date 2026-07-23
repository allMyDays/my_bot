package com.example.my_bot.exception.limit;

import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

public class TooManyRateLimitsException extends RateLimitException {

    public TooManyRateLimitsException() {
        super("Вы создали уже достаточно много временных лимитов.");
    }
}
