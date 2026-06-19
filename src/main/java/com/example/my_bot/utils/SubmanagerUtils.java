package com.example.my_bot.utils;

import lombok.NonNull;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;

public class SubmanagerUtils {



    public static String generateNewBindingCode(long submanagerId){
        return "[club%d|%s]".formatted(Math.abs(submanagerId), UUID.randomUUID().toString());
    }

    public static boolean stringMatchesABindingCode(@NonNull String str){
        return str.matches("\\[club\\d{1,10}\\|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\]");
    }








}
