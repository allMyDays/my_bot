package com.example.my_bot.command.commands;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.service.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.enumeration.DefaultRole.*;

@Command(commands = {"префикс", "prefix"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false)
public class PrefixChangeCommand implements ChatCommand {

    private ChatService chatService;

    private VkChatClient vkChatClient;

    @Autowired
    @Lazy
    public void setChatService(ChatService chatService, VkChatClient vkChatClient) {
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(String message, long chatId, long fromId, String[] args) throws ClientException, ApiException {

        if(args.length==0){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE,true);
            return;
        }

        if(args[0].toLowerCase().matches("(?iu)дефолт")){
            chatService.setChatPrefix(chatId, DEFAULT_CHAT_PREFIX);
            vkChatClient.sendText(chatId, "✅Префикс чата был сброшен на стандартный: «%s»".formatted(DEFAULT_CHAT_PREFIX),true);
            return;
        }

        if(args[0].length()>1){
            vkChatClient.sendText(chatId, "В качестве префикса можно установить только один символ.",true);
            return;
        }
        chatService.setChatPrefix(chatId, args[0].charAt(0));

        vkChatClient.sendText(chatId, "✅Префикс чата был установлен на «%s»".formatted(args[0]),true);

    }
}
