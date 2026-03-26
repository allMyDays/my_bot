package com.example.my_bot.command.commands.member;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.*;

import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.ChatUtils.createMention;

@Command(mainCommandName = "снятьвышедших", alternativeCommandNames = {"unassignexited"}, defaultRole = SENIOR_MODERATOR, eventable = true)
@RequiredArgsConstructor
public class UnassignExitedMembersCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private final MemberService memberService;
    private VkChatClient vkChatClient;

    @Autowired
    @Lazy
    public void setVkChatService(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long fromId = messageDto.getFromId();

        RoleDto callerRole = memberService.removePositiveRoleFromExitedMembers(messageDto.getChatId(),fromId);

        String message = "✅%s(Вы) успешно разжаловали всех вышедших и исключенных участников, чья роль ниже вашей («%s» с приоритетом %d)."
                .formatted(createMention(fromId),callerRole.getRoleName(), callerRole.getRolePriority());

        vkChatClient.sendText(message,messageDto.getPeerId(),false);

    }

}
