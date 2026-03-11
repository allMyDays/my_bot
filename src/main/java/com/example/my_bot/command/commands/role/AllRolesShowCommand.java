package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Set;

import static com.example.my_bot.enumeration.DefaultRole.*;


@Slf4j
@Command(mainCommandName = "роли", alternativeCommandNames = {"roles"}, defaultRole = MODERATOR, eventable = true)
public class AllRolesShowCommand implements ChatCommand {

    private VkChatClient vkChatClient;

    private RoleService roleService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient, RoleService roleService) {
        this.vkChatClient = vkChatClient;
        this.roleService = roleService;
    }


    @Override
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        long chatId = cmd.getChatId();

        StringBuilder sb = new StringBuilder();

        Set<RoleDto> roles = roleService.getAllRolesSortedInDescendingOrder(chatId);

        long customRoles = roles.stream()
                .filter(r->!isDefaultRole(r.getRolePriority()))
                .count();

        sb.append("В чате %d дополнительных ролей. Вот полный список:\n\n".formatted(customRoles));

        roles.forEach((r)->
                sb.append("%s — %d\n".formatted(r.getRoleName(), r.getRolePriority()))
        );

        vkChatClient.sendText(chatId, sb.toString(),true);

    }
}