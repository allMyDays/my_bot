package com.example.my_bot.exception;

public class UserCommandNotFoundException extends RuntimeException{

    public UserCommandNotFoundException() {
        super("Указанный вами аргумент не является действующей командой. Проверьте правильность написанного, а также просмотрите список команд.");
    }
}
