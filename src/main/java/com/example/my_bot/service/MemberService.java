package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.exception.member.MemberAlreadyHasThisRoleException;
import com.example.my_bot.exception.member.UserNeverBeenInChatException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.MemberMapper;
import com.example.my_bot.repository.MemberRepository;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.text.html.parser.Entity;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;

@Service
@Slf4j
public class MemberService {

    private final MemberRepository memberRepository;

    private final VkChatClient vkChatClient;

    private final MemberMapper memberMapper;

    private final ChatService chatService;

    private final CaffeineCacheManager cacheManager;

    private RoleService roleService;

    private final long groupId;

    public MemberService(
            MemberRepository memberRepository,
            VkChatClient vkChatClient,
            MemberMapper memberMapper,
            ChatService chatService,
            CaffeineCacheManager cacheManager,
            @Value("${vk.group.id}") long groupId) {
        this.memberRepository = memberRepository;
        this.vkChatClient = vkChatClient;
        this.memberMapper = memberMapper;
        this.chatService = chatService;
        this.cacheManager = cacheManager;
        this.groupId = groupId;
    }


    @Autowired
    @Lazy
    public void setRoleService(RoleService roleService) {
        this.roleService = roleService;
    }


    @Transactional
    public void synchronizeChatMembers(long chatId) throws ClientException, ApiException {


        List<ConversationMember> currentChatMembers = vkChatClient.getAllConversationMembers(chatId);
        Set<Long> vkUserIds = currentChatMembers.stream()
                .map(ConversationMember::getMemberId)
                .collect(Collectors.toSet());

        memberRepository.setUnknownLeaveAndChatAdminFalseForMembersNotInList(chatId, vkUserIds);


        Map<Long, MemberEntity> currentChatMemberMap =  memberRepository.findByChatIdAndUserIdIn(chatId, vkUserIds).stream()
                .collect(Collectors.toMap(MemberEntity::getUserId, Function.identity()));


        List<MemberEntity> newMembers = new ArrayList<>();
        for (ConversationMember vkMember : currentChatMembers) {
            long memberId = vkMember.getMemberId();
            MemberEntity entity = currentChatMemberMap.get(memberId);

            if (entity == null) {
                entity = new MemberEntity();
                entity.setUserId(memberId);
                entity.setChatId(chatId);
                entity.setFirstAppearance(Instant.ofEpochSecond(vkMember.getJoinDate()));
                newMembers.add(entity);
                entity.setInvitedById(vkMember.getInvitedBy());
            }

            entity.setPresenceType(IN_CHAT);
            if (Boolean.TRUE.equals(vkMember.getIsOwner())) {
                entity.setRolePriority(CHAT_CREATOR.getRolePriority());
                entity.setChatAdmin(true);
            } else if (Boolean.TRUE.equals(vkMember.getIsAdmin())) {
                int requiredRoleToAssign = SENIOR_ADMINISTRATOR.getRolePriority();
                if(entity.getRolePriority()<requiredRoleToAssign){
                    entity.setRolePriority(requiredRoleToAssign);
                }
                entity.setChatAdmin(true);
            } else {
                entity.setChatAdmin(false);
            } if(memberId==(groupId*-1)){
                entity.setRolePriority(CHAT_CREATOR.getRolePriority()*10);
                // даю боту роль выше чем у создателя, чтобы его никто не мог наказывать
            }

        }

        if (!newMembers.isEmpty()) {
            memberRepository.saveAll(newMembers);
        }

        chatService.setLastSyncToNow(chatId);
        invalidateMemberCache(chatId);

    }

    public int getMemberRolePriority(long chatId, long userId){
        Optional<MemberDto> member = getCachedMemberInfo(chatId, userId);
        return member.map(MemberDto::getRolePriority).orElse(MEMBER.getRolePriority());

    }

    @Transactional
    public void reAssignRequiredMembersMassively(long chatId, int oldRolePriority, int newRolePriority){

        if(roleService.getRoleByPriority(chatId,newRolePriority).isEmpty()){
            throw new RoleNotFoundException();
        }

        List<Long> memberIds = memberRepository.updateMembersRoleAndReturnIds(chatId, oldRolePriority, newRolePriority);

        changeCachedMembersRole(chatId, memberIds, newRolePriority);

    }

    @Transactional
    public void createNewMemberOrMarkAsPresent(long chatId, long userId, Long invitedById) {
        MemberEntity member = memberRepository.findByChatIdAndUserId(chatId, userId).orElse(null);
        if (member!=null) {
            member.setPresenceType(IN_CHAT);
            member.setChatAdmin(false);
        } else {
           member = memberRepository.save(new MemberEntity(chatId, userId, MEMBER.getRolePriority(), false, IN_CHAT, invitedById, Instant.now()));
        } putMemberToCache(chatId, member);
    }

