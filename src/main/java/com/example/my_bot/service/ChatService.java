package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.exception.ChatEntityNotFoundException;
import com.example.my_bot.repository.ChatRepository;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final static char DEFAULT_CHAT_PREFIX = '!';

    private final RedisService redisService;

    private final ChatRepository chatRepository;

    private VkChatClient vkChatClient;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    public static char getDefaultChatPrefix(){
        return DEFAULT_CHAT_PREFIX;
    }

    public Optional<ChatEntity> getChatEntity(long chatId){
        return chatRepository.findById(chatId);
    }

    public char getCachedChatPrefix(long chatId, boolean createAbsentChat){

        String redisKey = "prefix:"+chatId;

        Optional<String> prefixOptional = redisService.get(redisKey);

        if(prefixOptional.isPresent()) {
            String prefix = prefixOptional.get().trim();
            if (prefix.length() != 1) {
                log.warn("could not get cached prefix because its length wasn't 1: {}", prefix);
                redisService.delete(redisKey);
            } else return prefix.charAt(0);
        }

        ChatEntity chat;
        Optional<ChatEntity> optionalChat = getChatEntity(chatId);
        if(optionalChat.isEmpty()){
            if(createAbsentChat){
                chat = createNewChat(chatId, null);
            } else throw new ChatEntityNotFoundException(chatId);
        } else{
            chat = optionalChat.get();
        }
        redisService.saveTemp(redisKey, String.valueOf(chat.getPrefix()), 60 * 7);
        return chat.getPrefix();

    }


    public ChatEntity createNewChat(long chatId, @Nullable Character prefix){

        if(getChatEntity(chatId).isPresent()){
            throw new IllegalStateException("Chat with id %d already exists".formatted(chatId));
        }

        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        chat.setPrefix(prefix==null?getDefaultChatPrefix():prefix);
        chat = chatRepository.save(chat);
        try {
            vkChatClient.synchronizeChatMembers(chatId);
        } catch (ClientException | ApiException  e) {
            log.warn("error synchronizing members in new chat with id {}:",chat, e);
        }

        return chat;

    }











}
