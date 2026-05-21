package com.example.my_bot.service;

import com.example.my_bot.entity.MessageLogEntity;
import com.example.my_bot.repository.MessageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLogService{

    private final MessageLogRepository messageRepository;

    private final ConcurrentMap<Long, Set<MessageLogEntity>> temporaryMessagesCache = new ConcurrentHashMap<>();

    public void saveNewMessageLog(long chatId, long fromId, int conversationMessageId){
        MessageLogEntity newEntity = new MessageLogEntity(chatId, fromId, conversationMessageId, Instant.now(), false);
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

    @Scheduled(fixedRate = 100_000)
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
}
