package com.example.my_bot.exception.limit;

import com.example.my_bot.exception.role.RoleException;

public class LimitUsageOutOfBoundsException extends LimitException {

    public LimitUsageOutOfBoundsException(int min, int max) {
        super("Минимально возможное ограничение для лимита — %d. Максимальное — %d"
                .formatted(min, max));
    }
}
