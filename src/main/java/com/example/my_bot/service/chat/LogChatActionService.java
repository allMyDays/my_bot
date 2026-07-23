package com.example.my_bot.service.chat;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.vk.mapping.message.VkMessage;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Forward;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static com.example.my_bot.vk.enumeration.ChatErrorCode.*;
import static com.example.my_bot.vk.enumeration.ChatErrorCode.CHAT_FORWARD_DISABLED;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogChatActionService {

    private final ChatService chatService;
    private final VkChatClient vkChatClient;
    private final MessageMapper messageMapper;


    public void forwardMessageToLogChat(@NonNull CommandRoutingData commandRoutingData, @Nullable ChatDetailsDto currentChat, @NonNull VkMessage message) throws ClientException{

        long currentChatDBId = commandRoutingData.getDataBaseChatId();
        long currentChatApiId = commandRoutingData.getVkApiChatId();
        if(currentChat==null) return;

        Long boundLogChatId = currentChat.getBoundLogChat();

        if(boundLogChatId!=null&&message.getAction()==null){
            ChatDetailsDto logChat= chatService.getCachedChatDetails(boundLogChatId, false);

            if(!Objects.equals(currentChat.getBoundSubmanagerId(), logChat.getBoundSubmanagerId())){  // теперь сообщества в двух чатах разные, пересыл невозможен
                chatService.setBoundLogChatAsNull(currentChatDBId);
                return;
            }
            long logChatApiId = boundLogChatId;

            if(logChat.getBoundSubmanagerId()!=null){
                logChatApiId = chatService.getSubmanagerChatIdByMainChatId(logChat.getBoundSubmanagerId(), boundLogChatId);
            }
            SendMessageDto sendMessage = messageMapper.toSendMessageDto("",convertToPeerId(logChatApiId), boundLogChatId, commandRoutingData.getExecutorBot());
            Forward forward = new Forward();
            forward.setConversationMessageIds(List.of(message.getConversationMessageId()));
            forward.setPeerId(convertToPeerId(currentChatApiId));
            sendMessage.setForward(forward);
            sendMessage.setLogChatForward(true);

            try{
                vkChatClient.sendText(sendMessage);
            }
            catch(ApiException e){
                int errorCode = e.getCode();
                if(CURRENT_MESSAGE_CANNOT_BE_FORWARD.getCodes().contains(errorCode)){
                    return;  // данное сообщение нельзя переслать по техническим ограничениям вк
                }
                else if(NO_CHAT_ACCESS.getCodes().contains(errorCode)||YOU_ARE_RESTRICTED_TO_WRITE.getCodes().contains(errorCode)){
                    chatService.setBoundLogChatAsNull(currentChatDBId);
                    sendMessage.setText("\uD83E\uDD14Похоже, что меня исключили из привязанного логчата или запретили мне там писать. Логчат был отвязан от текущей беседы.");
                }
                else if(CHAT_FORWARD_DISABLED.getCodes().contains(errorCode)) {
                    chatService.setBoundLogChatAsNull(currentChatDBId);
                    sendMessage.setText("\uD83E\uDD14Похоже, что в данном чате включен запрет на пересыл сообщений. В таком случае я не могу пересылать сообщения отсюда в логчат. Логчат был отвязан от текущей беседы.");
                }
                else{
                    sendMessage.setText("Произошла ошибка при попытке переслать сообщение в привязанный логчат. "+e.getMessage());
                }
                sendMessage.setResponsePeerId(convertToPeerId(currentChatApiId));
                sendMessage.setForward(null);
                sendMessage.setLogChatForward(false);
                try {
                    vkChatClient.sendText(sendMessage);
                }
                catch (ApiException ex) {
                    log.warn("chat {} error: could not send error message after failing to forward mess in bound logchat {}",currentChatDBId,boundLogChatId, e);
                }
            }
        }
    }

}







