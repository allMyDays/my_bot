package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;

import java.util.Set;

import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "кикгрупп", alternativeCommandNames = {"kickgroups"}, defaultRole = ADMINISTRATOR, eventable = true)
public class KickCommunitiesCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*3);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final MessageMapper messageMapper;

    private final static DefaultRole KICK_MEMBERS_WITH_ROLE_LESS_THAN = MODERATOR;

    private final static int MEMBERS_LIMIT_AT_ONE_USAGE = 100;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        Page<MemberEntity> allRequiredCommunities = memberService.getNotKickedCommunitiesWithRoleLessThan(dataBaseChatId, KICK_MEMBERS_WITH_ROLE_LESS_THAN.getRolePriority(), MEMBERS_LIMIT_AT_ONE_USAGE);

        Set<Long> kickedCommunities = vkChatClient.kickManyChatMembers(
                commandMessage.getCommandRoutingData(),
                allRequiredCommunities.getContent().stream()
                        .filter(m->!m.isChatAdmin())
                        .map(MemberEntity::getUserId)
                        .toList()
        );

        vkChatClient.sendText(messageMapper.toSendMessageDto("✅Было исключено %d из %d сообществ с ролью ниже чем «%s»."
                .formatted(kickedCommunities.size(), allRequiredCommunities.getTotalElements(), KICK_MEMBERS_WITH_ROLE_LESS_THAN.getRoleName()),commandMessage));





    }
}
