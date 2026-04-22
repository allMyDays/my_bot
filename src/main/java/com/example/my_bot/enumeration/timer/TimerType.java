package com.example.my_bot.enumeration.timer;

import lombok.Getter;
@Getter
public enum TimerType {

    ONCE("в","Один раз в заданное время"),
    DAILY("ежедневно", "Каждый день в одно и то же время"),
    EACH("каждые", "Циклично через заданный промежуток времени");
   private final String cyrillicType;
   private final String description;

    TimerType(String cyrillicType, String description) {
        this.cyrillicType = cyrillicType;
        this.description = description;
    }

}
