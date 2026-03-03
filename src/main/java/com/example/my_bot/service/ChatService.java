package com.example.my_bot.service;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.ChatMemberEntity;
import com.example.my_bot.repository.ChatRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.util.List;
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
        chat.setChatId(chatId);
        if(prefix!=null){
            chat.setPrefix(prefix);
        }
        return chatRepository.save(chat);

    }











}
