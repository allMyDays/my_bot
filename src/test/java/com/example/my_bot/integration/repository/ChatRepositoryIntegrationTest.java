package com.example.my_bot.integration.repository;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.repository.chat.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ChatRepositoryIntegrationTest {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final Long chatId1 = 1L;
    private final Long chatId2 = 2L;
    private final Long chatId3 = 3L;
    private final Long boundLogChat1 = 100L;
    private final Long boundLogChat2 = 200L;
    private final Long submanagerId = 10L;
    private final Long submanagerChatId1 = 101L;
    private final Long submanagerChatId2 = 102L;
    private final String chatCode1 = "CODE_A";
    private final String chatCode2 = "CODE_B";
    private final TimeZoneType timeZoneType = TimeZoneType.GMT_PLUS_3;

    @BeforeEach
    void setUp() {
        // Чат 1 (с сабменеджером, subPostsEnabled = true)
        ChatEntity chat1 = new ChatEntity(chatId1);
        chat1.setChatTitle("Chat One");
        chat1.setChatCode(chatCode1);
        chat1.setBoundLogChat(boundLogChat1);
        chat1.setBoundSubmanagerId(submanagerId);
        chat1.setSubmanagerChatId(submanagerChatId1);
        chat1.setSubPostsEnabled(true);
        chat1.setTimeZoneType(timeZoneType);
        chat1.setSilentRestriction(false);
        chat1.setMessageReplying(true);
        chat1.setWarnMaxQuantity(5);
        chat1.setAutoUnban(true);
        chat1.setBanTimePeriodSec(3600L);
        chat1.setWarnTimePeriodSec(7200L);
        chat1.setPrefix('!');
        chat1.setLastSyncTime(null);

        // Чат 2 (с сабменеджером, subPostsEnabled = false)
        ChatEntity chat2 = new ChatEntity(chatId2);
        chat2.setChatTitle("Chat Two");
        chat2.setChatCode(chatCode2);
        chat2.setBoundLogChat(boundLogChat2);
        chat2.setBoundSubmanagerId(submanagerId);
        chat2.setSubmanagerChatId(submanagerChatId2);
        chat2.setSubPostsEnabled(false);
        chat2.setTimeZoneType(timeZoneType);
        chat2.setSilentRestriction(true);
        chat2.setMessageReplying(false);
        chat2.setWarnMaxQuantity(3);
        chat2.setAutoUnban(false);
        chat2.setBanTimePeriodSec(1800L);
        chat2.setWarnTimePeriodSec(null);
        chat2.setPrefix(null);
        chat2.setLastSyncTime(null);

        // Чат 3 (без сабменеджера)
        ChatEntity chat3 = new ChatEntity(chatId3);
        chat3.setChatTitle("Chat Three");
        chat3.setChatCode("CODE_C");
        chat3.setBoundLogChat(boundLogChat1);
        chat3.setBoundSubmanagerId(null);
        chat3.setSubmanagerChatId(null);
        chat3.setSubPostsEnabled(true);
        chat3.setTimeZoneType(timeZoneType);
        chat3.setSilentRestriction(false);
        chat3.setMessageReplying(true);
        chat3.setWarnMaxQuantity(2);
        chat3.setAutoUnban(true);
        chat3.setBanTimePeriodSec(null);
        chat3.setWarnTimePeriodSec(null);
        chat3.setPrefix(null);
        chat3.setLastSyncTime(null);

        entityManager.persistAndFlush(chat1);
        entityManager.persistAndFlush(chat2);
        entityManager.persistAndFlush(chat3);
        entityManager.clear();
    }

    @Test
    void findByChatCode_shouldReturnChatWhenExists() {
        Optional<ChatEntity> found = chatRepository.findByChatCode(chatCode1);
        Optional<ChatEntity> notFound = chatRepository.findByChatCode("NON_EXISTENT");

        assertThat(found).isPresent();
        assertThat(found.get().getChatId()).isEqualTo(chatId1);
        assertThat(notFound).isEmpty();
    }

    @Test
    void existsByBoundLogChat_shouldReturnTrueWhenExists() {
        boolean exists1 = chatRepository.existsByBoundLogChat(boundLogChat1);
        boolean exists2 = chatRepository.existsByBoundLogChat(999L);

        assertThat(exists1).isTrue();
        assertThat(exists2).isFalse();
    }

    @Test
    void findByBoundLogChat_shouldReturnChatsWithGivenBoundLogChat() {
        List<ChatEntity> chats = chatRepository.findByBoundLogChat(boundLogChat1);

        assertThat(chats).hasSize(2);
        assertThat(chats).extracting(ChatEntity::getChatId)
                .containsExactlyInAnyOrder(chatId1, chatId3);
    }

    @Test
    void findMainChatIdBySubmanagerChatId_shouldReturnMainChatIdWhenExists() {
        Optional<Long> mainChatId = chatRepository.findMainChatIdBySubmanagerChatId(submanagerId, submanagerChatId1);

        assertThat(mainChatId).isPresent();
        assertThat(mainChatId.get()).isEqualTo(chatId1);

        Optional<Long> notFound = chatRepository.findMainChatIdBySubmanagerChatId(submanagerId, 999L);
        assertThat(notFound).isEmpty();
    }

    @Test
    void findSubmanagerChatIdByMainChatId_shouldReturnSubmanagerChatIdWhenExists() {
        Optional<Long> submanagerChatId = chatRepository.findSubmanagerChatIdByMainChatId(submanagerId, chatId1);

        assertThat(submanagerChatId).isPresent();
        assertThat(submanagerChatId.get()).isEqualTo(submanagerChatId1);

        Optional<Long> notFound = chatRepository.findSubmanagerChatIdByMainChatId(submanagerId, 999L);
        assertThat(notFound).isEmpty();
    }

    @Test
    void findChatsByBoundSubmanagerAndSubPostsEnabled_shouldReturnOnlyEnabledChats() {
        List<ChatEntity> chats = chatRepository.findChatsByBoundSubmanagerAndSubPostsEnabled(submanagerId);

        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).getChatId()).isEqualTo(chatId1);
        assertThat(chats.get(0).isSubPostsEnabled()).isTrue();
    }
}