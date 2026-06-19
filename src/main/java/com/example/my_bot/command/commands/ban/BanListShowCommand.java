package com.example.my_bot.command.commands.ban;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;

import java.util.Map;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.*;

@Command(mainCommandName = "банлист", alternativeCommandNames = {"banlist"}, defaultRole = MODERATOR, eventable = true)
public class BanListShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(7,60*2);

    private final VkChatClient vkChatClient;
    private final MessageMapper messageMapper;
    private final GlobalUserService globalUserService;
    private final BanService banService;
    private final ChatService chatService;

    private final static String PERMANENT_ARGUMENT = "вечные";
    private final static int MEMBERS_LIMIT_AT_ONE_USAGE = 500;

    public BanListShowCommand(@Lazy VkChatClient vkChatClient, MessageMapper messageMapper, GlobalUserService globalUserService, BanService banService, ChatService chatService) {
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
        this.messageMapper = messageMapper;
        this.globalUserService = globalUserService;
        this.banService = banService;
    }

    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();
        TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);

        StringBuilder sb = new StringBuilder();

        boolean toShowPermanent = args.length>0&&args[0].equalsIgnoreCase(PERMANENT_ARGUMENT);


        Page<BanEntity> bans = toShowPermanent
                ? banService.getAllChatPermanentBans(chatId, MEMBERS_LIMIT_AT_ONE_USAGE)
                : banService.getAllChatTemporaryBans(chatId, MEMBERS_LIMIT_AT_ONE_USAGE);

        Map<Long, String> memberNamesMap = globalUserService.getUserFullNamesInRequiredCase(
                bans.stream()
                        .map(BanEntity::getMemberId)
                        .collect(Collectors.toSet()),
                NameCase.NOMINATIVE
        );

        sb.append("В чате [%d] пользователей находится %s бане:\n\n".formatted(bans.getTotalElements(),toShowPermanent?"в вечном":"во временном"));

        for(BanEntity banEntity:bans){
            sb.append("%s(%s) — %s"
                    .formatted(
                            createMention(banEntity.getMemberId()),
                            memberNamesMap.get(banEntity.getMemberId()),
                            banEntity.getBannedUntil()==null?"∞":"до "+getFormattedStringDateTime(banEntity.getBannedUntil(),chatTimeZone)
                    )
            );
            sb.append("\n");
        }
        if(bans.getContent().size()<bans.getTotalElements()){
            sb.append("\nБыло показано %d пользователей из %d."
                    .formatted(bans.getContent().size(), bans.getTotalElements())
            );
        }
        if(!toShowPermanent){
            if(!bans.getContent().isEmpty()){
                sb.append("\nДаты окончания блокировок указаны по ").append(chatTimeZone.getStringType());
            }
            sb.append("\nЧтобы посмотреть пользователей в вечном бане, добавьте аргумент «%s».".formatted(PERMANENT_ARGUMENT));
        }
        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),commandMessage));

    }

}
