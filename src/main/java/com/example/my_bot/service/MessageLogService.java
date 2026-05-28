package com.example.my_bot.service;

import com.example.my_bot.dto.member.InactiveMemberResult;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.entity.MessageLogEntity;
import com.example.my_bot.exception.member.InactiveMembersIntervalOutOfBoundsException;
import com.example.my_bot.repository.MessageLogRepository;
import com.example.my_bot.vk.VkMessage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.utils.ChatUtils.extractConversationId;
import static com.example.my_bot.utils.ChatUtils.isPersonalChat;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLogService{

    private final MemberService memberService;
    private final MessageLogRepository messageRepository;
    private final ConcurrentMap<Long, Set<MessageLogEntity>> temporaryMessagesCache = new ConcurrentHashMap<>();

    private final static int INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC = 60;

    private final static int INACTIVE_MEMBERS_MAX_PERIOD_SEC = 315_360_000;
    private final static int INACTIVE_MEMBERS_MIN_PERIOD_SEC = INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC;

    public void saveNewMessageLog(@NonNull VkMessage message){
        if(isPersonalChat(message.getPeerId())) return;
        if(message.getAction()!=null) return;
        long chatId = extractConversationId(message.getPeerId());
        int symbolsQuantity = message.getText()!=null?message.getText().length():0;

        MessageLogEntity newEntity = new MessageLogEntity(chatId, message.getFromId(), message.getConversationMessageId(), Instant.now(), symbolsQuantity,false);
        temporaryMessagesCache.compute(chatId, (key, existingSet) -> {
            if(existingSet==null){
                existingSet = ConcurrentHashMap.newKeySet();
            }
            existingSet.add(newEntity);
            return existingSet;
        });
    }

    public Optional<Long> getMessageOwnerId(long chatId, int conversationMessageId){
        Set<MessageLogEntity> freshChatMessages = temporaryMessagesCache.get(chatId);
        MessageLogEntity found;
        if(freshChatMessages!=null){
                     found = freshChatMessages.stream()
                    .filter(c->c.getConversationMessageId()==conversationMessageId)
                    .findFirst()
                             .orElse(null);
            if(found!=null) return Optional.of(found.getFromId());
        }
        found = messageRepository.findByChatIdAndConversationMessageId(chatId, conversationMessageId)
                .orElse(null);
        if(found!=null) return Optional.of(found.getFromId());
        return  Optional.empty();
    }

    @Scheduled(fixedRate = INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC * 1_000)
    protected void loadMessagesIntoTheDatabase(){
        log.info("Starting log messages batch save");

        Set<MessageLogEntity> collectedMessages = new HashSet<>();

        for (Long chatId : temporaryMessagesCache.keySet()) {
            Set<MessageLogEntity> oldSet = temporaryMessagesCache.replace(chatId, ConcurrentHashMap.newKeySet());
            if(oldSet!=null){
                collectedMessages.addAll(oldSet);
            }
        }
        if (!collectedMessages.isEmpty()) {
            messageRepository.saveAll(collectedMessages);
            messageRepository.flush();
        }
    }

    public List<InactiveMemberResult> findCurrentInactiveChatMembers(long chatId, long timePeriodSec){

        if(timePeriodSec<INACTIVE_MEMBERS_MIN_PERIOD_SEC||timePeriodSec>INACTIVE_MEMBERS_MAX_PERIOD_SEC){
            throw new InactiveMembersIntervalOutOfBoundsException(INACTIVE_MEMBERS_MIN_PERIOD_SEC, INACTIVE_MEMBERS_MAX_PERIOD_SEC);
        }
        List<InactiveMemberResult> result = new ArrayList<>();

        Instant thresholdDate = Instant.now().minusSeconds(timePeriodSec);

        Set<Long> requiredMembers = memberService.getAllCurrentChatMemberWithFirstAppearanceBeforeThan(chatId, thresholdDate);

        Map<Long, MessageLogRepository.MemberLastMessageProjection> lastMessagesMap =
                messageRepository.findLastMessageOfRequiredMembers(chatId, requiredMembers).stream()
                        .collect(Collectors.toMap(m->m.getFromId(), Function.identity()));

        for(Long userId: requiredMembers){
            var lastMessage = lastMessagesMap.get(userId);
            if(lastMessage!=null&&lastMessage.getLastMessageAt().isAfter(thresholdDate)){
                // участник имеет свежее последнее сообщение
                continue;
            }
            InactiveMemberResult inactiveMemberResult = new InactiveMemberResult();
            inactiveMemberResult.setUserId(userId);
            inactiveMemberResult.setLastMessage(lastMessage==null?null:lastMessage.getLastMessageAt());

            result.add(inactiveMemberResult);
        }
        return result;
    }
}
