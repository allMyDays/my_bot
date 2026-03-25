package com.example.my_bot.command.commands.bind;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.user.UserException;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.UserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.CANNOT_USE_THIS_COMMAND_IN_PERSONAL_DIALOGUE;
import static com.example.my_bot.constant.MessageConstant.MEMBER_LINK_IS_NOT_CORRECT;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.ChatUtils.createMention;
import static com.example.my_bot.utils.ChatUtils.isPersonalChat;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "отвязать", alternativeCommandNames = {"unbind"}, defaultRole = SENIOR_MODERATOR, eventable = false)
public class UnBindCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private VkChatClient vkChatClient;

    private final UserService userService;

    private final MemberService memberService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long peerId = messageDto.getPeerId();
        long fromId = messageDto.getFromId();;
        String[] args = messageDto.getFirstRowArguments();

        long userToUnbind;
        if(args.length>0){
            Optional<Long> optionalUserId =  memberService.getCachedMemberIdByUserInput(args[0]);
            if(optionalUserId.isEmpty()){
                vkChatClient.sendText(MEMBER_LINK_IS_NOT_CORRECT,peerId,true);
                return;
            }userToUnbind = optionalUserId.get();
        }else{
            userToUnbind = fromId;
        }
        try {
            userService.unBindChatFromUser(messageDto.getChatId(), fromId, userToUnbind);
        }catch (MemberException | UserException e){
            vkChatClient.sendText(e.getMessage(), messageDto.getPeerId(), true);
            return;
        }
        String message = "Вы успешно сняли с %s(%s) привязку этого чата.".formatted(createMention(userToUnbind),userToUnbind==fromId
                         ? "себя"
                         : userService.getUserNameInRequiredCase(userToUnbind, NameCase.DATIVE).orElse("данного пользователя"));

        vkChatClient.sendText(message, peerId,true);

    }
}
