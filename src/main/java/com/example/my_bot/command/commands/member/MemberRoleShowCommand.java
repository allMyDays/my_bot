package com.example.my_bot.command.commands.member;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.createMention;


@Slf4j
@Command(mainCommandName = "роль", alternativeCommandNames = {"role", "ктоя"}, defaultRole = MEMBER, eventable = true, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
public class MemberRoleShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final UserInputResolver userInputResolver;

    private final RoleService roleService;

    private final GlobalUserService userService;

    private final long theMainBotId;

    private final MessageMapper messageMapper;


    public MemberRoleShowCommand(MemberService memberService,
                                 UserInputResolver userInputResolver,
                                 RoleService roleService,
                                 GlobalUserService userService,
                                 @Value("${vk.main-bot.id}") long theMainBotId,
                                 MessageMapper messageMapper) {

        this.memberService = memberService;
        this.userInputResolver = userInputResolver;
        this.roleService = roleService;
        this.userService = userService;
        this.theMainBotId = theMainBotId;
        this.messageMapper = messageMapper;
    }

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        long memberToCheck;

        ParseMemberInputResult parseResult = userInputResolver.getMemberIdByAnyInput(commandMessage, 0);

        if(parseResult.getMemberId().isPresent()){
            memberToCheck = parseResult.getMemberId().get();
        }
        else{
            memberToCheck = commandMessage.getFromId();
        }

        int userRolePriority =  memberService.getMemberRolePriority(chatId, memberToCheck);
        String roleName = memberToCheck==-theMainBotId
                ? "Чат-менеджер"
                : roleService.getRoleName(chatId, userRolePriority).orElse("Unknown role");
        String userName = userService.getUserFullNameInRequiredCase(memberToCheck, NameCase.GENITIVE);


        vkChatClient.sendText(
                messageMapper.toSendMessageDto("Роль %s(%s) в чате — «%s». Приоритет роли: %d"
                                .formatted(createMention(memberToCheck),userName,roleName, userRolePriority),
                        commandMessage)
        );

        return SUCCESS;
    }
}