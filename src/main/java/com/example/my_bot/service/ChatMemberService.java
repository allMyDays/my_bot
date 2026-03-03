package com.example.my_bot.service;

import com.example.my_bot.entity.ChatMemberEntity;
import com.example.my_bot.repository.ChatMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMemberService {

    private final ChatMemberRepository memberRepository;


   public List<ChatMemberEntity> findByChatId(long chatId){
        return memberRepository.findByChatId(chatId);
    }

    public void save(List<ChatMemberEntity> memberEntities){
       memberRepository.saveAll(memberEntities);

    }




}
