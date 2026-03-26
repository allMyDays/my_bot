package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.repository.ChatRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatActionService {

    private final MemberService memberService;

    private final ChatService chatService;

    private static final long AUTO_SYNC_INTERVAL_MINUTES = 15;



    public void handleChatAction(long chatId, long fromId, ActionOneOf action){

        if(action==null){
            return;
        }
        MessageActionStatus type = action.getType();
        Long memberId = action.getMemberId();
        switch (type){
            case CHAT_INVITE_USER_BY_LINK:
                memberService.createNewMemberOrMarkAsPresent(chatId, fromId,null);
                break;
            case CHAT_INVITE_USER:
                Long invitedBy = fromId==memberId?null:fromId;  // самостоятельный возврат или приглашение
                memberService.createNewMemberOrMarkAsPresent(chatId, memberId,invitedBy);
                break;
            case CHAT_KICK_USER:
                MemberPresenceType presenceType = (fromId==memberId?SELF_LEAVE:KICKED);   // самостоятельный выход или исключение
                memberService.setPresenceTypeToUser(chatId, memberId, presenceType, true);
                break;


        }



        }


    public void checkLastChatSynchronizationAndExecute(long chatId) throws ClientException, ApiException {

        ChatDetailsDto chatDto = chatService.getCachedChatDetails(chatId, false);

        Optional<Instant> lastSync = chatDto.getOptionalLastSyncTime();

        if (lastSync.isEmpty()||
                Duration.between(lastSync.get(), Instant.now()).toMinutes() >= AUTO_SYNC_INTERVAL_MINUTES) {

            memberService.synchronizeChatMembers(chatId);
        }

    }









    }







