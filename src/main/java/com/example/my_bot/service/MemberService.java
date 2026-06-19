package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.*;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.FullNameMapper;
import com.example.my_bot.mapper.MemberMapper;
import com.example.my_bot.repository.MemberRepository;
import com.example.my_bot.service.chat.ChatService;
import com.github.benmanes.caffeine.cache.Cache;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import com.vk.api.sdk.objects.messages.responses.GetConversationMembersResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.ChatUtils.CHAT_MANAGER_ROLE_PRIORITY;

@Service
@Slf4j
public class MemberService {

    private final MemberRepository memberRepository;
    private final VkChatClient vkChatClient;
    private final MemberMapper memberMapper;
    private final ChatService chatService;
    private final CaffeineCacheManager cacheManager;
    private final FullNameMapper fullNameMapper;
    private final RoleService roleService;
    private final GlobalUserService globalUserService;
    private final long theMainBotId;


    public MemberService(
            MemberRepository memberRepository,
            VkChatClient vkChatClient,
            MemberMapper memberMapper,
            ChatService chatService,
            CaffeineCacheManager cacheManager,
            FullNameMapper fullNameMapper,
            @Lazy RoleService roleService,
            @Lazy GlobalUserService globalUserService,
            @Value("${vk.main-bot.id}") long theMainBotId) {
        this.memberRepository = memberRepository;
        this.vkChatClient = vkChatClient;
        this.memberMapper = memberMapper;
        this.chatService = chatService;
        this.cacheManager = cacheManager;
        this.fullNameMapper = fullNameMapper;
        this.roleService = roleService;
        this.globalUserService = globalUserService;
        this.theMainBotId = theMainBotId;
    }

    @Transactional
    public void synchronizeChatMembers(@NonNull CommandRoutingData commandRoutingData) throws ClientException, ApiException{

        long dataBaseChatId = commandRoutingData.getDataBaseChatId();

        GetConversationMembersResponse response= vkChatClient.getAllConversationMembersWithAllNameCases(
                commandRoutingData.getExecutorBot(),
                commandRoutingData.getVkApiChatId()
        );

        List<UserFullNameInEachCase> userFullNames = fullNameMapper.mapProfileNames(response.getProfiles());
        userFullNames.addAll(fullNameMapper.mapGroupNames(response.getGroups()));
        globalUserService.putFullNamesToTheDataBase(userFullNames);

        List<ConversationMember> currentChatMembers = response.getItems();
        Set<Long> vkUserIds = currentChatMembers.stream()
                .map(ConversationMember::getMemberId)
                .collect(Collectors.toSet());

        memberRepository.setUnknownLeaveAndChatAdminFalseForMembersNotInList(dataBaseChatId, vkUserIds);

        Map<Long, MemberEntity> currentChatMemberMap =  memberRepository.findByChatIdAndUserIdIn(dataBaseChatId, vkUserIds).stream()
                .collect(Collectors.toMap(MemberEntity::getUserId, Function.identity()));

        List<MemberEntity> newMembers = new ArrayList<>();
        Instant now = Instant.now();

        for(ConversationMember vkMember: currentChatMembers){
            long memberId= vkMember.getMemberId();
            MemberEntity entity= currentChatMemberMap.get(memberId);

            if(entity== null){
                entity = new MemberEntity();
                entity.setUserId(memberId);
                entity.setChatId(dataBaseChatId);
                entity.setFirstAppearance(now);
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
            }if(memberId==(theMainBotId *-1)){
                entity.setRolePriority(CHAT_MANAGER_ROLE_PRIORITY);
                // даю боту роль выше чем у создателя, чтобы его никто не мог наказывать
            }
        }
        if(!newMembers.isEmpty()) {
            memberRepository.saveAll(newMembers);
        }
        chatService.setLastSyncToNow(dataBaseChatId);
        invalidateMemberCache(dataBaseChatId);
    }

    public int getMemberRolePriority(long chatId, long userId){
        Optional<MemberDto> member = getCachedMemberInfo(chatId, userId);
        return member.map(MemberDto::getRolePriority).orElse(MEMBER.getRolePriority());
    }
    public Optional<Instant> getFirstAppearance(long chatId, long userId){
        Optional<MemberDto> member = getCachedMemberInfo(chatId, userId);
        return member.map(MemberDto::getFirstAppearance);
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
        setPresenceTypeToMembers(chatId, Set.of(userId), presenceType, createIfAbsent);
    }

