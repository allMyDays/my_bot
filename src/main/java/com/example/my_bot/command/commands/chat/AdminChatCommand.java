package com.example.my_bot.command.commands.chat;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.chat.AdminChatDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.key.ConfirmationCacheKeyBuilder;
import com.example.my_bot.exception.chat.ChatException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.AdminChatService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_IN_ADMIN_CHAT;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.utils.ChatUtils.DEFAULT_CHAT_PREFIX;

@Slf4j
@Command(mainCommandName = "админчат", alternativeCommandNames = {"adminchat"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false, adminChatCommandExecutionMode = ONLY_IN_ADMIN_CHAT)
@RequiredArgsConstructor
public class AdminChatCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(6,60*2);
    private final AdminChatService adminChatService;
    private final ChatService chatService;
    private final MessageMapper messageMapper;
    private final CaffeineCacheManager cacheManager;
    private VkChatClient vkChatClient;

    private final static String ADMIN_CHAT_MAIN_COMMAND = AdminChatCommand.class.getAnnotation(Command.class).mainCommandName();
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

        String setAdminChatCommand = "%c%s %s %s".formatted(DEFAULT_CHAT_PREFIX, ADMIN_CHAT_MAIN_COMMAND, FOR_ARGUMENT,currentChat.getChatCode());

        Optional<AdminChatDto> adminChat = adminChatService.getAdminChatData(dataBaseChatId);

        if(args.length==0){
            // !админчат

            if(adminChat.isPresent()){
                AtomicInteger ai = new AtomicInteger();
                sendMessage.setText("Данный чат является админ-чатом для [%d] бесед:\n\n".formatted(adminChat.get().getBoundChats().size())+
                        adminChat.get().getBoundChats().stream()
                                .map(chatId-> chatService.getCachedChatDetails(chatId, false))
                                .map(chat-> "%d. «%s» — %s\n".formatted(ai.incrementAndGet(), chat.getChatTitle(), chat.getChatCode()))
                                .collect(Collectors.joining()));
            }
            else{
                sendMessage.setText(
                        "Если хотите привязать админ-чат к данной беседе, " +
                                "напишите в другом желаемом чате (который хотите сделать админ-чатом) команду «%s»."
                                        .formatted(setAdminChatCommand));
            }
            vkChatClient.sendText(sendMessage);
            return SUCCESS;
        }
        if(args[0].equalsIgnoreCase(REMOVE_ARGUMENT)){
            // !админчат удалить

            if(adminChat.isEmpty()){
                sendMessage.setText("Текущий чат не является админ-чатом.");
                vkChatClient.sendText(sendMessage);
                return BUSINESS_LOGIC_ERROR;
            }

            if(args.length>=2){
                // !админчат удалить 6fgf553vd

                try {
                    adminChatService.unBindChatFromAdminChat(adminChat.get().getChatId(), args[1]);
                } catch (ChatException e){
                    sendMessage.setText(e.getMessage());
                    vkChatClient.sendText(sendMessage);
                    return BUSINESS_LOGIC_ERROR;
                }

                sendMessage.setText("Вы успешно отвязали от текущего админ-чата беседу с кодом «%s».".formatted(args[1]));
                vkChatClient.sendText(sendMessage);
                return SUCCESS;
            }

            String key = ConfirmationCacheKeyBuilder.REMOVE_ADMIN_CHAT.buildKey(dataBaseChatId, fromId);
            String cacheValue = cacheManager.getConfirmationCache().getIfPresent(key);

            if(cacheValue==null){
                cacheManager.getConfirmationCache().put(key, "");
                sendMessage.setText(
                        "Внимание: к данному админ-чату привязано [%d] бесед. Если вы хотите отвязать все беседы от админ-чата и сделать его обычным чатом, введите команду ещё раз."
                                .formatted(adminChat.get().getBoundChats().size())
                );
                vkChatClient.sendText(sendMessage);
                return ACTION_CONFIRMATION_IS_REQUIRED;

            }else{
                adminChatService.removeAdminChat(dataBaseChatId);
                sendMessage.setText("✅Все чаты были успешно отвязаны от текущего админ-чата, и теперь это обычная беседа.");
                vkChatClient.sendText(sendMessage);
                return SUCCESS;
            }
        }

        // !админчат для 6fgf553vd

        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        if(!args[0].equalsIgnoreCase(FOR_ARGUMENT)){
            sendMessage.setText("Если хотите установить админ-чат, структура команды должна быть такой — «%s»".formatted(setAdminChatCommand));
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        String userChatCode = args[1].trim();

        String key = ConfirmationCacheKeyBuilder.SET_ADMIN_CHAT.buildKey(dataBaseChatId, fromId);
        String cacheValue = cacheManager.getConfirmationCache().getIfPresent(key);

        if(cacheValue==null&&adminChat.isEmpty()){
            cacheManager.getConfirmationCache().put(key, "");

            sendMessage.setText("Если вы уверены что хотите установить этот чат как админ-чат, введите команду ещё раз.");
            vkChatClient.sendText(sendMessage);
            return ACTION_CONFIRMATION_IS_REQUIRED;
        }

        try{
            adminChatService.setAdminChat(userChatCode, dataBaseChatId, fromId);
        }
        catch (ChatException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        sendMessage.setText(
                "✅Вы успешно сделали текущий чат админ-чатом для беседы с кодом «%s».\nТеперь вы можете управлять тем чатом из текущего админ-чата."
                        .formatted(userChatCode)
        );

        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }
}
