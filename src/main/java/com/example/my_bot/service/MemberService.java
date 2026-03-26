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
import com.google.common.collect.ImmutableMap;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.ChatUtils.isValidLong;

@Service
@Slf4j
public class MemberService {

    private final MemberRepository memberRepository;

    private final VkChatClient vkChatClient;

    private final MemberMapper memberMapper;

    private final ChatService chatService;

    private final CaffeineCacheManager cacheManager;

    private RoleService roleService;

    private long groupId;

    private final Pattern MEMBER_MENTION = Pattern.compile("\\[(id|club)(\\d+)\\|[^]]+]");

    private static final Pattern VK_URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:m\\.)?(?:(?:vk\\.(?:com|ru))|vkontakte\\.ru)/(((id|club|public)\\d{1,11})|[a-zA-Z0-9_.]{4,32})");

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


    public List<MemberEntity> findByChatId(long chatId){
        return memberRepository.findByChatId(chatId);
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
            if(memberId==(groupId*-1)){
                continue;
            }
            MemberEntity entity = currentChatMemberMap.get(memberId);

            if (entity == null) {
                entity = new MemberEntity();
                entity.setUserId(memberId);
                entity.setChatId(chatId);
                entity.setFirstAppearance(Instant.now());
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
            }
        }

        if (!newMembers.isEmpty()) {
            memberRepository.saveAll(newMembers);
        }

        chatService.setLastSyncToNow(chatId);
        invalidateMemberRoleCache(chatId);

    }

    public int getCachedMemberRolePriority(long chatId, long userId){
        ImmutableMap<Long, MemberDto> members =  getCachedMembersWithRole(chatId);
        MemberDto memberDto = members.get(userId);
        if(memberDto!=null){
            return memberDto.getRolePriority();
        }return MEMBER.getRolePriority();

    }

    @Transactional
    public boolean reAssignRequiredMembersMassively(long chatId, int oldRolePriority, int newRolePriority){

        if(roleService.getRoleByPriority(chatId,newRolePriority).isEmpty()){
            throw new RoleNotFoundException();
        }

        int changedRows = memberRepository.updateRolePriorityForMembers(chatId, oldRolePriority, newRolePriority);

        invalidateMemberRoleCache(chatId);

        return changedRows>0;

    }

    @Transactional
    public void createNewMemberOrMarkAsPresent(long chatId, long userId, Long invitedById) {
        Optional<MemberEntity> existing = memberRepository.findByChatIdAndUserId(chatId, userId);
        if (existing.isPresent()) {
            MemberEntity member = existing.get();
            member.setPresenceType(IN_CHAT);
            member.setChatAdmin(false);
            invalidateMemberRoleCache(chatId);
        } else {
            memberRepository.save(new MemberEntity(chatId, userId, MEMBER.getRolePriority(), false, IN_CHAT, invitedById, Instant.now()));
        }
    }
    @Transactional
    public void setPresenceTypeToUser(long chatId, long userId, @NonNull MemberPresenceType presenceType, boolean createIfAbsent){
        Optional<MemberEntity> member = memberRepository.findByChatIdAndUserId(chatId, userId);
        if(member.isEmpty()){
            if(createIfAbsent){
                memberRepository.save(new MemberEntity(chatId, userId, MEMBER.getRolePriority(), false, presenceType, null, Instant.now()));
                return;
            }throw new UserNeverBeenInChatException(userId);
        }member.get().setPresenceType(presenceType);

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

        roleService.checkRoleInteractionAbility(newRolePriority,getCachedMemberRolePriority(chatId, fromId));


        RoleDto roleToChange = roleService.getRoleByPriority(chatId, memberToAssign.getRolePriority())
                .orElseThrow(RoleNotFoundException::new);

        memberToAssign.setRolePriority(newRolePriority);

        memberRepository.save(memberToAssign);

        invalidateMemberRoleCache(chatId);

        return new AssignMemberResult(roleToChange, roleToAssign);

    }

    @Transactional
    public AssignMemberResult assignNewRoleToMember(long chatId, long userToAssign, @NonNull String newRoleName, long fromId){

        RoleDto roleToAssign = roleService.getRoleByNameIgnoreCase(chatId, newRoleName.trim())
                .orElseThrow(RoleNotFoundException::new);

        return assignNewRoleToMember(chatId, userToAssign, roleToAssign.getRolePriority(), fromId);

    }

    public void checkMemberInteractionAbility(long chatId, long fromId, long userToInteract){

        int callerRole = getCachedMemberRolePriority(chatId, fromId);
        int targetUserRole = getCachedMemberRolePriority(chatId, userToInteract);

        if(callerRole<=targetUserRole){
            // никому нельзя наказывать участников с ролью выше или равной своей
            throw new MemberAccessDeniedException(userToInteract,fromId);
        }
    }


    public Optional<Long> getCachedMemberIdByUserInput(@NonNull String userInput){

        userInput=userInput.toLowerCase().trim();

        Matcher matcher = MEMBER_MENTION.matcher(userInput);
        if (matcher.find()) {
            String type = matcher.group(1); // "id" или "club"
            if(!isValidLong(matcher.group(2))){
                return Optional.empty();
            } long id = Long.parseLong(matcher.group(2));

            return Optional.of(type.equals("id")?id:(id*-1));
        }
        Matcher m = VK_URL_PATTERN.matcher(userInput);
        if (!m.find()) return Optional.empty();

        if (m.group(2) != null) {
            String prefix = m.group(3);
            String fullMatch = m.group(2);
            String numStr = fullMatch.substring(prefix.length());
            long id = Long.parseLong(numStr);
            if (prefix.equals("id")) {
                return Optional.of(id);
            } else {
                return Optional.of(-id);
            }
        } else {
            String userNickname = m.group(1);
            return cacheManager.getNicknameCache().get(userNickname,
                    k -> vkChatClient.getMemberIdByNickname(userNickname));

        }
    }
        private void invalidateMemberRoleCache(long chatId){
        cacheManager.getMemberRoleCache().invalidate(chatId);

        }

        public ImmutableMap<Long, MemberDto> getCachedMembersWithRole(long chatId){

        return cacheManager.getMemberRoleCache().get(chatId,
                k ->{
                    ImmutableMap.Builder<Long, MemberDto> resultMap = new ImmutableMap.Builder<>();
                    List<MemberEntity> members = memberRepository.findMembersWithNotZeroRole(chatId);
                    for(MemberEntity member:members){
                        resultMap.put(member.getUserId(), memberMapper.toMemberDto(member));
                    } return resultMap.build();
                });
             }

    }





