package com.example.my_bot.unit.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.*;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.*;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.FullNameMapper;
import com.example.my_bot.mapper.MemberMapper;
import com.example.my_bot.repository.MemberRepository;
import com.example.my_bot.service.*;
import com.example.my_bot.service.chat.ChatService;
import com.github.benmanes.caffeine.cache.Cache;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import com.vk.api.sdk.objects.messages.responses.GetConversationMembersResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.utils.ChatUtils.CHAT_MANAGER_ROLE_PRIORITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private FullNameMapper fullNameMapper;

    @Mock
    private RoleService roleService;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private Cache<Long, ConcurrentHashMap<Long, Optional<MemberDto>>> activeMembersCache;

    @Mock
    private Cache<Long, ConcurrentHashMap<NameCase, String>> fullNameCache;

    private MemberService memberService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final long userId = 200L;
    private final long theMainBotId = 777L;
    private final CommandRoutingData routingData = new CommandRoutingData();

    private MemberDto createMemberDto(long userId, int rolePriority, Integer immuneRolePriority,
                                      boolean isChatAdmin, MemberPresenceType presenceType,
                                      Instant firstAppearance, boolean isDmResponsesEnabled) {
        return new MemberDto(userId, rolePriority, immuneRolePriority, isChatAdmin,
                presenceType, firstAppearance, isDmResponsesEnabled);
    }

    @BeforeEach
    void setUp() throws Exception {
        routingData.setDataBaseChatId(chatId);
        routingData.setVkApiChatId(chatId);
        routingData.setExecutorBot(new GroupActor(111L, "token"));

        memberService = new MemberService(
                memberRepository,
                vkChatClient,
                memberMapper,
                chatService,
                cacheManager,
                fullNameMapper,
                roleService,
                globalUserService,
                theMainBotId
        );
        given(cacheManager.getActiveMembersCache()).willReturn(activeMembersCache);
        given(cacheManager.getFullNameCache()).willReturn(fullNameCache);
        given(activeMembersCache.get(anyLong(), any())).willReturn(new ConcurrentHashMap<>());
    }


    @Test
    void shouldSynchronizeChatMembersSuccessfully() throws ClientException, ApiException {
        List<ConversationMember> members = new ArrayList<>();
        ConversationMember member1 = new ConversationMember();
        member1.setMemberId(101L);
        member1.setIsOwner(false);
        member1.setIsAdmin(false);
        member1.setInvitedBy(100L);
        members.add(member1);

        ConversationMember member2 = new ConversationMember();
        member2.setMemberId(102L);
        member2.setIsOwner(true);
        member2.setIsAdmin(false);
        member2.setInvitedBy(0L);
        members.add(member2);

        ConversationMember member3 = new ConversationMember();
        member3.setMemberId(103L);
        member3.setIsOwner(false);
        member3.setIsAdmin(true);
        member3.setInvitedBy(100L);
        members.add(member3);

        ConversationMember botMember = new ConversationMember();
        botMember.setMemberId(-theMainBotId);
        botMember.setIsOwner(false);
        botMember.setIsAdmin(false);
        botMember.setInvitedBy(0L);
        members.add(botMember);

        GetConversationMembersResponse response = new GetConversationMembersResponse();
        response.setItems(members);
        response.setProfiles(List.of());
        response.setGroups(List.of());

        given(vkChatClient.getAllConversationMembersWithAllNameCases(any(), anyLong())).willReturn(response);
        given(memberRepository.setUnknownLeaveAndChatAdminFalseForMembersNotInList(eq(chatId), anySet()))
                .willReturn(1);
        given(memberRepository.findByChatIdAndUserIdIn(eq(chatId), anySet()))
                .willReturn(List.of());

        doNothing().when(chatService).setLastSyncToNow(chatId);

        memberService.synchronizeChatMembers(routingData);

        ArgumentCaptor<List<MemberEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(memberRepository).saveAll(captor.capture());
        List<MemberEntity> saved = captor.getValue();
        assertThat(saved).hasSize(4);
        for (MemberEntity entity : saved) {
            if (entity.getUserId() == 101L) {
                assertThat(entity.getRolePriority()).isEqualTo(MEMBER.getRolePriority());
                assertThat(entity.isChatAdmin()).isFalse();
            } else if (entity.getUserId() == 102L) {
                assertThat(entity.getRolePriority()).isEqualTo(CHAT_CREATOR.getRolePriority());
                assertThat(entity.isChatAdmin()).isTrue();
            } else if (entity.getUserId() == 103L) {
                assertThat(entity.getRolePriority()).isEqualTo(SENIOR_ADMINISTRATOR.getRolePriority());
                assertThat(entity.isChatAdmin()).isTrue();
            } else if (entity.getUserId() == -theMainBotId) {
                assertThat(entity.getRolePriority()).isEqualTo(CHAT_MANAGER_ROLE_PRIORITY);
            }
        }
        verify(chatService).setLastSyncToNow(chatId);
        verify(activeMembersCache).invalidate(chatId);
    }

    @Test
    void shouldReturnRolePriorityFromCache() {
        MemberDto dto = createMemberDto(userId, 50, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        MemberService spy = spy(memberService);
        doReturn(Optional.of(dto)).when(spy).getCachedMemberInfo(chatId, userId);

        int role = spy.getMemberRolePriority(chatId, userId);
        assertThat(role).isEqualTo(50);
    }

    @Test
    void shouldReturnDefaultRoleWhenMemberNotFound() {
        MemberService spy = spy(memberService);
        doReturn(Optional.empty()).when(spy).getCachedMemberInfo(chatId, userId);

        int role = spy.getMemberRolePriority(chatId, userId);
        assertThat(role).isEqualTo(MEMBER.getRolePriority());
    }


    @Test
    void shouldReturnFirstAppearance() {
        Instant now = Instant.now();
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, now, false);
        MemberService spy = spy(memberService);
        doReturn(Optional.of(dto)).when(spy).getCachedMemberInfo(chatId, userId);

        Optional<Instant> result = spy.getFirstAppearance(chatId, userId);
        assertThat(result).contains(now);
    }

    @Test
    void shouldReassignRolesMassively() {
        int oldRole = 10;
        int newRole = 20;
        List<Long> memberIds = List.of(101L, 102L);
        RoleDto roleDto = new RoleDto("newRole", newRole);
        given(roleService.getRoleByPriority(eq(chatId), eq(newRole))).willReturn(Optional.of(roleDto));
        given(memberRepository.updateMembersRoleAndReturnIds(eq(chatId), eq(oldRole), eq(newRole))).willReturn(memberIds);

        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        map.put(101L, Optional.of(createMemberDto(101L, oldRole, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false)));
        map.put(102L, Optional.of(createMemberDto(102L, oldRole, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false)));
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map);

        memberService.reAssignRequiredMembersMassively(chatId, oldRole, newRole);

        verify(memberRepository).updateMembersRoleAndReturnIds(chatId, oldRole, newRole);
        assertThat(map.get(101L).get().getRolePriority()).isEqualTo(newRole);
        assertThat(map.get(102L).get().getRolePriority()).isEqualTo(newRole);
    }

    @Test
    void shouldThrowWhenNewRoleNotFound() {
        int oldRole = 10;
        int newRole = 20;
        given(roleService.getRoleByPriority(eq(chatId), eq(newRole))).willReturn(Optional.empty());
        assertThatThrownBy(() -> memberService.reAssignRequiredMembersMassively(chatId, oldRole, newRole))
                .isInstanceOf(RoleNotFoundException.class);
        verify(memberRepository, never()).updateMembersRoleAndReturnIds(anyLong(), anyInt(), anyInt());
    }

    @Test
    void shouldCreateNewMember() {
        MemberEntity entity = new MemberEntity();
        entity.setChatId(chatId);
        entity.setUserId(userId);
        given(memberRepository.findByChatIdAndUserId(eq(chatId), eq(userId))).willReturn(Optional.empty());
        given(memberRepository.save(any(MemberEntity.class))).willReturn(entity);
        MemberDto dto = createMemberDto(userId, MEMBER.getRolePriority(), null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        given(memberMapper.toMemberDto(entity)).willReturn(dto);

        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map);

        memberService.createNewMemberOrMarkAsPresent(chatId, userId, null);

        verify(memberRepository).save(any(MemberEntity.class));
        assertThat(map).containsKey(userId);
        assertThat(map.get(userId)).contains(dto);
    }

    @Test
    void shouldMarkExistingMemberAsPresent() {
        MemberEntity entity = new MemberEntity();
        entity.setChatId(chatId);
        entity.setUserId(userId);
        entity.setPresenceType(MemberPresenceType.SELF_LEAVE);
        given(memberRepository.findByChatIdAndUserId(eq(chatId), eq(userId))).willReturn(Optional.of(entity));
        MemberDto dto = createMemberDto(userId, MEMBER.getRolePriority(), null, false, MemberPresenceType.SELF_LEAVE, Instant.now(), false);
        given(memberMapper.toMemberDto(entity)).willReturn(dto);

        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map); // исправлено

        memberService.createNewMemberOrMarkAsPresent(chatId, userId, 101L);

        assertThat(entity.getPresenceType()).isEqualTo(MemberPresenceType.IN_CHAT);
        assertThat(entity.getInvitedById()).isNull();
        assertThat(entity.isChatAdmin()).isFalse();
        verify(memberRepository, never()).save(any());
        assertThat(map).containsKey(userId);
    }

    @Test
    void shouldSetPresenceTypeToExistingMember() {
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setPresenceType(MemberPresenceType.IN_CHAT);
        given(memberRepository.findByChatIdAndUserIdIn(chatId, Set.of(userId))).willReturn(List.of(entity));
        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map); // исправлено
        MemberDto dto = createMemberDto(userId, MEMBER.getRolePriority(), null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        given(memberMapper.toMemberDto(entity)).willReturn(dto);

        memberService.setPresenceTypeToMember(chatId, userId, MemberPresenceType.SELF_LEAVE, false);

        assertThat(entity.getPresenceType()).isEqualTo(MemberPresenceType.SELF_LEAVE);
        verify(memberRepository, never()).saveAll(anyList());
        verify(memberRepository).flush();
        assertThat(map).containsKey(userId);
    }

    @Test
    void shouldCreateNewMemberWhenCreateIfAbsentTrue() {
        given(memberRepository.findByChatIdAndUserIdIn(eq(chatId), eq(Set.of(userId)))).willReturn(List.of());
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        given(memberRepository.saveAll(anyList())).willReturn(List.of(entity));
        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map);
        MemberDto dto = createMemberDto(userId, MEMBER.getRolePriority(), null, false, MemberPresenceType.SELF_LEAVE, Instant.now(), false);
        given(memberMapper.toMemberDto(any(MemberEntity.class))).willReturn(dto); // исправлено: для любого entity

        memberService.setPresenceTypeToMember(chatId, userId, MemberPresenceType.SELF_LEAVE, true);

        verify(memberRepository).saveAll(anyList());
        verify(memberRepository).flush();
        assertThat(map).containsKey(userId);
        assertThat(map.get(userId)).contains(dto);
    }

    @Test
    void shouldThrowWhenMemberNotFoundAndCreateIfAbsentFalse() {
        given(memberRepository.findByChatIdAndUserIdIn(chatId, Set.of(userId))).willReturn(List.of());
        assertThatThrownBy(() -> memberService.setPresenceTypeToMember(chatId, userId, MemberPresenceType.SELF_LEAVE, false))
                .isInstanceOf(UserNeverBeenInChatException.class);
        verify(memberRepository, never()).saveAll(anyList());
    }


    @Test
    void shouldGetLeftButNotKickedMembers() {
        Page<MemberEntity> page = new PageImpl<>(List.of(new MemberEntity()));
        given(memberRepository.findLeftButNotKickedMembersWithRoleLessThan(eq(chatId), anyInt(), any(PageRequest.class)))
                .willReturn(page);
        Page<MemberEntity> result = memberService.getLeftButNotKickedMembersWithRoleLessThan(chatId, 10, 5);
        assertThat(result).isSameAs(page);
    }

    @Test
    void shouldGetNotKickedCommunities() {
        Page<MemberEntity> page = new PageImpl<>(List.of(new MemberEntity()));
        given(memberRepository.findNotKickedCommunitiesWithRoleLessThan(eq(chatId), anyInt(), any(PageRequest.class)))
                .willReturn(page);
        Page<MemberEntity> result = memberService.getNotKickedCommunitiesWithRoleLessThan(chatId, 10, 5);
        assertThat(result).isSameAs(page);
    }

    @Test
    void shouldGetNotKickedMembersInvitedBy() {
        Page<MemberEntity> page = new PageImpl<>(List.of(new MemberEntity()));
        given(memberRepository.findNotKickedMembersInvitedByAndWithRoleLessThan(eq(chatId), anyLong(), anyInt(), any(PageRequest.class)))
                .willReturn(page);
        Page<MemberEntity> result = memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(chatId, 101L, 10, 5);
        assertThat(result).isSameAs(page);
    }

    @Test
    void shouldGetNotKickedNewMembers() {
        Instant after = Instant.now();
        Page<MemberEntity> page = new PageImpl<>(List.of(new MemberEntity()));
        given(memberRepository.findNotKickedNewMembersWithRoleLessThan(eq(chatId), any(Instant.class), anyInt(), any(PageRequest.class)))
                .willReturn(page);
        Page<MemberEntity> result = memberService.getNotKickedNewMembersWithRoleLessThan(chatId, after, 10, 5);
        assertThat(result).isSameAs(page);
    }


    @Test
    void shouldGetAllCurrentMembersBeforeThan() {
        Instant threshold = Instant.now();
        List<Long> ids = List.of(101L, 102L);
        given(memberRepository.findAllCurrentMemberWithFirstAppearanceBeforeThan(chatId, threshold)).willReturn(ids);
        List<Long> result = memberService.getAllCurrentChatMembersWithFirstAppearanceBeforeThan(chatId, threshold);
        assertThat(result).isEqualTo(ids);
    }

    @Test
    void shouldGetAllCurrentMembersWithRoleLessThan() {
        Instant threshold = Instant.now();
        int role = 10;
        List<Long> ids = List.of(101L, 102L);
        given(memberRepository.findAllCurrentMemberWithRoleLessThanAndFirstAppearanceBeforeThan(chatId, role, threshold))
                .willReturn(ids);
        List<Long> result = memberService.getAllCurrentChatMembersWithRoleLessThanAndFirstAppearanceBeforeThan(chatId, role, threshold);
        assertThat(result).isEqualTo(ids);
    }

    @Test
    void shouldRemovePositiveRoleFromExitedMembers() {
        int callerRole = 50;
        MemberService spy = spy(memberService);
        doReturn(callerRole).when(spy).getMemberRolePriority(chatId, fromId);
        RoleDto roleDto = new RoleDto("caller", callerRole);
        given(roleService.getRoleByPriority(chatId, callerRole)).willReturn(Optional.of(roleDto));
        List<Long> memberIds = List.of(101L, 102L);
        given(memberRepository.removePositiveRoleFromExitedMembersAndReturnIds(chatId, callerRole)).willReturn(memberIds);

        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        map.put(101L, Optional.of(createMemberDto(101L, callerRole, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false)));
        map.put(102L, Optional.of(createMemberDto(102L, callerRole, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false)));
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map);

        RoleDto result = spy.removePositiveRoleFromExitedMembers(chatId, fromId);
        assertThat(result).isSameAs(roleDto);
        verify(memberRepository).removePositiveRoleFromExitedMembersAndReturnIds(chatId, callerRole);
        assertThat(map.get(101L).get().getRolePriority()).isEqualTo(MEMBER.getRolePriority());
        assertThat(map.get(102L).get().getRolePriority()).isEqualTo(MEMBER.getRolePriority());
    }

    @Test
    void shouldNotRemoveIfCallerRoleIsMemberOrLower() {
        int callerRole = MEMBER.getRolePriority();
        MemberService spy = spy(memberService);
        doReturn(callerRole).when(spy).getMemberRolePriority(chatId, fromId);
        RoleDto roleDto = new RoleDto( "member", callerRole);
        given(roleService.getRoleByPriority(chatId, callerRole)).willReturn(Optional.of(roleDto));

        RoleDto result = spy.removePositiveRoleFromExitedMembers(chatId, fromId);
        assertThat(result).isSameAs(roleDto);
        verify(memberRepository, never()).removePositiveRoleFromExitedMembersAndReturnIds(anyLong(), anyInt());
    }

    @Test
    void shouldAssignNewRoleToMember() {
        int newRole = 30;
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setRolePriority(MEMBER.getRolePriority());
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.of(entity));
        RoleDto roleToAssign = new RoleDto("newRole", newRole);
        given(roleService.getRoleByPriority(chatId, newRole)).willReturn(Optional.of(roleToAssign));
        given(roleService.getRoleByPriority(chatId, MEMBER.getRolePriority())).willReturn(Optional.of(new RoleDto("member", MEMBER.getRolePriority())));
        MemberService spy = spy(memberService);
        doNothing().when(spy).checkMemberInteractionAbility(chatId, fromId, userId, true);
        doNothing().when(roleService).checkRoleInteractionAbility(chatId, newRole, fromId);
        given(memberRepository.save(entity)).willReturn(entity);
        MemberDto dto = createMemberDto(userId, newRole, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        given(memberMapper.toMemberDto(any(MemberEntity.class))).willReturn(dto); // тоже лучше заменить на any()
        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map); // исправлено

        AssignMemberResult result = spy.assignNewRoleToMember(chatId, userId, newRole, fromId);

        assertThat(result.getPreviousRole()).isNotNull();
        assertThat(result.getNewRole()).isSameAs(roleToAssign);
        assertThat(entity.getRolePriority()).isEqualTo(newRole);
        verify(spy).checkMemberInteractionAbility(chatId, fromId, userId, true);
        verify(roleService).checkRoleInteractionAbility(chatId, newRole, fromId);
        verify(memberRepository).save(entity);
        assertThat(map).containsKey(userId);
    }

    @Test
    void shouldThrowWhenUserToAssignIsSelf() {
        assertThatThrownBy(() -> memberService.assignNewRoleToMember(chatId, fromId, 30, fromId))
                .isInstanceOf(CannotApplyThisCommandToYourselfException.class);
        verify(memberRepository, never()).findByChatIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void shouldThrowWhenMemberNotFound() {
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> memberService.assignNewRoleToMember(chatId, userId, 30, fromId))
                .isInstanceOf(UserNeverBeenInChatException.class);
    }

    @Test
    void shouldThrowWhenRoleAlreadyAssigned() {
        MemberEntity entity = new MemberEntity();
        entity.setRolePriority(30);
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.of(entity));
        RoleDto roleToAssign = new RoleDto("role", 30);
        given(roleService.getRoleByPriority(chatId, 30)).willReturn(Optional.of(roleToAssign));
        assertThatThrownBy(() -> memberService.assignNewRoleToMember(chatId, userId, 30, fromId))
                .isInstanceOf(MemberAlreadyHasThisRoleException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldNotThrowWhenCallerHasHigherRole() {
        MemberService spy = spy(memberService);
        doReturn(50).when(spy).getMemberRolePriority(chatId, fromId);
        MemberDto target = createMemberDto(userId, 30, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        doReturn(Optional.of(target)).when(spy).getCachedMemberInfo(chatId, userId);

        spy.checkMemberInteractionAbility(chatId, fromId, userId, false);
    }

    @Test
    void shouldThrowWhenTargetHasHigherRole() {
        MemberService spy = spy(memberService);
        doReturn(30).when(spy).getMemberRolePriority(chatId, fromId);
        MemberDto target = createMemberDto(userId, 50, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        doReturn(Optional.of(target)).when(spy).getCachedMemberInfo(chatId, userId);

        assertThatThrownBy(() -> spy.checkMemberInteractionAbility(chatId, fromId, userId, false))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void shouldThrowWhenEqualAndBelowSeniorAdmin() {
        MemberService spy = spy(memberService);
        doReturn(30).when(spy).getMemberRolePriority(chatId, fromId);
        MemberDto target = createMemberDto(userId, 30, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        doReturn(Optional.of(target)).when(spy).getCachedMemberInfo(chatId, userId);

        assertThatThrownBy(() -> spy.checkMemberInteractionAbility(chatId, fromId, userId, false))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void shouldNotThrowWhenEqualAndSeniorAdminOrHigher() {
        MemberService spy = spy(memberService);
        doReturn(SENIOR_ADMINISTRATOR.getRolePriority()).when(spy).getMemberRolePriority(chatId, fromId);
        MemberDto target = createMemberDto(userId, SENIOR_ADMINISTRATOR.getRolePriority(), null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        doReturn(Optional.of(target)).when(spy).getCachedMemberInfo(chatId, userId);

        spy.checkMemberInteractionAbility(chatId, fromId, userId, false);
    }

    @Test
    void shouldThrowWhenImmunityBlocks() {
        MemberService spy = spy(memberService);
        doReturn(30).when(spy).getMemberRolePriority(chatId, fromId);
        MemberDto target = createMemberDto(userId, 20, 30, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        doReturn(Optional.of(target)).when(spy).getCachedMemberInfo(chatId, userId);

        assertThatThrownBy(() -> spy.checkMemberInteractionAbility(chatId, fromId, userId, true))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void shouldReturnTrueIfChatAdmin() {
        MemberDto dto = createMemberDto(userId, 0, null, true, MemberPresenceType.IN_CHAT, Instant.now(), false);
        MemberService spy = spy(memberService);
        doReturn(Optional.of(dto)).when(spy).getCachedMemberInfo(chatId, userId);
        boolean result = spy.isChatAdmin(chatId, userId);
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseIfNotAdmin() {
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        MemberService spy = spy(memberService);
        doReturn(Optional.of(dto)).when(spy).getCachedMemberInfo(chatId, userId);
        boolean result = spy.isChatAdmin(chatId, userId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseIfMemberNotFound() {
        MemberService spy = spy(memberService);
        doReturn(Optional.empty()).when(spy).getCachedMemberInfo(chatId, userId);
        boolean result = spy.isChatAdmin(chatId, userId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldGetAllChatAdmins() {
        List<Long> admins = List.of(101L, 102L);
        given(memberRepository.findAllChatAdmins(chatId)).willReturn(admins);
        List<Long> result = memberService.getAllChatAdmins(chatId);
        assertThat(result).isEqualTo(admins);
    }

    @Test
    void shouldReturnMemberFromCache() {
        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        map.put(userId, Optional.of(dto));
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map); // исправлено
        given(globalUserService.getUserFullNameInRequiredCase(userId, NameCase.NOMINATIVE)).willReturn("User Name");

        Optional<MemberDto> result = memberService.getCachedMemberInfo(chatId, userId);
        assertThat(result).contains(dto);
        verify(memberRepository, never()).findByChatIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void shouldLoadFromRepositoryWhenNotInCache() {
        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map);
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        given(memberRepository.findByChatIdAndUserId(eq(chatId), eq(userId))).willReturn(Optional.of(entity));
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        given(memberMapper.toMemberDto(any(MemberEntity.class))).willReturn(dto);
        given(globalUserService.getUserFullNameInRequiredCase(eq(userId), eq(NameCase.NOMINATIVE))).willReturn("User Name");

        Optional<MemberDto> result = memberService.getCachedMemberInfo(chatId, userId);
        assertThat(result).contains(dto);
        assertThat(map).containsKey(userId);
        verify(memberRepository).findByChatIdAndUserId(chatId, userId);
    }

    @Test
    void shouldFindByFullNameInCache() {
        ConcurrentHashMap<Long, Optional<MemberDto>> activeMap = new ConcurrentHashMap<>();
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        activeMap.put(userId, Optional.of(dto));
        given(activeMembersCache.get(eq(chatId), any())).willReturn(activeMap);

        ConcurrentHashMap<NameCase, String> nameMap = new ConcurrentHashMap<>();
        nameMap.put(NameCase.NOMINATIVE, "Ivan Petrov");
        given(fullNameCache.getIfPresent(userId)).willReturn(nameMap);

        Optional<Long> result = memberService.findCurrentMemberByFirstNameOrLastName(chatId, "Ivan");
        assertThat(result).contains(userId);
        verify(memberRepository, never()).findCurrentMemberByFullName(anyLong(), anyString(), any());
    }

    @Test
    void shouldSearchInRepositoryWhenNotFoundInCache() {
        ConcurrentHashMap<Long, Optional<MemberDto>> activeMap = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(activeMap);
        MemberRepository.MemberIdAndNameProjection proj = mock(MemberRepository.MemberIdAndNameProjection.class);
        given(proj.getUserId()).willReturn(userId);
        given(proj.getFullName()).willReturn("Ivan Petrov");
        given(memberRepository.findCurrentMemberByFullName(eq(chatId), eq("Ivan"), any(PageRequest.class)))
                .willReturn(List.of(proj));

        doNothing().when(fullNameCache).put(anyLong(), any());

        Optional<Long> result = memberService.findCurrentMemberByFirstNameOrLastName(chatId, "Ivan");
        assertThat(result).contains(userId);
        verify(memberRepository).findCurrentMemberByFullName(eq(chatId), eq("Ivan"), any(PageRequest.class));
        verify(fullNameCache).put(eq(userId), any());
    }

    @Test
    void shouldAssignImmunityToMember() {
        int newImmuneRole = 40;
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setImmuneRolePriority(null);
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.of(entity));
        RoleDto newImmuneRoleDto = new RoleDto("immune", newImmuneRole);
        given(roleService.getRoleByPriority(chatId, newImmuneRole)).willReturn(Optional.of(newImmuneRoleDto));
        MemberService spy = spy(memberService);
        doNothing().when(spy).checkMemberInteractionAbility(chatId, fromId, userId, true);
        doNothing().when(roleService).checkRoleInteractionAbility(chatId, newImmuneRole, fromId);
        given(memberRepository.save(entity)).willReturn(entity);
        MemberDto dto = createMemberDto(userId, 0, newImmuneRole, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        given(memberMapper.toMemberDto(any(MemberEntity.class))).willReturn(dto);
        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map);

        RoleDto result = spy.assignImmunityToMember(chatId, userId, newImmuneRole, fromId);
        assertThat(result).isSameAs(newImmuneRoleDto);
        assertThat(entity.getImmuneRolePriority()).isEqualTo(newImmuneRole);
        verify(memberRepository).save(entity);
        assertThat(map).containsKey(userId);
    }

    @Test
    void shouldThrowWhenImmunityAlreadySame() {
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setImmuneRolePriority(40);
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.of(entity));
        RoleDto newImmuneRoleDto = new RoleDto("immune", 40);
        given(roleService.getRoleByPriority(chatId, 40)).willReturn(Optional.of(newImmuneRoleDto));
        assertThatThrownBy(() -> memberService.assignImmunityToMember(chatId, userId, 40, fromId))
                .isInstanceOf(MemberAlreadyHasThisImmunityException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldRemoveImmunity() {
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setImmuneRolePriority(30);
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.of(entity));
        MemberService spy = spy(memberService);
        doNothing().when(spy).checkMemberInteractionAbility(chatId, fromId, userId, false);
        given(memberRepository.save(entity)).willReturn(entity);
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        given(memberMapper.toMemberDto(any(MemberEntity.class))).willReturn(dto);
        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map);

        spy.removeImmunityFromMember(chatId, userId, fromId);
        assertThat(entity.getImmuneRolePriority()).isNull();
        verify(memberRepository).save(entity);
        assertThat(map).containsKey(userId);
    }

    @Test
    void shouldSetDmResponsesSetting() {
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setDmResponsesEnabled(false);
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.of(entity));
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        given(memberMapper.toMemberDto(any(MemberEntity.class))).willReturn(dto);
        ConcurrentHashMap<Long, Optional<MemberDto>> map = new ConcurrentHashMap<>();
        given(activeMembersCache.get(eq(chatId), any())).willReturn(map);

        memberService.setDmResponsesSetting(chatId, userId, true);
        assertThat(entity.isDmResponsesEnabled()).isTrue();
        verify(memberRepository, never()).save(any());
        assertThat(map).containsKey(userId);
    }

    @Test
    void shouldThrowWhenNoImmunity() {
        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setImmuneRolePriority(null);
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.of(entity));
        assertThatThrownBy(() -> memberService.removeImmunityFromMember(chatId, userId, fromId))
                .isInstanceOf(MemberHasNoImmunityException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenMemberNotFoundForDmSetting() {
        given(memberRepository.findByChatIdAndUserId(chatId, userId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> memberService.setDmResponsesSetting(chatId, userId, true))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldGetDmResponsesEnabled() {
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, Instant.now(), true);
        MemberService spy = spy(memberService);
        doReturn(Optional.of(dto)).when(spy).getCachedMemberInfo(chatId, userId);
        boolean result = spy.isDmResponsesEnabled(chatId, userId);
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseIfDmResponsesNotSet() {
        MemberDto dto = createMemberDto(userId, 0, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
        MemberService spy = spy(memberService);
        doReturn(Optional.of(dto)).when(spy).getCachedMemberInfo(chatId, userId);
        boolean result = spy.isDmResponsesEnabled(chatId, userId);
        assertThat(result).isFalse();
    }
}