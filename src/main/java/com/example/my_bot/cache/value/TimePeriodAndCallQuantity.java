package com.example.my_bot.cache.value;

import lombok.Getter;

@Getter
public class TimePeriodAndCallQuantity{
    private final long timePeriod;
    private final int callQuantity;

    public TimePeriodAndCallQuantity(long timePeriod, int callQuantity) {
        this.timePeriod = timePeriod;
        this.callQuantity = callQuantity;
    }
}
