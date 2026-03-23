package com.example.my_bot.exception.limit;

public class RateLimitUsageOutOfBoundsException extends RateLimitException {

    public RateLimitUsageOutOfBoundsException(int min, int max) {
        super("Минимально возможное ограничение для лимита — %d. Максимальное — %d"
                .formatted(min, max));
    }
}
