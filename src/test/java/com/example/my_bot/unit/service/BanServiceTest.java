package com.example.my_bot.unit.service;

import com.example.my_bot.cache.key.ChatIdAndMemberIdKey;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ban.MemberBanStatus;
import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.exception.ban.UserHasNotBannedException;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.repository.BanRepository;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BanServiceTest {

    @Mock
    private BanRepository banRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private Cache<ChatIdAndMemberIdKey, MemberBanStatus> banCache;

    @InjectMocks
    private BanService banService;

    private final long chatId = 100L;
    private final long memberId = 1L;
    private final long fromId = 2L;

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getBanCache()).thenReturn(banCache);
    }


    @Test
    void createMemberBan_whenMemberIdEqualsFromId_shouldThrowException() {
        assertThrows(CannotApplyThisCommandToYourselfException.class,
                () -> banService.createMemberBan(chatId, memberId, null, null, memberId));
        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
        verify(banRepository, never()).save(any());
    }

    @Test
    void createMemberBan_whenMemberServiceThrows_shouldPropagateException() {
        doThrow(new RuntimeException("Interaction check failed"))
                .when(memberService).checkMemberInteractionAbility(chatId, fromId, memberId, true);

        assertThrows(RuntimeException.class,
                () -> banService.createMemberBan(chatId, memberId, null, null, fromId));
        verify(banRepository, never()).findByChatIdAndMemberId(anyLong(), anyLong());
        verify(banRepository, never()).save(any());
    }

    @Test
    void createMemberBan_whenNewBanAndTimePeriodNull_shouldCreatePermanentBan() {
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.empty());
        when(banRepository.save(any(BanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Instant> result = banService.createMemberBan(chatId, memberId, "Spam", null, fromId);

        assertTrue(result.isEmpty());

        ArgumentCaptor<BanEntity> captor = ArgumentCaptor.forClass(BanEntity.class);
        verify(banRepository).save(captor.capture());
        BanEntity saved = captor.getValue();
        assertEquals(chatId, saved.getChatId());
        assertEquals(memberId, saved.getMemberId());
        assertEquals(fromId, saved.getBannedBy());
        assertNull(saved.getBannedUntil());
        assertEquals("Spam", saved.getReason());
        assertNotNull(saved.getBannedAt());

        verify(banCache).put(any(ChatIdAndMemberIdKey.class), any(MemberBanStatus.class));
    }

    @Test
    void createMemberBan_whenReasonIsEmptyOrNull_shouldSetReasonToNull() {
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.empty());
        when(banRepository.save(any(BanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        banService.createMemberBan(chatId, memberId, "   ", 3600L, fromId);
        ArgumentCaptor<BanEntity> captor = ArgumentCaptor.forClass(BanEntity.class);
        verify(banRepository).save(captor.capture());
        assertNull(captor.getValue().getReason());

        banService.createMemberBan(chatId, memberId, null, 3600L, fromId);
        captor = ArgumentCaptor.forClass(BanEntity.class);
        verify(banRepository, times(2)).save(captor.capture());
        assertNull(captor.getValue().getReason());
    }

    @Test
    void createMemberBan_whenTimePeriodLessThanMin_shouldSetToMin() {
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.empty());
        when(banRepository.save(any(BanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        banService.createMemberBan(chatId, memberId, null, 10L, fromId); // 10 < 60
        ArgumentCaptor<BanEntity> captor = ArgumentCaptor.forClass(BanEntity.class);
        verify(banRepository).save(captor.capture());
        BanEntity saved = captor.getValue();
        Instant now = Instant.now();
        assertNotNull(saved.getBannedUntil());
        long diff = saved.getBannedUntil().getEpochSecond() - now.getEpochSecond();
        assertTrue(diff >= 59 && diff <= 61);
    }

    @Test
    void createMemberBan_whenTimePeriodGreaterThanMax_shouldSetToMax() {
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.empty());
        when(banRepository.save(any(BanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long huge = BanService.MAX_BAN_TIME_PERIOD_SEC + 1000;
        banService.createMemberBan(chatId, memberId, null, huge, fromId);
        ArgumentCaptor<BanEntity> captor = ArgumentCaptor.forClass(BanEntity.class);
        verify(banRepository).save(captor.capture());
        BanEntity saved = captor.getValue();
        assertNotNull(saved.getBannedUntil());
        long maxSec = BanService.MAX_BAN_TIME_PERIOD_SEC;
        long diff = saved.getBannedUntil().getEpochSecond() - Instant.now().getEpochSecond();
        assertTrue(diff >= maxSec - 1 && diff <= maxSec + 1);
    }

    @Test
    void createMemberBan_whenBanAlreadyExists_shouldUpdateExisting() {
        BanEntity existingBan = new BanEntity();
        existingBan.setId(1L);
        existingBan.setChatId(chatId);
        existingBan.setMemberId(memberId);
        existingBan.setBannedUntil(Instant.now().plusSeconds(1000));
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.of(existingBan));
        when(banRepository.save(any(BanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        banService.createMemberBan(chatId, memberId, "Updated reason", 200L, fromId);

        verify(banRepository).save(existingBan);
        assertEquals(fromId, existingBan.getBannedBy());
        assertEquals("Updated reason", existingBan.getReason());
        assertNotNull(existingBan.getBannedUntil());
        assertEquals(1L, existingBan.getId());
    }

    @Test
    void createMemberBan_whenTimePeriodNotNull_shouldReturnUnbanAt() {
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.empty());
        when(banRepository.save(any(BanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Instant> result = banService.createMemberBan(chatId, memberId, null, 300L, fromId);
        assertTrue(result.isPresent());
        Instant expectedUnban = Instant.now().plusSeconds(300);
        long diff = result.get().getEpochSecond() - expectedUnban.getEpochSecond();
        assertTrue(Math.abs(diff) <= 1);
    }


    @Test
    void deleteMemberBan_whenUserNotBanned_shouldThrowException() {

        when(banCache.get(any(ChatIdAndMemberIdKey.class), any()))
                .thenAnswer(invocation -> {
                    Function<ChatIdAndMemberIdKey, MemberBanStatus> loader = invocation.getArgument(1);
                    return loader.apply(new ChatIdAndMemberIdKey(chatId, memberId));
                });
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.empty());

        assertThrows(UserHasNotBannedException.class,
                () -> banService.deleteMemberBan(chatId, memberId));
        verify(banRepository, never()).deleteByChatIdAndMemberId(anyLong(), anyLong());
        verify(banCache, never()).invalidate(any());
    }

    @Test
    void deleteMemberBan_whenUserBanned_shouldDeleteAndInvalidateCache() {
        when(banCache.get(any(ChatIdAndMemberIdKey.class), any()))
                .thenAnswer(invocation -> {
                    Function<ChatIdAndMemberIdKey, MemberBanStatus> loader = invocation.getArgument(1);
                    return loader.apply(new ChatIdAndMemberIdKey(chatId, memberId));
                });
        BanEntity banEntity = new BanEntity();
        banEntity.setBannedUntil(Instant.now().plusSeconds(1000));
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.of(banEntity));

        banService.deleteMemberBan(chatId, memberId);

        verify(banCache).invalidate(new ChatIdAndMemberIdKey(chatId, memberId));
        verify(banRepository).deleteByChatIdAndMemberId(chatId, memberId);
    }

    @Test
    void getMemberBanStatus_whenInCacheAndNotExpired_shouldReturnFromCache() {
        Instant until = Instant.now().plusSeconds(1000);
        MemberBanStatus cachedStatus = new MemberBanStatus(memberId, true, until);
        ChatIdAndMemberIdKey key = new ChatIdAndMemberIdKey(chatId, memberId);
        when(banCache.get(eq(key), any())).thenReturn(cachedStatus);

        MemberBanStatus result = banService.getMemberBanStatus(chatId, memberId);

        assertEquals(memberId, result.getMemberId());
        assertTrue(result.isBanned());
        assertEquals(until, result.getBannedUntil());
        verify(banRepository, never()).findByChatIdAndMemberId(anyLong(), anyLong());
    }

    @Test
    void getMemberBanStatus_whenInCacheButExpired_shouldReturnNotBanned() {
        Instant past = Instant.now().minusSeconds(10);
        MemberBanStatus cachedStatus = new MemberBanStatus(memberId, true, past);
        ChatIdAndMemberIdKey key = new ChatIdAndMemberIdKey(chatId, memberId);
        when(banCache.get(eq(key), any())).thenReturn(cachedStatus);

        MemberBanStatus result = banService.getMemberBanStatus(chatId, memberId);

        assertFalse(result.isBanned());
        assertNull(result.getBannedUntil());
        assertEquals(memberId, result.getMemberId());
        verify(banCache, never()).put(any(), any());
    }

    @Test
    void getMemberBanStatus_whenNotInCacheAndFoundInDb_shouldLoadAndReturnBanned() {
        ChatIdAndMemberIdKey key = new ChatIdAndMemberIdKey(chatId, memberId);
        Instant until = Instant.now().plusSeconds(1000);
        BanEntity banEntity = new BanEntity();
        banEntity.setBannedUntil(until);
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.of(banEntity));
        when(banCache.get(eq(key), any())).thenAnswer(invocation -> {
            Function<ChatIdAndMemberIdKey, MemberBanStatus> loader = invocation.getArgument(1);
            return loader.apply(key);
        });

        MemberBanStatus result = banService.getMemberBanStatus(chatId, memberId);

        assertTrue(result.isBanned());
        assertEquals(until, result.getBannedUntil());
        assertEquals(memberId, result.getMemberId());
    }

    @Test
    void getMemberBanStatus_whenNotInCacheAndNotFoundInDb_shouldReturnNotBanned() {
        ChatIdAndMemberIdKey key = new ChatIdAndMemberIdKey(chatId, memberId);
        when(banRepository.findByChatIdAndMemberId(chatId, memberId)).thenReturn(Optional.empty());
        when(banCache.get(eq(key), any())).thenAnswer(invocation -> {
            Function<ChatIdAndMemberIdKey, MemberBanStatus> loader = invocation.getArgument(1);
            return loader.apply(key);
        });

        MemberBanStatus result = banService.getMemberBanStatus(chatId, memberId);

        assertFalse(result.isBanned());
        assertNull(result.getBannedUntil());
        assertEquals(memberId, result.getMemberId());
    }


    @Test
    void getAllChatPermanentBans_shouldCallRepositoryWithPageRequest() {
        Page<BanEntity> expectedPage = mock(Page.class);
        when(banRepository.getAllChatPermanentBans(chatId, PageRequest.of(0, 10))).thenReturn(expectedPage);

        Page<BanEntity> result = banService.getAllChatPermanentBans(chatId, 10);

        assertSame(expectedPage, result);
        verify(banRepository).getAllChatPermanentBans(chatId, PageRequest.of(0, 10));
    }

    @Test
    void getAllChatTemporaryBans_shouldCallRepositoryWithCurrentTimeAndPageRequest() {
        Page<BanEntity> expectedPage = mock(Page.class);
        when(banRepository.getAllChatTemporaryBans(eq(chatId), any(Instant.class), eq(PageRequest.of(0, 20))))
                .thenReturn(expectedPage);

        Page<BanEntity> result = banService.getAllChatTemporaryBans(chatId, 20);

        assertSame(expectedPage, result);
        verify(banRepository).getAllChatTemporaryBans(eq(chatId), any(Instant.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    void deleteExpiredDbBans_shouldCallRepositoryDeleteExpiredBans() {
        banService.deleteExpiredDbBans();
        verify(banRepository).deleteExpiredBans(any(Instant.class));
    }
}
