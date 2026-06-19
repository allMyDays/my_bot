package com.example.my_bot.handler;

import com.example.my_bot.cache.value.callback.GroupIdAndChatId;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.event.DataForEventExecution;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.dto.user.GlobalUserDetailsDto;
import com.example.my_bot.enumeration.event.ReactionType;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.submanager.SubmanagerBindingService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.service.chat.ChatActionService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventExecutionService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.SubmanagerUtils;
import com.example.my_bot.vk.*;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Forward;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.UNKNOWN_ERROR_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.WELCOME_MESSAGE;
import static com.example.my_bot.utils.ChatUtils.*;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.vk.enumeration.ChatErrorCode.*;
import static java.util.Objects.requireNonNull;

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
    private final SubmanagerService submanagerService;
    private final SubmanagerBindingService submanagerBindingService;
    private final CaffeineCacheManager cacheManager;

    private final GroupActor theMainBotGroupActor;
    private final long theMainBotId;
    private final MemberService memberService;


    public AsyncEventHandler(CommandDispatcher commandDispatcher,
                             VkChatClient vkChatClient,
                             MessageMapper messageMapper,
                             GlobalUserService userService,
                             ChatActionService chatActionService,
                             EventExecutionService eventExecutionService,
                             ChatService chatService,
                             MessageLogService messageLogService,
                             SubmanagerService submanagerService, SubmanagerBindingService submanagerBindingService, CaffeineCacheManager cacheManager,
                             @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor,
                             @Value("${vk.main-bot.id}") long theMainBotId, MemberService memberService) {
        this.commandDispatcher = commandDispatcher;
        this.vkChatClient = vkChatClient;
        this.messageMapper = messageMapper;
        this.userService = userService;
        this.chatActionService = chatActionService;
        this.eventExecutionService = eventExecutionService;
        this.chatService = chatService;
        this.messageLogService = messageLogService;
        this.submanagerService = submanagerService;
        this.submanagerBindingService = submanagerBindingService;
        this.cacheManager = cacheManager;
        this.theMainBotGroupActor = theMainBotGroupActor;
        this.theMainBotId = theMainBotId;
        this.memberService = memberService;
    }

    @Async
    public void handleNewMessageEvent(@NonNull VkMessageNew messageNew, boolean isCallback){
        VkMessage message = messageNew.getMessageObject().getMessage();
        long groupId = messageNew.getGroupId();
        long peerId = message.getPeerId();
        int conversationMessageId = message.getConversationMessageId();
        long fromId = message.getFromId();
        VkAction action = message.getAction();
        String text = message.getText();

        CommandRoutingData routingData =
                validateVkEventAndBuildRoutingData(groupId, peerId, fromId, messageNew.getSecretKey(), message.getAction(),text, isCallback)
                        .orElse(null);
        if(routingData==null) return;

        ChatDetailsDto chatDetails = routingData.getDataBaseChatId()==null
                ? null
                : chatService.getCachedChatDetails(routingData.getDataBaseChatId(), true);

        try{
            commandDispatcher.dispatch(messageMapper.toCommandMessageDto(routingData, message, chatDetails!=null&&chatDetails.isMessageReplying()));

            if(!isPersonalChat(peerId)){
                chatActionService.handleChatAction(routingData,fromId,action);
                messageLogService.saveNewMessageLog(routingData.getDataBaseChatId(), fromId, conversationMessageId, action, message.getText(),false);
                handleMessageForLogChat(routingData, chatDetails, message);
                eventExecutionService.executeRequiredChatEvents(
                        new DataForEventExecution(routingData.getDataBaseChatId(), fromId, conversationMessageId, action, message.getAttachments(), message.getText(), message.getFwdMessages(),message.getReplyMessage(),null,message.getExpireTTL()!=null, routingData)
                );
            }if(chatDetails!=null){
                    chatActionService.checkLastChatSynchronizationAndExecute(routingData);
            }

        }catch (Exception e) {
            log.error("Произошла ошибка: ",e);
            try {
                vkChatClient.sendText(messageMapper.toSendMessageDto(
                        UNKNOWN_ERROR_MESSAGE,
                        peerId,chatDetails==null?null:chatDetails.getChatId(),
                        routingData.getResponderBot()
                        )
                );
            } catch (ClientException | ApiException e2) {
                log.error("Ошибка при попытке отправить сообщение об ошибке в диалог c peerId {}: ",peerId,e2);
            }
        }
    }

    @Async
    public void handleNewReactionEvent(@NonNull VkMessageReactionEvent reactionEvent, boolean isCallback){
        VkReactionObject object = reactionEvent.getObject();
        if(object==null||object.getReactionId()<=0) return;

        long peerId = object.getPeerId();
        if(isPersonalChat(peerId)) return;  // на реакции нужно реагировать только в многопользовательских чатах

        long groupId = reactionEvent.getGroupId();
        int conversationMessageId = object.getConversationMessageId();
        long fromId = object.getReactedId();

        CommandRoutingData routingData =
                    validateVkEventAndBuildRoutingData(groupId, peerId, fromId, reactionEvent.getSecretKey(), null,null,isCallback)
                            .orElse(null);
        if(routingData==null) return;

        Optional<ReactionType> foundReaction = ReactionType.findByReactionId(object.getReactionId());
        if(foundReaction.isEmpty()){
            log.info("vk sent reaction event with unknown reaction ID {}", object.getReactionId());
                return;
            }

        eventExecutionService.executeRequiredChatEvents(
                new DataForEventExecution(routingData.getDataBaseChatId(), fromId, conversationMessageId, null, null, null, null, null, foundReaction.get(), false, routingData)
        );

    }

    private Optional<CommandRoutingData> validateVkEventAndBuildRoutingData(long groupId, long peerId, long fromId, @Nullable String secretKey, @Nullable VkAction action, @Nullable String messageText, boolean isCallback){

        CommandRoutingData routingData = new CommandRoutingData();
        routingData.setOriginalEventPeerId(peerId);

        if(isCallback){
            // пришло событие от субменеджера
            if(Math.abs(groupId)==theMainBotId){  // callback только для субменеджеров
                log.warn("the main bot id came in callback event. peerId {}, secret {}", peerId, secretKey);
                return Optional.empty();
            }
            if(isPersonalChat(peerId)) return Optional.empty(); // субменеджеры работают только в многопользовательских чатах, не в личных сообщениях

            SubmanagerDto subInfo = submanagerService.getSubmanagerOrThrowIfAbsents(groupId);
            if(secretKey==null||!subInfo.getSecretKey().equals(secretKey.trim())){
                log.warn("came callback event from submanager {}, but secret keys don't match. peerId {}",peerId, groupId);
                return Optional.empty();
            }
            routingData.setReceivedEventBot(subInfo.getGroupActor());
            long submanagerChatId = extractConversationId(peerId);

            if(submanagerBindingService.tryHandleSubmanagerBinding(groupId, fromId, submanagerChatId, subInfo, messageText)) return Optional.empty();

            ChatDetailsDto chatDetails= chatService.getMainChatDataBySubmanagerChatId(groupId, submanagerChatId).orElse(null);
            if(chatDetails==null){
                log.info("came callback event from submanager {}, but couldn't find main chat data by submanager chat id {}", groupId, submanagerChatId);
                return Optional.empty();
            }
            Long boundSubmanager = chatDetails.getBoundSubmanagerId();
            if(!Objects.equals(boundSubmanager, groupId)){
                log.info("came callback event from submanager {}, but required chat {} has bound submanager {}",groupId, chatDetails.getChatId(), boundSubmanager);
                return Optional.empty();
            }
            routingData.setDataBaseChatId(chatDetails.getChatId());  // над каким чатом бот будет работать
            routingData.setVkApiChatId(submanagerChatId);  // тот же самый чат, но со стороны субменеджера
            routingData.setResponsePeerId(peerId);  // куда бот будет отвечать
            routingData.setExecutorBot(subInfo.getGroupActor()); // кто выполнит действие
            routingData.setResponderBot(subInfo.getGroupActor());  // кто ответит
        }
        else{
            // пришло событие от основной группы чат-менеджера
            GlobalUserDetailsDto caller = userService.getOrCreateUser(fromId);
            if(groupId!=theMainBotId){  // longpoll только для основного бота
                log.warn("foreign group {} came in longpoll event. peerId {}", groupId, peerId);
                return Optional.empty();
            } routingData.setReceivedEventBot(theMainBotGroupActor);

            if(isPersonalChat(peerId)){
                // личные сообщения чат-менеджера
                routingData.setResponsePeerId(fromId);  // чат-менеджер отправит ответ в личные сообщения того же юзера
                routingData.setResponderBot(theMainBotGroupActor);

                Long boundChat = caller.getBoundChat();  // чат, который привязан к личным сообщениям пользователя
                if(boundChat!=null){
                    ChatDetailsDto chatDetails = chatService.getCachedChatDetails(boundChat, true);
                    routingData.setDataBaseChatId(boundChat);

                    Long boundSubmanager = chatDetails.getBoundSubmanagerId();
                    if(boundSubmanager!=null){
                        // у чата, который привязан к лс пользователя, есть субменеджер
                        SubmanagerDto subInfo = submanagerService.getSubmanagerOrThrowIfAbsents(boundSubmanager);
                        long submanagerChatId = chatService.getSubmanagerChatIdByMainChatId(boundSubmanager, boundChat);

                        routingData.setExecutorBot(subInfo.getGroupActor());
                        routingData.setVkApiChatId(submanagerChatId);
                    }else{
                        // у чата, который привязан к лс пользователя, нет субменеджера
                        routingData.setExecutorBot(theMainBotGroupActor);
                        routingData.setVkApiChatId(boundChat);
                    }

                }else{ // к личным сообщениям пользователя никакой чат не привязан
                    routingData.setExecutorBot(theMainBotGroupActor);
                }
            }
            else{  // многопользовательская беседа с основной группой чат-менеджера
                long chatId = ChatUtils.extractConversationId(peerId);
                ChatDetailsDto chatDetails = chatService.getCachedChatDetails(chatId, true);
                if(chatDetails.getBoundSubmanagerId()!=null){
                    // основной бот находится в чате с привязанным субменеджером. отвечать основной бот не должен
                    return Optional.empty();
                }
                //  основной бот находится в чате без привязанного субменеджера, должен отвечать
                if(tryHandleTheMainBotChatAdding(action, chatId)){
                    return Optional.empty();
                }
                routingData.setResponsePeerId(peerId);
                routingData.setDataBaseChatId(chatId);
                routingData.setVkApiChatId(chatId);
                routingData.setExecutorBot(theMainBotGroupActor);
                routingData.setResponderBot(theMainBotGroupActor);
            }
        }
        if(routingData.getDataBaseChatId()!=null&&memberService.isDmResponsesEnabled(routingData.getDataBaseChatId(), fromId)){
            routingData.setResponderBot(theMainBotGroupActor);
            routingData.setResponsePeerId(fromId);
        }
        return Optional.of(routingData);
    }

    /**
     * @return true, если данный бот был добавлен в чат
     */
    private boolean tryHandleTheMainBotChatAdding(@Nullable VkAction action, long dataBaseChatId){

        if(action==null) return false;
        VkActionType type = action.getType();
        if(type!=VkActionType.CHAT_INVITE_USER) return false;
        if(!Objects.equals(action.getMemberId(),-theMainBotId)) return false;

        try{
            vkChatClient.sendText(messageMapper.toSendMessageDto(WELCOME_MESSAGE, convertToPeerId(dataBaseChatId),dataBaseChatId, theMainBotGroupActor));
        }catch (ClientException|ApiException e){
            log.warn("chat {} error: couldn't send welcome message after the bot's just been added", dataBaseChatId);
        }
        return true;

    }

    private void handleMessageForLogChat(@NonNull CommandRoutingData commandRoutingData, @Nullable ChatDetailsDto currentChat, @NonNull VkMessage message) throws ClientException{
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

            try {
                vkChatClient.sendText(sendMessage);

            } catch(ApiException e){
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
                } catch (ApiException ex) {
                    log.warn("chat {} error: could not send error message after failing to forward mess in bound logchat {}",currentChatDBId,boundLogChatId, e);
                }
            }
        }
    }
}
