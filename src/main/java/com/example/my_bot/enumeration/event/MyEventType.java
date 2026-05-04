package com.example.my_bot.enumeration.event;
import static com.example.my_bot.enumeration.event.ChatEventType.*;
import static com.example.my_bot.enumeration.event.EventArgumentType.*;
import static com.example.my_bot.vk.enumeration.VkActionType.*;

import com.example.my_bot.config.AdvancedEventConfig;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.example.my_bot.vk.enumeration.VkMessageAttachmentType;
import lombok.Getter;
import lombok.NonNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


public enum MyEventType {
    ANY_MESSAGE("сообщение","Отправка любого сообщения", TEXT_OR_ATTACHMENT, new AdvancedEventConfig(true, false, 10_000)),


    INVITE_ANOTHER("приглашение", "Приглашение в чат", ACTION, Set.of(CHAT_INVITE_USER), new AdvancedEventConfig(true, false, 500)),
    KICK_ANOTHER("исключение","Исключение участника", ACTION, Set.of(CHAT_KICK_USER), new AdvancedEventConfig(true, false, 500)),
    ENTRANCE_BY_LINK("вход","Вход по ссылке", ACTION, Set.of(CHAT_INVITE_USER_BY_LINK), new AdvancedEventConfig(false, false)),
    CHANGE_TITLE("название","Смена названия чата",  ACTION, Set.of(CHAT_TITLE_UPDATE), new AdvancedEventConfig(true, false, 100)),
    SELF_RETURN("возврат","Самостоятельное возвращение в чат", ACTION, Set.of(CHAT_INVITE_USER),  new AdvancedEventConfig(true, false, 100)),
    CHANGE_PIN_MESSAGE("закреп","Смена закреплённого сообщения", ACTION, Set.of(CHAT_PIN_MESSAGE, CHAT_UNPIN_MESSAGE),new AdvancedEventConfig(true, false, 100)),
    SELF_LEAVE("выход", "Выход из чата", ACTION, Set.of(CHAT_KICK_USER), new AdvancedEventConfig(true, false, 100)),
    INVITE_BANNED("забаненный", "Приглашение забаненного", ACTION, Set.of(CHAT_INVITE_USER), new AdvancedEventConfig(true, false, 200)),
    INVITE_GROUP("сообщество", "Приглашение сообщества в чат", ACTION, Set.of(CHAT_INVITE_USER),  new AdvancedEventConfig(true, false, 100)),
    SCREENSHOT("скриншот", "Создание скриншота чата", ACTION, Set.of(CHAT_SCREENSHOT), new AdvancedEventConfig(true, false, 100)),
    CHANGE_CHAT_PHOTO("фоточата","Смена фотографии чата", ACTION, Set.of(CHAT_PHOTO_UPDATE, CHAT_PHOTO_REMOVE), new AdvancedEventConfig(true, false, 100)),
    CHAT_STYLE_UPDATE("оформление", "Смена оформления чата", ACTION, Set.of(CONVERSATION_STYLE_UPDATE), new AdvancedEventConfig(true, false, 100)),
    WITH_SUBSCRIPTION("естьподписка", "Наличие подписки на сообщество", ACTION, Set.of(CHAT_INVITE_USER, CHAT_INVITE_USER_BY_LINK), INTEGER, 1, 9,  new AdvancedEventConfig(false, false)),
    WITHOUT_SUBSCRIPTION("нетподписки", "Отсутствие подписки на сообщество", ACTION, Set.of(CHAT_INVITE_USER, CHAT_INVITE_USER_BY_LINK),  INTEGER, 1, 9,  new AdvancedEventConfig(false, false)),


    FWD_QUANTITY("пересланные","Количество пересланных", FWD_MESSAGES, INTEGER, 1, 100,  new AdvancedEventConfig(true, false, 10_000)),


