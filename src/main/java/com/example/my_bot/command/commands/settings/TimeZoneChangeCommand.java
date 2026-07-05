package com.example.my_bot.command.commands.settings;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.Instant;

import static com.example.my_bot.enumeration.command.CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;

@Command(mainCommandName = "таймзона",alternativeCommandNames = {"timezone"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class TimeZoneChangeCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private ChatService chatService;

    private VkChatClient vkChatClient;

    private final MessageMapper messageMapper;

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

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        TimeZoneType timeZoneToAssign;

        if(args.length == 0){
            timeZoneToAssign = TimeZoneType.GMT_PLUS_3;
        }
        else{
            timeZoneToAssign = TimeZoneType.findZoneByStringType(args[0]).orElse(null);
            if(timeZoneToAssign == null){
                sendMessage.setText("Не найдено временной зоны по указанному аргументу.");
                vkChatClient.sendText(sendMessage);
                return ARGUMENT_VALIDATION_ERROR;
            }
        }
        chatService.setChatTimeZone(chatId, timeZoneToAssign);
        sendMessage.setText(
                "✅Временная зона чата была успешно установлена на %s.".formatted(timeZoneToAssign.getStringType())
                +"\nТекущее время: "+ TimeUtils.getFormattedStringDateTimeWithTimeZone(Instant.now(), timeZoneToAssign)
        );

        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }
}
