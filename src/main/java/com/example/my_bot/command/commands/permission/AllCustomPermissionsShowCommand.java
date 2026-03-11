package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.permission.RolePermissionDto;
import com.example.my_bot.dto.permission.SetCommandPermissionResult;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.ChatService;
import com.example.my_bot.service.CommandPermissionService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.utils.VkChatUtils.isNumber;
import static com.example.my_bot.utils.VkChatUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "разрешения", alternativeCommandNames = {"права"}, defaultRole = MODERATOR, eventable = true)
@RequiredArgsConstructor
public class AllCustomPermissionsShowCommand implements ChatCommand {

    private final VkChatClient vkChatClient;

    private final CommandPermissionService permissionService;

    private final ChatService chatService;
    private final RoleService roleService;


    @Override
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        long chatId = cmd.getChatId();

        Collection<RolePermissionDto> permissions = permissionService.getCachedCustomRolePermissions(chatId)
                .values();


        Map<Integer, List<String>> permissionMap = permissions.stream()
                .collect(Collectors.groupingBy(
                        RolePermissionDto::getRolePriority,
                        () -> new TreeMap<>(Comparator.reverseOrder()),
                        Collectors.mapping(RolePermissionDto::getCommandName, Collectors.toList())
                ));

        StringBuilder sb = new StringBuilder();
        sb.append("В чате установлено %d кастомных настроек прав для команд:".formatted(permissions.size()));

        Map<Integer, String> roleMap = roleService.getRequiredRolesWithNoSorting(chatId, permissionMap.keySet()).stream()
                .collect(Collectors.toMap(RoleDto::getRolePriority, RoleDto::getRoleName));

        char chatPrefix = chatService.getChatPrefix(chatId).orElse(DEFAULT_CHAT_PREFIX);
        for(Map.Entry<Integer, List<String>> entry: permissionMap.entrySet()){
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
        vkChatClient.sendText(chatId, sb.toString(), true);
    }




}
