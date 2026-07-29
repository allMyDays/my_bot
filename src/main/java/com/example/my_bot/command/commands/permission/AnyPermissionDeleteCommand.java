package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.permission.MemberPermissionService;
import com.example.my_bot.service.permission.RolePermissionService;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@Command(mainCommandName = "сброситьправо", alternativeCommandNames = {"ungrant"}, defaultRole = ADMINISTRATOR, eventable = false, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class AnyPermissionDeleteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final RolePermissionService rolePermissionService;

    private final MemberPermissionService memberPermissionService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService userService;

    private final MessageMapper messageMapper;


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();
        Optional<Long> userId=Optional.empty();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        if(args.length==0){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        if(args.length==2){
            userId = userInputResolver.getMemberIdByStringInput(chatId, args[1]);
            if(userId.isEmpty()){
                sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
                vkChatClient.sendText(sendMessage);
                return ARGUMENT_VALIDATION_ERROR;
            }
        }

        try{
            if(userId.isEmpty()){
              rolePermissionService.deleteCustomRolePermission(chatId, args[0], commandMessage.getFromId());
            }
            else{
                memberPermissionService.deleteCustomMemberPermission(chatId, args[0], userId.get(),commandMessage.getFromId());
            }
        }
        catch (CommandException | PermissionException | MemberException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }

        sendMessage.setText(userId.map(aLong ->{
                    String userName = userService.getUserFullNameInRequiredCase(aLong, NameCase.ACCUSATIVE);

                    return  "✅Настройка сброшена. Теперь возможность использовать эту команду у %s(%s) зависит только от уровня его роли."
                            .formatted(createMention(aLong),userName);
                }
        ).orElse("✅Настройка прав для указанной команды была сброшена до дефолтной роли."));


        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }
}
