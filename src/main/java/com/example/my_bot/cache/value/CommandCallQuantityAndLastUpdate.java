package com.example.my_bot.cache.value;

import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class CommandCallQuantityAndLastUpdate {
    private AtomicInteger commandCallQuantity;
    private Instant lastUpdate;

    public CommandCallQuantityAndLastUpdate(@NonNull AtomicInteger commandCallQuantity, @NonNull Instant lastUpdate) {
        this.commandCallQuantity = commandCallQuantity;
        this.lastUpdate = lastUpdate;
    }

    public void setCommandCallQuantity(@NonNull AtomicInteger commandCallQuantity) {
        this.commandCallQuantity = commandCallQuantity;
    }

    public void setLastUpdate(@NonNull Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