    @Transactional
    public void setPresenceTypeToMember(long chatId, long userId, @NonNull MemberPresenceType presenceType, boolean createIfAbsent){
        MemberEntity member = memberRepository.findByChatIdAndUserId(chatId, userId).orElse(null);
        if(member==null){
            if(createIfAbsent){
                member =memberRepository.save(new MemberEntity(chatId, userId, MEMBER.getRolePriority(), false, presenceType, null, Instant.now()));
                putMemberToCache(chatId, member);
                return;
            }throw new UserNeverBeenInChatException(userId);
        }
        if(member.getPresenceType()!=presenceType){
            member.setPresenceType(presenceType);
            putMemberToCache(chatId, member);
        }

    }

    @Transactional
    public void setPresenceTypeToMembers(long chatId, @NonNull Set<Long> userIds, @NonNull MemberPresenceType presenceType, boolean createIfAbsent) {
        if (userIds.isEmpty()) {
            return;
        }

        List<MemberEntity> existingMembers = memberRepository.findByChatIdAndUserIdIn(chatId, userIds);
        Map<Long, MemberEntity> existingMap = existingMembers.stream()
                .collect(Collectors.toMap(MemberEntity::getUserId, Function.identity()));

        List<MemberEntity> toSave = new ArrayList<>();
        List<MemberEntity> toPutInCache = new ArrayList<>();
        List<Long> missingUserIds = new ArrayList<>();

        for (Long userId : userIds) {
            MemberEntity member = existingMap.get(userId);
            if (member != null) {
                if (member.getPresenceType() != presenceType) {
                    member.setPresenceType(presenceType);
                    toPutInCache.add(member);
                }
            } else if (createIfAbsent) {
                member = new MemberEntity(chatId, userId, MEMBER.getRolePriority(), false, presenceType, null, Instant.now());
                toSave.add(member);
                toPutInCache.add(member);
            } else {
                missingUserIds.add(userId);
            }
        }

        if (!missingUserIds.isEmpty()) {
            throw new UserNeverBeenInChatException(missingUserIds);
        } if (!toSave.isEmpty()) {
            memberRepository.saveAll(toSave);
        }

        if (!toPutInCache.isEmpty()) {
            memberRepository.flush();
            putMembersToCache(chatId, toPutInCache);
        }
    }

    public Page<MemberEntity> getLeftButNotKickedMembersWithRoleLessThan(long chatId, int rolePriority, int limit){
        return memberRepository.findLeftButNotKickedMembersWithRoleLessThan(chatId, rolePriority, PageRequest.of(0, limit));

    }
    public Page<MemberEntity> getNotKickedCommunitiesWithRoleLessThan(long chatId, int rolePriority, int limit){
        return memberRepository.findNotKickedCommunitiesWithRoleLessThan(chatId, rolePriority, PageRequest.of(0, limit));

    }
    public Page<MemberEntity> getNotKickedMembersInvitedByAndWithRoleLessThan(long chatId, long inviter, int rolePriority, int limit){
        return memberRepository.findNotKickedMembersInvitedByAndWithRoleLessThan(chatId,inviter, rolePriority, PageRequest.of(0, limit));

    }

    public Page<MemberEntity> findNotKickedNewMembersWithRoleLessThan(long chatId, Instant after , int rolePriority, int limit){
        return memberRepository.findNotKickedNewMembersWithRoleLessThan(chatId,after,rolePriority, PageRequest.of(0, limit));
    }