    @Transactional
    public void setPresenceTypeToMembers(long chatId, @NonNull Set<Long> userIds, @NonNull MemberPresenceType presenceType, boolean createIfAbsent) {
        if(userIds.isEmpty()) return;

        List<MemberEntity> existingMembers = memberRepository.findByChatIdAndUserIdIn(chatId, userIds);
        Map<Long, MemberEntity> existingMap = existingMembers.stream()
                .collect(Collectors.toMap(MemberEntity::getUserId, Function.identity()));

        List<MemberEntity> toSave = new ArrayList<>();
        List<MemberEntity> toPutInCache = new ArrayList<>();
        List<Long> missingUserIds = new ArrayList<>();

        for(Long userId: userIds){
            MemberEntity member = existingMap.get(userId);
            if (member!= null){
                if(member.getPresenceType()!= presenceType){
                    member.setPresenceType(presenceType);
                    toPutInCache.add(member);
                }
            } else if(createIfAbsent){
                member= new MemberEntity(chatId, userId, MEMBER.getRolePriority(), false, presenceType, null, Instant.now());
                toSave.add(member);
                toPutInCache.add(member);
            }else{
                missingUserIds.add(userId);
            }
        }

        if(!missingUserIds.isEmpty()){
            throw new UserNeverBeenInChatException(missingUserIds);
        }if(!toSave.isEmpty()){
            memberRepository.saveAll(toSave);
        }
        if(!toPutInCache.isEmpty()){
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

    public Page<MemberEntity> getNotKickedNewMembersWithRoleLessThan(long chatId, Instant after , int rolePriority, int limit){
        return memberRepository.findNotKickedNewMembersWithRoleLessThan(chatId,after,rolePriority, PageRequest.of(0, limit));
    }

    public List<Long> getAllCurrentChatMemberWithFirstAppearanceBeforeThan(long chatId, @NonNull Instant thresholdDate){
        return memberRepository.findAllCurrentMemberWithFirstAppearanceBeforeThan(chatId, thresholdDate);
    }

    public List<Long> getAllCurrentChatMemberWithRoleLessThanAndFirstAppearanceBeforeThan(long chatId, int rolePriority, @NonNull Instant thresholdDate){
        return memberRepository.findAllCurrentMemberWithRoleLessThanAndFirstAppearanceBeforeThan(chatId, rolePriority, thresholdDate);
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
        checkMemberInteractionAbility(chatId, fromId, userToAssign,true);
        roleService.checkRoleInteractionAbility(chatId, newRolePriority,fromId);

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

    public void checkMemberInteractionAbility(long chatId, long fromId, long userToInteract, boolean compareImmunity){

        int callerRole = getMemberRolePriority(chatId, fromId);
        Optional<MemberDto> targetUser = getCachedMemberInfo(chatId, userToInteract);

        int targetUserRole = targetUser.map(MemberDto::getRolePriority)
                .orElse(MEMBER.getRolePriority());

        Integer targetUserImmunityRole = targetUser.map(MemberDto::getImmuneRolePriority)
                .orElse(null);

        if((callerRole<=targetUserRole)||(compareImmunity&&targetUserImmunityRole!=null&&callerRole<=targetUserImmunityRole)){
            throw new MemberAccessDeniedException(userToInteract,fromId);
        }

    }

    public boolean isChatAdmin(long chatId, long memberId){
        Optional<MemberDto> member = getCachedMemberInfo(chatId, memberId);
        return member.map(MemberDto::isChatAdmin).orElse(false);
    }

    public List<Long> getAllChatAdmins(long chatId){
        return memberRepository.findAllChatAdmins(chatId);
    }

    public Optional<MemberDto> getCachedMemberInfo(long chatId, long memberId){

        ConcurrentHashMap<Long, Optional<MemberDto>> members = cacheManager.getActiveMembersCache().get(chatId,k->new ConcurrentHashMap<>());
        Optional<MemberDto> memberToReturn = members.computeIfAbsent(memberId, key-> {
           Optional<MemberEntity> member = memberRepository.findByChatIdAndUserId(chatId, memberId);
           return member.map(memberMapper::toMemberDto);
        });
        memberToReturn.ifPresent(m->  // вгрузить в кеш имена активно общающихся участников чата
                globalUserService.getUserFullNameInRequiredCase(m.getUserId(),NameCase.NOMINATIVE)
        );
        return memberToReturn;

    }
    private void invalidateMemberCache(long chatId){
        cacheManager.getActiveMembersCache().invalidate(chatId);
    }

    public List<MemberEntity> getMembersWithPositiveRole(long chatId){
        return memberRepository.findMembersWithPositiveRole(chatId);
    }

    public Optional<Long> findCurrentMemberByFirstNameOrLastName(long chatId, @NonNull String firstNameOrLastName){
        firstNameOrLastName = firstNameOrLastName.trim();

        ConcurrentHashMap<Long, Optional<MemberDto>> activeMembersCache = cacheManager.getActiveMembersCache()
                .get(chatId,k-> new ConcurrentHashMap<>());
        Cache<Long, ConcurrentHashMap<NameCase, String>> fullNameCache = cacheManager.getFullNameCache();

        for(Optional<MemberDto> optionalMember: activeMembersCache.values()){  // быстрый поиск имени участника в кеше
            if(optionalMember.isEmpty()) continue;
            MemberDto member = optionalMember.get();
            if(member.getPresenceType()!=IN_CHAT) continue;
            ConcurrentHashMap<NameCase, String> fullName = fullNameCache.getIfPresent(member.getUserId());
            if(fullName==null) continue;
            String nominativeFullName = fullName.get(NameCase.NOMINATIVE);
            if(nominativeFullName==null) continue;

            if(nominativeFullName.contains(firstNameOrLastName)) return optionalMember.map(MemberDto::getUserId);
        }
        List<MemberRepository.MemberIdAndNameProjection> foundMembers=
                memberRepository.findCurrentMemberByFullName(chatId, firstNameOrLastName,  PageRequest.of(0, 1));
        Optional<MemberRepository.MemberIdAndNameProjection> foundMember=
                Optional.ofNullable(foundMembers.isEmpty()?null:foundMembers.get(0));

        foundMember.ifPresent(m-> {
                        ConcurrentHashMap<NameCase, String> fullName = new ConcurrentHashMap<>();
                        fullName.put(NameCase.NOMINATIVE, m.getFullName());
                        fullNameCache.put(m.getUserId(),fullName);
        });
        return foundMember.map(MemberRepository.MemberIdAndNameProjection::getUserId);
    }

    public List<MemberEntity> getMembersWithImmunity(long chatId){
        return memberRepository.findMembersWithImmunity(chatId);
    }

    @Transactional
    public RoleDto assignImmunityToMember(long chatId, long userToAlter, int newImmuneRolePriority, long fromId){
        if(userToAlter==fromId){
            throw new CannotApplyThisCommandToYourselfException();
        }
        MemberEntity memberToAlter = memberRepository.findByChatIdAndUserId(chatId, userToAlter)
                .orElseThrow(()->new UserNeverBeenInChatException(userToAlter));

        RoleDto newImmuneRole = roleService.getRoleByPriority(chatId, newImmuneRolePriority)
                .orElseThrow(RoleNotFoundException::new);

        if(Objects.equals(newImmuneRole.getRolePriority(), memberToAlter.getImmuneRolePriority())){
            throw new MemberAlreadyHasThisImmunityException(userToAlter);
        }
        checkMemberInteractionAbility(chatId, fromId, userToAlter,true);
        roleService.checkRoleInteractionAbility(chatId, newImmuneRolePriority, fromId);

        memberToAlter.setImmuneRolePriority(newImmuneRolePriority);
        putMemberToCache(chatId,memberRepository.save(memberToAlter));

        return newImmuneRole;
    }

    @Transactional
    public RoleDto assignImmunityToMember(long chatId, long userToAlter, @NonNull String newImmuneRoleName, long fromId){

        RoleDto newImmuneRole = roleService.getRoleByNameIgnoreCase(chatId, newImmuneRoleName.trim())
                .orElseThrow(RoleNotFoundException::new);

        return assignImmunityToMember(chatId,userToAlter,newImmuneRole.getRolePriority(),fromId);
    }

    @Transactional
    public void removeImmunityFromMember(long chatId, long userToAlter, long fromId){
        if(userToAlter==fromId){
            throw new CannotApplyThisCommandToYourselfException();
        }
        MemberEntity memberToAlter = memberRepository.findByChatIdAndUserId(chatId, userToAlter)
                .orElseThrow(()->new UserNeverBeenInChatException(userToAlter));

        if(memberToAlter.getImmuneRolePriority()==null){
            throw new MemberHasNoImmunityException(memberToAlter.getUserId());
        }
        checkMemberInteractionAbility(chatId, fromId, userToAlter,false);

        memberToAlter.setImmuneRolePriority(null);
        putMemberToCache(chatId,memberRepository.save(memberToAlter));
    }

    private void putMemberToCache(long chatId, @NonNull MemberEntity member){
        putMembersToCache(chatId, List.of(member));
    }

    private void putMembersToCache(long chatId, @NonNull List<MemberEntity> membersToPut){

        ConcurrentHashMap<Long, Optional<MemberDto>> memberCache = cacheManager.getActiveMembersCache()
                .get(chatId,k-> new ConcurrentHashMap<>());

        for(MemberEntity currentMember: membersToPut){
               memberCache.put(currentMember.getUserId(), Optional.of(memberMapper.toMemberDto(currentMember)));
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





