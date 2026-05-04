package com.example.my_bot.command.commands.bind;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.CANNOT_USE_THIS_COMMAND_IN_PERSONAL_DIALOGUE;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "привязать", alternativeCommandNames = {"bind"}, defaultRole = SENIOR_MODERATOR, eventable = false)
public class BindCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private VkChatClient vkChatClient;

    private final GlobalUserService userService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long peerId = messageDto.getPeerId();
        long fromId = messageDto.getFromId();;

        if(ChatUtils.isPersonalChat(peerId)){
            vkChatClient.sendText(CANNOT_USE_THIS_COMMAND_IN_PERSONAL_DIALOGUE, messageDto.getPeerId(), true);
            return;
        }
        String message;
        if(!vkChatClient.canTheBotWriteToUser(fromId)){
            message = "Для выполнения этой команды, %s(Вы) должны разрешить мне (группе) отправлять вам личные сообщения. Для этого напишите любой текст в личные сообщения сообщества."
                    .formatted(createMention(fromId));
            vkChatClient.sendText(message, messageDto.getPeerId(), true);
            return;
        }
        userService.bindChatToUser(messageDto.getChatId(), fromId);
        try{
            message = "\uD83D\uDD17 Вы успешно привязали чат к своим личным сообщениям. " +
                    "Теперь вы можете писать команды и получать ответы на них прямо здесь. Команды продолжат выполняться в том чате.";

            vkChatClient.sendText(message, fromId, true);
            return;
        }catch (Exception e){
            log.error("chat {} error: user {} allowed personal messages, but I could not send it to him",messageDto.getChatId(), fromId, e);
            message = "%s(Вы) успешно привязали чат к своим личным сообщениям, однако мне не удалось отправить вам личное сообщение. " +
                    "Убедитесь, что вы разрешили мне отправлять себе личные сообщения.";
            vkChatClient.sendText(message, messageDto.getPeerId(), true);

        }




















        vkChatClient.sendText("ПОНГ", messageDto.getPeerId(), true);

    }
}
