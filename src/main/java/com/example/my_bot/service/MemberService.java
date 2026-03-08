package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.ChatDetailsDto;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.enumeration.RedisKeyBuilder.STAFF;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    private final VkChatClient vkChatClient;

    private final MemberMapper memberMapper;

    private final MemberJsonMapper memberJsonMapper;

    private final RedisService redisService;

    private final ChatService chatService;

    private MemberService selfLink;

    private static final long AUTO_SYNC_INTERVAL_MINUTES = 15;

    private final static int STAFF_CACHE_TTL_SECONDS = 600;

    @Autowired
    @Lazy
    public void setSelfLink(MemberService selfLink) {
        this.selfLink = selfLink;
    }


   public List<MemberEntity> findByChatId(long chatId){
        return memberRepository.findByChatId(chatId);
    }

    private void save(List<MemberEntity> memberEntities){
       memberRepository.saveAll(memberEntities);

    }

    @Transactional
    public void synchronizeChatMembers(long chatId) throws ClientException, ApiException {


        List<ConversationMember> membersWhoAreInChat = vkChatClient.getAllConversationMembers(chatId);
        Set<Long> vkUserIds = membersWhoAreInChat.stream()
                .map(ConversationMember::getMemberId)
                .collect(Collectors.toSet());

        memberRepository.markMembersAsLeft(chatId, vkUserIds);


        Map<Long, MemberEntity> dbMembersMap =  memberRepository.findByChatIdAndUserIdIn(chatId, vkUserIds).stream()
                .collect(Collectors.toMap(MemberEntity::getUserId, Function.identity()));


        List<MemberEntity> newMembers = new ArrayList<>();
        for (ConversationMember vkMember : membersWhoAreInChat) {
            long userId = vkMember.getMemberId();
            MemberEntity entity = dbMembersMap.get(userId);

            if (entity == null) {
                entity = new MemberEntity();
                entity.setUserId(userId);
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

        if (!newMembers.isEmpty()) {
            memberRepository.saveAll(newMembers);
        }

        chatService.setLastSyncToNow(chatId);
        invalidateStaffMembersCache(chatId);

    }

    public void checkLastSyncAndPerform(long chatId) throws ClientException, ApiException {

        ChatDetailsDto chatDto = chatService.getCachedChatDetails(chatId, false);

        if (chatDto.getLastSyncTime()==null||
                Duration.between(chatDto.getLastSyncTime(), Instant.now()).toMinutes() >= AUTO_SYNC_INTERVAL_MINUTES) {

            selfLink.synchronizeChatMembers(chatId);
        }

    }

    public List<MemberWithRoleDto> getCachedMembersWithRole(long chatId){

       Map<String, String> hash = redisService.getHash(STAFF.buildKey(chatId));

       if(!hash.isEmpty()){
           return memberJsonMapper.fromJson(hash.values().stream().toList());
       }

       List<MemberWithRoleDto> memberDtoList = memberMapper.toMemberWithRoleDtoList(
               memberRepository.findMembersWithNotZeroRole(chatId));


        Map<String, String> mapToSave = new HashMap<>();
        for (MemberWithRoleDto dto : memberDtoList) {
            mapToSave.put(String.valueOf(dto.getUserId()), memberJsonMapper.toJson(dto));
        }
        redisService.setHash(STAFF.buildKey(chatId), mapToSave, STAFF_CACHE_TTL_SECONDS);

        return memberDtoList;

    }

    public int getCachedMemberRolePriority(long chatId, long userId){

       Optional<Integer> priorityOptional =  getCachedMembersWithRole(chatId).stream()
               .filter(m->m.getUserId()==userId)
               .map(MemberWithRoleDto::getRolePriority)
               .findFirst();

        return priorityOptional.orElseGet(MEMBER::getRolePriority);

    }

    private void invalidateStaffMembersCache(long chatId){
        redisService.delete(STAFF.buildKey(chatId));

    }

    public boolean updateRolePriorityForMembers(long chatId, int oldRolePriority, int newOldPriority){

        int changedRows = memberRepository.updateRolePriorityForMembers(chatId, oldRolePriority, newOldPriority);

        invalidateStaffMembersCache(chatId);

        return changedRows>0;

    }









}
