package com.example.my_bot.utils;

public class VkChatUtils {


    public static long extractConversationId(long peerId){
        if (peerId >= 2000000000) {
            return peerId - 2000000000L;
        } else throw new RuntimeException("Cannot extract ChatId because peerId belongs to personal conversation");

    }

    public static boolean isPersonalChat(long peerId){
        return peerId<2000000000;
    }











}
