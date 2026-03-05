package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.mapper.MemberMapper;
import com.example.my_bot.mapper.json.MemberJsonMapper;
import com.example.my_bot.repository.MemberRepository;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.CHAT_CREATOR;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    private final VkChatClient vkChatClient;

    private final MemberMapper memberMapper;

    private final MemberJsonMapper memberJsonMapper;

    private final RedisService redisService;

    private final static int STAFF_CACHE_TTL_SECONDS = 600;


   public List<MemberEntity> findByChatId(long chatId){
        return memberRepository.findByChatId(chatId);
    }

    public void save(List<MemberEntity> memberEntities){
       memberRepository.saveAll(memberEntities);

    }

    @Transactional
    public void synchronizeChatMembers(long chatId) throws ClientException, ApiException {

        List<ConversationMember> vkMemberList = vkChatClient.getAllConversationMembers(chatId);

        Map<Long, MemberEntity> existing = findByChatId(chatId).stream()
                        .collect(Collectors.toMap(MemberEntity::getUserId, Function.identity()));

        Set<Long> currentVkIds = new HashSet<>();

        List<MemberEntity> newMembers = new ArrayList<>();

        for (ConversationMember vkMember : vkMemberList) {

            long id = vkMember.getMemberId();
            currentVkIds.add(id);

            MemberEntity entity = existing.get(id);

            if (entity == null) {
                entity = new MemberEntity();
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
        for (MemberEntity entity : existing.values()) {
            if (!currentVkIds.contains(entity.getUserId())) {
                entity.setInChat(false);
                entity.setChatAdmin(false);
            }
        }

        if (!newMembers.isEmpty()) {
              save(newMembers);
        }
    }

    public List<MemberWithRoleDto> getMembersWithRole(long chatId){

       String redisKey = "staff:"+chatId;

       Map<String, String> hash = redisService.getHash(redisKey);

       if(!hash.isEmpty()){
           return memberJsonMapper.fromJson(hash.values().stream().toList());
       }

       List<MemberWithRoleDto> memberDtoList = memberMapper.toMemberWithRoleDtoList(
               memberRepository.findMembersWithRole(chatId));


        Map<String, String> mapToSave = new HashMap<>();
        for (MemberWithRoleDto dto : memberDtoList) {
            mapToSave.put(String.valueOf(dto.getUserId()), memberJsonMapper.toJson(dto));
        }
        redisService.setHash(redisKey, mapToSave, STAFF_CACHE_TTL_SECONDS);

        return memberDtoList;

    }







}
