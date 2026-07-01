package com.example.my_bot.command.commands.settings;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.client.VkCommunityClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.utils.GroupUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.CommandExecutionStatus.VK_API_ERROR;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.createMention;

@Command(mainCommandName = "лсответы",alternativeCommandNames = {"dmresponses"}, defaultRole = MODERATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class DMResponsesSwitchCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private final MemberService memberService;
    private final VkChatClient vkChatClient;
    private final VkCommunityClient vkCommunityClient;
    private final MessageMapper messageMapper;

    private final long theMainBotId;
    private final GroupActor theMainBotGroupActor;

    public DMResponsesSwitchCommand(
            MemberService memberService,
            @Lazy VkChatClient vkChatClient,
            @Lazy VkCommunityClient vkCommunityClient,
            MessageMapper messageMapper,
            @Value("${vk.main-bot.id}") long theMainBotId,
            @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor){

        this.memberService = memberService;
        this.vkChatClient = vkChatClient;
        this.vkCommunityClient = vkCommunityClient;
        this.messageMapper = messageMapper;
        this.theMainBotId = theMainBotId;
        this.theMainBotGroupActor = theMainBotGroupActor;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException{

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        long fromId = commandMessage.getFromId();

        SendMessageDto sendMessage =  messageMapper.toSendMessageDto(commandMessage);

        boolean isEnabled = memberService.isDmResponsesEnabled(chatId, fromId);

        if(!isEnabled&&!vkCommunityClient.canTheMainBotWriteToUser(fromId)){
            sendMessage.setText(
                    "Если хотите получать ответы на команды в личные сообщения, %s(Вы) должны разрешить %s(Чат-менеджеру) отправлять вам личные сообщения. Для этого напишите любой текст в личные сообщения сообщества: %s."
                            .formatted(createMention(fromId), createMention(-theMainBotId), GroupUtils.createPrivateMessagesLink(theMainBotId))
            );
            vkChatClient.sendText(sendMessage);
            return VK_API_ERROR;
        }
        memberService.setDmResponsesSetting(chatId, fromId, !isEnabled);

        sendMessage.setText(
                "Теперь мне %s присылать ответы на ваши команды вам в личные сообщения, а не в эту многопользовательскую беседу."
                        .formatted(isEnabled?"нельзя":"можно")
        );

        if(!isEnabled){
            // настройка включена
            sendMessage.setResponsePeerId(fromId);
            sendMessage.setResponderBot(theMainBotGroupActor);
            sendMessage.setReplyToMessageId(false);
        }
        else{
            sendMessage.setResponsePeerId(commandMessage.getCommandRoutingData().getOriginalEventPeerId());
            sendMessage.setResponderBot(commandMessage.getCommandRoutingData().getReceivedEventBot());
        }

        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }
}
