package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "кикновичков", alternativeCommandNames = {"kicknew"}, defaultRole = ADMINISTRATOR, eventable = true)
public class KickNewCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*3);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final ChatService chatService;

    private final int MAX_COMMAND_PERIOD_IN_SECONDS = 86_400;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        long peerId = messageDto.getPeerId();

        String[] args = messageDto.getFirstRowArguments();
        if(args.length<2){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId, true);
            return;
        }
        Optional<Long> optionalPeriod = TimeUtils.toSecondsFromString(args[0], args[1]);
        if(optionalPeriod.isEmpty()){
            vkChatClient.sendText(INVALID_TIME_PERIOD_MESSAGE,peerId, true);
            return;
        }long periodInSeconds = optionalPeriod.get();

        if(periodInSeconds>MAX_COMMAND_PERIOD_IN_SECONDS){
            vkChatClient.sendText(
                    "Максимальный период, за который можно исключить новичков — %s"
                            .formatted(TimeUtils.formatDurationFromSeconds(MAX_COMMAND_PERIOD_IN_SECONDS,false)),
                    peerId,
                    true);
            return;
        }

        DefaultRole requiredRole = MODERATOR;

        Instant kickAfter = Instant.now().minusSeconds(periodInSeconds);

        TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);

        String dateToShow = TimeUtils.getStringDateTimeWithTimeZone(kickAfter, chatTimeZone);
        Page<MemberEntity> allRequiredMembers = memberService.findNotKickedNewMembersWithRoleLessThan(chatId,kickAfter,requiredRole.getRolePriority(), 100);

        Set<Long> kickedCommunities = vkChatClient.kickManyChatMembers(chatId,
                allRequiredMembers.getContent().stream()
                        .filter(m->!m.isChatAdmin())
                        .map(MemberEntity::getUserId)
                        .toList());

        vkChatClient.sendText("✅Было исключено %d из %d новичков с ролью ниже чем «%s», впервые появившихся в чате после %s"
                .formatted(kickedCommunities.size(), allRequiredMembers.getTotalElements(), requiredRole.getRoleName(), dateToShow), messageDto.getPeerId(), true);





    }
}
