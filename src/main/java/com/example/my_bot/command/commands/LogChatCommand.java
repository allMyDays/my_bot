package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.key.ConfirmationCacheKeyBuilder;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.chat.ChatException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Forward;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.utils.ChatUtils.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "логчат", alternativeCommandNames = {"logchat"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true)
@RequiredArgsConstructor
public class LogChatCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);
    private final ChatService chatService;
    private final MessageMapper messageMapper;
    private final GlobalUserService globalUserService;
    private final MessageLogService messageLogService;
    private final CaffeineCacheManager cacheManager;
    private VkChatClient vkChatClient;

    private final static String logChatMainCommand = LogChatCommand.class.getAnnotation(Command.class).mainCommandName();
    private final static String FOR_ARGUMENT = "для";
    private final static String REMOVE_ARGUMENT = "удалить";


    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException{

        String[] args = messageDto.getFirstRowArguments();
        long chatId = messageDto.getChatId();
        long fromId = messageDto.getFromId();

        ChatDetailsDto currentChat = chatService.getCachedChatDetails(chatId,false);
        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",messageDto);

        String fullSetCommand = "%c%s %s %s".formatted(DEFAULT_CHAT_PREFIX,logChatMainCommand, FOR_ARGUMENT,currentChat.getChatCode());

        List<ChatEntity> boundChats = chatService.findByBoundLogChat(chatId);
        if(args.length==0){
            if(!boundChats.isEmpty()){
                AtomicInteger ai = new AtomicInteger();
                sendMessage.setText("Данный чат является логчатом для [%d] бесед со следующим кодом:\n".formatted(boundChats.size())+
                boundChats.stream()
                        .map(c->ai.incrementAndGet()+". "+c.getChatCode()+"\n")
                        .collect(Collectors.joining()));
            }
            else if(currentChat.getBoundLogChat()==null){
                sendMessage.setText(
                        "К данному чату не привязан логчат. Чтобы сохранять все сообщения из этого чата, " +
                                "напишите в другом желаемом чате (который хотите сделать логчатом) команду «%s»."
                                        .formatted(fullSetCommand));

            }else{
                sendMessage.setText("К данному чату привязан логчат c кодом «%s»."
                        .formatted(
                                chatService.getCachedChatDetails(currentChat.getBoundLogChat(),false).getChatCode()
                        ));
            }
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(args[0].equalsIgnoreCase(REMOVE_ARGUMENT)){
            // !логчат удалить

            if(currentChat.getBoundLogChat()==null&&boundChats.isEmpty()){
                sendMessage.setText("К текущему чату не привязан логчат, а также текущий чат сам не является логчатом.");
                vkChatClient.sendText(sendMessage);
                return;
            }
            String key = ConfirmationCacheKeyBuilder.REMOVE_LOG_CHAT.buildKey(chatId, fromId);
            String cacheValue = cacheManager.getConfirmationCache().getIfPresent(key);
            if(cacheValue==null){
                cacheManager.getConfirmationCache().put(key, "");
            }
            if(!boundChats.isEmpty()){
                if(cacheValue==null){
                    sendMessage.setText(
                            "Внимание: к данному логчату привязано [%d] бесед. Если вы хотите отвязать все беседы от логчата, введите команду ещё раз."
                            .formatted(boundChats.size())
                    );
                }else{
                    chatService.removeLogChat(chatId);
                    sendMessage.setText("✅Все чаты были успешно отвязаны от текущего логчата, и теперь это обычная беседа.");
                }
            }else{
                if(cacheValue==null){
                    sendMessage.setText("Если вы точно хотите отвязать логчат от текущего чата, введите команду ещё раз.");
                }else{
                    chatService.setBoundLogChatAsNull(chatId);
                    sendMessage.setText("✅Логчат был успешно отвязан от текущего чата.");
                }
            }
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(isValidInteger(args[0])){  // !логчат 10 (вывести последние N сообщений из логчата)
            if(currentChat.getBoundLogChat()==null){
                sendMessage.setText("К текущему чату не привязан логчат.");
                vkChatClient.sendText(sendMessage);
                return;
            }
            List<Integer> requiredMessageIds =
                    messageLogService.findLastMessagesForwardedToLogChat(currentChat.getBoundLogChat(), Integer.parseInt(args[0]));

            Forward forward = new Forward();
            forward.setConversationMessageIds(requiredMessageIds);
            forward.setPeerId(convertToPeerId(currentChat.getBoundLogChat()));
            sendMessage.setForward(forward);
            sendMessage.setText("Последние [%d] сообщений из привязанного логчата:".formatted(requiredMessageIds.size()));
            vkChatClient.sendText(sendMessage);
            return;
        }

        // !логчат для 6fgf553vd

        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(!args[0].equalsIgnoreCase(FOR_ARGUMENT)){
            sendMessage.setText("Структура команды должна быть такой — «%s»".formatted(fullSetCommand));
            vkChatClient.sendText(sendMessage);
            return;
        }
        String userChatCode = args[1].trim();
        try{
            chatService.makeLogChat(userChatCode, chatId, fromId);
        }catch (ChatException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        sendMessage.setText(
                "✅Вы успешно сделали текущий чат логчатом для беседы с кодом %s\nТеперь я буду пересылать сюда все сообщения из того чата."
                        .formatted(userChatCode)
        );
        vkChatClient.sendText(sendMessage);

       Optional<ChatEntity> targetChat = chatService.findByChatCode(userChatCode);
       if(targetChat.isPresent()){
           sendMessage.setReplyToMessageId(false);
           sendMessage.setPeerId(convertToPeerId(targetChat.get().getChatId()));
           sendMessage.setText(
                   "%s(%s) установил логчат для данной беседы. Если хотите удалить логчат, используйте команду «%c%s %s»."
                           .formatted(createMention(fromId),globalUserService.getUserNameInRequiredCase(fromId, NameCase.NOMINATIVE),
                                   DEFAULT_CHAT_PREFIX,logChatMainCommand,REMOVE_ARGUMENT)
           );
           vkChatClient.sendText(sendMessage);

       }

    }
}
