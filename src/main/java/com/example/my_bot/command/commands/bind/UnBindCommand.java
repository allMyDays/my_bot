package com.example.my_bot.command.commands.bind;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.user.GlobalUserException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "отвязать", alternativeCommandNames = {"unbind"}, defaultRole = SENIOR_MODERATOR, eventable = false)
public class UnBindCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private VkChatClient vkChatClient;

    private final GlobalUserService userService;

    private final UserInputResolver userInputResolver;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long fromId = messageDto.getFromId();;

        SendMessageDto sendMessage =  messageMapper.toSendMessageDto("",messageDto);

        long userToUnbind;

        ParseMemberInputResult parseResult = userInputResolver.getMemberIdByAnyInput(messageDto, 0);

        if(parseResult.getMemberId().isPresent()){
            userToUnbind = parseResult.getMemberId().get();
        }else{
            userToUnbind = messageDto.getFromId();
        }

        try {
            userService.unBindChatFromUser(messageDto.getChatId(), fromId, userToUnbind);
        }catch (MemberException | GlobalUserException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        sendMessage.setText("Вы успешно сняли с %s(%s) привязку этого чата.".formatted(createMention(userToUnbind),userToUnbind==fromId
                         ? "себя"
                         : userService.getUserNameInRequiredCase(userToUnbind, NameCase.DATIVE))
        );

        vkChatClient.sendText(sendMessage);

    }
}
