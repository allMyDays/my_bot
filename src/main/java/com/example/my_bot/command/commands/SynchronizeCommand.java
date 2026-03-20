package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName ="синхронизация", alternativeCommandNames = {"resync"}, defaultRole = MODERATOR, eventable = true)
public class SynchronizeCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(2,60*2);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        memberService.synchronizeChatMembers(cmd.getChatId());

        vkChatClient.sendText(cmd.getChatId(), "✅Текущие участники чата были синхронизированы с моей базой данных.",true);

    }
}
