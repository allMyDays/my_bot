package com.example.my_bot.bot;

import api.longpoll.bots.LongPollBot;
import api.longpoll.bots.exceptions.VkApiException;
import api.longpoll.bots.model.events.messages.MessageNew;
import api.longpoll.bots.model.objects.basic.Message;
import com.example.my_bot.api.MessageSender;
import com.example.my_bot.command.CommandDispatcher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VkBot extends LongPollBot implements MessageSender {

    private final String accessToken;
    private final CommandDispatcher commandDispatcher;

    @Autowired
    public VkBot(@Value("${vk.access-token}") String accessToken,
                 CommandDispatcher commandDispatcher) {
        this.accessToken = accessToken;
        this.commandDispatcher = commandDispatcher;
    }

    @PostConstruct
    public void start() {
        new Thread(() -> {
            try {
                startPolling();
            } catch (VkApiException e) {
                log.error("Ошибка при запуске polling", e);
            }
        }).start();
    }


    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public void onMessageNew(MessageNew messageNew) {
        try {
            Message message = messageNew.getMessage();
            commandDispatcher.dispatch(message);

        } catch (Exception e) {
            log.error("Произошла ошибка.", e);
        }
    }

    @Override
    public void sendText(int peerId, String text) throws VkApiException {
        vk.messages.send()
                .setPeerId(peerId)
                .setMessage(text)
                .execute();

    }
}
