package com.example.my_bot.service.chat;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.ban.UnbanCommand;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.ban.MemberBanStatus;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.TextUtils;
import com.example.my_bot.utils.TimeUtils;
import com.example.my_bot.vk.VkAction;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.example.my_bot.enumeration.member.MemberPresenceType.*;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatActionService {

    private final MemberService memberService;

    private final ChatService chatService;

    private final BanService banService;

    private final VkChatClient vkChatClient;

    private final CommandAccessService commandAccessService;

    private final MessageMapper messageMapper;

    private static final long AUTO_SYNC_INTERVAL_MINUTES = 60;



    public void handleChatAction(long chatId, long fromId, VkAction action){

        if(action==null){
            return;
        }
        VkActionType type = action.getType();
        Long memberId = action.getMemberId();
        boolean hasBeenKicked;

        switch (type){
            case CHAT_INVITE_USER_BY_LINK->{
                hasBeenKicked = handleBannedMemberOnJoin(chatId, fromId, fromId);
                if(!hasBeenKicked){
                    memberService.createNewMemberOrMarkAsPresent(chatId, fromId,null);
                }
            }
            case CHAT_INVITE_USER->{
                hasBeenKicked = handleBannedMemberOnJoin(chatId, fromId, memberId);
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

    public void checkLastChatSynchronizationAndExecute(long chatId) {

        ChatDetailsDto chatDto = chatService.getCachedChatDetails(chatId, true);

        Optional<Instant> lastSync = chatDto.getOptionalLastSyncTime();

        if(lastSync.isEmpty()||Duration.between(lastSync.get(),Instant.now()).toMinutes()>=AUTO_SYNC_INTERVAL_MINUTES){
            try {
                memberService.synchronizeChatMembers(chatId);
            }catch (Exception e){
                log.warn("chat {} error while auto synchronization",chatDto,e);
                chatService.setLastSyncToNow(chatId);
            }
        }
    }

    /**
     @return true, если пользователь был исключён по бизнес-логике
     */
    private boolean handleBannedMemberOnJoin(long chatId, long fromId, long memberId){

        boolean hasBeenKicked = false;
        boolean isKickRequired = true;

        MemberBanStatus banStatus = banService.getMemberBanStatus(chatId, memberId);
        if(!banStatus.isBanned()){
            return false;
        }
            TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);
            Optional<Instant> bannedUntil = banStatus.getBannedUntil();
            String message = "%s(Этот пользователь) в бане до %s.".formatted(
                    createMention(memberId),
                    bannedUntil.map(instant -> TimeUtils.getStringDateTimeWithTimeZone(instant, chatTimeZone)).orElse("∞")
            );

            if(chatService.isAutoUnban(chatId)&&fromId!=memberId){
                String unbanCommandName = UnbanCommand.class.getAnnotation(Command.class).mainCommandName();
                int callerRole = memberService.getMemberRolePriority(chatId, fromId);
                boolean canUnban = commandAccessService.checkCommandAuthorization(chatId, unbanCommandName,callerRole, fromId);
                if(canUnban){
                    banService.deleteMemberBan(chatId, memberId);
                    message+="\nНо бан был автоматически снят потому, что пользователя пригласил участник с правом на использование команды «%s»."
                            .formatted(unbanCommandName);
                    isKickRequired = false;

                }
            }
            if(isKickRequired){
                try {
                    vkChatClient.kickOneChatMember(chatId, memberId);
                    hasBeenKicked = true;
                } catch (ClientException | ApiException e) {
                    log.warn("chat {} error: cannot remove banned member {} that just has been linked. ",chatId, memberId, e);
                    message +=" Однако возникла ошибка при попытке исключить этого пользователя: "+e.getMessage();
                }
            }
            try {
                vkChatClient.sendText(messageMapper.toSendMessageDto(message,ChatUtils.convertToPeerId(chatId)));
            } catch (ClientException | ApiException e) {
                log.warn("chat {} error: cannot send info about banned member {} that just has been linked. ",chatId, memberId, e);
            }
        return hasBeenKicked;
    }
}







