package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.GlobalUserEntity;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.repository.MemberRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

import static com.example.my_bot.enumeration.member.MemberPresenceType.UNKNOWN_LEAVE;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MemberRepositoryIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final long chatId = 1L;
    private final long userId1 = 101L;
    private final long userId2 = 102L;
    private final long userId3 = 103L;
    private final long userId4 = 104L;
    private final long inviterId = 1000L;

    private Instant now;

    @BeforeAll
    static void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @BeforeEach
    void setUp() {
        now = Instant.now();

        // Глобальные пользователи для проекции
        GlobalUserEntity user1 = new GlobalUserEntity();
        user1.setUserId(userId1);
        user1.setFullNameInNom("Алексей Иванов");

        GlobalUserEntity user2 = new GlobalUserEntity();
        user2.setUserId(userId2);
        user2.setFullNameInNom("Мария Петрова");

        GlobalUserEntity user3 = new GlobalUserEntity();
        user3.setUserId(userId3);
        user3.setFullNameInNom("Иван Сидоров");

        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.persist(user3);

        // Члены чата
        MemberEntity member1 = new MemberEntity();
        member1.setChatId(chatId);
        member1.setUserId(userId1);
        member1.setRolePriority(10);
        member1.setChatAdmin(true);
        member1.setPresenceType(MemberPresenceType.IN_CHAT);
        member1.setInvitedById(inviterId);
        member1.setFirstAppearance(now.minusSeconds(120));
        member1.setImmuneRolePriority(null);

        MemberEntity member2 = new MemberEntity();
        member2.setChatId(chatId);
        member2.setUserId(userId2);
        member2.setRolePriority(5);
        member2.setChatAdmin(false);
        member2.setPresenceType(MemberPresenceType.IN_CHAT);
        member2.setInvitedById(inviterId);
        member2.setFirstAppearance(now.minusSeconds(60));
        member2.setImmuneRolePriority(20);

        MemberEntity member3 = new MemberEntity();
        member3.setChatId(chatId);
        member3.setUserId(userId3);
        member3.setRolePriority(0);
        member3.setChatAdmin(false);
        member3.setPresenceType(MemberPresenceType.SELF_LEAVE);
        member3.setInvitedById(inviterId);
        member3.setFirstAppearance(now.minusSeconds(30));
        member3.setImmuneRolePriority(null);

        MemberEntity member4 = new MemberEntity();
        member4.setChatId(chatId);
        member4.setUserId(userId4);
        member4.setRolePriority(3);
        member4.setChatAdmin(false);
        member4.setPresenceType(MemberPresenceType.KICKED);
        member4.setInvitedById(inviterId);
        member4.setFirstAppearance(now.minusSeconds(10));
        member4.setImmuneRolePriority(null);

        entityManager.persistAndFlush(member1);
        entityManager.persistAndFlush(member2);
        entityManager.persistAndFlush(member3);
        entityManager.persistAndFlush(member4);
        entityManager.clear();
    }

    @Test
    void findByChatId_shouldReturnAllMembers() {
        List<MemberEntity> members = memberRepository.findByChatId(chatId);
        assertThat(members).hasSize(4);
        assertThat(members).extracting(MemberEntity::getUserId)
                .containsExactlyInAnyOrder(userId1, userId2, userId3, userId4);
    }

    @Test
    void findMembersWithPositiveRole_shouldReturnMembersWithRolePriorityGreaterThanZero() {
        List<MemberEntity> members = memberRepository.findMembersWithPositiveRole(chatId);
        assertThat(members).hasSize(3);
        assertThat(members).extracting(MemberEntity::getUserId)
                .containsExactlyInAnyOrder(userId1, userId2, userId4);
        assertThat(members).extracting(MemberEntity::getRolePriority)
                .allMatch(p -> p > 0);
    }

    @Test
    void findMembersWithImmunity_shouldReturnMembersWithImmuneRolePriorityNotNull() {
        List<MemberEntity> members = memberRepository.findMembersWithImmunity(chatId);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getUserId()).isEqualTo(userId2);
        assertThat(members.get(0).getImmuneRolePriority()).isEqualTo(20);
    }

    @Test
    void findMembersWithRequiredRole_shouldReturnMembersWithExactRolePriority() {
        List<MemberEntity> members = memberRepository.findMembersWithRequiredRole(chatId, 10L);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getUserId()).isEqualTo(userId1);

        List<MemberEntity> empty = memberRepository.findMembersWithRequiredRole(chatId, 99L);
        assertThat(empty).isEmpty();
    }

    @Test
    void updateMembersRole_shouldUpdateAllMembersWithOldPriority() {
        int updated = memberRepository.updateMembersRole(chatId, 5, 7);
        assertThat(updated).isEqualTo(1);

        List<MemberEntity> members = memberRepository.findMembersWithRequiredRole(chatId, 7L);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getUserId()).isEqualTo(userId2);

        List<MemberEntity> old = memberRepository.findMembersWithRequiredRole(chatId, 5L);
        assertThat(old).isEmpty();
    }

    @Test
    void setUnknownLeaveAndChatAdminFalseForMembersNotInList_shouldUpdateMembers() {
        Set<Long> keepUserIds = Set.of(userId1, userId2);

        int updated = memberRepository.setUnknownLeaveAndChatAdminFalseForMembersNotInList(chatId, keepUserIds);
        assertThat(updated).isEqualTo(2);

        Optional<MemberEntity> member3 = memberRepository.findByChatIdAndUserId(chatId, userId3);
        assertThat(member3).isPresent();
        // presenceType остаётся SELF_LEAVE, т.к. не был IN_CHAT
        assertThat(member3.get().getPresenceType()).isEqualTo(MemberPresenceType.SELF_LEAVE);
        assertThat(member3.get().isChatAdmin()).isFalse();

        Optional<MemberEntity> member4 = memberRepository.findByChatIdAndUserId(chatId, userId4);
        assertThat(member4).isPresent();
        // presenceType остаётся KICKED
        assertThat(member4.get().getPresenceType()).isEqualTo(MemberPresenceType.KICKED);
        assertThat(member4.get().isChatAdmin()).isFalse();

        Optional<MemberEntity> member1 = memberRepository.findByChatIdAndUserId(chatId, userId1);
        assertThat(member1).isPresent();
        assertThat(member1.get().getPresenceType()).isEqualTo(MemberPresenceType.IN_CHAT);
        assertThat(member1.get().isChatAdmin()).isTrue();
    }

    @Test
    void findByChatIdAndUserIdIn_shouldReturnMembersForGivenUserIds() {
        Set<Long> userIds = Set.of(userId1, userId3, 999L);
        List<MemberEntity> members = memberRepository.findByChatIdAndUserIdIn(chatId, userIds);
        assertThat(members).hasSize(2);
        assertThat(members).extracting(MemberEntity::getUserId)
                .containsExactlyInAnyOrder(userId1, userId3);
    }

    @Test
    void findByChatIdAndUserId_shouldReturnMemberWhenExists() {
        Optional<MemberEntity> found = memberRepository.findByChatIdAndUserId(chatId, userId2);
        Optional<MemberEntity> notFound = memberRepository.findByChatIdAndUserId(chatId, 999L);
        assertThat(found).isPresent();
        assertThat(found.get().getRolePriority()).isEqualTo(5);
        assertThat(notFound).isEmpty();
    }

    @Test
    void removePositiveRoleFromExitedMembers_shouldResetRoleForNonInChatMembers() {
        int updated = memberRepository.removePositiveRoleFromExitedMembers(chatId, 10);
        assertThat(updated).isEqualTo(1);

        Optional<MemberEntity> member4 = memberRepository.findByChatIdAndUserId(chatId, userId4);
        assertThat(member4).isPresent();
        assertThat(member4.get().getRolePriority()).isEqualTo(0);

        Optional<MemberEntity> member2 = memberRepository.findByChatIdAndUserId(chatId, userId2);
        assertThat(member2).isPresent();
        assertThat(member2.get().getRolePriority()).isEqualTo(5);
    }

    @Test
    void findLeftButNotKickedMembersWithRoleLessThan_shouldReturnLeftMembers() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<MemberEntity> page = memberRepository.findLeftButNotKickedMembersWithRoleLessThan(chatId, 10, pageable);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(userId3);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findNotKickedCommunitiesWithRoleLessThan_shouldReturnMembersWithNegativeUserId() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<MemberEntity> page = memberRepository.findNotKickedCommunitiesWithRoleLessThan(chatId, 10, pageable);
        // Все userId положительные, поэтому результат пустой
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void findNotKickedMembersInvitedByAndWithRoleLessThan_shouldReturnMembersByInviter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<MemberEntity> page = memberRepository.findNotKickedMembersInvitedByAndWithRoleLessThan(chatId, inviterId, 10, pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).extracting(MemberEntity::getUserId)
                .containsExactlyInAnyOrder(userId2, userId3);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findNotKickedNewMembersWithRoleLessThan_shouldReturnMembersAfterGivenDate() {
        Instant after = now.minusSeconds(40);
        Pageable pageable = PageRequest.of(0, 10);
        Page<MemberEntity> page = memberRepository.findNotKickedNewMembersWithRoleLessThan(chatId, after, 10, pageable);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(userId3);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findAllCurrentMembersByChatId_shouldReturnOnlyInChatUserIds() {
        List<Long> userIds = memberRepository.findAllCurrentMembersByChatId(chatId);
        assertThat(userIds).containsExactlyInAnyOrder(userId1, userId2);
    }

    @Test
    void findAllCurrentMemberWithFirstAppearanceBeforeThan_shouldReturnCurrentMembersBeforeDate() {
        Instant threshold = now.minusSeconds(50);
        List<Long> userIds = memberRepository.findAllCurrentMemberWithFirstAppearanceBeforeThan(chatId, threshold);
        // userId1 (120 сек) и userId2 (60 сек) – оба меньше 50? 60 < 50? Нет, 60 > 50, значит только userId1 подходит.
        // На самом деле условие firstAppearance < threshold, т.е. older than threshold.
        // firstAppearance now-120 < now-50 -> да; now-60 < now-50 -> да, т.к. 60 > 50, значит now-60 меньше than now-50 (поскольку 60 секунд назад – раньше).
        // Так что оба подходят.
        assertThat(userIds).containsExactlyInAnyOrder(userId1, userId2);
    }

    @Test
    void findAllCurrentMemberWithRoleLessThanAndFirstAppearanceBeforeThan_shouldReturnCurrentMembersWithRoleAndDate() {
        Instant threshold = now.minusSeconds(50);
        List<Long> userIds = memberRepository.findAllCurrentMemberWithRoleLessThanAndFirstAppearanceBeforeThan(
                chatId, 10, threshold);
        // role < 10: userId2 (5) подходит, userId1 (10) не подходит. firstAppearance: userId2 now-60 < now-50 => подходит.
        assertThat(userIds).containsExactly(userId2);
    }

    @Test
    void findAllChatAdmins_shouldReturnAdminUserIds() {
        List<Long> admins = memberRepository.findAllChatAdmins(chatId);
        assertThat(admins).containsExactly(userId1);
    }

    @Test
    void findCurrentMemberByFullName_shouldReturnMembersWithMatchingName() {
        String searchName = "Алексей";
        Pageable pageable = PageRequest.of(0, 10);
        List<MemberRepository.MemberIdAndNameProjection> results =
                memberRepository.findCurrentMemberByFullName(chatId, searchName, pageable);
        assertThat(results).hasSize(1);
        MemberRepository.MemberIdAndNameProjection proj = results.get(0);
        assertThat(proj.getUserId()).isEqualTo(userId1);
        assertThat(proj.getFullName()).isEqualTo("Алексей Иванов");
    }
}
