package com.example.my_bot.cache.value.callback;

import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;

@Getter
public class SecretKeyAndConfirmationCodeAndCompletableFuture {
    private final String secretKey;
    private final String confirmationCode;
    private final CompletableFuture<Boolean> futureConfirmationResult;


    public SecretKeyAndConfirmationCodeAndCompletableFuture(@NonNull String secretKey, @NonNull String confirmationCode, @NonNull CompletableFuture<Boolean> futureConfirmationResult) {
        this.secretKey = secretKey;
        this.confirmationCode = confirmationCode;
        this.futureConfirmationResult = futureConfirmationResult;
    }
}



