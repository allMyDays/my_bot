package com.example.my_bot.enumeration.event;
import static com.example.my_bot.enumeration.event.ChatEventType.*;
import static com.example.my_bot.enumeration.event.EventArgumentType.*;
import static com.vk.api.sdk.objects.messages.MessageActionStatus.*;

import com.vk.api.sdk.objects.messages.MessageActionStatus;
import lombok.Getter;
import lombok.NonNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


public enum MyEventType {
    ANY_MESSAGE("сообщение","Отправка любого сообщения", TEXT_OR_ATTACHMENT),

    INVITE_ANOTHER("приглашение", "Приглашение в чат", ACTION, Set.of(CHAT_INVITE_USER)),
    KICK_ANOTHER("исключение","Исключение участника", ACTION, Set.of(CHAT_KICK_USER)),
    ENTRANCE_BY_LINK("вход","Вход по ссылке", ACTION, Set.of(CHAT_INVITE_USER_BY_LINK)),
    CHANGE_TITLE("название","Смена названия чата",  ACTION, Set.of(CHAT_TITLE_UPDATE)),
    SELF_RETURN("возврат","Самостоятельное возвращение в чат", ACTION, Set.of(CHAT_INVITE_USER)),
    CHANGE_PIN_MESSAGE("закреп","Смена закреплённого сообщения", ACTION, Set.of(CHAT_PIN_MESSAGE, CHAT_UNPIN_MESSAGE)),
    SELF_LEAVE("выход", "Выход из чата", ACTION, Set.of(CHAT_KICK_USER)),
    INVITE_BANNED("забаненный", "Приглашение забаненного", ACTION, Set.of(CHAT_INVITE_USER)),
    INVITE_GROUP("сообщество", "Приглашение сообщества в чат", ACTION, Set.of(CHAT_INVITE_USER)),
    SCREENSHOT("скриншот", "Создание скриншота чата", ACTION, Set.of(CHAT_SCREENSHOT)),
    CHANGE_CHAT_PHOTO("фоточата","Смена фотографии чата", ACTION, Set.of(CHAT_PHOTO_UPDATE, CHAT_PHOTO_REMOVE)),


    MARKET("товар","Отправка товара в чат", ATTACHMENT),
    STICKER("стикер","Отправка любого стикера", ATTACHMENT),
    VOICE_MESSAGE("голосовое", "Отправка голосового сообщения", ATTACHMENT),
    POLL("опрос", "Опрос в сообщении", ATTACHMENT),
    CALL("звонок", "Создание звонка в чате", ATTACHMENT),


    WORD_FILTER("фильтр","Фильтр слов", TEXT, STRING,1,500),
    STRICT_WORD_FILTER("строгийфильтр","Строгий фильтр слов", TEXT, STRING,1,500),
    MINIMUM_SYMBOLS("минсимволов","Минимальное количество символов", TEXT, INTEGER, 1, 300),
    MAXIMUM_SYMBOLS("макссимволов","Максимальное количество символов", TEXT, INTEGER, 5, 600),
    EMOJI_QUANTITY("эмоджи","Количество эмоджи в сообщении",TEXT, INTEGER,1,3000),
    ROW_QUANTITY("строки","Количество строк", TEXT, INTEGER, 2, 300),
    ALL_MENTION("пушвсех","Упоминание всех участников", TEXT),
    ONLINE_MENTION("пушонлайн", "Упоминание всех онлайн участников", TEXT),
    ANY_LINK("ссылка","Любая ссылка", TEXT),
    ZALGO("зальго","Зальго-текст в сообщении", TEXT),
    CHAT_INVITE_LINK("чатссылка","Ссылка на чат", TEXT),
    PUSH_QUANTITY("числопушей","Количество пушей участников в сообщении",TEXT, INTEGER,1,300),
    SAME_MESSAGES("одинаковые","Одинаковые сообщения в минуту", TEXT);







    @Getter
    private final String cyrillicType;
    @Getter
    private final String description;
    @Getter
    private final ChatEventType chatEventType;
    @Getter
    private final EventArgumentType argumentType;
    @Getter
    private final int argMin;
    @Getter
    private final int argMax;

    private final Set<MessageActionStatus> chatActionTypeList;

    private static final Map<String, MyEventType> cyrillicTypeMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z -> z.cyrillicType.toLowerCase(),
                            Function.identity()
                    ));

    public Optional<Set<MessageActionStatus>> getChatActionTypeList(){
        return Optional.ofNullable(chatActionTypeList);
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, Set<MessageActionStatus> chatActionType) {
        this.cyrillicType = cyrillicType;
        this.description = description;
        this.chatEventType = chatEventType;
        this.argumentType = EventArgumentType.NONE;
        this.argMin = -1;
        this.argMax = -1;
        chatActionTypeList = Collections.unmodifiableSet(chatActionType);
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType) {
        this.cyrillicType = cyrillicType;
        this.description = description;
        this.chatEventType = chatEventType;
        this.argumentType = EventArgumentType.NONE;
        this.argMin = -1;
        this.argMax = -1;
        this.chatActionTypeList = null;
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, EventArgumentType argumentType, int argMin, int argMax) {
        this.cyrillicType = cyrillicType;
        this.description = description;
        this.argumentType = argumentType;
        this.chatEventType = chatEventType;
        this.argMin = argMin;
        this.argMax = argMax;
        this.chatActionTypeList = null;
    }


    public static Optional<MyEventType> findByCyrillicType(@NonNull String type){
        return Optional.ofNullable(cyrillicTypeMAP.get(type.trim().toLowerCase()));
    }




}
