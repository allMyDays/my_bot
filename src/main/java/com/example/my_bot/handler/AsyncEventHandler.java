package com.example.my_bot.handler;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.enumeration.event.ReactionType;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.chat.ChatActionService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventExecutionService;
import com.example.my_bot.vk.*;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Forward;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.UNKNOWN_ERROR_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.WELCOME_MESSAGE;
import static com.example.my_bot.utils.ChatUtils.*;
import static com.example.my_bot.vk.enumeration.ChatErrorCode.*;

@Component
@Slf4j
public class AsyncEventHandler {

    private final CommandDispatcher commandDispatcher;

    private final VkChatClient vkChatClient;

    private final MessageMapper messageMapper;

    private final GlobalUserService userService;

    private final ChatActionService chatActionService;

    private final EventExecutionService eventExecutionService;

    private final ChatService chatService;

    private final MessageLogService messageLogService;

    private final long groupId;

    public AsyncEventHandler(CommandDispatcher commandDispatcher,
                             VkChatClient vkChatClient,
                             MessageMapper messageMapper,
                             GlobalUserService userService,
                             ChatActionService chatActionService,
                             EventExecutionService eventExecutionService,
                             ChatService chatService,
                             MessageLogService messageLogService,
                             @Value("${vk.group.id}") long groupId) {
        this.commandDispatcher = commandDispatcher;
        this.vkChatClient = vkChatClient;
        this.messageMapper = messageMapper;
        this.userService = userService;
        this.chatActionService = chatActionService;
        this.eventExecutionService = eventExecutionService;
        this.chatService = chatService;
        this.messageLogService = messageLogService;
        this.groupId = groupId;
    }

    @Async
    public void handleNewMessageEvent(@NonNull VkMessageNew messageNew){
        VkMessage message = messageNew.getMessageObject().getMessage();
        long peerId = message.getPeerId();
        int conversationMessageId = message.getConversationMessageId();
        long fromId = message.getFromId();
        VkAction action = message.getAction();

        long chatId;

        if(isPersonalChat(peerId)){
            Optional<Long> boundChat = userService.getOrCreateUser(fromId).getOptionalBoundChat();
            if(boundChat.isEmpty()) return;
            else chatId=boundChat.get();

        }else{
            chatId = extractConversationId(peerId);
            if (hasTheBotJustBeenAdded(action)){
                try {
                    vkChatClient.sendText(messageMapper.toSendMessageDto(WELCOME_MESSAGE, convertToPeerId(chatId)));
                } catch (ClientException |ApiException e) {
                    log.warn("chat {} error: couldn't send welcome message after the bot's just been added", chatId);
                } return;
            }
        }
        try{
            ChatDetailsDto currentChat = chatService.getCachedChatDetails(chatId, true);
            commandDispatcher.dispatch(messageMapper.toCommandMessageDto(chatId, message, currentChat.isMessageReplying()));

            if(!isPersonalChat(peerId)){
                chatActionService.handleChatAction(chatId,fromId,action);
                eventExecutionService.executeRequiredChatEvents(chatId,fromId,conversationMessageId,action,message.getAttachments(),message.getText(),message.getFwdMessages(),null,message.getExpireTTL()!=null);
                handleMessageForLogChat(currentChat, message);
                messageLogService.saveNewMessageLog(chatId, fromId, conversationMessageId);
            }
            chatActionService.checkLastChatSynchronizationAndExecute(chatId);

        }catch (Exception e) {
            log.error("Произошла ошибка: ",e);
            try {
                vkChatClient.sendText(messageMapper.toSendMessageDto(UNKNOWN_ERROR_MESSAGE, convertToPeerId(chatId)));
            } catch (ClientException | ApiException e2) {
                log.error("Ошибка при попытке отправить сообщение об ошибке в диалог c peerId {}: ",peerId,e2);

            }
        }
    }
    public void handleNewReactionEvent(@NonNull VkMessageReactionEvent event){
        VkReactionObject object = event.getObject();
        if(object==null) return;

        if(!isPersonalChat(object.getPeerId())){
            long chatId = extractConversationId(object.getPeerId());
            Optional<ReactionType> foundReaction = ReactionType.findByReactionId(object.getReactionId());
            if(foundReaction.isEmpty()){
                log.info("vk sent reaction event with unknown reaction ID {}", object.getReactionId());
                return;
            }
            eventExecutionService.executeRequiredChatEvents(chatId,object.getReactedId(),object.getConversationMessageId(),null,null,null,null,foundReaction.get(),false);
        }
    }


    /**
     * @return true, если данный бот был добавлен в чат
     */
    private boolean hasTheBotJustBeenAdded(@Nullable VkAction action) {
        if(action==null) return false;
        VkActionType type = action.getType();
        if(type!=VkActionType.CHAT_INVITE_USER||action.getMemberId()==null) return false;
        return action.getMemberId()==-groupId;
    }
    private void handleMessageForLogChat(ChatDetailsDto currentChat, VkMessage message) throws ClientException {

        long chatId = currentChat.getChatId();
        Long logChatId = currentChat.getBoundLogChat();

        if(logChatId!=null&&message.getAction()==null){
            SendMessageDto sendMessage = messageMapper.toSendMessageDto(
                    "Из чата "+currentChat.getChatCode(),
                    convertToPeerId(logChatId)
            );
            Forward forward = new Forward();
            forward.setConversationMessageIds(List.of(message.getConversationMessageId()));
            forward.setPeerId(convertToPeerId(chatId));
            sendMessage.setForward(forward);

            try {
                vkChatClient.sendText(sendMessage);
            }catch(ApiException e){
                int errorCode = e.getCode();
                if(NO_CHAT_ACCESS.getCodes().contains(errorCode)
                        || YOU_ARE_RESTRICTED_TO_WRITE.getCodes().contains(errorCode)
                ){      // кикнули или запретили писать в чат
                    chatService.setBoundLogChatAsNull(chatId);
                    sendMessage.setText("\uD83E\uDD14Похоже, что меня исключили из привязанного логчата или запретили мне там писать. Логчат был отвязан от текущей беседы.");
                }else if(CHAT_FORWARD_DISABLED.getCodes().contains(errorCode)){
                    chatService.setBoundLogChatAsNull(chatId);
                    sendMessage.setText("\uD83E\uDD14Похоже, что в данном чате включен запрет на пересыл сообщений. В таком случае я не могу пересылать сообщения отсюда в логчат. Логчат был отвязан от текущей беседы.");
                }else{
                    sendMessage.setText("Произошла ошибка при попытке переслать сообщение в привязанный логчат. "+e.getMessage());
                }
                sendMessage.setPeerId(convertToPeerId(chatId));
                sendMessage.setForward(null);
                try {
                    vkChatClient.sendText(sendMessage);
                } catch (ApiException ex) {
                    log.warn("chat {} error: could not send error message after failing to forward mess in bound logchat {}",chatId,logChatId, e);
                }
            }

        }
    }





}
