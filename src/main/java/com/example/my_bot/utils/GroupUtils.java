package com.example.my_bot.utils;

import lombok.NonNull;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public class GroupUtils{

    public static final int CB_CONFIRMATION_CODE_MAX_TTL_SEC = 1_800;

    private static final Pattern GROUP_TOKEN_PATTERN =
            Pattern.compile("^vk1\\.[a-z]\\.[A-Za-z0-9_-]{50,}$");

    private static final String CB_SERVER_SECRET_KEY_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_";


    public static boolean isGroupToken(@NonNull String token){
        return GROUP_TOKEN_PATTERN.matcher(token.trim()).matches();
    }
    public static String createPrivateMessagesLink(long groupId){
        return "vk.me/club"+Math.abs(groupId);
    }

    public static String generateCBServerSecretKey(){
        SecureRandom random = new SecureRandom();

        return random.ints(50, 0, CB_SERVER_SECRET_KEY_CHARS.length())
                .mapToObj(CB_SERVER_SECRET_KEY_CHARS::charAt)
                .collect(StringBuilder::new,
                        StringBuilder::append,
                        StringBuilder::append)
                .toString();
    }



}
