package com.example.my_bot.command.commands.ban;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.ban.BanException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "разбанить", alternativeCommandNames = {"разбан", "unban", "снятьбан"}, defaultRole = SENIOR_MODERATOR, eventable = true)
public class UnbanCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private VkChatClient vkChatClient;

    private final BanService banService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService globalUserService;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();

        long memberToUnban;

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("", messageDto);

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(messageDto, 0);
        if(inputResult.getMemberId().isPresent()){
            memberToUnban = inputResult.getMemberId().get();
        }else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return;
        }

       try{
           banService.deleteMemberBan(chatId, memberToUnban);
       }catch (BanException e){
           sendMessage.setText(e.getMessage());
           vkChatClient.sendText(sendMessage);
           return;
       }

       sendMessage.setText("✅С %s(%s) был успешно снят бан. Теперь вам нужно самостоятельно пригласить этого пользователя в чат.".formatted(
               createMention(memberToUnban),
               globalUserService.getUserNameInRequiredCase(memberToUnban, NameCase.GENITIVE).orElse("данного участника")
       ));

       vkChatClient.sendText(sendMessage);

    }
}
