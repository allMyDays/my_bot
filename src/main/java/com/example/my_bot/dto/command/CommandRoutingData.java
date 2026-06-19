package com.example.my_bot.dto.command;

import com.vk.api.sdk.client.actors.GroupActor;
import lombok.*;

import java.util.Optional;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class CommandRoutingData {

    private Long dataBaseChatId;  // над каким чатом работаю

    private Long vkApiChatId;   // тот же самый чат, но значение нужно отправить в vk api методы

    private long responsePeerId;  // куда нужно отправить ответ

    private GroupActor executorBot;  // какой бот делает действие

    private GroupActor responderBot;  // какой бот пишет ответ

    private GroupActor receivedEventBot;  // какой бот принял событие

    private long originalEventPeerId;  // изначальный peerId из события нового сообщения

    public CommandRoutingData(@NonNull CommandRoutingData other){

        this.dataBaseChatId = other.getDataBaseChatId();
        this.vkApiChatId = other.getVkApiChatId();
        this.responsePeerId = other.getResponsePeerId();
        this.executorBot = other.getExecutorBot();
        this.responderBot = other.getResponderBot();
        this.originalEventPeerId = other.getOriginalEventPeerId();
    }

}
