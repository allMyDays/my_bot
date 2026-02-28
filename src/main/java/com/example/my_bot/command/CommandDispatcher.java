package com.example.my_bot.command;

import api.longpoll.bots.exceptions.VkApiException;
import api.longpoll.bots.model.objects.basic.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CommandDispatcher {


    private final Map<String, BotCommand> commands = new HashMap<>();

    @Autowired
    public CommandDispatcher(List<BotCommand> commandList) {
        for (BotCommand cmd : commandList) {
            commands.put(cmd.getCommand(), cmd);
        }
    }



    public void dispatch(Message message) throws VkApiException{
             if(!message.hasText()) return;
             String text = message.getText().trim();
             if(!text.startsWith("!")) return;

             String[] parts = text.split("\\s+");
             String prefix = "!";
             String commandName = parts[0].substring(prefix.length()).toLowerCase();

             String[] args = Arrays.copyOfRange(parts, 1, parts.length);


        BotCommand cmd = commands.get(commandName);
        if (cmd != null) {
            cmd.execute(message, args);
        } else {
           // Неизвестная команда

        }


    }





}
