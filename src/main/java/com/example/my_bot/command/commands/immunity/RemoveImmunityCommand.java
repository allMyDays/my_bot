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
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.example.my_bot.constant.MessageConstant.MEMBER_ARGUMENT_ABSENTS;
import static com.example.my_bot.constant.MessageConstant.MEMBER_ROLE_HAS_BEEN_CHANGED;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@Command(mainCommandName = "снятьиммунитет", alternativeCommandNames = {"снятьиммун", "unimmune"}, defaultRole = SENIOR_MODERATOR, eventable = true)
@RequiredArgsConstructor
public class RemoveImmunityCommand implements ChatCommand {

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

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, messageDto);

        long userToAlter;
        ParseMemberInputResult parseResult = userInputResolver.getMemberIdByAnyInput(messageDto,0);
        if(parseResult.getMemberId().isPresent()){
            userToAlter = parseResult.getMemberId().get();
        }else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return;
        }
        try{
            memberService.removeImmunityFromMember(chatId, userToAlter, messageDto.getFromId());
        }catch(RoleException | MemberException | CommandException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        String username = userService.getUserFullNameInRequiredCase(userToAlter, NameCase.GENITIVE);

        sendMessage.setText("✅ С %s(%s) был успешно снят иммунитет.".formatted(createMention(userToAlter), username));
        vkChatClient.sendText(sendMessage);

    }


}
