package com.example.my_bot.service.chat;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.chat.AdminChatDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.command.HandleAdminChatCommandStatus;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.enumeration.key.ButtonPayloadKey;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.VkKeyboardActionService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.utils.TextUtils;
import com.example.my_bot.vk.mapping.button.VkButtonConfig;
import com.github.benmanes.caffeine.cache.*;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.example.my_bot.enumeration.command.HandleAdminChatCommandStatus.*;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.*;
import static com.example.my_bot.enumeration.key.ButtonPayloadKey.*;
import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static com.example.my_bot.utils.KeyboardUtils.createButtonPayload;
import static com.example.my_bot.utils.TextUtils.createMention;


@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChatActionService {

    private final ChatService chatService;
    private final AdminChatService adminChatService;
    private final MessageMapper messageMapper;
    private final VkKeyboardActionService keyboardService;
    private final VkChatClient vkChatClient;
    private final CommandRegistry commandRegistry;
    private final CommandAccessService commandAccessService;
    private final MemberService memberService;
    private final GroupActor theMainBotGroupActor;
    private final SubmanagerService submanagerService;
    private final GlobalUserService globalUserService;

    private final static int MAX_CHAT_BUTTONS_PER_ONE_RAW = 3;

    private final Cache<Long, String> sentCommandCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final Cache<Long, StringBuilder> messagesToSendToAdminChat = Caffeine.newBuilder()
            .scheduler(Scheduler.systemScheduler())
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .removalListener((Long key, StringBuilder value, RemovalCause cause)->{
                if(cause==RemovalCause.EXPIRED){
                    sendMessageToAdminChat(key, value.toString());
                }
            })
            .build();


    public HandleAdminChatCommandStatus handleAdminChatCommand(@NonNull Command cmdAnnotation, @NonNull String fullCommandWithNoPrefix, @NonNull CommandRoutingData routingData, boolean isEventOrTimerMode) throws ClientException, ApiException {

        Long dataBaseChatId = routingData.getDataBaseChatId();
        AdminChatDto adminChat;

        if(dataBaseChatId==null||(adminChat=adminChatService.getAdminChatData(dataBaseChatId).orElse(null))==null)
            return NOT_ADMIN_CHAT;

        if(cmdAnnotation.adminChatCommandExecutionMode()==ONLY_IN_ADMIN_CHAT||isEventOrTimerMode) return MUST_BE_EXECUTED_IN_ADMIN_CHAT;

        routingData = new CommandRoutingData(routingData);
        routingData.setResponsePeerId(convertToPeerId(routingData.getVkApiChatId()));
        routingData.setResponderBot(routingData.getExecutorBot());

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("Выберите чат, в котором нужно применить эту команду.", routingData);

        List<VkButtonConfig> chatButtons = new ArrayList<>(
                adminChat.getBoundChats().stream()
                        .map(id -> chatService.getCachedChatDetails(id, false))
                        .map(chat ->
                                new VkButtonConfig(
                                        chat.getChatTitle(),
                                        KeyboardButtonColor.POSITIVE,
                                        createButtonPayload(ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT, chat.getChatId().toString())
                                )
                        )
                        .toList()
        );

        if(chatButtons.size()>1&&cmdAnnotation.adminChatCommandExecutionMode()==ALL_BOUND_CHATS_AT_ONCE){
            chatButtons.add(new VkButtonConfig(
                            "Все привязанные чаты",
                            KeyboardButtonColor.NEGATIVE,
                            createButtonPayload(ADMIN_CHAT_EXECUTE_COMMAND_IN_ALL_BOUND_CHATS, "")
                    )
            );
        }

        chatButtons.add(new VkButtonConfig(
                "Этот админ-чат",
                KeyboardButtonColor.PRIMARY,
                createButtonPayload(ADMIN_CHAT_EXECUTE_COMMAND_IN_THIS_ADMIN_CHAT, "")
                )
        );

        sendMessage.setKeyboard(keyboardService.createAutoLayoutKeyboard(chatButtons, MAX_CHAT_BUTTONS_PER_ONE_RAW));
        vkChatClient.sendText(sendMessage);

        sentCommandCache.put(dataBaseChatId, fullCommandWithNoPrefix);

        return VK_KEYBOARD_IS_SENT;
    }

    public void handleClickedAdminChatButton(@NonNull CommandRoutingData routingData, @NonNull ButtonPayloadKey key, @NonNull String value, long fromId) throws ClientException, ApiException {

        long adminChatDataBaseChatId = routingData.getDataBaseChatId();

        AdminChatDto adminChat = adminChatService.getAdminChatData(adminChatDataBaseChatId).orElse(null);
        if(adminChat==null){
            log.warn("admin chat button has been just clicked, but the chat {} is not an admin chat", adminChatDataBaseChatId);
            return;
        }

        String fullCommandWithNoPrefix = sentCommandCache.asMap().remove(adminChat.getChatId());
        if(fullCommandWithNoPrefix==null){
            log.info("admin chat button has been clicked, but couldn't find the command that's required to be executed");
            return;
        }
        String[] commandAndArgs = UserInputResolver.splitFullCommandIntoTwoElements(fullCommandWithNoPrefix);

        Map.Entry<ChatCommand, Command> commandData = commandRegistry.getCommandWithTheAnnotation(commandAndArgs[0])
                .orElseThrow(()->new UserCommandNotFoundException(commandAndArgs[0]));

        Set<Long> chatsToExecuteIn=null;

        switch (key){
            case ADMIN_CHAT_EXECUTE_COMMAND_IN_THIS_ADMIN_CHAT -> {
                chatsToExecuteIn = Set.of(adminChatDataBaseChatId);
            }
            case ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT -> {
                if(!TextUtils.isValidLong(value)){
                    log.warn("admin chat button 'in one chat' has been clicked, but come chat id is invalid: {}", value);
                    return;
                }
                long chatToExecuteIn = Long.parseLong(value);

                if(!adminChat.getBoundChats().contains(chatToExecuteIn)){
                    log.warn("admin chat button 'in one chat' has been clicked, but come chat {} is not bound to the admin chat {}", chatToExecuteIn, adminChat.getChatId());
                    return;
                }
                chatsToExecuteIn = Set.of(chatToExecuteIn);
            }
            case ADMIN_CHAT_EXECUTE_COMMAND_IN_ALL_BOUND_CHATS -> {
                if(commandData.getValue().adminChatCommandExecutionMode()==ONLY_SINGLE_BOUND_CHAT_AT_ONCE){
                    log.warn("admin chat button 'in all chats' has been clicked, but the required command {} does not support such mode", commandData.getValue().mainCommandName());
                    return;
                }
                chatsToExecuteIn = adminChat.getBoundChats();
            }
        }

        StringBuilder result = new StringBuilder("📋 Результат выполнения команды:");

        routingData = new CommandRoutingData(routingData);
        routingData.setResponsePeerId(convertToPeerId(routingData.getVkApiChatId()));
        routingData.setResponderBot(routingData.getExecutorBot());

        int chatCounter=1;
        for (long currentChatToExecuteIn: chatsToExecuteIn){

            ChatDetailsDto currentChatDetails = chatService.getCachedChatDetails(currentChatToExecuteIn, false);
            result.append("\n%d. Чат «%s»: ".formatted(chatCounter++, currentChatDetails.getChatTitle()));

            int memberRolePriority = memberService.getMemberRolePriority(currentChatToExecuteIn, fromId);
            if(!commandAccessService.checkCommandAuthorization(currentChatToExecuteIn, commandData.getValue().mainCommandName(),memberRolePriority,fromId)){
                result.append("🚫 у вас нет доступа к этой команде.");
                continue;
            }
            if(!commandAccessService.checkCommandRateLimit(currentChatToExecuteIn,commandData.getValue().mainCommandName(),memberRolePriority,fromId).canExecuteCommand()){
                result.append("🚫 у вас временной лимит на эту команду.");
                continue;
            }

            routingData.setDataBaseChatId(currentChatToExecuteIn);

            long vkApiChatId = currentChatToExecuteIn;
            GroupActor executorBot = theMainBotGroupActor;

            if(currentChatDetails.getBoundSubmanagerId()!=null){
                SubmanagerDto subInfo = submanagerService.getSubmanagerOrThrowIfAbsents(currentChatDetails.getBoundSubmanagerId());
                vkApiChatId = chatService.getSubmanagerChatIdByMainChatId(subInfo.getGroupId(), currentChatToExecuteIn);
                executorBot = subInfo.getGroupActor();
            }
            routingData.setVkApiChatId(vkApiChatId);
            routingData.setExecutorBot(executorBot);

            CommandExecutionStatus executionResult;
            try{
                executionResult = commandData.getKey().execute(
                        messageMapper.toCommandMessageDto(routingData,fromId,fullCommandWithNoPrefix,0, false, false, chatsToExecuteIn.size()>1)
                );
            }
            catch (Exception e){
                log.warn("error execution command {} in admin chat {}", commandData.getValue(),adminChat.getChatId(), e);
                result.append("‼ произошла неизвестная ошибка.");
                continue;
            }
            result.append(executionResult.getDescription());
        }

        routingData.setDataBaseChatId(adminChatDataBaseChatId);
        vkChatClient.sendText(messageMapper.toSendMessageDto(result.toString(), routingData));
    }

    @Async
    public void sendMessageAboutAUsedCommand(long boundChatId, @NonNull Command cmdAnnotation, long fromId){

        Optional<Long> adminChatId = adminChatService.findLatestAdminChatIdByBoundChatId(boundChatId);
        if(adminChatId.isEmpty()) return;

        String message = "↪Команда «%s» была использована %s(%s).\n%s"
                .formatted(cmdAnnotation.mainCommandName(), createMention(fromId), globalUserService.getUserFullNameInRequiredCase(fromId, NameCase.INSTRUMENTAL), buildChatSource(boundChatId));
        putMessageToQueue(adminChatId.get(), message);
    }

    @Async
    public void sendMessageAboutAnExecutedEvent(long boundChatId, @NonNull MyEventType eventType, @Nullable String executedCommand, long causerId){

        Optional<Long> adminChatId = adminChatService.findLatestAdminChatIdByBoundChatId(boundChatId);
        if(adminChatId.isEmpty()) return;

        String message = "💥Было активировано событие «%s».\n🐤Участник, на которого оно сработало: %s(%s)\n↪Команда, которая была применена: %s\n%s"
                .formatted(eventType.getDescription(), createMention(causerId), globalUserService.getUserFullNameInRequiredCase(causerId, NameCase.NOMINATIVE), executedCommand==null?"none":executedCommand, buildChatSource(boundChatId));

        putMessageToQueue(adminChatId.get(), message);
    }

    private void sendMessageToAdminChat(long adminChatId, @NonNull String message){

        ChatDetailsDto adminChatInfo = chatService.getCachedChatDetails(adminChatId, false);

        CommandRoutingData routingData = new CommandRoutingData();
        routingData.setDataBaseChatId(adminChatId);

        long vkApiChatId = adminChatId;
        GroupActor responderBot = theMainBotGroupActor;

        if(adminChatInfo.getBoundSubmanagerId()!=null){
            SubmanagerDto subInfo = submanagerService.getSubmanagerOrThrowIfAbsents(adminChatInfo.getBoundSubmanagerId());
            vkApiChatId = chatService.getSubmanagerChatIdByMainChatId(subInfo.getGroupId(), adminChatId);
            responderBot = subInfo.getGroupActor();
        }
        routingData.setVkApiChatId(vkApiChatId);
        routingData.setResponderBot(responderBot);
        routingData.setResponsePeerId(convertToPeerId(vkApiChatId));

        try{
            vkChatClient.sendText(messageMapper.toSendMessageDto(message, routingData));
        }
        catch (Exception e){
            log.warn("fail send message event to admin chat {}", adminChatId, e);
        }
    }

    private void putMessageToQueue(long adminChatId, @NonNull String message){
        messagesToSendToAdminChat.asMap().compute(adminChatId, (k, v)->{
            StringBuilder sb = v==null?new StringBuilder():v;
            sb.append("\n\n").append(message);
            return sb;
        });
    }

    private String buildChatSource(long boundChat){
        return "❗Из чата «%s».".formatted(chatService.getCachedChatDetails(boundChat, false).getChatTitle());
    }

}
