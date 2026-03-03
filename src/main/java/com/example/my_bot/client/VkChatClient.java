package com.example.my_bot.client;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VkChatClient{
    private final VkApiClient vkApiClient;
    private final GroupActor groupActor;




    public void sendText(long peerId, String text) throws ClientException, ApiException {
        vkApiClient.messages()
                .sendDeprecated(groupActor)
                .peerId(peerId)
                .message(text)
                .randomId((int) (System.currentTimeMillis() & 0xFFFFFFFFL))
                .execute();

    }

    public void sendWelcomeMessage(long peerId) throws ClientException, ApiException {
      sendText(peerId, "Меня добавили! Отлично! Для моей полноценной работы нужно нажать на название чата " +
              "и кликнуть по кнопке «Назначить администратором» напротив меня в списке участников. " );

    }

    public void sendUnknownErrorException(long peerId) throws ClientException, ApiException {
        sendText(peerId, "При обработке запроса произошла ошибка. Пожалуйста, сообщите разработчику." );

    }


}
