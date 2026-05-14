package com.example.my_bot.command.commands.settings;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.SwitchChatSettingResult.ON;

@Command(mainCommandName = "ответы",alternativeCommandNames = {"reply"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class MessageReplyingChangeCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private ChatService chatService;

    private VkChatClient vkChatClient;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setChatService(ChatService chatService, VkChatClient vkChatClient) {
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        SwitchChatSettingResult switchResult = chatService.switchMessageReplying(messageDto.getChatId());

        SendMessageDto sendMessage =  messageMapper.toSendMessageDto(
                "Теперь мне %s отвечать на ваши команды посредством пересыла вашего сообщения в чате."
                        .formatted((switchResult==ON?"можно":"нельзя")), messageDto
        );
        sendMessage.setReplyToMessageId(switchResult==ON);
        vkChatClient.sendText(sendMessage);

    }

}
