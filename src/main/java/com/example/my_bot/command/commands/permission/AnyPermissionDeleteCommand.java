package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.service.MemberPermissionService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RolePermissionService;
import com.example.my_bot.service.UserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.MEMBER_LINK_IS_NOT_CORRECT;
import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.ChatUtils.createMention;

@Slf4j
@Command(mainCommandName = "сброситьправо", alternativeCommandNames = {"ungrant"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class AnyPermissionDeleteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final RolePermissionService rolePermissionService;

    private final MemberPermissionService memberPermissionService;

    private final MemberService memberService;

    private final UserService userService;





    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getChatId();
        String[] args = commandMessage.getFirstRowArguments();
        Optional<Long> userId=Optional.empty();
        if(args.length==0){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }if(args.length==2){
            userId = memberService.getCachedMemberIdByUserInput(args[1]);
            if(userId.isEmpty()){
                vkChatClient.sendText(chatId, MEMBER_LINK_IS_NOT_CORRECT, true);
                return;
            }
        }
        try{
            if(userId.isEmpty()){
              rolePermissionService.deleteCustomRolePermission(chatId, args[0], commandMessage.getFromId());
            }else{
                memberPermissionService.deleteCustomMemberPermission(chatId, args[0], userId.get(),commandMessage.getFromId());
            }
        }catch (CommandException | PermissionException | MemberException e){
            vkChatClient.sendText(chatId, e.getMessage(), true);
            return;
        }

        vkChatClient.sendText(chatId, userId.map(aLong ->{
              String userName = userService.getUserNameInRequiredCase(aLong, NameCase.ACCUSATIVE)
                            .orElse("этого участника");

               return  "✅Настройка сброшена. Теперь возможность использовать эту команду у %s(%s) зависит только от уровня его роли."
                       .formatted(createMention(aLong),userName);
          }
        ).orElse("✅Настройка прав для указанной команды была сброшена до дефолтной роли."), true);

    }

}