    @Transactional
    public RoleDto removePositiveRoleFromExitedMembers(long chatId, long fromId){    // возвращает роль человека, который вызвал метод

        RoleDto callerRole = roleService.getRoleByPriority(chatId, getMemberRolePriority(chatId, fromId))
                .orElseThrow(RoleNotFoundException::new);

        if(callerRole.getRolePriority()<= MEMBER.getRolePriority()) return callerRole; // у человека роль участника или ниже, значит он никого не снимет

        List<Long> memberIds = memberRepository.removePositiveRoleFromExitedMembersAndReturnIds(chatId, callerRole.getRolePriority());
        changeCachedMembersRole(chatId, memberIds,MEMBER.getRolePriority());
        return callerRole;
    }

    
    @Transactional
    public AssignMemberResult assignNewRoleToMember(long chatId, long userToAssign, int newRolePriority, long fromId){

        if(userToAssign==fromId){
            throw new CannotApplyThisCommandToYourselfException();
        }
        MemberEntity memberToAssign = memberRepository.findByChatIdAndUserId(chatId, userToAssign)
                .orElseThrow(()->new UserNeverBeenInChatException(userToAssign));

        RoleDto roleToAssign = roleService.getRoleByPriority(chatId, newRolePriority)
                .orElseThrow(RoleNotFoundException::new);

        if(memberToAssign.getRolePriority()==newRolePriority){
            throw new MemberAlreadyHasThisRoleException(userToAssign);
        }

        checkMemberInteractionAbility(chatId, fromId, userToAssign);

        roleService.checkRoleInteractionAbility(newRolePriority, getMemberRolePriority(chatId, fromId));


        RoleDto roleToChange = roleService.getRoleByPriority(chatId, memberToAssign.getRolePriority())
                .orElseThrow(RoleNotFoundException::new);

        memberToAssign.setRolePriority(newRolePriority);

        putMemberToCache(chatId,memberRepository.save(memberToAssign));

        return new AssignMemberResult(roleToChange, roleToAssign);

    }

    @Transactional
    public AssignMemberResult assignNewRoleToMember(long chatId, long userToAssign, @NonNull String newRoleName, long fromId){

        RoleDto roleToAssign = roleService.getRoleByNameIgnoreCase(chatId, newRoleName.trim())
                .orElseThrow(RoleNotFoundException::new);

        return assignNewRoleToMember(chatId, userToAssign, roleToAssign.getRolePriority(), fromId);

    }

    public void checkMemberInteractionAbility(long chatId, long fromId, long userToInteract){

        int callerRole = getMemberRolePriority(chatId, fromId);
        int targetUserRole = getMemberRolePriority(chatId, userToInteract);

        if(callerRole<=targetUserRole){
            // никому нельзя наказывать участников с ролью выше или равной своей
            throw new MemberAccessDeniedException(userToInteract,fromId);
        }
    }
    public boolean isChatAdmin(long chatId, long memberId){
        Optional<MemberDto> member = getCachedMemberInfo(chatId, memberId);
        return member.map(MemberDto::isChatAdmin).orElse(false);
    }

    public Optional<MemberDto> getCachedMemberInfo(long chatId, long memberId){

        ConcurrentHashMap<Long, Optional<MemberDto>> members = cacheManager.getActiveMembersCache().get(chatId,k->new ConcurrentHashMap<>());
        return members.computeIfAbsent(memberId, key-> {
           Optional<MemberEntity> member = memberRepository.findByChatIdAndUserId(chatId, memberId);
           return member.map(memberMapper::toMemberDto);

        });
    }

       private void invalidateMemberCache(long chatId){
        cacheManager.getActiveMembersCache().invalidate(chatId);

        }

        public List<MemberEntity> getMembersWithPositiveRole(long chatId){
           return memberRepository.findMembersWithPositiveRole(chatId);
        }


    private MemberDto putMemberToCache(long chatId, @NonNull MemberEntity member){

        MemberDto memberDto = memberMapper.toMemberDto(member);

        ConcurrentHashMap<Long, Optional<MemberDto>> members = cacheManager.getActiveMembersCache()
                .get(chatId,k-> new ConcurrentHashMap<>());
        members.put(memberDto.getUserId(), Optional.of(memberDto));

        return memberDto;

    }
    private void putMembersToCache(long chatId, @NonNull List<MemberEntity> membersToPut){

        ConcurrentHashMap<Long, Optional<MemberDto>> memberCache = cacheManager.getActiveMembersCache()
                .get(chatId,k-> new ConcurrentHashMap<>());

        for(MemberEntity currentMember: membersToPut){
            if(currentMember!=null){
               memberCache.put(currentMember.getUserId(), Optional.of(memberMapper.toMemberDto(currentMember)));
            }
        }
    }


    private void changeCachedMembersRole(long chatId, @NonNull List<Long> memberIds, int newRolePriority){

        ConcurrentHashMap<Long, Optional<MemberDto>> memberCache = cacheManager.getActiveMembersCache()
                .get(chatId,k-> new ConcurrentHashMap<>());

        for(Long currentMemberId: memberIds){
            if(currentMemberId!=null){
                memberCache.computeIfPresent(currentMemberId, (id, memberOptional) -> {
                    memberOptional.ifPresent(memberDto -> memberDto.setRolePriority(newRolePriority));
                    return memberOptional;
                });
            }
        }
    }
}





