package com.example.my_bot.command;


import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.DefaultRole;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;

public interface ChatCommand {


    void execute(CommandMessageDto commandMessage) throws ClientException, ApiException;

    CommandCooldown getCooldown();






}
