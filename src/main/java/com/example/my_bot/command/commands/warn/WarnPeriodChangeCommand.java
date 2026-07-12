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
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;

@Command(mainCommandName = "срокпреда",alternativeCommandNames = {"времяпреда","warnPeriod"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class WarnPeriodChangeCommand implements ChatCommand {

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

        if(args.length==0){        // отключить стандартный срок преда
            chatService.disableDefaultWarnTimePeriod(chatId);
            sendMessage.setText("✅ Теперь по умолчанию участникам будут выдаваться вечные предупреждения.");
            vkChatClient.sendText(sendMessage);
            return SUCCESS;
        }
        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        Long warnTimePeriodSec = TimeUtils.toSecondsFromString(args[0],args[1]).orElse(null);
        if(warnTimePeriodSec==null){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        warnTimePeriodSec = chatService.setDefaultWarnTimePeriod(chatId, warnTimePeriodSec);

        sendMessage.setText("✅ Теперь по умолчанию участникам будут выдаваться предупреждения, которые удалятся сами через %s"
                .formatted(TimeUtils.formatDurationFromSeconds(warnTimePeriodSec,true)));

        vkChatClient.sendText(sendMessage);

        return SUCCESS;
    }

}
