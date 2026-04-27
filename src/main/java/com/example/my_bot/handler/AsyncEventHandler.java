package com.example.my_bot.handler;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.mapper.CommandMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatActionService;
import com.example.my_bot.service.event.EventExecutionService;
import com.example.my_bot.vk.VkMessage;
import com.example.my_bot.vk.VkMessageNew;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.callback.MessageNew;
import com.vk.api.sdk.objects.messages.Message;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.UNKNOWN_ERROR_MESSAGE;
import static com.example.my_bot.utils.ChatUtils.extractConversationId;
import static com.example.my_bot.utils.ChatUtils.isPersonalChat;

@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncEventHandler {

    private final CommandDispatcher commandDispatcher;

    private final VkChatClient vkChatClient;

    private final CommandMapper commandMapper;

    private final GlobalUserService userService;

    private final ChatActionService chatActionService;

    private final EventExecutionService eventExecutionService;



    @Async
    public void handleMessageNew(@NonNull VkMessageNew messageNew) {
        VkMessage message = messageNew.getMessageObject().getMessage();
        long peerId = message.getPeerId();
        long fromId = message.getFromId();
        long chatId;

        if(isPersonalChat(peerId)){
            Optional<Long> boundChat = userService.getOrCreateUser(fromId).getOptionalBoundChat();
            if(boundChat.isEmpty()) return;
            else chatId=boundChat.get();

        }else{
            chatId = extractConversationId(peerId);
        }

        try {
            if(chatId==19) return;
            commandDispatcher.dispatch(commandMapper.toCommandMessageDto(chatId, message));

            if(!isPersonalChat(peerId)){
                chatActionService.handleChatAction(chatId,fromId,message.getAction());
                eventExecutionService.executeRequiredChatEvents(message);
            } chatActionService.checkLastChatSynchronizationAndExecute(chatId);

        }catch (Exception e) {
            log.error("Произошла ошибка: ",e);
            try {
                vkChatClient.sendText(UNKNOWN_ERROR_MESSAGE, peerId,true);
            } catch (ClientException | ApiException e2) {
                log.error("Ошибка при попытке отправить сообщение об ошибке в диалог c peerId {}: ",peerId,e2);

            }
        }
    }
}
