package com.example.my_bot.command.commands;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.service.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.SwitchChatSettingResult.ON;

@Command(mainCommandName = "тихийзапрет",alternativeCommandNames = {"silentrestr","silentrestriction"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false)
public class SilentRestrictionChangeCommand implements ChatCommand {

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
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        SwitchChatSettingResult switchResult = chatService.switchSilentRestriction(cmd.getChatId());
            vkChatClient.sendText(
                    cmd.getChatId(),
                    "Теперь мне "+ (switchResult==ON?"нельзя":"можно") +" говорить участникам чата о том, что им не хватило прав на использование определенной команды.",
                    true);


    }

}
