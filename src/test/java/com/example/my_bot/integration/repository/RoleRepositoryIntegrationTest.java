package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RoleRepositoryIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final Long chatId1 = 1L;
    private final Long chatId2 = 2L;

    @BeforeEach
    void setUp() {
        RoleEntity role1 = new RoleEntity(chatId1, 1, "ADMIN");
        RoleEntity role2 = new RoleEntity(chatId1, 2, "USER");
        RoleEntity role3 = new RoleEntity(chatId2, 1, "MODERATOR");
        RoleEntity role4 = new RoleEntity(chatId2, 3, "ADMIN"); // приоритет 3

        entityManager.persist(role1);
        entityManager.persist(role2);
        entityManager.persist(role3);
        entityManager.persist(role4);
        entityManager.flush();
    }

    @Test
    void findByChatId_shouldReturnRolesForGivenChat() {
        // when
        List<RoleEntity> rolesForChat1 = roleRepository.findByChatId(chatId1);
        List<RoleEntity> rolesForChat2 = roleRepository.findByChatId(chatId2);

        // then
        assertThat(rolesForChat1).hasSize(2);
        assertThat(rolesForChat1).extracting(RoleEntity::getRoleName)
                .containsExactlyInAnyOrder("ADMIN", "USER");

        assertThat(rolesForChat2).hasSize(2);
        assertThat(rolesForChat2).extracting(RoleEntity::getRoleName)
                .containsExactlyInAnyOrder("MODERATOR", "ADMIN");
    }

    @Test
    void findByChatIdAndRolePriority_shouldReturnRoleWhenExists() {
        // when
        Optional<RoleEntity> found = roleRepository.findByChatIdAndRolePriority(chatId1, 1);
        Optional<RoleEntity> notFound = roleRepository.findByChatIdAndRolePriority(chatId1, 99);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getRoleName()).isEqualTo("ADMIN");
        assertThat(notFound).isEmpty();
    }

    @Test
    void findByChatIdAndRoleName_shouldReturnRoleWhenExists() {
        // when
        Optional<RoleEntity> found = roleRepository.findByChatIdAndRoleName(chatId2, "MODERATOR");
        Optional<RoleEntity> notFound = roleRepository.findByChatIdAndRoleName(chatId2, "USER");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getRolePriority()).isEqualTo(1);
        assertThat(notFound).isEmpty();
    }

    @Test
    void updateRoleName_shouldUpdateRoleNameAndReturnAffectedRows() {
        // given
        String newName = "SUPER_ADMIN";

        // when
        int updatedRows = roleRepository.updateRoleName(chatId1, 1L, newName);

        // then
        assertThat(updatedRows).isEqualTo(1);

        entityManager.flush();
        entityManager.clear();

        Optional<RoleEntity> updatedOpt = roleRepository.findByChatIdAndRolePriority(chatId1, 1);
        assertThat(updatedOpt).isPresent();
        RoleEntity updated = updatedOpt.get();
        assertThat(updated.getRoleName()).isEqualTo(newName);

        Optional<RoleEntity> otherOpt = roleRepository.findByChatIdAndRolePriority(chatId1, 2);
        assertThat(otherOpt).isPresent();
        RoleEntity other = otherOpt.get();
        assertThat(other.getRoleName()).isEqualTo("USER");
    }

    @Test
    void updateRoleName_shouldReturnZeroWhenRoleNotFound() {
        // when
        int updatedRows = roleRepository.updateRoleName(chatId1, 99L, "NEW_NAME");

        // then
        assertThat(updatedRows).isZero();
    }

    @Test
    void findByChatIdAndRoleNameIgnoreCase_shouldFindRoleIgnoringCase() {
        // given
        String nameLower = "admin";
        String nameUpper = "ADMIN";

        // when
        Optional<RoleEntity> foundLower = roleRepository.findByChatIdAndRoleNameIgnoreCase(chatId1, nameLower);
        Optional<RoleEntity> foundUpper = roleRepository.findByChatIdAndRoleNameIgnoreCase(chatId1, nameUpper);

        // then
        assertThat(foundLower).isPresent();
        assertThat(foundLower.get().getRolePriority()).isEqualTo(1);
        assertThat(foundUpper).isPresent();
        assertThat(foundUpper.get().getRolePriority()).isEqualTo(1);
    }

    @Test
    void findByChatIdAndRolePriorityIn_shouldReturnRolesWithPrioritiesInSet() {
        // given
        Set<Integer> priorities = Set.of(1, 3);

        // when
        List<RoleEntity> roles = roleRepository.findByChatIdAndRolePriorityIn(chatId2, priorities);

        // then
        assertThat(roles).hasSize(2);
        assertThat(roles).extracting(RoleEntity::getRolePriority)
                .containsExactlyInAnyOrder(1, 3);
    }

    @Test
    void deleteByChatIdAndRolePriority_shouldDeleteRoleAndReturnAffectedRows() {

        Optional<RoleEntity> before = roleRepository.findByChatIdAndRolePriority(chatId2, 1);
        assertThat(before).isPresent();

        // when
        int deletedRows = roleRepository.deleteByChatIdAndRolePriority(chatId2, 1);

        // then
        assertThat(deletedRows).isEqualTo(1);

        Optional<RoleEntity> after = roleRepository.findByChatIdAndRolePriority(chatId2, 1);
        assertThat(after).isEmpty();

        List<RoleEntity> remaining = roleRepository.findByChatId(chatId2);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getRolePriority()).isEqualTo(3);
    }

    @Test
    void deleteByChatIdAndRolePriority_shouldReturnZeroWhenRoleNotFound() {
        // when
        int deletedRows = roleRepository.deleteByChatIdAndRolePriority(chatId2, 99);

        // then
        assertThat(deletedRows).isZero();
    }
}