package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.resolver.MemberInputResolver;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;
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

    private final MemberInputResolver memberInputResolver;

    private final RoleService roleService;

    private final GlobalUserService userService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        long peerId = messageDto.getPeerId();

        long memberToCheck;

        ParseMemberInputResult parseResult = memberInputResolver.getMemberIdByAnyInput(messageDto, 0);

        if(parseResult.getMemberId().isPresent()){
            memberToCheck = parseResult.getMemberId().get();
        }else{
            memberToCheck = messageDto.getFromId();
        }

        int userRolePriority =  memberService.getCachedMemberRolePriority(chatId, memberToCheck);
        String roleName = roleService.getRoleName(chatId, userRolePriority).orElse("Unknown role");
        String userName = userService.getUserNameInRequiredCase(memberToCheck, NameCase.GENITIVE)
                .orElse("участника");

        vkChatClient.sendText("Роль %s(%s) в чате — «%s». Приоритет роли: %d".formatted(createMention(memberToCheck),userName,roleName, userRolePriority)
                ,peerId
                , true);


    }
}