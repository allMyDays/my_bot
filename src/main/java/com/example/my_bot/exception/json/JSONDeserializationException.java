package com.example.my_bot.exception.json;

import lombok.NonNull;

public class JSONDeserializationException extends RuntimeException{

    public JSONDeserializationException(@NonNull Class<?> clazz, @NonNull String JSON, @NonNull Exception e) {

        super("Произошла ошибка при попытке десериализовать JSON в объект класса %s. JSON: %s Текст ошибки: %s"
                .formatted(clazz.getName(), JSON, e.getMessage()), e);


  }
}
