package com.example.my_bot.command;


import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;

public interface BotCommand {

    String getCommand();
    void execute(String message, long peerId, long fromId, String[] args) throws ClientException, ApiException;





}
