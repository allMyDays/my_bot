package com.example.my_bot.enumeration;

import lombok.Getter;
import lombok.NonNull;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CommandExecutionStatus {

    SUCCESS("команда выполнилась с успехом"),
    ARGUMENT_VALIDATION_ERROR("ошибка валидации аргументов команды"),
    BUSINESS_LOGIC_ERROR("ошибка бизнес-логики"),
    VK_API_ERROR("ошибка вызова методов VK API"),
    ACTION_CONFIRMATION_IS_REQUIRED("требуется подтверждение действия (повторный ввод команды)");

    @Getter
    private final String description;


    CommandExecutionStatus(@NonNull String description) {
        this.description = description;
    }
}
