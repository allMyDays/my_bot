package com.example.my_bot.command.commands.settings;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.DefaultRole.MEMBER;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "настройки", alternativeCommandNames = {"settings"}, defaultRole = MEMBER, eventable = true)
public class ChatSettingsShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(10,60*2);

    private VkChatClient vkChatClient;

    private final ChatService chatService;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    private final static String on = "включено";
    private final static String off = "выключено";



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        ChatDetailsDto details = chatService.getCachedChatDetails(messageDto.getChatId(), false);


        String sb = "⚙ Список настроек чата:\n\n" +
                "\n ❗ Префикс: " + details.getOptionalPrefix().map(a -> on).orElse(off) +
                "\n 🔕 Тихий запрет: " + (details.isSilentRestriction() ? on : off) +
                "\n ↪ Пересыл команд: " + (details.isMessageReplying()? on : off) +
                "\n \uD83C\uDF0D Таймзона: " + details.getTimeZoneType().getStringType() +
                "\n ⌚ Дефолтный срок бана: " + details.getOptionalBanPeriod().map(p -> TimeUtils.formatDurationFromSeconds(p, true)).orElse(off) +
                "\n \uD83D\uDD27 Авторазбан приглашенных: " + (details.isAutoUnban() ? on : off);

        sb+="\n\n\uD83D\uDDD3 Код чата: "+details.getChatCode();

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb,messageDto));

    }
}
