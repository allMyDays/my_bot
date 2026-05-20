package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.event.ReactionType;
import com.example.my_bot.mapper.MessageMapper;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;

@Slf4j
@Command(mainCommandName = "реакции", alternativeCommandNames = {"reactions"}, defaultRole = ADMINISTRATOR, eventable = true)
@RequiredArgsConstructor
public class ReactionsCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(10,60*2);

    private VkChatClient vkChatClient;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        ReactionType[] reactions =ReactionType.values();

        StringBuilder sb = new StringBuilder("Доступно %d реакций, которые можно использовать в событиях:\n".formatted(reactions.length));
        for(ReactionType reaction: reactions){
            sb
                    .append(reaction.getEmoji())
                    .append("   —   ")
                    .append(reaction.getReactionId())
                    .append("\n");

        }
        sb.append("Слева указаны эмоджи, справа — ID реакции. Каждый тип можно использовать в событии.");

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),messageDto));

    }
}
