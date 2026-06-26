package com.example.my_bot.command.commands.bind;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "привязки", alternativeCommandNames = {"bindlist"}, defaultRole = SENIOR_MODERATOR, eventable = false)
public class BindListShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(2,60*4);

    private VkChatClient vkChatClient;
    private final GlobalUserService userService;
    private final MessageMapper messageMapper;
    private final GlobalUserService globalUserService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        Set<Long> users = userService.findUserIdsByBoundChat(commandMessage.getCommandRoutingData().getDataBaseChatId());

        Map<Long, String> memberNamesMap = globalUserService.getUserFullNamesInRequiredCase(users, NameCase.NOMINATIVE);

        StringBuilder sb = new StringBuilder();

        sb.append("Этот чат привязан у %d участников:\n\n".formatted(users.size()));
        AtomicInteger counter = new AtomicInteger(1);
        users.forEach(userId->sb.append(counter.getAndIncrement()).append(". %s(%s)\n"
                        .formatted(createMention(userId),memberNamesMap.get(userId))
                )
        );

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),commandMessage));

    }
}