    ATTACHMENT_QUANTITY("вложения","Количество вложений", ATTACHMENTS, INTEGER, 1, 10,  new AdvancedEventConfig(true, false, 2_000)),
    ATTACH_PHOTO("фото","Количество фото", ATTACHMENTS, INTEGER, 1, 10, VkMessageAttachmentType.PHOTO, new AdvancedEventConfig(true, false, 2_000)),
    SONG("аудио","Количество аудиозаписей(песней)", ATTACHMENTS, INTEGER, 1, 10, VkMessageAttachmentType.AUDIO,  new AdvancedEventConfig(true, false, 2_000)),
    DOCUMENT("документ","Количество документов", ATTACHMENTS, INTEGER, 1, 10, VkMessageAttachmentType.DOC, new AdvancedEventConfig(true, false, 2_000)),
    MARKET("товар","Товар в сообщении", ATTACHMENTS, VkMessageAttachmentType.MARKET, new AdvancedEventConfig(true, false, 2_000)),
    STICKER("стикер","Отправка любого стикера", ATTACHMENTS, VkMessageAttachmentType.STICKER,  new AdvancedEventConfig(true, false, 2_000)),
    POST("пост","Пост в сообщении", ATTACHMENTS, VkMessageAttachmentType.WALL,new AdvancedEventConfig(true, false, 2_000)),
    POST_COMMENT("коммент", "Комментарий к посту", ATTACHMENTS, VkMessageAttachmentType.WALL_REPLY, new AdvancedEventConfig(true, false, 2_000)),
    POLL("опрос", "Опрос в сообщении", ATTACHMENTS, VkMessageAttachmentType.POLL,new AdvancedEventConfig(true, false, 2_000)),
    CALL("звонок", "Создание звонка в чате", ATTACHMENTS, VkMessageAttachmentType.CALL,  new AdvancedEventConfig(true, false,2_000)),
    GRAFFITI("граффити", "Граффити в сообщении", ATTACHMENTS, VkMessageAttachmentType.GRAFFITI, new AdvancedEventConfig(true, false, 2_000)),
    VOICE_MESSAGE("голосовое", "Отправка голосового сообщения", ATTACHMENTS, VkMessageAttachmentType.AUDIO_MESSAGE, new AdvancedEventConfig(true, false, 7_000)),
    LONG_VOICE_MESSAGE("длинноегс", "Длинное голосовое сообщение", ATTACHMENTS, INTEGER, 3, 1_500, VkMessageAttachmentType.AUDIO_MESSAGE,  new AdvancedEventConfig(true, true, 7_000)),
    SHORT_VOICE_MESSAGE("короткоегс", "Короткое голосовое сообщение", ATTACHMENTS, INTEGER, 2, 1_000, VkMessageAttachmentType.AUDIO_MESSAGE,  new AdvancedEventConfig(true, true, 7_000)),
    STORY("история","История в сообщении", ATTACHMENTS, VkMessageAttachmentType.STORY, new AdvancedEventConfig(true, false, 2_000)),
    VIDEO("видео","Количество видео", ATTACHMENTS, INTEGER, 1, 10, VkMessageAttachmentType.VIDEO,  new AdvancedEventConfig(true, false, 2_000)),
    VIDEO_MESSAGE("видеосообщение", "Отправка видеосообщения", ATTACHMENTS, VkMessageAttachmentType.VIDEO,  new AdvancedEventConfig(true, false, 2_000)),
    VK_CLIP("клип", "Отправка VK клипа", ATTACHMENTS, VkMessageAttachmentType.VIDEO,  new AdvancedEventConfig(true, false, 2_000)),



