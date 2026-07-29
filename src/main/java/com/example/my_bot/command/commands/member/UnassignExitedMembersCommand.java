package com.example.my_bot.command.commands.member;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.createMention;

@Command(mainCommandName = "снятьвышедших", alternativeCommandNames = {"unassignexited"}, defaultRole = SENIOR_MODERATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class UnassignExitedMembersCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private final MemberService memberService;
    private VkChatClient vkChatClient;
    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatService(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long fromId = commandMessage.getFromId();
        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        RoleDto callerRole = memberService.removePositiveRoleFromExitedMembers(chatId,fromId);

        String message = "✅%s(Вы) успешно разжаловали всех вышедших и исключенных участников, чья роль ниже вашей («%s» с приоритетом %d)."
                .formatted(createMention(fromId),callerRole.getRoleName(), callerRole.getRolePriority());

        vkChatClient.sendText(messageMapper.toSendMessageDto(message,commandMessage));
        return SUCCESS;
    }
}
