package com.example.my_bot.enumeration.timer;

public enum TimerType {

    ONCE("в"), DAILY("ежедневно"), EACH("каждые");
   private final String cyrillicType;

    TimerType(String cyrillicType) {
        this.cyrillicType = cyrillicType;
    }

    public String getCyrillicType() {
        return cyrillicType;
    }
}
