package com.example.my_bot.exception.timer;

import com.example.my_bot.exception.role.RoleException;

public class TimerNotFoundException extends TimerException {


    public TimerNotFoundException(long id) {
        super("Не найдено таймера с id "+id);
    }
}
