package com.example.my_bot.command.commands.mute;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.getFormattedStringDateTime;

@Command(mainCommandName = "мутлист", alternativeCommandNames = {"mutelist"}, defaultRole = MODERATOR, eventable = true)
public class MuteListShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private final VkChatClient vkChatClient;
    private final MessageMapper messageMapper;
    private final GlobalUserService globalUserService;
    private final BanService banService;
    private final ChatService chatService;


    public MuteListShowCommand(@Lazy VkChatClient vkChatClient, MessageMapper messageMapper, GlobalUserService globalUserService, BanService banService, ChatService chatService) {
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
        this.messageMapper = messageMapper;
        this.globalUserService = globalUserService;
        this.banService = banService;
    }

    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();

        StringBuilder sb = new StringBuilder();

        Set<Long> membersWithMute = vkChatClient.getMembersWithWriteRestriction(chatId);

        Map<Long, String> memberNamesMap = globalUserService.getUserFullNamesInRequiredCase(membersWithMute, NameCase.NOMINATIVE);

        sb.append("В чате [%d] участникам запрещено писать сообщения:\n\n".formatted(membersWithMute.size()));

        int counter = 1;
        for(long member:membersWithMute){
            sb.append("%d. %s(%s)".formatted(counter++, createMention(member), memberNamesMap.get(member)));
            sb.append("\n");
        }

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),messageDto));

    }

}
