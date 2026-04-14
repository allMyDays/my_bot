package com.example.my_bot.service.chat;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.ban.MemberBanStatus;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ActionOneOf;
import com.vk.api.sdk.objects.messages.MessageActionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.example.my_bot.enumeration.member.MemberPresenceType.*;
import static com.example.my_bot.utils.ChatUtils.createMention;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatActionService {

    private final MemberService memberService;

    private final ChatService chatService;

    private final BanService banService;

    private final VkChatClient vkChatClient;

    private static final long AUTO_SYNC_INTERVAL_MINUTES = 20;



    public void handleChatAction(long chatId, long fromId, ActionOneOf action){

        if(action==null){
            return;
        }
        MessageActionStatus type = action.getType();
        Long memberId = action.getMemberId();
        boolean hasBeenKicked;
        switch (type){
            case CHAT_INVITE_USER_BY_LINK->{
                hasBeenKicked = removeBannedMember(chatId, fromId);
                if(!hasBeenKicked){
                    memberService.createNewMemberOrMarkAsPresent(chatId, fromId,null);
                }
            }
            case CHAT_INVITE_USER->{
                hasBeenKicked =removeBannedMember(chatId, memberId);
                if(!hasBeenKicked){
                    Long invitedBy = fromId==memberId?null:fromId;  // самостоятельный возврат или приглашение
                    memberService.createNewMemberOrMarkAsPresent(chatId, memberId,invitedBy);
                }
            }
            case CHAT_KICK_USER->{
                MemberPresenceType presenceType = (fromId==memberId?SELF_LEAVE:KICKED);   // самостоятельный выход или исключение
                memberService.setPresenceTypeToMember(chatId, memberId, presenceType, true);
            }

           }

        }

    public void checkLastChatSynchronizationAndExecute(long chatId) throws ClientException, ApiException {

        ChatDetailsDto chatDto = chatService.getCachedChatDetails(chatId, true);

        Optional<Instant> lastSync = chatDto.getOptionalLastSyncTime();

        if (lastSync.isEmpty()||
                Duration.between(lastSync.get(), Instant.now()).toMinutes() >= AUTO_SYNC_INTERVAL_MINUTES) {

            memberService.synchronizeChatMembers(chatId);
        }

    }

    private boolean removeBannedMember(long chatId, long memberId){

        boolean successfulKick = false;
        MemberBanStatus banStatus = banService.getMemberBanStatus(chatId, memberId);
        if(banStatus.isBanned()){
            TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);
            Optional<Instant> bannedUntil = banStatus.getBannedUntil();
            String message = "%s(Этот пользователь) в бане до %s.".formatted(
                    createMention(memberId),
                    bannedUntil.map(instant -> TimeUtils.getStringDateTimeWithTimeZone(instant, chatTimeZone)).orElse("∞")
            );
            try {
                vkChatClient.kickOneChatMember(chatId, memberId);
                successfulKick = true;
            } catch (ClientException | ApiException e) {
                log.warn("chat {} error: cannot remove banned member {} that just has been linked. ",chatId, memberId, e);
                message +=" Однако возникла ошибка при попытке исключить этого пользователя: "+e.getMessage();
            }
            try {
                vkChatClient.sendText(message, ChatUtils.convertToPeerId(chatId), true);
            } catch (ClientException | ApiException e) {
                log.warn("chat {} error: cannot send info about banned member {} that just has been linked. ",chatId, memberId, e);
            }
        }
        return successfulKick;
    }
}







