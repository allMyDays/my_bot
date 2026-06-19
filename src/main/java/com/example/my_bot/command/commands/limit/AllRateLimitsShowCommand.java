package com.example.my_bot.command.commands.limit;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleRateLimitService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;

@Slf4j
@Command(mainCommandName = "лимиты", alternativeCommandNames = {"limits"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class AllRateLimitsShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final RoleRateLimitService roleRateLimitService;

    private final RoleService roleService;

    private final MessageMapper messageMapper;


    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        List<RoleRateLimitDto> roleLimits = roleRateLimitService.getRoleLimitsSortedByEntityId(chatId);
        Map<Integer, String> roleMap = roleService.getAllRolesWithNoSorting(chatId);

        StringBuilder sb = new StringBuilder();

        sb.append("В чате установлено %d пользовательских временных лимитов для команд.\n".formatted(roleLimits.size()));

        int counter = 1;
        for(RoleRateLimitDto limit: roleLimits){
            String roleName = roleMap.get(limit.getRolePriority());
            sb.append(counter++).append(". Лимит команды «%s» в [%d] использований за %s для роли %s. %s \n"
                    .formatted(
                            limit.getCommandName(),
                            limit.getMaxUsage(),
                            TimeUtils.formatDurationFromSeconds(limit.getTimePeriodSec(),true),
                            roleName==null?"с приоритетом "+limit.getRolePriority():"«%s»".formatted(roleName),
                            limit.isPersonal()?"Считается отдельно для каждого участника.":"Лимит общий для всех участников с этой ролью."
                    )
            );


        } vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),commandMessage));
    }
}
