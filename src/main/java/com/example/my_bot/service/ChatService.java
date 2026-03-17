package com.example.my_bot.service;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.exception.chat.ChatEntityAlreadyExistsException;
import com.example.my_bot.exception.chat.ChatEntityNotFoundException;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.repository.ChatRepository;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final CaffeineCacheManager cacheManager;

    private final ChatRepository chatRepository;

    private final ChatMapper chatMapper;

    private final static Set<Character> FORBIDDEN_PREFIXES = Set.of('*','@');

    private ChatService selfLink;

    @Autowired
    @Lazy
    public void setSelfLink(ChatService selfLink) {
        this.selfLink = selfLink;
    }

    private Optional<ChatEntity> getChatEntity(long chatId){
        return chatRepository.findById(chatId);
    }

    private ChatEntity findByChatIdOrThrow(long chatId){
        return getChatEntity(chatId).orElseThrow(()->
                new ChatEntityNotFoundException(chatId));

    }

    public ChatDetailsDto getCachedChatDetails(long chatId, boolean createIfAbsent) {
        return cacheManager.getChatDetailsCache().get(chatId, id -> {
            Optional<ChatEntity> optionalChat = getChatEntity(id);
            if (optionalChat.isPresent()) {
                return chatMapper.toChatDetailsDto(optionalChat.get());
            }
            if (createIfAbsent) {
                ChatEntity newEntity = selfLink.createNewChat(id, null);
                return chatMapper.toChatDetailsDto(newEntity);
            } else {
                throw new ChatEntityNotFoundException(id);
            }
        });
    }
    @Transactional
    public void setChatPrefix(long chatId, char newPrefix){

        if(FORBIDDEN_PREFIXES.contains(newPrefix)){
            throw new ForbiddenPrefixException(newPrefix);
        }
        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setPrefix(newPrefix);
        putChatToCache(chatId, chatRepository.save(chat));
    }
    @Transactional
    public void disableChatPrefix(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setPrefix(null);
        putChatToCache(chatId, chatRepository.save(chat));
    }

    public Optional<Character> getChatPrefix(long chatId){
       return getCachedChatDetails(chatId, false).getOptionalPrefix();

    }
    @Transactional
    public void setLastSyncToNow(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setLastSyncTime(Instant.now());

        putChatToCache(chatId, chatRepository.save(chat));
    }
    @Transactional
    public ChatEntity createNewChat(long chatId, @Nullable Character prefix){

        if(getChatEntity(chatId).isPresent()){
            throw new ChatEntityAlreadyExistsException(chatId);
        }
        if (prefix != null && FORBIDDEN_PREFIXES.contains(prefix)) {
            throw new ForbiddenPrefixException(prefix);
        }

        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        chat.setPrefix(prefix==null?DEFAULT_CHAT_PREFIX:prefix);
        return chatRepository.save(chat);

    }

    private ChatDetailsDto putChatToCache(long chatId, @NonNull ChatEntity chat){

        ChatDetailsDto chatDto = chatMapper.toChatDetailsDto(chat);

        cacheManager.getChatDetailsCache().put(chatId, chatDto);

        return chatDto;

    }


}
