package com.example.my_bot.exception.command;

public class NoUserCommandsFoundException extends CommandException{

    public NoUserCommandsFoundException() {
        super("Похоже, что вы не ввели ни одной валидной команды в качестве аргумента. Проверьте команды на опечатки, а также перечитайте список команд.");
    }
}
