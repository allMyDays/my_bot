package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.MEMBER_LINK_IS_NOT_CORRECT;
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.utils.ChatUtils.createMention;


@Slf4j
@Command(mainCommandName = "роль", alternativeCommandNames = {"role", "ктоя"}, defaultRole = MEMBER, eventable = true)
@RequiredArgsConstructor
public class UserRoleShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final RoleService roleService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        long chatId = cmd.getChatId();
        long memberToCheck;

        if(cmd.getFirstRowArguments().length==0){
            if(cmd.hasReplyMessage()){
                memberToCheck = cmd.getReplyMessageFromId().get();
            }else if(cmd.hasFwdMessages()){
                memberToCheck = cmd.getFwdMessageFromIds().get(0);
            }else{
                memberToCheck= cmd.getFromId();
            }
        }else{
            Optional<Long> memberOptional = memberService.getCachedMemberIdByUserInput(cmd.getFirstRowArguments()[0]);
            if(memberOptional.isEmpty()){
                vkChatClient.sendText(chatId, MEMBER_LINK_IS_NOT_CORRECT, true);
                return;
            } memberToCheck = memberOptional.get();
        }
        int userRolePriority =  memberService.getCachedMemberRolePriority(chatId, memberToCheck);
        String roleName = roleService.getRoleName(chatId, userRolePriority).orElse("Unknown role");
        vkChatClient.sendText(chatId,
                createMention(memberToCheck)+
                        (cmd.getFromId()==memberToCheck?"(Ваша)":"(Участник) имеет")+" роль в чате — «%s». Приоритет роли: %d"
                .formatted(roleName, userRolePriority),true);


    }
}