package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.DefaultRole.MEMBER;


@Slf4j
@Command(commands = {"роль", "role", "ктоя"}, defaultRole = MEMBER, eventable = true)
@RequiredArgsConstructor
public class UserRoleShowCommand implements ChatCommand {

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final RoleService roleService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(long chatId, long fromId, String[] args) throws ClientException, ApiException {

        if(args.length==0){
            int priority =  memberService.getCachedMemberRolePriority(chatId, fromId);
            vkChatClient.sendText(chatId, "Ваша роль в чате — «%s». Приоритет роли: %d"
                    .formatted(roleService.getRoleName(chatId, priority).orElse("Неизвестная роль"), priority),true);
            return;
        }


    }
}