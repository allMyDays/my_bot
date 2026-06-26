package com.example.my_bot.service.chat;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.ban.UnbanCommand;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.ban.MemberBanStatus;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.CommandAccessService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.vk.mapping.action.VkAction;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.example.my_bot.enumeration.member.MemberPresenceType.*;
import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.getFormattedStringDateTimeWithTimeZone;

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
    private final GlobalUserService globalUserService;


    private static final long AUTO_SYNC_INTERVAL_MINUTES = 90;



    public void handleChatAction(@NonNull CommandRoutingData commandRoutingData, long fromId, VkAction action){

        Long dataBaseChatId = commandRoutingData.getDataBaseChatId();
        if(dataBaseChatId==null||action==null){
            return;
        }
        VkActionType type = action.getType();
        Long memberId = action.getMemberId();
        boolean hasBeenKicked;

        switch (type){
            case CHAT_INVITE_USER_BY_LINK->{
                hasBeenKicked = handleBannedMemberOnJoin(commandRoutingData, fromId, fromId);
                if(!hasBeenKicked){
                    memberService.createNewMemberOrMarkAsPresent(dataBaseChatId, fromId,null);
                }
            }
            case CHAT_INVITE_USER->{
                hasBeenKicked = handleBannedMemberOnJoin(commandRoutingData, fromId, memberId);
                if(!hasBeenKicked){
                    Long invitedBy = fromId==memberId?null:fromId;  // самостоятельный возврат или приглашение
                    memberService.createNewMemberOrMarkAsPresent(dataBaseChatId, memberId,invitedBy);
                }
            }
            case CHAT_KICK_USER->{
                MemberPresenceType presenceType = (fromId==memberId?SELF_LEAVE:KICKED);   // самостоятельный выход или исключение
                memberService.setPresenceTypeToMember(dataBaseChatId, memberId, presenceType, true);
            }
            case CHAT_TITLE_UPDATE -> {
                chatService.setChatTitle(dataBaseChatId, action.getText());
            }

        }
    }

    public void checkLastChatSynchronizationAndExecute(@NonNull CommandRoutingData routingData) throws ClientException {
        long dataBaseChatId = routingData.getDataBaseChatId();

        ChatDetailsDto chatDto = chatService.getCachedChatDetails(dataBaseChatId, true);

        Optional<Instant> lastSync = chatDto.getOptionalLastSyncTime();

        if(lastSync.isEmpty()||Duration.between(lastSync.get(),Instant.now()).toMinutes()>=AUTO_SYNC_INTERVAL_MINUTES){

            Optional<String> chatTitle = vkChatClient.getChatTitle(routingData.getVkApiChatId(), routingData.getExecutorBot());
            chatTitle.ifPresent(title-> chatService.setChatTitle(routingData.getDataBaseChatId(), title));

            try {
                memberService.synchronizeChatMembers(routingData);
            }catch (Exception e){
                log.warn("chat {} error while auto synchronization",chatDto,e);
                chatService.setLastSyncToNow(dataBaseChatId);
            }
        }
    }

    /**
     @return true, если пользователь был исключён по бизнес-логике
     */
    private boolean handleBannedMemberOnJoin(@NonNull CommandRoutingData commandRoutingData, long fromId, long memberId){

        boolean hasBeenKicked = false;
        boolean isKickRequired = true;
        long dataBaseChatId = commandRoutingData.getDataBaseChatId();

        MemberBanStatus banStatus = banService.getMemberBanStatus(dataBaseChatId, memberId);
        if(!banStatus.isBanned()){
            return false;
        }
            TimeZoneType chatTimeZone = chatService.getChatTimeZone(dataBaseChatId);
            Optional<Instant> bannedUntil = banStatus.getOptionalBannedUntil();
            String message = "%s(%s) в %s.".formatted(
                    createMention(memberId),
                    globalUserService.getUserFullNameInRequiredCase(memberId, NameCase.NOMINATIVE),
                    bannedUntil.map(until -> "бане до "+getFormattedStringDateTimeWithTimeZone(until, chatTimeZone))
                            .orElse("вечном бане")
            );

            if(chatService.isAutoUnban(dataBaseChatId)&&fromId!=memberId){
                String unbanCommandName = UnbanCommand.class.getAnnotation(Command.class).mainCommandName();
                int callerRole = memberService.getMemberRolePriority(dataBaseChatId, fromId);
                boolean canUnban = commandAccessService.checkCommandAuthorization(dataBaseChatId, unbanCommandName,callerRole, fromId);
                if(canUnban){
                    banService.deleteMemberBan(dataBaseChatId, memberId);
                    message+="\nНо бан был автоматически снят потому, что пользователя пригласил участник с правом на использование команды «%s»."
                            .formatted(unbanCommandName);
                    isKickRequired = false;

                }
            }
            if(isKickRequired){
                try {
                    vkChatClient.kickOneChatMember(commandRoutingData, memberId);
                    hasBeenKicked = true;
                } catch (ClientException | ApiException e) {
                    log.warn("chat {} error: cannot remove banned member {} that just has been linked. ",dataBaseChatId, memberId, e);
                    message +=" Однако возникла ошибка при попытке исключить этого пользователя: "+e.getMessage();
                }
            }
            try {
                vkChatClient.sendText(messageMapper.toSendMessageDto(message, convertToPeerId(commandRoutingData.getVkApiChatId()),dataBaseChatId, commandRoutingData.getExecutorBot()));
            } catch (ClientException | ApiException e) {
                log.warn("chat {} error: cannot send info about banned member {} that just has been linked. ",dataBaseChatId, memberId, e);
            }
        return hasBeenKicked;
    }
}







