package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.GlobalUserEntity;
import com.example.my_bot.repository.GlobalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class GlobalUserRepositoryIntegrationTest {

    @Autowired
    private GlobalUserRepository globalUserRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final Long boundChat1 = 1L;
    private final Long boundChat2 = 2L;
    private final Long userId1 = 101L;
    private final Long userId2 = 102L;
    private final Long userId3 = 103L;

    @BeforeEach
    void setUp() {
        // Создаём глобальных пользователей с разными boundChat
        GlobalUserEntity user1 = new GlobalUserEntity();
        user1.setUserId(userId1);
        user1.setFullNameInNom("User One");
        user1.setBoundChat(boundChat1);

        GlobalUserEntity user2 = new GlobalUserEntity();
        user2.setUserId(userId2);
        user2.setFullNameInNom("User Two");
        user2.setBoundChat(boundChat1);

        GlobalUserEntity user3 = new GlobalUserEntity();
        user3.setUserId(userId3);
        user3.setFullNameInNom("User Three");
        user3.setBoundChat(boundChat2);

        entityManager.persistAndFlush(user1);
        entityManager.persistAndFlush(user2);
        entityManager.persistAndFlush(user3);
        entityManager.clear();
    }

    @Test
    void findUserIdsByBoundChat_shouldReturnUserIdsForGivenBoundChat() {
        // when
        Set<Long> userIdsForChat1 = globalUserRepository.findUserIdsByBoundChat(boundChat1);
        Set<Long> userIdsForChat2 = globalUserRepository.findUserIdsByBoundChat(boundChat2);
        Set<Long> userIdsForNonExistent = globalUserRepository.findUserIdsByBoundChat(999L);

        // then
        assertThat(userIdsForChat1).containsExactlyInAnyOrder(userId1, userId2);
        assertThat(userIdsForChat2).containsExactlyInAnyOrder(userId3);
        assertThat(userIdsForNonExistent).isEmpty();
    }

    @Test
    void findUserIdsByBoundChat_shouldReturnEmptySetWhenBoundChatHasNoUsers() {
        // given
        Long emptyBoundChat = 100L;

        // when
        Set<Long> result = globalUserRepository.findUserIdsByBoundChat(emptyBoundChat);

        // then
        assertThat(result).isEmpty();
    }
}