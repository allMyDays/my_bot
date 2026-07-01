package com.example.my_bot.command.commands.ban;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.enumeration.chat.SwitchChatSettingResult.ON;

@Command(mainCommandName = "авторазбан",alternativeCommandNames = {"autounban"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false, adminChatCommandExecutionMode= ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class AutoUnbanCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);
    private final MessageMapper messageMapper;
    private ChatService chatService;
    private VkChatClient vkChatClient;

    private final static String UNBAN_COMMAND_NAME = UnbanCommand.class.getAnnotation(Command.class).mainCommandName();

    @Autowired
    @Lazy
    public void setChatService(ChatService chatService, VkChatClient vkChatClient) {
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        SwitchChatSettingResult switchResult = chatService.switchAutoUnban(dataBaseChatId);

        vkChatClient.sendText(
                messageMapper.toSendMessageDto(
                            "✅Теперь мне %s автоматически снимать бан с пользователей, которые были приглашены участниками, которым хватает прав на команду «%s»."
                                    .formatted(switchResult==ON?"можно":"нельзя",UNBAN_COMMAND_NAME),
                            commandMessage
                )
        );

        return SUCCESS;
    }

}
