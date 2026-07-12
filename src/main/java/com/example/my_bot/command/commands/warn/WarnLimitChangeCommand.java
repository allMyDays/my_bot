package com.example.my_bot.command.commands.warn;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TextUtils;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;

@Command(mainCommandName = "лимитпредов",alternativeCommandNames = {"warnlimit"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class WarnLimitChangeCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private final MessageMapper messageMapper;

    private ChatService chatService;

    private VkChatClient vkChatClient;

    @Autowired
    @Lazy
    public void setChatService(ChatService chatService, VkChatClient vkChatClient) {
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        String[] args = commandMessage.getFirstRowArguments();
        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        if(args.length<1){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        if(!TextUtils.isValidInteger(args[0])){
            sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        int limit = chatService.setMaxWarnLimit(chatId, Integer.parseInt(args[0]));

        sendMessage.setText("✅ Теперь участники получат наказание если наберут %d предупреждений.".formatted(limit));

        vkChatClient.sendText(sendMessage);

        return SUCCESS;
    }

}
