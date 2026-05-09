package com.example.my_bot.exception.event;

import lombok.NonNull;

public class TooManyExceptionalMembersException extends EventException{

    public TooManyExceptionalMembersException() {

        super("Превышен лимит добавления пользователей в одно событие. Удалите существующего пользователя, чтобы добавить нового.");
    }
    public TooManyExceptionalMembersException(@NonNull String message) {

        super(message);
    }
}
