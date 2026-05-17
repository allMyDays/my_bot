package com.example.my_bot.utils;

import lombok.NonNull;

import java.security.SecureRandom;
import java.util.Arrays;

public class ChatUtils {

    public final static long PEER_ID_CHAT_OFFSET = 2_000_000_000L;

    public final static int MAX_MESSAGE_LENGTH = 4096;

    public final static char DEFAULT_CHAT_PREFIX = '!';

    private static final String CHAT_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CHAT_CODE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();


    public static String generateNewChatCode() {
        StringBuilder code = new StringBuilder(CHAT_CODE_LENGTH);

        for (int i = 0; i < CHAT_CODE_LENGTH; i++) {
            int index = RANDOM.nextInt(CHAT_CODE_CHARS.length());
            code.append(CHAT_CODE_CHARS.charAt(index));
        }

        return code.toString();
    }

    public static long extractConversationId(long peerId){
        if (peerId >= PEER_ID_CHAT_OFFSET) {
            return peerId - PEER_ID_CHAT_OFFSET;
        } else throw new IllegalArgumentException("Cannot extract ChatId because peerId belongs to personal conversation");

    }

    public static boolean isPersonalChat(long peerId){
        return peerId<PEER_ID_CHAT_OFFSET;
    }

    public static long convertToPeerId(long chatId){
        if(chatId<0||chatId>1_000_000_000){
            throw new IllegalArgumentException("ChatId is invalid, cannot extract PeerId");
        } return chatId+PEER_ID_CHAT_OFFSET;

     }

    public static boolean isGroupId(long memberId) {
        return memberId<0;
    }





}
