package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.entity.ChatMemberEntity;
import com.example.my_bot.repository.ChatMemberRepository;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import com.vk.api.sdk.objects.messages.responses.GetConversationMembersResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.CHAT_CREATOR;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.VkChatUtils.extractPeerId;

@Service
@RequiredArgsConstructor
public class ChatMemberService {

    private final ChatMemberRepository memberRepository;

    private final VkChatClient vkChatClient;


   public List<ChatMemberEntity> findByChatId(long chatId){
        return memberRepository.findByChatId(chatId);
    }

    public void save(List<ChatMemberEntity> memberEntities){
       memberRepository.saveAll(memberEntities);

    }

    @Transactional
    public void synchronizeChatMembers(long chatId) throws ClientException, ApiException {

        List<ConversationMember> vkMemberList = vkChatClient.getAllConversationMembers(chatId);

        Map<Long, ChatMemberEntity> existing = findByChatId(chatId).stream()
                        .collect(Collectors.toMap(ChatMemberEntity::getUserId, Function.identity()));

        Set<Long> currentVkIds = new HashSet<>();

        List<ChatMemberEntity> newMembers = new ArrayList<>();

        for (ConversationMember vkMember : vkMemberList) {

            long id = vkMember.getMemberId();
            currentVkIds.add(id);

            ChatMemberEntity entity = existing.get(id);

            if (entity == null) {
                entity = new ChatMemberEntity();
                entity.setUserId(id);
                entity.setChatId(chatId);
                newMembers.add(entity);
            }

            entity.setInvitedById(vkMember.getInvitedBy());
            entity.setInChat(true);

            if (Boolean.TRUE.equals(vkMember.getIsOwner())) {
                entity.setRolePriority(CHAT_CREATOR.getRolePriority());
                entity.setChatAdmin(true);
            } else if (Boolean.TRUE.equals(vkMember.getIsAdmin())) {
                entity.setRolePriority(SENIOR_ADMINISTRATOR.getRolePriority());
                entity.setChatAdmin(true);
            } else {
                entity.setChatAdmin(false);
            }
        }

        // помечаю вышедших
        for (ChatMemberEntity entity : existing.values()) {
            if (!currentVkIds.contains(entity.getUserId())) {
                entity.setInChat(false);
                entity.setChatAdmin(false);
            }
        }

        if (!newMembers.isEmpty()) {
              save(newMembers);
        }
    }




}
