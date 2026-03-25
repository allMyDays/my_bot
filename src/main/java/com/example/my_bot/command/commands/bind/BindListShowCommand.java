package com.example.my_bot.command.commands.bind;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.UserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.ChatUtils.createMention;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "привязки", alternativeCommandNames = {"bindlist"}, defaultRole = SENIOR_MODERATOR, eventable = false)
public class BindListShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(2,60*4);

    private VkChatClient vkChatClient;

    private final UserService userService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long peerId = messageDto.getPeerId();
        long fromId = messageDto.getFromId();;

        List<Long> users = userService.findUserIdsByBoundChat(messageDto.getChatId());

        StringBuilder sb = new StringBuilder();

        sb.append("Этот чат привязан у %d участников:\n\n".formatted(users.size()));
        AtomicInteger counter = new AtomicInteger(1);
        users.forEach(userId->sb.append(counter.getAndIncrement()).append(". %s".formatted(createMention(userId))));

        vkChatClient.sendText(sb.toString(), messageDto.getPeerId(), true);

    }
}
