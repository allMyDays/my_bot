package com.example.my_bot.command.commands.chat;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.key.ConfirmationCacheKeyBuilder;
import com.example.my_bot.exception.chat.ChatException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Forward;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.utils.ChatUtils.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "логчат", alternativeCommandNames = {"logchat"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
@RequiredArgsConstructor
public class LogChatCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);
    private final ChatService chatService;
    private final MessageMapper messageMapper;
    private final MessageLogService messageLogService;
    private final CaffeineCacheManager cacheManager;
    private VkChatClient vkChatClient;

    private final static String LOG_CHAT_MAIN_COMMAND = LogChatCommand.class.getAnnotation(Command.class).mainCommandName();
    private final static String FOR_ARGUMENT = "для";
    private final static String REMOVE_ARGUMENT = "удалить";


    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException{

        String[] args = commandMessage.getFirstRowArguments();
        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        long fromId = commandMessage.getFromId();

        ChatDetailsDto currentChat = chatService.getCachedChatDetails(dataBaseChatId,false);
        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        String setLogChatCommand = "%c%s %s %s".formatted(DEFAULT_CHAT_PREFIX, LOG_CHAT_MAIN_COMMAND, FOR_ARGUMENT,currentChat.getChatCode());

        List<ChatEntity> boundChats = chatService.findByBoundLogChat(dataBaseChatId);

        if(args.length==0){
            // !логчат

            if(!boundChats.isEmpty()){
                AtomicInteger ai = new AtomicInteger();
                sendMessage.setText("Данный чат является логчатом для [%d] бесед:\n\n".formatted(boundChats.size())+
                boundChats.stream()
                        .map(chat->"%d. «%s» — %s\n".formatted(ai.incrementAndGet(), chat.getChatTitle(), chat.getChatCode()))
                        .collect(Collectors.joining()));
            }
            else if(currentChat.getBoundLogChat()==null){
                sendMessage.setText(
                        "К данному чату не привязан логчат. Чтобы сохранять все сообщения из этого чата, " +
                                "напишите в другом желаемом чате (который хотите сделать логчатом) команду «%s»."
                                        .formatted(setLogChatCommand));

            }
            else{
                ChatDetailsDto chatDetails = chatService.getCachedChatDetails(currentChat.getBoundLogChat(),false);
                sendMessage.setText(
                        "К данному чату привязан логчат с кодом «%s». Название чата: «%s».".formatted(chatDetails.getChatCode(), chatDetails.getChatTitle())
                );
            }
            vkChatClient.sendText(sendMessage);
            return SUCCESS;
        }
        if(args[0].equalsIgnoreCase(REMOVE_ARGUMENT)){
            // !логчат удалить

            if(currentChat.getBoundLogChat()==null&&boundChats.isEmpty()){
                sendMessage.setText("К текущему чату не привязан логчат, а также текущий чат сам не является логчатом.");
                vkChatClient.sendText(sendMessage);
                return BUSINESS_LOGIC_ERROR;
            }
            if(!boundChats.isEmpty()){  // это логчат

                String key = ConfirmationCacheKeyBuilder.REMOVE_LOG_CHAT.buildKey(dataBaseChatId, fromId);
                String cacheValue = cacheManager.getConfirmationCache().getIfPresent(key);

                if(cacheValue==null){
                    cacheManager.getConfirmationCache().put(key, "");
                    sendMessage.setText(
                            "Внимание: к данному логчату привязано [%d] бесед. Если вы хотите отвязать все беседы от логчата, введите команду ещё раз."
                            .formatted(boundChats.size())
                    );
                    vkChatClient.sendText(sendMessage);
                    return ACTION_CONFIRMATION_IS_REQUIRED;
                }
                else{
                    chatService.removeLogChat(dataBaseChatId);
                    sendMessage.setText("✅Все чаты были успешно отвязаны от текущего логчата, и теперь это обычная беседа.");
                }
            }
            else{  // это чат с привязанным логчатом

                chatService.setBoundLogChatAsNull(dataBaseChatId);
                sendMessage.setText("✅Логчат был успешно отвязан от текущего чата.");
            }
            vkChatClient.sendText(sendMessage);
            return SUCCESS;
        }

        if(isValidInteger(args[0])){
            // !логчат 10 (вывести последние N сообщений из логчата)

            if(currentChat.getBoundLogChat()==null){
                sendMessage.setText("К текущему чату не привязан логчат.");
                vkChatClient.sendText(sendMessage);
                return BUSINESS_LOGIC_ERROR;
            }
            long logChatApiId = currentChat.getBoundLogChat();
            ChatDetailsDto logChat= chatService.getCachedChatDetails(currentChat.getBoundLogChat(), false);

            if(logChat.getBoundSubmanagerId()!=null){
                logChatApiId = chatService.getSubmanagerChatIdByMainChatId(logChat.getBoundSubmanagerId(), currentChat.getBoundLogChat());
            }
            List<Integer> requiredMessageIds =
                    messageLogService.findLastMessagesForwardedToLogChat(currentChat.getBoundLogChat(), Integer.parseInt(args[0]));

            Forward forward = new Forward();
            forward.setConversationMessageIds(requiredMessageIds);
            forward.setPeerId(convertToPeerId(logChatApiId));
            sendMessage.setForward(forward);
            sendMessage.setText("Последние [%d] сообщений из привязанного логчата:".formatted(requiredMessageIds.size()));
            vkChatClient.sendText(sendMessage);
            return SUCCESS;
        }

        // !логчат для 6fgf553vd

        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        if(!args[0].equalsIgnoreCase(FOR_ARGUMENT)){
            sendMessage.setText("Структура команды должна быть такой — «%s»".formatted(setLogChatCommand));
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        String userChatCode = args[1].trim();

        try{
            chatService.setLogChat(userChatCode, dataBaseChatId, fromId);
        }
        catch (ChatException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        sendMessage.setText(
                "✅Вы успешно сделали текущий чат логчатом для беседы с кодом %s\nТеперь я буду пересылать сюда все сообщения из того чата."
                        .formatted(userChatCode)
        );

        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }
}
