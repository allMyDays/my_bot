package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.exception.member.MemberAlreadyHasThisRoleException;
import com.example.my_bot.exception.member.UserNeverBeenInChatException;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.MemberMapper;
import com.example.my_bot.repository.MemberRepository;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.utils.ChatUtils.isValidLong;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    private final VkChatClient vkChatClient;

    private final MemberMapper memberMapper;

    private final ChatService chatService;

    private MemberService selfLink;

    private RoleService roleService;

    private final CaffeineCacheManager cacheManager;

    private static final long AUTO_SYNC_INTERVAL_MINUTES = 15;

    private final Pattern MEMBER_MENTION = Pattern.compile("\\[(id|club)(\\d+)\\|[^]]+]");

    private static final Pattern VK_URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:m\\.)?(?:(?:vk\\.(?:com|ru))|vkontakte\\.ru)/(((id|club|public)\\d{1,11})|[a-zA-Z0-9_.]{4,32})");


    @Autowired
    @Lazy
    public void setSelfLink(MemberService selfLink) {
        this.selfLink = selfLink;
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
                int requiredRolePriority = SENIOR_ADMINISTRATOR.getRolePriority();
                if(entity.getRolePriority()<requiredRolePriority){
                    entity.setRolePriority(requiredRolePriority);
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

    public void checkLastSyncAndPerform(long chatId) throws ClientException, ApiException {

        ChatDetailsDto chatDto = chatService.getCachedChatDetails(chatId, false);

        Optional<Instant> lastSync = chatDto.getOptionalLastSyncTime();

        if (lastSync.isEmpty()||
                Duration.between(lastSync.get(), Instant.now()).toMinutes() >= AUTO_SYNC_INTERVAL_MINUTES) {

            selfLink.synchronizeChatMembers(chatId);
        }

    }

    public Map<Long, MemberDto> getCachedMembersWithRole(long chatId){

        return cacheManager.getMemberRoleCache().get(chatId,
                k ->{
            ConcurrentMap<Long, MemberDto> resultMap = new ConcurrentHashMap<>();
            List<MemberEntity> members = memberRepository.findMembersWithNotZeroRole(chatId);
            for(MemberEntity member:members){
                resultMap.put(member.getUserId(), memberMapper.toMemberDto(member));
            } return resultMap;
        });
    }

    public int getCachedMemberRolePriority(long chatId, long userId){
        Map<Long, MemberDto> members =  getCachedMembersWithRole(chatId);
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
        long callerRolePriority = getCachedMemberRolePriority(chatId, fromId);

        if(memberToAssign.getRolePriority()>callerRolePriority){
            throw new MemberAccessDeniedException(userToAssign, fromId);
        } if(newRolePriority>callerRolePriority){
            throw new RoleAccessDeniedException();
        }

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

    }





