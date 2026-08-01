package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.RolePermissionEntity;
import com.example.my_bot.repository.permission.RolePermissionRepository;
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
class RolePermissionRepositoryIntegrationTest {

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final Long chatId1 = 1L;
    private final Long chatId2 = 2L;
    private final String command1 = "/start";
    private final String command2 = "/help";
    private final String command3 = "/settings";
    private final int priorityAdmin = 10;
    private final int priorityModerator = 5;
    private final int priorityUser = 1;

    @BeforeEach
    void setUp() {
        RolePermissionEntity perm1 = new RolePermissionEntity(chatId1, command1, priorityAdmin);
        RolePermissionEntity perm2 = new RolePermissionEntity(chatId1, command2, priorityModerator);
        RolePermissionEntity perm3 = new RolePermissionEntity(chatId1, command3, priorityUser);

        // Разрешение для chatId2
        RolePermissionEntity perm4 = new RolePermissionEntity(chatId2, command1, priorityModerator);

        entityManager.persistAndFlush(perm1);
        entityManager.persistAndFlush(perm2);
        entityManager.persistAndFlush(perm3);
        entityManager.persistAndFlush(perm4);
        entityManager.clear();
    }

    @Test
    void findByChatId_shouldReturnPermissionsForGivenChat() {
        // when
        List<RolePermissionEntity> permissionsForChat1 = rolePermissionRepository.findByChatId(chatId1);
        List<RolePermissionEntity> permissionsForChat2 = rolePermissionRepository.findByChatId(chatId2);

        // then
        assertThat(permissionsForChat1).hasSize(3);
        assertThat(permissionsForChat1).extracting(RolePermissionEntity::getCommandName)
                .containsExactlyInAnyOrder(command1, command2, command3);
        assertThat(permissionsForChat1).extracting(RolePermissionEntity::getRolePriority)
                .containsExactlyInAnyOrder(priorityAdmin, priorityModerator, priorityUser);

        assertThat(permissionsForChat2).hasSize(1);
        assertThat(permissionsForChat2.get(0).getCommandName()).isEqualTo(command1);
        assertThat(permissionsForChat2.get(0).getRolePriority()).isEqualTo(priorityModerator);
    }

    @Test
    void updateRolePermissionForRequiredCommands_shouldUpdatePriorityForSpecifiedCommands() {
        // given
        Set<String> commandsToUpdate = Set.of(command1, command2);
        int newPriority = 20;

        // when
        int updated = rolePermissionRepository.updateRolePermissionForRequiredCommands(
                chatId1, commandsToUpdate, newPriority
        );

        // then
        assertThat(updated).isEqualTo(2);

        entityManager.flush();
        entityManager.clear();

        List<RolePermissionEntity> permissions = rolePermissionRepository.findByChatId(chatId1);
        for (RolePermissionEntity p : permissions) {
            if (commandsToUpdate.contains(p.getCommandName())) {
                assertThat(p.getRolePriority()).isEqualTo(newPriority);
            } else {
                assertThat(p.getRolePriority()).isEqualTo(priorityUser);
            }
        }
    }

    @Test
    void updateRolePermissionForRequiredCommands_shouldReturnZeroWhenNoCommandsMatch() {
        // given
        Set<String> nonExistentCommands = Set.of("non_existent_command");

        // when
        int updated = rolePermissionRepository.updateRolePermissionForRequiredCommands(
                chatId1, nonExistentCommands, 99
        );

        // then
        assertThat(updated).isZero();
    }

    @Test
    void deleteRolePermissionForOneCommand_shouldDeleteSpecificPermission() {
        // given
        List<RolePermissionEntity> before = rolePermissionRepository.findByChatId(chatId1);
        assertThat(before).hasSize(3);

        // when
        int deleted = rolePermissionRepository.deleteRolePermissionForOneCommand(chatId1, command2);

        // then
        assertThat(deleted).isEqualTo(1);

        List<RolePermissionEntity> after = rolePermissionRepository.findByChatId(chatId1);
        assertThat(after).hasSize(2);
        assertThat(after).extracting(RolePermissionEntity::getCommandName)
                .doesNotContain(command2);
    }

    @Test
    void deleteRolePermissionForOneCommand_shouldReturnZeroWhenCommandNotFound() {
        // when
        int deleted = rolePermissionRepository.deleteRolePermissionForOneCommand(chatId1, "non_existent_command");

        // then
        assertThat(deleted).isZero();
    }
}