package com.example.my_bot.command.commands.ban;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.CommandExecutionStatus;
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
import static com.example.my_bot.enumeration.CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR;
import static com.example.my_bot.enumeration.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;

@Command(mainCommandName = "срокбана",alternativeCommandNames = {"banPeriod"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
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
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        String[] args = commandMessage.getFirstRowArguments();
        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        final String banCommandName = BanCommand.class.getAnnotation(Command.class).mainCommandName();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        if(args.length==0){        // отключить стандартный срок бана
            chatService.disableDefaultBanPeriod(chatId);
            sendMessage.setText("✅Дефолтный срок бана был отключён. Теперь, при команде «%s» без аргументов времени, я буду выдавать вечный бан.".formatted(banCommandName));
            vkChatClient.sendText(sendMessage);
            return SUCCESS;
        }
        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        Long banPeriodInSeconds = TimeUtils.toSecondsFromString(args[0],args[1]).orElse(null);
        if(banPeriodInSeconds==null){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        long newPeriodSeconds = chatService.setDefaultBanPeriod(chatId, banPeriodInSeconds);

        sendMessage.setText("✅Дефолтный срок бана был успешно установлен на %s Теперь, при команде «%s» без аргументов времени, я буду выдавать бан на этот период."
                .formatted(TimeUtils.formatDurationFromSeconds(newPeriodSeconds,true),banCommandName));

        vkChatClient.sendText(sendMessage);

        return SUCCESS;

    }

}
