package com.example.my_bot.exception.member;

public abstract class MemberException extends RuntimeException {
    public MemberException(String message) {
        super(message);
    }
}