package com.example.my_bot.command.commands.ban;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.INVALID_TIME_PERIOD_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;

@Command(mainCommandName = "срокбана",alternativeCommandNames = {"banPeriod"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true)
public class BanPeriodChangeCommand implements ChatCommand {

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
        String message;
        final String banCommandName = BanCommand.class.getAnnotation(Command.class).mainCommandName();

        if(args.length==0){        // отключить стандартный срок бана
            chatService.disableDefaultBanPeriod(chatId);
            message = "✅Дефолтный срок бана был отключён. Теперь, при команде «%s» без аргументов времени, я буду выдавать вечный бан.".formatted(banCommandName);
            vkChatClient.sendText(message,peerId,true);
            return;
        }if(args.length<2){
            vkChatClient.sendText( NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId,true);
            return;
        }
        Long banPeriodInSeconds = TimeUtils.toSecondsFromString(args[0],args[1]).orElse(null);
        if(banPeriodInSeconds==null){
            vkChatClient.sendText(INVALID_TIME_PERIOD_MESSAGE,peerId, true);
            return;
        }
        long newPeriodSeconds = chatService.setDefaultBanPeriod(chatId, banPeriodInSeconds);

        message = "✅Дефолтный срок бана был успешно установлен на %s Теперь, при команде «%s» без аргументов времени, я буду выдавать бан на этот период."
                .formatted(TimeUtils.formatDurationFromSeconds(newPeriodSeconds,true),banCommandName);


        vkChatClient.sendText(message, peerId, true);

    }

}
