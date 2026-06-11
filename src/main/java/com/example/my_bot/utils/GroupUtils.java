package com.example.my_bot.utils;

import lombok.NonNull;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public class GroupUtils{

    private static final Pattern GROUP_TOKEN_PATTERN =
            Pattern.compile("^vk1\\.[a-z]\\.[A-Za-z0-9_-]{50,}$");



    public static boolean isGroupToken(@NonNull String token){
        return GROUP_TOKEN_PATTERN.matcher(token.trim()).matches();
    }

}
