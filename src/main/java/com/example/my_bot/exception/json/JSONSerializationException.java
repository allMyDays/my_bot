package com.example.my_bot.exception.json;

import lombok.NonNull;

public class JSONSerializationException extends RuntimeException{

    public JSONSerializationException(@NonNull Class<?> clazz, @NonNull Exception e) {

        super("Произошла ошибка при попытке сериализовать объект класса %s в JSON. Текст ошибки: %s"
                .formatted(clazz.getName(), e.getMessage()), e);


  }
}
