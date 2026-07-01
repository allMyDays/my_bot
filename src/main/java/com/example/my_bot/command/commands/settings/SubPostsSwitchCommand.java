package com.example.my_bot.command.commands.settings;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.THIS_COMMAND_IS_ONLY_FOR_CHATS_WITH_SUBMANAGERS;
import static com.example.my_bot.enumeration.CommandExecutionStatus.BUSINESS_LOGIC_ERROR;
import static com.example.my_bot.enumeration.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.enumeration.chat.SwitchChatSettingResult.ON;
import static com.example.my_bot.utils.TextUtils.createMention;

@Command(mainCommandName = "субпосты",alternativeCommandNames = {"subposts"}, defaultRole = ADMINISTRATOR, eventable = false, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class SubPostsSwitchCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private ChatService chatService;
    private VkChatClient vkChatClient;
    private final MessageMapper messageMapper;
    private final SubmanagerService submanagerService;

    @Autowired
    @Lazy
    public void setChatService(ChatService chatService, VkChatClient vkChatClient) {
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);
        CommandRoutingData commandRouting = commandMessage.getCommandRoutingData();

        if(!submanagerService.isSubmanager(commandRouting.getExecutorBot())){
            sendMessage.setText(THIS_COMMAND_IS_ONLY_FOR_CHATS_WITH_SUBMANAGERS);
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }

        long chatId = commandRouting.getDataBaseChatId();
        SwitchChatSettingResult switchResult = chatService.switchSubPosts(chatId);

        sendMessage.setText(
                "Теперь мне %s присылать в текущий чат все новые посты из %s(этой группы)."
                        .formatted((switchResult==ON?"можно":"нельзя"), createMention(-commandRouting.getExecutorBot().getGroupId()))
        );

        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }
}
