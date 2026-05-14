package com.example.my_bot.command.commands.ban;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.INVALID_TIME_PERIOD_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;

@Command(mainCommandName = "срокбана",alternativeCommandNames = {"banPeriod"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true)
@RequiredArgsConstructor
public class BanPeriodChangeCommand implements ChatCommand {

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
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        String[] args = messageDto.getFirstRowArguments();
        long chatId = messageDto.getChatId();
        final String banCommandName = BanCommand.class.getAnnotation(Command.class).mainCommandName();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",messageDto);



        if(args.length==0){        // отключить стандартный срок бана
            chatService.disableDefaultBanPeriod(chatId);
            sendMessage.setText("✅Дефолтный срок бана был отключён. Теперь, при команде «%s» без аргументов времени, я буду выдавать вечный бан.".formatted(banCommandName));
            vkChatClient.sendText(sendMessage);
            return;
        }if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        Long banPeriodInSeconds = TimeUtils.toSecondsFromString(args[0],args[1]).orElse(null);
        if(banPeriodInSeconds==null){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        long newPeriodSeconds = chatService.setDefaultBanPeriod(chatId, banPeriodInSeconds);

        sendMessage.setText("✅Дефолтный срок бана был успешно установлен на %s Теперь, при команде «%s» без аргументов времени, я буду выдавать бан на этот период."
                .formatted(TimeUtils.formatDurationFromSeconds(newPeriodSeconds,true),banCommandName));

        vkChatClient.sendText(sendMessage);

    }

}
