package com.example.my_bot.command.commands;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.utils.ChatUtils.DEFAULT_CHAT_PREFIX;

@Command(mainCommandName = "префикс",alternativeCommandNames = {"prefix"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false)
public class PrefixChangeCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private ChatService chatService;

    private VkChatClient vkChatClient;

    @Autowired
    @Lazy
    public void setChatService(ChatService chatService, VkChatClient vkChatClient) {
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        String[] args = messageDto.getFirstRowArguments();
        long chatId = messageDto.getChatId();
        long peerId = messageDto.getPeerId();

        if(args.length==0){
            String message;
            Optional<Character> optionalPrefix = chatService.getChatPrefix(chatId);
            if(optionalPrefix.isEmpty()){
                chatService.setChatPrefix(chatId, DEFAULT_CHAT_PREFIX);
                message = String.format("✅Префикс чата был установлен на стандартный: %s",DEFAULT_CHAT_PREFIX);
            }else{
                chatService.disableChatPrefix(chatId);
                message = String.format("✅Префикс чата был отключён. " +
                        "Теперь команды в чате можно писать как без префикса, так и со стандартным префиксом: %s",DEFAULT_CHAT_PREFIX);
            }
            vkChatClient.sendText(message,peerId,true);
            return;
        }

        if(args[0].length()>1){
            vkChatClient.sendText("В качестве префикса можно установить только один символ.",peerId,true);
            return;
        }
        try{
         chatService.setChatPrefix(chatId, args[0].charAt(0));
        }catch (ForbiddenPrefixException e){
            vkChatClient.sendText(e.getMessage(),peerId, true);
            return;
        }

        vkChatClient.sendText((("✅Префикс чата был установлен на: %s\n" +
                "Теперь команды в чате можно писать ТОЛЬКО с этим префиксом.").formatted(args[0])),
                peerId,
                true);

    }

}
