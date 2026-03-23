package com.example.my_bot.exception.limit;

import com.example.my_bot.exception.role.RoleException;

import static com.example.my_bot.utils.TimeUtils.formatDuration;

public class LimitPeriodOutOfBoundsException extends LimitException {

    public LimitPeriodOutOfBoundsException(int minInSeconds, int maxInSeconds) {
        super("Минимальный период для лимита — %s, максимальный — %s"
                .formatted(formatDuration(minInSeconds, true), formatDuration(maxInSeconds, true)));
    }
}
