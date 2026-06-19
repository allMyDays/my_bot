package com.example.my_bot.exception.submanager;

public class CannotFindSubmanagerChatIdByMainChatIdException extends SubmanagerException {
    public CannotFindSubmanagerChatIdByMainChatIdException(long submanagerId, long mainChatId) {
        super("Не удалось найти id чата, который видит субменеджер со своей стороны. Id субменеджера: %d, main chat id: %d"
                .formatted(submanagerId, mainChatId)
        );
    }
}
