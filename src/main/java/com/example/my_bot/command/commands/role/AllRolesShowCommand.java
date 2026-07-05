package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.TreeMap;

import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;


@Slf4j
@Command(mainCommandName = "роли", alternativeCommandNames = {"roles"}, defaultRole = MODERATOR, eventable = true, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
@RequiredArgsConstructor
public class AllRolesShowCommand implements ChatCommand {

    private VkChatClient vkChatClient;

    private RoleService roleService;

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient, RoleService roleService) {
        this.vkChatClient = vkChatClient;
        this.roleService = roleService;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        StringBuilder sb = new StringBuilder();

        TreeMap<Integer, String> roles = roleService.getAllRolesSortedInDescendingOrder(chatId);

        long customRoles = roles.keySet().stream()
                .filter(r->!isDefaultRole(r))
                .count();

        sb.append("В чате %d дополнительных ролей. Вот полный список:\n\n".formatted(customRoles));

        roles.forEach((key, value) -> sb.append("%s — %d\n".formatted(value, key)));

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(), commandMessage));

        return SUCCESS;
    }
}