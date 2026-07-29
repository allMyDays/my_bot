package com.example.my_bot.constant;

public class MessageConstant {

    public static final String WELCOME_MESSAGE = "Меня добавили! Отлично! Для моей полноценной работы нужно нажать на название чата " +
            "и кликнуть по кнопке «Назначить администратором» напротив меня в списке участников. ";

    public static final String UNKNOWN_ERROR_MESSAGE = "При обработке запроса произошла ошибка, у которой нет более подробного описания.";

    public static final String NOT_ENOUGH_ARGUMENTS_MESSAGE = "Вы ввели недостаточно аргументов для обработки этой команды.";

    public static final String NOT_VALID_INTEGER_MESSAGE = "Указанный вами аргумент не является корректным числовым значением.";

    public static final String THIS_COMMAND_IS_ONLY_FOR_CHATS_WITH_SUBMANAGERS = "Данную команду можно использовать только в чатах с субменеджерами.";

    public static final String MEMBER_ARGUMENT_ABSENTS = "Необходимо указать участника, к которому вы хотите применить эту команду.";

    public static final String MEMBER_LINK_IS_NOT_CORRECT = "Не удалось получить участника по указанному вами строчному аргументу.";

    public static final String INVALID_TIME_PERIOD_MESSAGE = "Указанный вами аргумент не является корректным временным периодом. Правильный пример: 2 минуты / 3 часа / 1 день";

    public static final String NOT_VALID_TIME = "Введён некорректный аргумент времени. Пример: 07:30";

    public static final String NOT_VALID_DATE_TIME = "Введён некорректный аргумент времени с датой. Пример: 07:30 01.01.2027";

    public static final String NOT_VALID_DATE = "Введён некорректный аргумент даты. Пример: 01.01.2027";

    public static final String MEMBER_ROLE_HAS_BEEN_CHANGED = "✅Роль %s(%s) изменена: «%s» ➜ «%s».";

    public static final String THE_BOT_IS_RESTRICTED_TO_WRITE_ERROR = "Ошибка: мне запретили писать сообщения в этот чат.";

    public static final String THE_BOT_IS_NOT_CHAT_ADMIN_ERROR = "Ошибка: я не являюсь администратором этого чата.";

    public static final String THE_BOT_HAS_NO_CHAT_ACCESS_ERROR = "Ошибка: не удалось получить доступ к чату. Скорее всего у меня нет прав администратора. Выдайте мне права.";






}
