package com.example.my_bot.command.commands.member;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import java.util.*;

import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.ChatUtils.createMention;

@Command(mainCommandName = "управляющие", alternativeCommandNames = {"staff", "админы"}, defaultRole = MEMBER, eventable = true)
//@RequiredArgsConstructor
public class StaffShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(7,60*2);

    private final MemberService memberService;
    private final RoleService roleService;
    private VkChatClient vkChatClient;
    private final long groupId;

    public StaffShowCommand(MemberService memberService, RoleService roleService, @Value("${vk.group.id}") long groupId) {
        this.memberService = memberService;
        this.roleService = roleService;
        this.groupId = groupId;
    }

    @Autowired
    @Lazy
    public void setVkChatService(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();

        StringBuilder sb = new StringBuilder();

        long exitedStaffMembers = 0;
        long allStaffMembers = 0;

        TreeMap<Integer, List<MemberDto>> staffMap =  new TreeMap<>(Comparator.reverseOrder());
        Collection<MemberDto> allMembersWithNotZeroRole = memberService.getCachedMembersWithRole(chatId).values();

        for (MemberDto memberDto : allMembersWithNotZeroRole) {
            if(memberDto.getRolePriority()<= MEMBER.getRolePriority()||memberDto.getUserId()==-groupId){
                // отсеиваю участников с отрицательной ролью и самого бота
                continue;
            }
            staffMap.computeIfAbsent(memberDto.getRolePriority(), k -> new ArrayList<>()).add(memberDto);
            if (!memberDto.getPresenceType().equals(IN_CHAT)) {
                exitedStaffMembers++;
            }allStaffMembers++;
        }

        sb.append("В чате %d управляющих (из них %d сейчас отсутствует).\n\n".formatted(allStaffMembers, exitedStaffMembers));

        Map<Integer, String> roleMap = roleService.getAllRolesWithNoSorting(chatId);


        for(Map.Entry<Integer, List<MemberDto>> entry: staffMap.entrySet()){
            sb.append(roleMap.get(entry.getKey())).append(" ").append("(%d):\n".formatted(entry.getKey()));
            for(MemberDto member:entry.getValue()){
            if(member.isChatAdmin()){
                sb.append("\uD83D\uDCA0 ");
            } sb.append(createMention(member.getUserId()));
            if(!member.getPresenceType().equals(IN_CHAT)){
                sb.append(" \uD83D\uDEAA");
            } sb.append("\n");

            }sb.append("\n");
        }

        vkChatClient.sendText(sb.toString(),messageDto.getPeerId(),true);

    }

}
