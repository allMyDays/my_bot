package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.MemberPermissionEntity;
import com.example.my_bot.repository.permission.MemberPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MemberPermissionRepositoryIntegrationTest {

    @Autowired
    private MemberPermissionRepository memberPermissionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final Long chatId1 = 1L;
    private final Long chatId2 = 2L;
    private final Long userId1 = 101L;
    private final Long userId2 = 102L;
    private final String command1 = "/start";
    private final String command2 = "/help";
    private final String command3 = "/settings";

    @BeforeEach
    void setUp() {
        MemberPermissionEntity perm1 = new MemberPermissionEntity(chatId1, command1, userId1, true);
        MemberPermissionEntity perm2 = new MemberPermissionEntity(chatId1, command2, userId1, false);
        MemberPermissionEntity perm3 = new MemberPermissionEntity(chatId1, command1, userId2, true);

        MemberPermissionEntity perm4 = new MemberPermissionEntity(chatId2, command3, userId1, true);

        entityManager.persistAndFlush(perm1);
        entityManager.persistAndFlush(perm2);
        entityManager.persistAndFlush(perm3);
        entityManager.persistAndFlush(perm4);
        entityManager.clear();
    }

    @Test
    void findByChatId_shouldReturnPermissionsForGivenChat() {
        List<MemberPermissionEntity> permissionsForChat1 = memberPermissionRepository.findByChatId(chatId1);
        List<MemberPermissionEntity> permissionsForChat2 = memberPermissionRepository.findByChatId(chatId2);

        assertThat(permissionsForChat1).hasSize(3);
        assertThat(permissionsForChat1).extracting(MemberPermissionEntity::getUserId)
                .containsExactlyInAnyOrder(userId1, userId1, userId2);
        assertThat(permissionsForChat1).extracting(MemberPermissionEntity::getCommandName)
                .containsExactlyInAnyOrder(command1, command2, command1);

        assertThat(permissionsForChat2).hasSize(1);
        assertThat(permissionsForChat2.get(0).getCommandName()).isEqualTo(command3);
    }

    @Test
    void deleteMemberPermissionForOneCommand_shouldDeleteSpecificPermission() {
        int deleted = memberPermissionRepository.deleteMemberPermissionForOneCommand(chatId1, command2, userId1);
        assertThat(deleted).isEqualTo(1);

        List<MemberPermissionEntity> after = memberPermissionRepository.findByChatId(chatId1);
        assertThat(after).hasSize(2);
        assertThat(after).extracting(MemberPermissionEntity::getCommandName)
                .doesNotContain(command2);
    }

    @Test
    void deleteMemberPermissionForOneCommand_shouldReturnZeroWhenPermissionNotFound() {
        int deleted = memberPermissionRepository.deleteMemberPermissionForOneCommand(chatId1, command3, userId1);
        assertThat(deleted).isZero();
    }

    @Test
    void updateUserPermissionsForRequiredCommands_shouldUpdatePermissionsForSpecifiedCommands() {
        Set<String> commandsToUpdate = Set.of(command1, command2);
        Long userId = userId1;

        List<MemberPermissionEntity> before = memberPermissionRepository.findByChatId(chatId1);
        MemberPermissionEntity beforePerm1 = before.stream()
                .filter(p -> p.getUserId().equals(userId) && p.getCommandName().equals(command1))
                .findFirst().get();
        assertThat(beforePerm1.isAllowed()).isTrue();

        int updated = memberPermissionRepository.updateUserPermissionsForRequiredCommands(
                chatId1, commandsToUpdate, userId, false);
        assertThat(updated).isEqualTo(2);

        entityManager.flush();
        entityManager.clear();

        List<MemberPermissionEntity> after = memberPermissionRepository.findByChatId(chatId1);
        for (MemberPermissionEntity p : after) {
            if (p.getUserId().equals(userId) && commandsToUpdate.contains(p.getCommandName())) {
                assertThat(p.isAllowed()).isFalse();
            }
        }

        MemberPermissionEntity permUserId2 = after.stream()
                .filter(p -> p.getUserId().equals(userId2) && p.getCommandName().equals(command1))
                .findFirst().get();
        assertThat(permUserId2.isAllowed()).isTrue();
    }

    @Test
    void updateUserPermissionsForRequiredCommands_shouldReturnZeroWhenNoCommandsMatch() {
        Set<String> commandsToUpdate = Set.of("non_existent_command");
        int updated = memberPermissionRepository.updateUserPermissionsForRequiredCommands(
                chatId1, commandsToUpdate, userId1, false);
        assertThat(updated).isZero();
    }
}