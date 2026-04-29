package com.example.my_bot.handler;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.mapper.CommandMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatActionService;
import com.example.my_bot.service.event.EventExecutionService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.vk.VkAction;
import com.example.my_bot.vk.VkMessage;
import com.example.my_bot.vk.VkMessageNew;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.UNKNOWN_ERROR_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.WELCOME_MESSAGE;
import static com.example.my_bot.utils.ChatUtils.extractConversationId;
import static com.example.my_bot.utils.ChatUtils.isPersonalChat;

@Component
@Slf4j
public class AsyncEventHandler {

    private final CommandDispatcher commandDispatcher;

    private final VkChatClient vkChatClient;

    private final CommandMapper commandMapper;

    private final GlobalUserService userService;

    private final ChatActionService chatActionService;

    private final EventExecutionService eventExecutionService;

    private final long groupId;

    public AsyncEventHandler(CommandDispatcher commandDispatcher,
                             VkChatClient vkChatClient,
                             CommandMapper commandMapper,
                             GlobalUserService userService,
                             ChatActionService chatActionService,
                             EventExecutionService eventExecutionService,
                             @Value("${vk.group.id}") long groupId) {
        this.commandDispatcher = commandDispatcher;
        this.vkChatClient = vkChatClient;
        this.commandMapper = commandMapper;
        this.userService = userService;
        this.chatActionService = chatActionService;
        this.eventExecutionService = eventExecutionService;
        this.groupId = groupId;
    }

    @Async
    public void handleMessageNew(@NonNull VkMessageNew messageNew){
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
            if (hasTheBotJustBeenAdded(chatId, message.getAction())){
                try {
                    vkChatClient.sendText(WELCOME_MESSAGE, ChatUtils.convertToPeerId(chatId), true);
                } catch (ClientException |ApiException e) {
                    log.warn("chat {} error: couldn't send welcome message after the bot's just been added", chatId);
                } return;
            }
        }

        try {
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

    /**
     * @return true, если данный бот был добавлен в чат
     */
    private boolean hasTheBotJustBeenAdded(long chatId, @Nullable VkAction action) {
        if(action==null) return false;
        VkActionType type = action.getType();
        if(type!=VkActionType.CHAT_INVITE_USER||action.getMemberId()==null) return false;
        return action.getMemberId()==-groupId;
    }





}
