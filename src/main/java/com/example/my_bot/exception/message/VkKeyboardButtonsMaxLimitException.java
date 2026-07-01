package com.example.my_bot.exception.message;

import lombok.NonNull;

public class VkKeyboardButtonsMaxLimitException extends MessageException {

    public VkKeyboardButtonsMaxLimitException(int maxLimit){
        super("Максимальный лимит кнопок  — %d".formatted(maxLimit));
    }

    public VkKeyboardButtonsMaxLimitException(@NonNull String message){
        super(message);
    }



}
