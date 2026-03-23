package com.example.my_bot.utils;

import lombok.NonNull;

public class ChatUtils {

    public static final long PEER_ID_CHAT_OFFSET = 2_000_000_000L;


    public static long extractConversationId(long peerId){
        if (peerId >= PEER_ID_CHAT_OFFSET) {
            return peerId - PEER_ID_CHAT_OFFSET;
        } else throw new IllegalArgumentException("Cannot extract ChatId because peerId belongs to personal conversation");

    }

    public static boolean isPersonalChat(long peerId){
        return peerId<PEER_ID_CHAT_OFFSET;
    }

    public static long extractPeerId(long chatId){
        if(chatId<0||chatId>1_000_000_000){
            throw new IllegalArgumentException("ChatId is invalid, cannot extract PeerId");
        } return chatId+PEER_ID_CHAT_OFFSET;

     }

     public static String createMention(long memberId){

        if(memberId<0) {
            return "@club"+(memberId*-1);
        } return "@id"+memberId;
    }


    public static boolean isValidInteger(String str) {
        if (str == null || !(str=str.trim()).matches("-?\\d+")) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidLong(String str) {
        if (str == null || !(str=str.trim()).matches("-?\\d+")) {
            return false;
        }
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNumber(@NonNull String str) {
        return str.trim().matches("-?\\d+");
    }


}
