package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Sticker;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.List;

import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_IN_ADMIN_CHAT;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.BUSINESS_LOGIC_ERROR;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;
import static com.vk.api.sdk.objects.messages.MessageAttachmentType.STICKER;

@Slf4j
@Command(mainCommandName = "стикеринфа", alternativeCommandNames = {"stickerinfo"}, defaultRole = MEMBER, eventable = true, onlyForConversations = false, adminChatCommandExecutionMode = ONLY_IN_ADMIN_CHAT)
@RequiredArgsConstructor
public class StickerInfoCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(10,60*2);

    private VkChatClient vkChatClient;
    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException{

        List<ForeignMessage> fwd = commandMessage.getReplyOrFwdMessages();
        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        if(fwd.isEmpty()||fwd.get(0).getAttachments()==null||fwd.get(0).getAttachments().isEmpty()||fwd.get(0).getAttachments().get(0).getSticker()==null){
            sendMessage.setText("Чтобы узнать информацию о стикере, перешлите или ответьте на сообщение, в котором есть стикер.");
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }

        Sticker sicker = fwd.get(0).getAttachments().get(0).getSticker();
        sendMessage.setText("⚙ Стикер %d из набора %d".formatted(sicker.getStickerId(), sicker.getProductId()));

        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }
}
