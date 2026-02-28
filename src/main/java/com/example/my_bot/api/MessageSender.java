package com.example.my_bot.api;

import api.longpoll.bots.exceptions.VkApiException;

public interface MessageSender {
    void sendText(int peerId, String text) throws VkApiException;

}
