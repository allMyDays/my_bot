package com.example.my_bot.bot;

import api.longpoll.bots.LongPollBot;
import api.longpoll.bots.exceptions.VkApiException;
import api.longpoll.bots.model.events.messages.MessageNew;
import api.longpoll.bots.model.objects.basic.Message;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VkBot extends LongPollBot {

    private final String accessToken;

    @Autowired
    public VkBot(@Value("${vk.access-token}") String accessToken) {
        this.accessToken = accessToken;
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
            if (message.hasText()) {
                // Используем сервис для обработки текста
               // String reply = messageService.processMessage(message.getText());
                vk.messages.send()
                        .setPeerId(message.getPeerId())
                        .setMessage("hello")
                        .execute();
            }
        } catch (VkApiException e) {
            log.error("Ошибка отправки сообщения", e);
        }
    }
}
