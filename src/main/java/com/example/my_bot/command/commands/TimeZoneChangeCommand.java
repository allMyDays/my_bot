package com.example.my_bot.command.commands;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.Instant;
import java.util.Optional;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;

@Command(mainCommandName = "таймзона",alternativeCommandNames = {"timezone"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true)
public class TimeZoneChangeCommand implements ChatCommand {

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

        TimeZoneType timeZoneToAssign;

        if (args.length == 0) {
            timeZoneToAssign = TimeZoneType.GMT_PLUS_3;
        } else {
            timeZoneToAssign = TimeZoneType.findZoneByStringType(args[0]).orElse(null);
            if (timeZoneToAssign == null) {
                vkChatClient.sendText("Не найдено временной зоны по указанному аргументу.", peerId, true);
                return;
            }
        }
        chatService.setChatTimeZone(chatId, timeZoneToAssign);
        String message = "✅Временная зона чата была успешно установлена на %s.".formatted(timeZoneToAssign.getStringType())
                +"\nТекущее время: "+ TimeUtils.getStringDateTimeWithTimeZone(Instant.now(), timeZoneToAssign);


        vkChatClient.sendText(message,peerId,true);


    }
}
