package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.utils.VkChatUtils.createMention;

@Command(mainCommandName = "управляющие", alternativeCommandNames = {"staff", "админы"}, defaultRole = MEMBER, eventable = true)
@RequiredArgsConstructor
public class StaffShowCommand implements ChatCommand {

    private final MemberService memberService;
    private final RoleService roleService;
    private VkChatClient vkChatClient;

    @Autowired
    @Lazy
    public void setVkChatService(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        long chatId = cmd.getChatId();

        StringBuilder sb = new StringBuilder();
        List<MemberWithRoleDto> staffList = memberService.getCachedMembersWithRole(chatId);

        Map<Integer, List<MemberWithRoleDto>> staffMap = staffList.stream()  // сортировка по приоритету, от большего приоритета к меньшему
                .collect(Collectors.groupingBy(
                        MemberWithRoleDto::getRolePriority,
                        () -> new TreeMap<>(Comparator.reverseOrder()),
                        Collectors.toList()
                ));

        long exitedMembers = staffList.stream()
                .filter(m -> !m.isInChat())
                .count();

        sb.append("В чате %d участников имеют роль (из них %d сейчас отсутствует).\n\n".formatted(staffList.size(), exitedMembers));

        Map<Integer, String> roleMap = roleService.getRequiredRolesWithNoSorting(chatId, staffMap.keySet()).stream()
                .collect(Collectors.toMap(RoleDto::getRolePriority, RoleDto::getRoleName));


        for(Map.Entry<Integer, List<MemberWithRoleDto>> entry: staffMap.entrySet()){
            sb.append(roleMap.get(entry.getKey())).append(" ").append("(%d):\n".formatted(entry.getKey()));
            for(MemberWithRoleDto member:entry.getValue()){
            if(member.isChatAdmin()){
                sb.append("\uD83D\uDCA0 ");
            } sb.append(createMention(member.getUserId()));
            if(!member.isInChat()){
                sb.append(" \uD83D\uDEAA");
            } sb.append("\n");

            }sb.append("\n");
        }

        vkChatClient.sendText(chatId,sb.toString(),true);

    }
}
