package com.example.my_bot.command.commands.mute;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.Set;

import static com.example.my_bot.enumeration.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.createMention;

@Command(mainCommandName = "мутлист", alternativeCommandNames = {"муты","mutelist"}, defaultRole = MODERATOR, eventable = true, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
public class MuteListShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private final VkChatClient vkChatClient;
    private final MessageMapper messageMapper;
    private final GlobalUserService globalUserService;


    public MuteListShowCommand(@Lazy VkChatClient vkChatClient, MessageMapper messageMapper, GlobalUserService globalUserService) {
        this.vkChatClient = vkChatClient;
        this.messageMapper = messageMapper;
        this.globalUserService = globalUserService;
    }

    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        StringBuilder sb = new StringBuilder();

        Set<Long> membersWithMute = vkChatClient.getMembersWithWriteRestriction(
                commandMessage.getCommandRoutingData().getExecutorBot(),
                commandMessage.getCommandRoutingData().getVkApiChatId()
        );

        Map<Long, String> memberNamesMap = globalUserService.getUserFullNamesInRequiredCase(membersWithMute, NameCase.NOMINATIVE);

        sb.append("В чате [%d] участникам запрещено писать сообщения:\n\n".formatted(membersWithMute.size()));

        int counter = 1;
        for(long member:membersWithMute){
            sb.append("%d. %s(%s)".formatted(counter++, createMention(member), memberNamesMap.get(member)));
            sb.append("\n");
        }

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),commandMessage));
        return SUCCESS;

    }

}
