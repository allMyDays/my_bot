package com.example.my_bot.command.commands.bind;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.client.VkCommunityClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.GroupUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.CANNOT_USE_THIS_COMMAND_IN_PERSONAL_DIALOGUE;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName = "привязать", alternativeCommandNames = {"bind"}, defaultRole = SENIOR_MODERATOR, eventable = false, onlyForConversations = true)
public class BindCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private final VkChatClient vkChatClient;
    private final VkCommunityClient vkCommunityClient;
    private final GlobalUserService userService;
    private final MessageMapper messageMapper;

    private final long theMainBotId;
    private final GroupActor theMainBotGroupActor;

    public BindCommand(@Lazy VkChatClient vkChatClient,
                       @Lazy VkCommunityClient vkCommunityClient,
                       GlobalUserService userService,
                       MessageMapper messageMapper,
                       @Value("${vk.main-bot.id}") long theMainBotId,
                       @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor) {

        this.vkChatClient = vkChatClient;
        this.vkCommunityClient = vkCommunityClient;
        this.userService = userService;
        this.messageMapper = messageMapper;
        this.theMainBotId = theMainBotId;
        this.theMainBotGroupActor = theMainBotGroupActor;
    }

    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long originalPeerId = commandMessage.getCommandRoutingData().getOriginalEventPeerId();
        long fromId = commandMessage.getFromId();;

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",commandMessage);

        if(ChatUtils.isPersonalChat(originalPeerId)){
            sendMessage.setText(CANNOT_USE_THIS_COMMAND_IN_PERSONAL_DIALOGUE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        long targetChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        if(!vkCommunityClient.canTheMainBotWriteToUser(fromId)){
            sendMessage.setText(
                    "Для выполнения этой команды, %s(Вы) должны разрешить %s(Чат-менеджеру) отправлять вам личные сообщения. Для этого напишите любой текст в личные сообщения сообщества: %s."
                            .formatted(createMention(fromId), createMention(-theMainBotId), GroupUtils.createPrivateMessagesLink(theMainBotId))
            );
            vkChatClient.sendText(sendMessage);
            return;
        }
        userService.bindChatToUser(targetChatId, fromId);
        try{
            sendMessage.setResponsePeerId(fromId);
            sendMessage.setResponderBot(theMainBotGroupActor);
            sendMessage.setReplyToMessageId(false);
            sendMessage.setText("\uD83D\uDD17 Вы успешно привязали чат к своим личным сообщениям. " +
                    "Теперь вы можете писать команды и получать ответы на них прямо здесь. Команды продолжат выполняться в том чате."
            );
            vkChatClient.sendText(sendMessage);
        }catch (Exception e){
            log.error("chat {} error: user {} allowed personal messages, but I could not send it to him",targetChatId, fromId, e);
            sendMessage.setResponsePeerId(commandMessage.getCommandRoutingData().getResponsePeerId());
            sendMessage.setResponderBot(commandMessage.getCommandRoutingData().getResponderBot());

            sendMessage.setReplyToMessageId(true);
            sendMessage.setText(
                    "Вы успешно привязали чат к своим личным сообщениям, однако мне не удалось отправить вам личное сообщение. " +
                    "Убедитесь, что вы разрешили %s(Чат-менеджеру) отправлять себе личные сообщения.".formatted(createMention(-theMainBotId))
            );
            vkChatClient.sendText(sendMessage);
        }

    }
}
