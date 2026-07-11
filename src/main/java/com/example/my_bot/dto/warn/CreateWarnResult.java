package com.example.my_bot.dto.warn;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Optional;


@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class CreateWarnResult {

    private int newWarnQuantity;

    private int maxWarnQuantity;

    private Instant expiresAt;

    private boolean isWarnLimitReached;

    public CreateWarnResult setNewWarnQuantity(int newWarnQuantity) {
        this.newWarnQuantity = newWarnQuantity;
        return this;
    }

    public CreateWarnResult setMaxWarnQuantity(int maxWarnQuantity) {
        this.maxWarnQuantity = maxWarnQuantity;
        return this;
    }

    public CreateWarnResult setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public CreateWarnResult setWarnLimitReached(boolean warnLimitReached) {
        isWarnLimitReached = warnLimitReached;
        return this;
    }

    public Optional<Instant> getOptionalExpiresAt() {
        return Optional.ofNullable(expiresAt);
    }
}
