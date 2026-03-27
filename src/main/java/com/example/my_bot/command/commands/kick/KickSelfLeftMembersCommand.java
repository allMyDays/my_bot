package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.DefaultRole;
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

import static com.example.my_bot.enumeration.DefaultRole.*;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "киквышедших", alternativeCommandNames = {"kickleft"}, defaultRole = ADMINISTRATOR, eventable = true)
public class KickSelfLeftMembersCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(2,60*3);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();

        DefaultRole requiredRole = MODERATOR;

        Page<MemberEntity> allRequiredMembers = memberService.getSelfLeftOrUnknownLeftMembersWithRoleLessThan(chatId, requiredRole.getRolePriority(), 100);

        Set<Long> kickedMembers = vkChatClient.kickManyChatMembers(chatId,
                allRequiredMembers.getContent().stream()
                        .map(MemberEntity::getUserId)
                        .toList());

        vkChatClient.sendText("✅Было исключено %d из %d вышедших, но не исключённых участников с ролью ниже чем «%s»."
                .formatted(kickedMembers.size(), allRequiredMembers.getTotalElements(), requiredRole.getRoleName()), messageDto.getPeerId(), true);





    }
}
