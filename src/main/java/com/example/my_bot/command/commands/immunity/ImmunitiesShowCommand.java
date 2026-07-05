package com.example.my_bot.command.commands.immunity;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.TextUtils.createMention;

@Command(mainCommandName = "иммунитеты", alternativeCommandNames = {"иммуны", "immunities"}, defaultRole = MODERATOR, eventable = true, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
public class ImmunitiesShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(7,60*2);

    private final MemberService memberService;
    private final RoleService roleService;
    private VkChatClient vkChatClient;
    private final MessageMapper messageMapper;
    private final GlobalUserService globalUserService;

    public ImmunitiesShowCommand(MemberService memberService, RoleService roleService, MessageMapper messageMapper, GlobalUserService globalUserService) {
        this.memberService = memberService;
        this.roleService = roleService;
        this.messageMapper = messageMapper;
        this.globalUserService = globalUserService;
    }

    @Autowired
    @Lazy
    public void setVkChatService(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        StringBuilder sb = new StringBuilder();

        List<MemberEntity> membersWithImmune = memberService.getMembersWithImmunity(chatId);

        Map<Long, String> memberNamesMap = globalUserService.getUserFullNamesInRequiredCase(
                membersWithImmune.stream()
                        .map(MemberEntity::getUserId)
                        .collect(Collectors.toSet()),
                NameCase.NOMINATIVE
        );

        sb.append("В чате %d участников имеют иммунитет от ролей.\n\n".formatted(membersWithImmune.size()));

        Map<Integer, String> roleMap = roleService.getAllRolesWithNoSorting(chatId);

        for(MemberEntity member:membersWithImmune){
            if(!member.getPresenceType().equals(IN_CHAT)){
                sb.append("\uD83D\uDEAA ");
            }
            sb.append("%s(%s) — %s"
                    .formatted(
                            createMention(member.getUserId()),
                            memberNamesMap.get(member.getUserId()),
                            Optional.ofNullable(roleMap.get(member.getImmuneRolePriority())).orElse("роль с приоритетом "+member.getImmuneRolePriority()))
            );
            sb.append("\n");
        }
        if(!membersWithImmune.isEmpty()){
            sb.append("\n❓На этих пользователей не могут воздействовать управляющие, чья роль ниже или равна роли напротив.");
        }

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),commandMessage));
        return SUCCESS;

    }

}
