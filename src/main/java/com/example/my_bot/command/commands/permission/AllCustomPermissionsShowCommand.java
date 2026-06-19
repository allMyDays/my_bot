package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.*;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.permission.MemberPermissionService;
import com.example.my_bot.service.permission.RolePermissionService;
import com.example.my_bot.utils.TextUtils;
import com.google.common.collect.ImmutableMap;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.service.permission.MemberPermissionService.getMaxCustomMemberPermissionsCount;
import static com.example.my_bot.service.permission.RolePermissionService.getMaxCustomRolePermissionsCount;
import static com.example.my_bot.utils.ChatUtils.DEFAULT_CHAT_PREFIX;

@Slf4j
@Command(mainCommandName = "права", alternativeCommandNames = {"разрешения"}, defaultRole = MODERATOR, eventable = true)
@RequiredArgsConstructor
public class AllCustomPermissionsShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private final VkChatClient vkChatClient;

    private final RolePermissionService rolePermissionService;

    private final MemberPermissionService memberPermissionService;

    private final MessageMapper messageMapper;

    private final ChatService chatService;
    private final RoleService roleService;
    private final GlobalUserService userService;


    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        ImmutableMap<String, Integer> rolePermissions = rolePermissionService.getCachedCustomRolePermissions(chatId);
        ImmutableMap<String, ImmutableMap<Long, Boolean>> memberPermissions = memberPermissionService.getCachedCustomMemberPermissions(chatId);

        Map<Integer, List<String>> rolePermissionMap = rolePermissions.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(
                                Map.Entry::getKey,
                                Collectors.toList()
                        )
                ));
        Map<Long, Map<String, Boolean>> memberPermissionMap = new HashMap<>();
        AtomicInteger memberPermissionSize = new AtomicInteger();
        memberPermissions.forEach((command, userMap)->{
            userMap.forEach((user,isAllowed)->{
                Map<String, Boolean> userPermissions = memberPermissionMap.computeIfAbsent(user, k -> new HashMap<>());
                userPermissions.put(command, isAllowed);
                memberPermissionSize.getAndIncrement();
            });
        });

        StringBuilder sb = new StringBuilder();
        sb.append("В чате установлено (%d/%d) кастомных ограничений команд для ролей".formatted(rolePermissions.size(), getMaxCustomRolePermissionsCount()));
        if(memberPermissionSize.get()!=0){
            sb.append(" и (%d/%d) персональных ограничений для участников".formatted(memberPermissionSize.get(),getMaxCustomMemberPermissionsCount()));
        }sb.append(":\n");

        Map<Integer, String> roleMap = roleService.getAllRolesWithNoSorting(chatId);

        char chatPrefix = chatService.getChatPrefix(chatId).orElse(DEFAULT_CHAT_PREFIX);
        for(Map.Entry<Integer, List<String>> entry: rolePermissionMap.entrySet()){
            String roleName = roleMap.get(entry.getKey());
            if(roleName!=null){
                sb.append("\nРоль «%s» (приоритет %d):".formatted(roleName,entry.getKey()));
            }else{
                sb.append("\nРоль с приоритетом %d:".formatted(entry.getKey()));
            }
            for(String command: entry.getValue()){
                sb.append("\n➕ ").append(chatPrefix).append(command);
            }
        }

        memberPermissionMap.forEach((userId, permissionMap)->{
            String mention = TextUtils.createMention(userId);
            String userName = userService.getUserFullNameInRequiredCase(userId, NameCase.GENITIVE);
            sb.append("\nПерсонально для %s(%s):".formatted(mention,userName));
            permissionMap.forEach((command,isAllowed)->{
                sb.append("\n").append(isAllowed ? "➕ " : "➖ ").append(chatPrefix).append(command);
            });
        });

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(), commandMessage));
    }




}