    WORD_FILTER("фильтр","Фильтр слов", TEXT, STRING,1,150,  new AdvancedEventConfig(true, true, 1_000)),
    STRICT_WORD_FILTER("строгийфильтр","Строгий фильтр слов", TEXT, STRING,1,150,new AdvancedEventConfig(true, true, 1_000)),
    MAXIMUM_SYMBOLS("макссимволов","Максимальное количество символов", TEXT, INTEGER, 5, 600, new AdvancedEventConfig(true, false, 100_000)),
    EMOJI_QUANTITY("эмоджи","Количество эмоджи",TEXT, INTEGER,1,3000,  new AdvancedEventConfig(true, false, 5_000)),
    ROW_QUANTITY("строки","Количество строк", TEXT, INTEGER, 2, 300,  new AdvancedEventConfig(true, true, 5_000)),
    ALL_MENTION("пушвсех","Упоминание всех участников", TEXT, new AdvancedEventConfig(true, false, 200)),
    ONLINE_MENTION("пушонлайн", "Упоминание всех онлайн участников", TEXT,  new AdvancedEventConfig(true, false, 200)),
    ANY_LINK("ссылка","Любая ссылка", TEXT,  new AdvancedEventConfig(true, false,500)),
    ZALGO("зальго","сообщение с Zalgo", TEXT,  new AdvancedEventConfig(true, false,100)),
    CHAT_INVITE_LINK("чатссылка","Ссылка на чат", TEXT,  new AdvancedEventConfig(true, false,50)),
    CAPS("капс", "сообщение КАПСом", TEXT, new AdvancedEventConfig(true, false,5_000)),
    REGEX_FILTER("регулярка","Регулярное выражение", TEXT, STRING,1,35,  new AdvancedEventConfig(true, true,1_000)),
    SELF_DESTRUCTING_MESSAGE("исчезающее", "Исчезающее сообщение", TEXT,  new AdvancedEventConfig(true, false,5_000)),
    ANY_PUSH_QUANTITY("пуши","Количество пушей", TEXT, INTEGER, 1, 300,  new AdvancedEventConfig(true, false,5_000)),
    SAME_MESSAGES("одинаковые", "Одинаковые сообщения подряд", TEXT, INTEGER, 2, 100,  new AdvancedEventConfig(false, false)),
    SHORT_MESSAGE("короткоесмс","Сообщение с минимальным количеством символов", TEXT, INTEGER, 1, 300, new AdvancedEventConfig(true, true,7_000));


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
    @Getter
    private final AdvancedEventConfig advancedEventConfig;

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

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, Set<VkActionType> vkActionTypeSet, AdvancedEventConfig advancedEventConfig) {
        this(cyrillicType, description, chatEventType, vkActionTypeSet,EventArgumentType.NONE, -1, -1, advancedEventConfig);
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, Set<VkActionType> vkActionTypeSet, EventArgumentType argumentType, int argMin, int argMax, AdvancedEventConfig advancedEventConfig){
        this.cyrillicType = cyrillicType;
        this.description = description;
        this.chatEventType = chatEventType;
        this.argumentType = argumentType;
        this.argMin = argMin;
        this.argMax = argMax;
        this.vkActionTypeSet = vkActionTypeSet==null?null:Collections.unmodifiableSet(vkActionTypeSet);
        vkAttachmentType = null;
        this.advancedEventConfig = advancedEventConfig;
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, AdvancedEventConfig advancedEventConfig) {
        this(cyrillicType, description, chatEventType, (Set<VkActionType>) null, advancedEventConfig);
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, VkMessageAttachmentType vkAttachmentType, AdvancedEventConfig advancedEventConfig) {
        this(cyrillicType, description, chatEventType, NONE, -1, -1, vkAttachmentType, advancedEventConfig);

    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, EventArgumentType argumentType, int argMin, int argMax, VkMessageAttachmentType vkAttachmentType, AdvancedEventConfig advancedEventConfig) {
        this.cyrillicType = cyrillicType;
        this.description = description;
        this.argumentType = argumentType;
        this.chatEventType = chatEventType;
        this.argMin = argMin;
        this.argMax = argMax;
        this.vkActionTypeSet = null;
        this.vkAttachmentType = vkAttachmentType;
        this.advancedEventConfig = advancedEventConfig;
    }

    MyEventType(String cyrillicType, String description, ChatEventType chatEventType, EventArgumentType argumentType, int argMin, int argMax, AdvancedEventConfig advancedEventConfig) {
        this(cyrillicType, description, chatEventType, argumentType, argMin, argMax, null, advancedEventConfig);
    }


    public static Optional<MyEventType> findByCyrillicType(@NonNull String type){
        return Optional.ofNullable(cyrillicTypeMAP.get(type.trim().toLowerCase()));
    }




}
