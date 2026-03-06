package com.example.my_bot.command.impl.role;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.utils.VkChatUtils.createMention;
import static com.example.my_bot.utils.VkChatUtils.extractConversationId;

@Component
public class StaffShowCommand implements ChatCommand {

    private MemberService memberService;
    private VkChatClient vkChatClient;

    @Autowired
    @Lazy
    public void setChatService(MemberService memberService, VkChatClient vkChatClient) {
       this.memberService = memberService;
        this.vkChatClient = vkChatClient;
    }

    @Override
    public String getCommand() {
        return "управляющие";
    }

    @Override
    public void execute(String message, long chatId, long fromId, String[] args) throws ClientException, ApiException {

        StringBuilder sb = new StringBuilder();
        List<MemberWithRoleDto> staffList = memberService.getMembersWithRole(chatId);

        Map<Integer, List<MemberWithRoleDto>> staffMap = staffList.stream()  // сортировка по приоритету, от большего приоритета к меньшему
                .collect(Collectors.groupingBy(
                        MemberWithRoleDto::getRolePriority,
                        () -> new TreeMap<>(Comparator.reverseOrder()),
                        Collectors.toList()
                ));

        long exitedMembers = staffList.stream()
                .filter(m -> !m.isInChat())
                .count();

        sb.append("В чате %d участников имеют роль (из них %d отсутствует в чате)\n\n".formatted(staffList.size(), exitedMembers));

        for(Map.Entry<Integer, List<MemberWithRoleDto>> entry: staffMap.entrySet()){
            String roleName = DefaultRole.getRoleNameByPriority(entry.getKey())
                    .orElse("Неизвестная роль");
            sb.append(roleName).append(" ").append("(%d):\n".formatted(entry.getKey()));
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
