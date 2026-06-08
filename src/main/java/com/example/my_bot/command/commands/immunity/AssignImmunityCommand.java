package com.example.my_bot.command.commands.immunity;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName = "иммунитет", alternativeCommandNames = {"иммун", "immunity"}, defaultRole = SENIOR_MODERATOR, eventable = true)
@RequiredArgsConstructor
public class AssignImmunityCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private final VkChatClient vkChatClient;

    private final MemberService memberService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService userService;

    private final MessageMapper messageMapper;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, messageDto);

        // варианты:
        // !иммунитет @durov администратор
        // !иммунитет администратор (пересланное смс)

        if(args.length==0){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        long userToAlter;
        String immuneRoleToGive;

        ParseMemberInputResult parseResult = userInputResolver.getMemberIdByAnyInput(messageDto,0);
        if(parseResult.getMemberId().isPresent()){
            userToAlter = parseResult.getMemberId().get();
        }else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(!parseResult.isFwdMessage()){
            // !иммунитет @durov администратор
            if(args.length<2){
                sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
                vkChatClient.sendText(sendMessage);
                return;
            } immuneRoleToGive=args[1];
        }else{
            // !иммунитет администратор (пересланное смс)
            immuneRoleToGive=args[0];
        }
        if(isNumber(immuneRoleToGive)&&!isValidInteger(immuneRoleToGive)){
            sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }

        RoleDto newImmuneRole;
        try{
         if(isNumber(immuneRoleToGive)){
            newImmuneRole= memberService.assignImmunityToMember(chatId, userToAlter,Integer.parseInt(immuneRoleToGive), messageDto.getFromId());
         }else{
            newImmuneRole = memberService.assignImmunityToMember(chatId, userToAlter,immuneRoleToGive, messageDto.getFromId());
         }
        }catch(MemberException | RoleException | CommandException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }

        String username = userService.getUserFullNameInRequiredCase(userToAlter, NameCase.ACCUSATIVE);

        sendMessage.setText("✅ Теперь на %s(%s) не смогут воздействовать участники с ролью «%s» и ниже.".formatted(createMention(userToAlter), username, newImmuneRole.getRoleName()));
        vkChatClient.sendText(sendMessage);
    }


}
