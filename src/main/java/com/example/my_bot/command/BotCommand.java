package com.example.my_bot.command;

import api.longpoll.bots.exceptions.VkApiException;
import api.longpoll.bots.model.objects.basic.Message;

public interface BotCommand {

    String getCommand();
    void execute(Message message, String[] args) throws VkApiException;





}
