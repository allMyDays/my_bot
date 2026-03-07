package com.example.my_bot.service;

import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.exception.ChatEntityNotFoundException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.mapper.json.ChatJsonMapper;
import com.example.my_bot.repository.ChatRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.enumeration.RedisKeyBuilder.CHAT;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final static int CHAT_CACHE_TTL_SECONDS = 600;

    private final RedisService redisService;

    private final ChatRepository chatRepository;

    private final ChatJsonMapper chatJsonMapper;

    private final ChatMapper chatMapper;


    public Optional<ChatEntity> getChatEntity(long chatId){
        return chatRepository.findById(chatId);
    }

    public ChatEntity findByChatIdOrThrow(long chatId){
        return getChatEntity(chatId).orElseThrow(()->
                new ChatEntityNotFoundException(chatId));

    }

    public ChatDetailsDto getCachedChatDetails(long chatId, boolean createIfAbsents){

        Optional<String> valueOptional = redisService.get(CHAT.buildKey(chatId));

        if(valueOptional.isPresent()){
            return chatJsonMapper.fromJson(valueOptional.get());
        }

        Optional<ChatEntity> chatOptional = getChatEntity(chatId);

        ChatEntity chat;

        if(chatOptional.isEmpty()){
            if(createIfAbsents){
                chat = createNewChat(chatId, null);
            } else throw new ChatEntityNotFoundException(chatId);

        }else{
            chat=chatOptional.get();
        }

        return updateChatCache(CHAT.buildKey(chatId), chat);

    }

    public void setChatPrefix(long chatId, char newPrefix){

        ChatEntity chat = findByChatIdOrThrow(chatId);

        chat.setPrefix(newPrefix);

        updateChatCache(CHAT.buildKey(chatId), chatRepository.save(chat));
    }

    public void disableChatPrefix(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);

        chat.setPrefix(null);

        updateChatCache(CHAT.buildKey(chatId), chatRepository.save(chat));
    }

    public void setLastSyncToNow(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);

        chat.setLastSyncTime(Instant.now());

        updateChatCache(CHAT.buildKey(chatId), chatRepository.save(chat));
    }

    private ChatDetailsDto updateChatCache(String redisKey, ChatEntity chat){

        ChatDetailsDto chatDto = chatMapper.toChatDetailsDto(chat);

        redisService.saveTemp(redisKey, chatJsonMapper.toJson(chatDto), CHAT_CACHE_TTL_SECONDS);

        return chatDto;

    }

    public ChatEntity createNewChat(long chatId, @Nullable Character prefix){

        if(getChatEntity(chatId).isPresent()){
            throw new IllegalStateException("Chat with id %d already exists".formatted(chatId));
        }

        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        chat.setPrefix(prefix==null?DEFAULT_CHAT_PREFIX:prefix);
        return chatRepository.save(chat);

    }











}
