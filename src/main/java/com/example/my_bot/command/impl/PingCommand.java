package com.example.my_bot.command.impl;

import api.longpoll.bots.exceptions.VkApiException;
import api.longpoll.bots.model.objects.basic.Message;
import com.example.my_bot.api.MessageSender;
import com.example.my_bot.command.BotCommand;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PingCommand implements BotCommand {

    private MessageSender messageSender;

    @Autowired
    @Lazy
    public void setMessageSender(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public String getCommand() {
        return "пинг";
    }

    @Override
    public void execute(Message message, String[] args) throws VkApiException {

        messageSender.sendText(message.getPeerId(), "ПОНГ");




    }
}
