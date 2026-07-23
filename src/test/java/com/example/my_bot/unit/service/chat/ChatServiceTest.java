package com.example.my_bot.unit.service.chat;

import com.example.my_bot.cache.key.GroupIdAndChatIdKey;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.exception.chat.ChatEntityAlreadyExistsException;
import com.example.my_bot.exception.chat.ChatEntityNotFoundException;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.exception.chat.LogChatException;
import com.example.my_bot.exception.submanager.CannotFindSubmanagerChatIdByMainChatIdException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.repository.chat.ChatRepository;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.github.benmanes.caffeine.cache.Cache;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;


import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMapper chatMapper;

    @Mock
    private MemberService memberService;

    @Mock
    private Cache<Long, ChatDetailsDto> chatDetailsCache;

    @Mock
    private Cache<GroupIdAndChatIdKey, Optional<Long>> mainChatIdBySubmanagerCache;

    @Mock
    private Cache<GroupIdAndChatIdKey, Optional<Long>> submanagerChatIdByMainCache;

    @InjectMocks
    private ChatService chatService;

    private final long chatId = 123L;
    private final long submanagerId = 456L;
    private final long submanagerChatId = 789L;
    private final long fromId = 100L;
    private final String chatCode = "abc123";
    private final String newTitle = "New Title";
    private final char newPrefix = '!';
    private final TimeZoneType timeZone = TimeZoneType.GMT_PLUS_3;


    @Test
    void shouldGetCachedChatDetailsWhenPresent() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity entity = new ChatEntity();
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatDetailsCache.get(eq(chatId), any())).willReturn(dto);

        ChatDetailsDto result = chatService.getCachedChatDetails(chatId, false);
        assertThat(result).isSameAs(dto);
        verify(chatRepository, never()).findById(anyLong());
        verify(chatRepository, never()).save(any());
    }

    @Test
    void shouldCreateNewChatWhenAbsentAndCreateIfAbsentTrue() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        given(chatDetailsCache.get(eq(chatId), any())).willAnswer(invocation -> {
            Function<Long, ChatDetailsDto> loader = invocation.getArgument(1);
            return loader.apply(chatId);
        });
        given(chatRepository.findById(chatId)).willReturn(Optional.empty());
        given(chatRepository.saveAndFlush(any(ChatEntity.class))).willAnswer(invocation -> {
            ChatEntity entity = invocation.getArgument(0);
            entity.setChatId(chatId);
            return entity;
        });
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(any(ChatEntity.class))).willReturn(dto);

        ChatDetailsDto result = chatService.getCachedChatDetails(chatId, true);
        assertThat(result).isSameAs(dto);
        verify(chatRepository).saveAndFlush(any(ChatEntity.class));
        verify(chatMapper).toChatDetailsDto(any(ChatEntity.class));
    }

    @Test
    void shouldThrowWhenChatNotFoundAndCreateIfAbsentFalse() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        given(chatDetailsCache.get(eq(chatId), any())).willAnswer(invocation -> {
            Function<Long, ChatDetailsDto> loader = invocation.getArgument(1);
            return loader.apply(chatId);
        });
        given(chatRepository.findById(chatId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> chatService.getCachedChatDetails(chatId, false))
                .isInstanceOf(ChatEntityNotFoundException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void shouldReturnChatEntityWhenExists() {
        ChatEntity entity = new ChatEntity();
        given(chatRepository.findById(chatId)).willReturn(Optional.of(entity));
        ChatEntity result = chatService.findByChatIdOrThrow(chatId);
        assertThat(result).isSameAs(entity);
    }

    @Test
    void shouldThrowWhenChatNotFound() {
        given(chatRepository.findById(chatId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> chatService.findByChatIdOrThrow(chatId))
                .isInstanceOf(ChatEntityNotFoundException.class);
    }

    @Test
    void shouldReturnChatByCode() {
        ChatEntity entity = new ChatEntity();
        given(chatRepository.findByChatCode(chatCode)).willReturn(Optional.of(entity));
        ChatEntity result = chatService.findByChatCodeOrThrow(chatCode);
        assertThat(result).isSameAs(entity);
    }

    @Test
    void shouldThrowWhenCodeNotFound() {
        given(chatRepository.findByChatCode(chatCode)).willReturn(Optional.empty());
        assertThatThrownBy(() -> chatService.findByChatCodeOrThrow(chatCode))
                .isInstanceOf(ChatEntityNotFoundException.class);
    }

    @Test
    void shouldSetPrefixSuccessfully() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        chatService.setChatPrefix(chatId, newPrefix);

        assertThat(chat.getPrefix()).isEqualTo(newPrefix);
        verify(chatRepository).save(chat);
        assertThat(map).containsKey(chatId);
    }

    @Test
    void shouldThrowForForbiddenPrefix() {
        char forbidden = '*';
        assertThatThrownBy(() -> chatService.setChatPrefix(chatId, forbidden))
                .isInstanceOf(ForbiddenPrefixException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void shouldDisablePrefix() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setPrefix('!');
        chat.setChatId(chatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        chatService.disableChatPrefix(chatId);

        assertThat(chat.getPrefix()).isNull();
        verify(chatRepository).save(chat);
        assertThat(map).containsKey(chatId);
    }

    @Test
    void shouldSetChatTitle() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        chatService.setChatTitle(chatId, newTitle);

        assertThat(chat.getChatTitle()).isEqualTo(newTitle);
        verify(chatRepository).save(chat);
        assertThat(map).containsKey(chatId);
    }

    @Test
    void shouldToggleSilentRestriction() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setSilentRestriction(false);
        chat.setChatId(chatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        SwitchChatSettingResult result = chatService.switchSilentRestriction(chatId);

        assertThat(result).isEqualTo(SwitchChatSettingResult.ON);
        assertThat(chat.isSilentRestriction()).isTrue();
        verify(chatRepository).save(chat);
        verify(chatDetailsCache).asMap();
    }

    @Test
    void shouldToggleMessageReplying() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        chat.setMessageReplying(true);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        SwitchChatSettingResult result = chatService.switchMessageReplying(chatId);

        assertThat(result).isEqualTo(SwitchChatSettingResult.OFF);
        assertThat(chat.isMessageReplying()).isFalse();
    }

    @Test
    void shouldGetSilentRestriction() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatDetailsDto dto = new ChatDetailsDto();
        dto.setSilentRestriction(true);
        given(chatDetailsCache.get(eq(chatId), any())).willReturn(dto);
        boolean result = chatService.isSilentRestriction(chatId);
        assertThat(result).isTrue();
    }

    @Test
    void shouldGetChatPrefix() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatDetailsDto dto = new ChatDetailsDto();
        dto.setPrefix('!');
        given(chatDetailsCache.get(eq(chatId), any())).willReturn(dto);
        Optional<Character> result = chatService.getChatPrefix(chatId);
        assertThat(result).contains('!');
    }

    @Test
    void shouldGetChatTimeZone() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatDetailsDto dto = new ChatDetailsDto();
        dto.setTimeZoneType(TimeZoneType.GMT_PLUS_3);
        given(chatDetailsCache.get(eq(chatId), any())).willReturn(dto);
        TimeZoneType result = chatService.getChatTimeZone(chatId);
        assertThat(result).isEqualTo(TimeZoneType.GMT_PLUS_3);
    }

    @Test
    void shouldSetChatTimeZone() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        chatService.setChatTimeZone(chatId, timeZone);

        assertThat(chat.getTimeZoneType()).isEqualTo(timeZone);
        verify(chatRepository).save(chat);
        verify(chatDetailsCache).asMap();
    }

    @Test
    void shouldSetBanTimePeriod() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        long period = 3600;
        long result = chatService.setDefaultBanTimePeriod(chatId, period);
        assertThat(result).isEqualTo(period);
        assertThat(chat.getBanTimePeriodSec()).isEqualTo(period);
        verify(chatRepository).save(chat);
        verify(chatDetailsCache).asMap();
    }

    @Test
    void shouldClampBanPeriodToMaxAndMin() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        long result = chatService.setDefaultBanTimePeriod(chatId, BanService.MAX_BAN_TIME_PERIOD_SEC + 100);
        assertThat(result).isEqualTo(BanService.MAX_BAN_TIME_PERIOD_SEC);
        result = chatService.setDefaultBanTimePeriod(chatId, BanService.MIN_BAN_TIME_PERIOD_SEC - 10);
        assertThat(result).isEqualTo(BanService.MIN_BAN_TIME_PERIOD_SEC);
    }

    @Test
    void shouldGetOptionalBanPeriod() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatDetailsDto dto = new ChatDetailsDto();
        dto.setBanTimePeriodSec(100L);
        given(chatDetailsCache.get(eq(chatId), any())).willReturn(dto);
        Optional<Long> result = chatService.getDefaultBanTimePeriod(chatId);
        assertThat(result).contains(100L);
    }

    @Test
    void shouldSetLastSyncToNow() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat =new ChatEntity();
        chat.setChatId(chatId);

        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        given(chatRepository.save(chat)).willReturn(chat);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(chat)).willReturn(dto);
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);

        chatService.setLastSyncToNow(chatId);

        assertThat(chat.getLastSyncTime()).isNotNull();
        verify(chatRepository).save(chat);
        verify(chatDetailsCache).asMap();
    }

    @Test
    void shouldCreateNewChatSuccessfully() {
        given(chatRepository.saveAndFlush(any(ChatEntity.class))).willAnswer(invocation -> {
            ChatEntity entity = invocation.getArgument(0);
            entity.setChatId(chatId);
            return entity;
        });
        ChatEntity result = chatService.createNewChatWithStandardSettings(chatId);
        assertThat(result).isNotNull();
        assertThat(result.getChatId()).isEqualTo(chatId);
        verify(chatRepository).saveAndFlush(any(ChatEntity.class));
    }

    @Test
    void shouldRetryOnUniqueConstraintViolation() {
        DataIntegrityViolationException ex = mock(DataIntegrityViolationException.class);
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        given(cause.getConstraintName()).willReturn("uk_chat_chat_code");
        given(ex.getCause()).willReturn(cause);
        given(chatRepository.saveAndFlush(any(ChatEntity.class)))
                .willThrow(ex)
                .willAnswer(invocation -> invocation.getArgument(0));

        ChatEntity result = chatService.createNewChatWithStandardSettings(chatId);
        assertThat(result).isNotNull();
        verify(chatRepository, times(2)).saveAndFlush(any(ChatEntity.class));
    }

    @Test
    void shouldThrowIfOtherConstraintViolation() {
        DataIntegrityViolationException ex = mock(DataIntegrityViolationException.class);
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        given(cause.getConstraintName()).willReturn("other_constraint");
        given(ex.getCause()).willReturn(cause);
        given(chatRepository.saveAndFlush(any(ChatEntity.class))).willThrow(ex);

        assertThatThrownBy(() -> chatService.createNewChatWithStandardSettings(chatId))
                .isInstanceOf(ChatEntityAlreadyExistsException.class);
    }

    @Test
    void shouldSetLogChatSuccessfully() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity currentChat = new ChatEntity();
        currentChat.setChatId(chatId);
        currentChat.setBoundSubmanagerId(null);
        ChatEntity targetChat = new ChatEntity();
        targetChat.setChatId(999L);
        targetChat.setBoundSubmanagerId(null);
        targetChat.setBoundLogChat(null);

        given(chatRepository.findByChatCode(chatCode)).willReturn(Optional.of(currentChat));
        given(chatRepository.findById(999L)).willReturn(Optional.of(targetChat));
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(SENIOR_ADMINISTRATOR.getRolePriority());
        given(chatRepository.existsByBoundLogChat(chatId)).willReturn(false);

        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatMapper.toChatDetailsDto(currentChat)).willReturn(dto);

        chatService.setLogChat(chatCode, 999L, fromId);

        assertThat(currentChat.getBoundLogChat()).isEqualTo(999L);
        verify(chatDetailsCache).asMap();
        assertThat(map).containsKey(chatId);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenSameChat() {
        ChatEntity current = new ChatEntity();
        current.setChatId(chatId);
        given(chatRepository.findByChatCode(chatCode)).willReturn(Optional.of(current));
        given(chatRepository.findById(chatId)).willReturn(Optional.of(current));

        assertThatThrownBy(() -> chatService.setLogChat(chatCode, chatId, fromId))
                .isInstanceOf(LogChatException.class)
                .hasMessageContaining("не могут быть один и тем же");
    }

    @Test
    void shouldRemoveLogChat() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat1 = new ChatEntity();
        chat1.setChatId(111L);
        ChatEntity chat2 = new ChatEntity();
        chat2.setChatId(222L);
        List<ChatEntity> boundChats = List.of(chat1, chat2);
        given(chatRepository.findByBoundLogChat(chatId)).willReturn(boundChats);

        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);
        given(chatMapper.toChatDetailsDto(any(ChatEntity.class))).willReturn(new ChatDetailsDto());

        chatService.removeLogChat(chatId);

        boundChats.forEach(c -> assertThat(c.getBoundLogChat()).isNull());
        verify(chatDetailsCache, times(boundChats.size())).asMap();
        verify(chatRepository, never()).save(any());
    }

    @Test
    void shouldSetBoundLogChatAsNull() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        chat.setBoundLogChat(999L);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));

        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);
        given(chatMapper.toChatDetailsDto(chat)).willReturn(new ChatDetailsDto());

        chatService.setBoundLogChatAsNull(chatId);

        assertThat(chat.getBoundLogChat()).isNull();
        verify(chatDetailsCache).asMap();
        verify(chatRepository, never()).save(any());
    }

    @Test
    void shouldSetBoundSubmanagerAndUpdateCaches() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        given(cacheManager.getMainChatIdBySubmanagerChatIdCache()).willReturn(mainChatIdBySubmanagerCache);
        given(cacheManager.getSubmanagerChatIdByMainChatIdCache()).willReturn(submanagerChatIdByMainCache);

        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);
        given(chatMapper.toChatDetailsDto(chat)).willReturn(new ChatDetailsDto());

        ConcurrentMap<GroupIdAndChatIdKey, Optional<Long>> mainMap = new ConcurrentHashMap<>();
        ConcurrentMap<GroupIdAndChatIdKey, Optional<Long>> subMap = new ConcurrentHashMap<>();
        given(mainChatIdBySubmanagerCache.asMap()).willReturn(mainMap);
        given(submanagerChatIdByMainCache.asMap()).willReturn(subMap);

        chatService.setBoundSubmanager(chatId, submanagerId, submanagerChatId);

        assertThat(chat.getBoundSubmanagerId()).isEqualTo(Math.abs(submanagerId));
        assertThat(chat.getSubmanagerChatId()).isEqualTo(submanagerChatId);
        verify(chatDetailsCache).asMap();

        GroupIdAndChatIdKey key1 = new GroupIdAndChatIdKey(Math.abs(submanagerId), submanagerChatId);
        GroupIdAndChatIdKey key2 = new GroupIdAndChatIdKey(Math.abs(submanagerId), chatId);
        assertThat(mainMap.get(key1)).contains(chatId);
        assertThat(subMap.get(key2)).contains(submanagerChatId);
    }

    @Test
    void shouldClearBoundSubmanagerAndCaches() {
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        given(cacheManager.getMainChatIdBySubmanagerChatIdCache()).willReturn(mainChatIdBySubmanagerCache);
        given(cacheManager.getSubmanagerChatIdByMainChatIdCache()).willReturn(submanagerChatIdByMainCache);

        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        chat.setBoundSubmanagerId(submanagerId);
        chat.setSubmanagerChatId(submanagerChatId);
        given(chatRepository.findById(chatId)).willReturn(Optional.of(chat));
        ConcurrentMap<Long, ChatDetailsDto> map = new ConcurrentHashMap<>();
        given(chatDetailsCache.asMap()).willReturn(map);
        given(chatMapper.toChatDetailsDto(chat)).willReturn(new ChatDetailsDto());

        ConcurrentMap<GroupIdAndChatIdKey, Optional<Long>> mainMap = new ConcurrentHashMap<>();
        ConcurrentMap<GroupIdAndChatIdKey, Optional<Long>> subMap = new ConcurrentHashMap<>();
        given(mainChatIdBySubmanagerCache.asMap()).willReturn(mainMap);
        given(submanagerChatIdByMainCache.asMap()).willReturn(subMap);

        chatService.setBoundSubmanagerAsNull(chatId, submanagerId, submanagerChatId);

        assertThat(chat.getBoundSubmanagerId()).isNull();
        assertThat(chat.getSubmanagerChatId()).isNull();
        verify(chatDetailsCache).asMap();

        GroupIdAndChatIdKey key1 = new GroupIdAndChatIdKey(Math.abs(submanagerId), submanagerChatId);
        GroupIdAndChatIdKey key2 = new GroupIdAndChatIdKey(Math.abs(submanagerId), chatId);
        assertThat(mainMap.get(key1)).isEmpty();
        assertThat(subMap.get(key2)).isEmpty();
    }

    @Test
    void shouldFindChatsBySubmanager() {
        List<ChatEntity> expected = List.of(new ChatEntity());
        given(chatRepository.findChatsByBoundSubmanagerAndSubPostsEnabled(Math.abs(submanagerId))).willReturn(expected);
        List<ChatEntity> result = chatService.findChatsByBoundSubmanagerIdAndSubPostsEnabled(submanagerId);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void shouldCheckExistsByBoundLogChat() {
        given(chatRepository.existsByBoundLogChat(chatId)).willReturn(true);
        boolean result = chatService.existsByBoundLogChat(chatId);
        assertThat(result).isTrue();
    }

    @Test
    void shouldFindByBoundLogChat() {
        List<ChatEntity> expected = List.of(new ChatEntity());
        given(chatRepository.findByBoundLogChat(chatId)).willReturn(expected);
        List<ChatEntity> result = chatService.findByBoundLogChat(chatId);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void shouldGetMainChatDataWhenExists() {
        given(cacheManager.getMainChatIdBySubmanagerChatIdCache()).willReturn(mainChatIdBySubmanagerCache);
        given(cacheManager.getChatDetailsCache()).willReturn(chatDetailsCache);
        long mainChatId = 111L;
        given(mainChatIdBySubmanagerCache.get(any(GroupIdAndChatIdKey.class), any()))
                .willReturn(Optional.of(mainChatId));
        ChatDetailsDto dto = new ChatDetailsDto();
        given(chatDetailsCache.get(eq(mainChatId), any())).willReturn(dto);

        Optional<ChatDetailsDto> result = chatService.getMainChatDataBySubmanagerChatId(submanagerId, submanagerChatId);
        assertThat(result).contains(dto);
    }

    @Test
    void shouldReturnEmptyWhenNoMainChatId() {
        given(cacheManager.getMainChatIdBySubmanagerChatIdCache()).willReturn(mainChatIdBySubmanagerCache);
        given(mainChatIdBySubmanagerCache.get(any(GroupIdAndChatIdKey.class), any()))
                .willReturn(Optional.empty());
        Optional<ChatDetailsDto> result = chatService.getMainChatDataBySubmanagerChatId(submanagerId, submanagerChatId);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnSubmanagerChatIdWhenExists() {
        given(cacheManager.getSubmanagerChatIdByMainChatIdCache()).willReturn(submanagerChatIdByMainCache);
        given(submanagerChatIdByMainCache.get(any(GroupIdAndChatIdKey.class), any()))
                .willReturn(Optional.of(submanagerChatId));
        long result = chatService.getSubmanagerChatIdByMainChatId(submanagerId, chatId);
        assertThat(result).isEqualTo(submanagerChatId);
    }

    @Test
    void shouldThrowWhenNoSubmanagerChatId() {
        given(cacheManager.getSubmanagerChatIdByMainChatIdCache()).willReturn(submanagerChatIdByMainCache);
        given(submanagerChatIdByMainCache.get(any(GroupIdAndChatIdKey.class), any()))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> chatService.getSubmanagerChatIdByMainChatId(submanagerId, chatId))
                .isInstanceOf(CannotFindSubmanagerChatIdByMainChatIdException.class);
    }
}