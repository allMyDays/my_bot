package com.example.my_bot.enumeration.event;
import static com.example.my_bot.enumeration.event.ChatEventType.*;
import static com.example.my_bot.enumeration.event.EventArgumentType.*;

import com.example.my_bot.exception.event.EventTypeNotRequiresArgumentException;
import com.example.my_bot.exception.event.IncorrectEventArgumentException;
import com.example.my_bot.utils.ChatUtils;
import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum MyEventType {

    ANY_MESSAGE("сообщение","Отправка любого сообщения", ALL),
    EMOJI_QUANTITY("эмоджи","Количество эмоджи в сообщении",TEXT, INTEGER,1,3000),
    INVITE("приглашение", "Приглашение в чат", ACTION),
    KICK("исключение","Исключение участника", ACTION),
    ENTRANCE_BY_LINK("вход","Вход по ссылке", ACTION),
    CHANGE_TITLE("название","Смена названия чата",  ACTION),
    EXIT("выход", "Выход из чата", ACTION),
    INVITE_BANNED("забаненный", "Приглашение забаненного", ACTION),
    SCREENSHOT("скриншот", "Создание скриншота чата", ACTION),
    PRODUCT("товар","Отправка товара в чат", ATTACHMENT),
    STICKER("стикер","Отправка любого стикера", ATTACHMENT),
    VOICE_MESSAGE("голосовое", "Отправка голосового сообщения", ATTACHMENT),
    POLL("опрос", "Опрос в сообщении", ATTACHMENT),
    WORD_FILTER("фильтр","Фильтр слов", TEXT, STRING,1,500);

    private final String cyrillicType;

    private final String description;

    private final ChatEventType chatEventType;

    private final EventArgumentType argumentType;

    private final int argMin;

    private final int argMax;


    private static final Map<String, MyEventType> cyrillicTypeMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z -> z.cyrillicType.toLowerCase(),
                            Function.identity()
                    ));


    MyEventType(String cyrillicType, String description, ChatEventType chatEventType) {
        this(cyrillicType, description, chatEventType, EventArgumentType.NONE, -1,-1);
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, EventArgumentType argumentType, int argMin, int argMax) {
        this.cyrillicType = cyrillicType;
        this.description = description;
        this.argumentType = argumentType;
        this.chatEventType = chatEventType;
        this.argMin = argMin;
        this.argMax = argMax;
    }


    public static Optional<MyEventType> findByCyrillicType(@NonNull String type){
        return Optional.ofNullable(cyrillicTypeMAP.get(type.trim().toLowerCase()));
    }




}
