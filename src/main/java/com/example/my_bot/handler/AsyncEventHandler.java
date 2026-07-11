package com.example.my_bot.handler;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.chat.ChatDetailsDto;
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
import com.example.my_bot.service.VkKeyboardActionService;
import com.example.my_bot.service.chat.LogChatActionService;
import com.example.my_bot.service.submanager.SubmanagerActionService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.service.chat.ChatActionService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventExecutionService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.example.my_bot.vk.mapping.action.VkAction;
import com.example.my_bot.vk.mapping.message.VkMessage;
import com.example.my_bot.vk.mapping.message.VkMessageNew;
import com.example.my_bot.vk.mapping.post.VkWallPostNew;
import com.example.my_bot.vk.mapping.reaction.VkMessageReactionEvent;
import com.example.my_bot.vk.mapping.reaction.VkReactionObject;
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
    private final SubmanagerActionService submanagerActionService;
    private final VkKeyboardActionService vkKeyboardActionService;
    private final LogChatActionService logChatActionService;

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
                             SubmanagerService submanagerService,
                             SubmanagerActionService submanagerActionService,
                             VkKeyboardActionService vkKeyboardActionService,
                             LogChatActionService logChatActionService,
                             @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor,
                             @Value("${vk.main-bot.id}") long theMainBotId,
                             MemberService memberService) {
        this.commandDispatcher = commandDispatcher;
        this.vkChatClient = vkChatClient;
        this.messageMapper = messageMapper;
        this.userService = userService;
        this.chatActionService = chatActionService;
        this.eventExecutionService = eventExecutionService;
        this.chatService = chatService;
        this.messageLogService = messageLogService;
        this.submanagerService = submanagerService;
        this.submanagerActionService = submanagerActionService;
        this.vkKeyboardActionService = vkKeyboardActionService;
        this.logChatActionService = logChatActionService;
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
                logChatActionService.forwardMessageToLogChat(routingData, chatDetails, message);
                eventExecutionService.executeRequiredChatEvents(
                        new DataForEventExecution(routingData.getDataBaseChatId(), fromId, conversationMessageId, action, message.getAttachments(), message.getText(), message.getFwdMessages(),message.getReplyMessage(),null,message.getExpireTTL()!=null, routingData, false)
                );
            }
            if(chatDetails!=null){
                    chatActionService.checkLastChatSynchronizationAndExecute(routingData);
            }
            if(message.getPayload()!=null){
                vkKeyboardActionService.handleClickedButton(text, message.getPayload(), routingData, fromId);
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
                new DataForEventExecution(routingData.getDataBaseChatId(), fromId, conversationMessageId, null, null, null, null, null, foundReaction.get(), false, routingData, false)
        );
    }

    @Async
    public void handleNewWallPostEvent(@NonNull VkWallPostNew postNew){

        SubmanagerDto subInfo = validateCallbackEvent(postNew.getGroupId(),postNew.getSecretKey()).orElse(null);
        if(subInfo==null) return;
        submanagerActionService.sendNewSubPostToRequiredChats(subInfo, postNew.getObject().getPostId(), postNew.getObject().getFromId());
    }

    private Optional<SubmanagerDto> validateCallbackEvent(long groupId, String secretKey){

        if(Math.abs(groupId)==theMainBotId){
            // callback только для субменеджеров
            log.warn("the main bot id {} came in callback event. secret key {}", groupId, secretKey);
            return Optional.empty();
        }
        SubmanagerDto subInfo = submanagerService.getSubmanagerOrThrowIfAbsents(groupId);
        if(secretKey==null||!subInfo.getSecretKey().equals(secretKey.trim())){
            log.warn("came callback event from submanager {}, but come secret key {} don't match with {}", groupId, secretKey, subInfo.getSecretKey());
            return Optional.empty();
        }
        return Optional.of(subInfo);
    }

    private Optional<CommandRoutingData> validateVkEventAndBuildRoutingData(long groupId, long peerId, long fromId, @Nullable String secretKey, @Nullable VkAction action, @Nullable String messageText, boolean isCallback){

        CommandRoutingData routingData = new CommandRoutingData();
        routingData.setOriginalEventPeerId(peerId);

        if(isCallback){
            // пришло событие от субменеджера
            SubmanagerDto subInfo = validateCallbackEvent(groupId, secretKey).orElse(null);

            if(subInfo==null||isPersonalChat(peerId))
                return Optional.empty(); // субменеджеры работают только в многопользовательских чатах, не в личных сообщениях

            routingData.setReceivedEventBot(subInfo.getGroupActor());
            long submanagerChatId = extractConversationId(peerId);

            if(submanagerActionService.tryHandleSubmanagerBinding(groupId, fromId, submanagerChatId, subInfo, messageText)) return Optional.empty();

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
            }
            routingData.setReceivedEventBot(theMainBotGroupActor);

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

}
