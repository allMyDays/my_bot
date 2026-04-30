package com.example.my_bot.enumeration.event;
import static com.example.my_bot.enumeration.event.ChatEventType.*;
import static com.example.my_bot.enumeration.event.EventArgumentType.*;
import static com.example.my_bot.vk.enumeration.VkActionType.*;

import com.example.my_bot.vk.enumeration.VkActionType;
import com.example.my_bot.vk.enumeration.VkMessageAttachmentType;
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
    CHAT_STYLE_UPDATE("оформление", "Смена оформления чата", ACTION, Set.of(CONVERSATION_STYLE_UPDATE)),
    WITH_SUBSCRIPTION("естьподписка", "Наличие подписки на сообщество", ACTION, Set.of(CHAT_INVITE_USER, CHAT_INVITE_USER_BY_LINK), INTEGER, 1, 9),
    WITHOUT_SUBSCRIPTION("нетподписки", "Отсутствие подписки на сообщество", ACTION, Set.of(CHAT_INVITE_USER, CHAT_INVITE_USER_BY_LINK),  INTEGER, 1, 9),


    FWD_QUANTITY("пересланные","Количество пересланных", FWD_MESSAGES, INTEGER, 1, 100),


    ATTACHMENT_QUANTITY("вложения","Количество вложений", ATTACHMENTS, INTEGER, 1, 10),
    ATTACH_PHOTO("фото","Фото в сообщении", ATTACHMENTS, INTEGER, 1, 10, VkMessageAttachmentType.PHOTO),
    SONG("аудио","Аудио(песня) в сообщении", ATTACHMENTS, INTEGER, 1, 10, VkMessageAttachmentType.AUDIO),
    DOCUMENT("документ","Документ в сообщении", ATTACHMENTS, INTEGER, 1, 10, VkMessageAttachmentType.DOC),
    MARKET("товар","Товар в сообщении", ATTACHMENTS, VkMessageAttachmentType.MARKET),
    STICKER("стикер","Отправка любого стикера", ATTACHMENTS, VkMessageAttachmentType.STICKER),
    POST("пост","Пост в сообщении", ATTACHMENTS, VkMessageAttachmentType.WALL),
    POST_COMMENT("коммент", "Комментарий к посту", ATTACHMENTS, VkMessageAttachmentType.WALL_REPLY),
    POLL("опрос", "Опрос в сообщении", ATTACHMENTS, VkMessageAttachmentType.POLL),
    CALL("звонок", "Создание звонка в чате", ATTACHMENTS, VkMessageAttachmentType.CALL),
    GRAFFITI("граффити", "Граффити в сообщении", ATTACHMENTS, VkMessageAttachmentType.GRAFFITI),
    VOICE_MESSAGE("голосовое", "Отправка голосового сообщения", ATTACHMENTS, VkMessageAttachmentType.AUDIO_MESSAGE),
    LONG_VOICE_MESSAGE("длинноегс", "Длинное голосовое сообщение", ATTACHMENTS, INTEGER, 3, 1_500, VkMessageAttachmentType.AUDIO_MESSAGE),
    SHORT_VOICE_MESSAGE("короткоегс", "Короткое голосовое сообщение", ATTACHMENTS, INTEGER, 2, 1_000, VkMessageAttachmentType.AUDIO_MESSAGE),
    STORY("история","История в сообщении", ATTACHMENTS, VkMessageAttachmentType.STORY),
    VIDEO("видео","Видео в сообщении", ATTACHMENTS, INTEGER, 1, 10, VkMessageAttachmentType.VIDEO),
    VIDEO_MESSAGE("видеосообщение", "Отправка видеосообщения", ATTACHMENTS, VkMessageAttachmentType.VIDEO),
    VK_CLIP("клип", "Отправка VK клипа", ATTACHMENTS, VkMessageAttachmentType.VIDEO),



    WORD_FILTER("фильтр","Фильтр слов", TEXT, STRING,1,150),
    STRICT_WORD_FILTER("строгийфильтр","Строгий фильтр слов", TEXT, STRING,1,150),
    MINIMUM_SYMBOLS("минсимволов","Минимальное количество символов", TEXT, INTEGER, 1, 300),
    MAXIMUM_SYMBOLS("макссимволов","Максимальное количество символов", TEXT, INTEGER, 5, 600),
    EMOJI_QUANTITY("эмоджи","Количество эмоджи в сообщении",TEXT, INTEGER,1,3000),
    ROW_QUANTITY("строки","Количество строк", TEXT, INTEGER, 2, 300),
    ALL_MENTION("пушвсех","Упоминание всех участников", TEXT),
    ONLINE_MENTION("пушонлайн", "Упоминание всех онлайн участников", TEXT),
    ANY_LINK("ссылка","Любая ссылка", TEXT),
    ZALGO("зальго","Зальго-текст в сообщении", TEXT),
    CHAT_INVITE_LINK("чатссылка","Ссылка на чат", TEXT),
    CAPS("капс", "сообщение КАПСом", TEXT),
    REGEX_FILTER("регулярка","Регулярное выражение", TEXT, STRING,1,35),
    SELF_DESTRUCTING_MESSAGE("исчезающее", "Исчезающее сообщение", TEXT),
    ANY_PUSH_QUANTITY("пуши","Количество пушей в сообщении", TEXT, INTEGER, 1, 300),
    SAME_MESSAGES("одинаковые", "Одинаковые сообщения подряд", TEXT, INTEGER, 2, 100);


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

    private final Set<VkActionType> vkActionTypeSet;

    private final VkMessageAttachmentType vkAttachmentType;

    private static final Map<String, MyEventType> cyrillicTypeMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z -> z.cyrillicType.toLowerCase(),
                            Function.identity()
                    ));

    public Optional<Set<VkActionType>> getVkActionTypeSet(){
        return Optional.ofNullable(vkActionTypeSet);
    }

    public Optional<VkMessageAttachmentType> getVkAttachmentType() {
        return Optional.ofNullable(vkAttachmentType);
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, Set<VkActionType> vkActionTypeSet) {
        this(cyrillicType, description, chatEventType, vkActionTypeSet,EventArgumentType.NONE, -1, -1);
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, Set<VkActionType> vkActionTypeSet, EventArgumentType argumentType, int argMin, int argMax){
        this.cyrillicType = cyrillicType;
        this.description = description;
        this.chatEventType = chatEventType;
        this.argumentType = argumentType;
        this.argMin = argMin;
        this.argMax = argMax;
        this.vkActionTypeSet = vkActionTypeSet==null?null:Collections.unmodifiableSet(vkActionTypeSet);
        vkAttachmentType = null;
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType) {
        this(cyrillicType, description, chatEventType, (Set<VkActionType>) null);
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, VkMessageAttachmentType vkAttachmentType) {
        this(cyrillicType, description, chatEventType, NONE, -1, -1, vkAttachmentType);

    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, EventArgumentType argumentType, int argMin, int argMax, VkMessageAttachmentType vkAttachmentType) {
        this.cyrillicType = cyrillicType;
        this.description = description;
        this.argumentType = argumentType;
        this.chatEventType = chatEventType;
        this.argMin = argMin;
        this.argMax = argMax;
        this.vkActionTypeSet = null;
        this.vkAttachmentType = vkAttachmentType;
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, EventArgumentType argumentType, int argMin, int argMax) {
        this(cyrillicType, description, chatEventType, argumentType, argMin, argMax, null);
    }


    public static Optional<MyEventType> findByCyrillicType(@NonNull String type){
        return Optional.ofNullable(cyrillicTypeMAP.get(type.trim().toLowerCase()));
    }




}
