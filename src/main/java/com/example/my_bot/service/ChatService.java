package com.example.my_bot.service;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.repository.ChatRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;

    public Optional<ChatEntity> getChatEntity(long chatId){
        return chatRepository.findById(chatId);
    }


    public ChatEntity createChatEntity(long chatId, @Nullable Character prefix){

        if(getChatEntity(chatId).isPresent()){
            throw new IllegalStateException("Chat with id %d already exists".formatted(chatId));
        }

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        if(prefix!=null){
            chat.setPrefix(prefix);
        }
        return chatRepository.save(chat);

    }











}
