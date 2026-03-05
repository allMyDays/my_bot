package com.example.my_bot.command;


import com.example.my_bot.enumeration.DefaultRole;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;

public interface ChatCommand {

    String getCommand();


    void execute(String message, long chatId, long fromId, String[] args) throws ClientException, ApiException;







}
