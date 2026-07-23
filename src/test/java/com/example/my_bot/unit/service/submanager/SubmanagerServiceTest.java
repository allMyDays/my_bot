package com.example.my_bot.unit.service.submanager;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.entity.SubmanagerEntity;
import com.example.my_bot.exception.submanager.SubmanagerNotFoundException;
import com.example.my_bot.mapper.SubmanagerMapper;
import com.example.my_bot.repository.SubmanagerRepository;
import com.example.my_bot.service.CryptoService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.github.benmanes.caffeine.cache.Cache;
import com.vk.api.sdk.client.actors.GroupActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmanagerServiceTest {

    @Mock
    private CryptoService cryptoService;

    @Mock
    private SubmanagerRepository submanagerRepository;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private SubmanagerMapper submanagerMapper;

    @Mock
    private Cache<Long, Optional<SubmanagerDto>> submanagerCache;

    private final long theMainBotId = 123L;
    private final GroupActor theMainBotGroupActor = new GroupActor(theMainBotId, "mainToken");

    private SubmanagerService submanagerService;

    private final long groupId = 456L;
    private final String groupToken = "token123";
    private final int serverId = 1;
    private final String secretKey = "secret";

    @BeforeEach
    void setUp() {
        submanagerService = new SubmanagerService(
                cryptoService,
                submanagerRepository,
                cacheManager,
                submanagerMapper,
                theMainBotId,
                theMainBotGroupActor
        );
    }

    @Test
    void shouldSaveAndUpdateCacheWhenGroupIdNotMainBot() {
        given(cacheManager.getSubmanagerInfoCache()).willReturn(submanagerCache);

        long absGroupId = Math.abs(groupId);
        String encryptedToken = "encryptedToken";
        given(cryptoService.encrypt(groupToken)).willReturn(encryptedToken);

        SubmanagerEntity savedEntity = new SubmanagerEntity(absGroupId, encryptedToken, serverId, secretKey);
        given(submanagerRepository.save(any(SubmanagerEntity.class))).willReturn(savedEntity);

        SubmanagerDto expectedDto = new SubmanagerDto(absGroupId, encryptedToken, serverId, secretKey);
        given(submanagerMapper.toSubmanagerDto(savedEntity)).willReturn(expectedDto);

        ConcurrentMap<Long, Optional<SubmanagerDto>> realMap = new ConcurrentHashMap<>();
        given(submanagerCache.asMap()).willReturn(realMap);

        submanagerService.createOrUpdateSubmanagerInfo(groupId, groupToken, serverId, secretKey);

        ArgumentCaptor<SubmanagerEntity> captor = ArgumentCaptor.forClass(SubmanagerEntity.class);
        verify(submanagerRepository).save(captor.capture());
        SubmanagerEntity captured = captor.getValue();
        assertThat(captured.getGroupId()).isEqualTo(absGroupId);
        assertThat(captured.getEncryptedToken()).isEqualTo(encryptedToken);
        assertThat(captured.getServerId()).isEqualTo(serverId);
        assertThat(captured.getSecretKey()).isEqualTo(secretKey);

        assertThat(realMap).containsKey(absGroupId);
        Optional<SubmanagerDto> cached = realMap.get(absGroupId);
        assertThat(cached).contains(expectedDto);

        verify(submanagerCache).asMap();
        verify(submanagerMapper).toSubmanagerDto(savedEntity);
    }

    @Test
    void shouldDoNothingWhenGroupIdEqualsMainBotId() {
        submanagerService.createOrUpdateSubmanagerInfo(theMainBotId, groupToken, serverId, secretKey);

        verify(submanagerRepository, never()).save(any());
        verify(cacheManager, never()).getSubmanagerInfoCache();
        verifyNoInteractions(cryptoService);
    }

    @Test
    void shouldUseAbsoluteGroupId() {
        given(cacheManager.getSubmanagerInfoCache()).willReturn(submanagerCache);

        long negativeGroupId = -456L;
        long absGroupId = 456L;
        String encryptedToken = "encryptedToken";
        given(cryptoService.encrypt(groupToken)).willReturn(encryptedToken);

        SubmanagerEntity savedEntity = new SubmanagerEntity(absGroupId, encryptedToken, serverId, secretKey);
        given(submanagerRepository.save(any(SubmanagerEntity.class))).willReturn(savedEntity);

        SubmanagerDto expectedDto = new SubmanagerDto(absGroupId, encryptedToken, serverId, secretKey);
        given(submanagerMapper.toSubmanagerDto(savedEntity)).willReturn(expectedDto);

        ConcurrentMap<Long, Optional<SubmanagerDto>> realMap = new ConcurrentHashMap<>();
        given(submanagerCache.asMap()).willReturn(realMap);

        submanagerService.createOrUpdateSubmanagerInfo(negativeGroupId, groupToken, serverId, secretKey);

        ArgumentCaptor<SubmanagerEntity> captor = ArgumentCaptor.forClass(SubmanagerEntity.class);
        verify(submanagerRepository).save(captor.capture());
        assertThat(captor.getValue().getGroupId()).isEqualTo(absGroupId);

        assertThat(realMap).containsKey(absGroupId);
        verify(submanagerCache).asMap();
        verify(submanagerMapper).toSubmanagerDto(savedEntity);
    }

    @Test
    void shouldReturnSubmanagerWhenExists() {
        given(cacheManager.getSubmanagerInfoCache()).willReturn(submanagerCache);
        SubmanagerDto expectedDto = new SubmanagerDto(groupId, "token", serverId, secretKey);
        given(submanagerCache.get(eq(groupId), any())).willReturn(Optional.of(expectedDto));

        SubmanagerDto result = submanagerService.getSubmanagerOrThrowIfAbsents(groupId);
        assertThat(result).isSameAs(expectedDto);
    }

    @Test
    void shouldThrowWhenSubmanagerNotFound() {
        given(cacheManager.getSubmanagerInfoCache()).willReturn(submanagerCache);
        given(submanagerCache.get(eq(groupId), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> submanagerService.getSubmanagerOrThrowIfAbsents(groupId))
                .isInstanceOf(SubmanagerNotFoundException.class)
                .hasMessageContaining(String.valueOf(groupId));
    }

    @Test
    void shouldReturnOptionalFromCacheWhenPresent() {
        given(cacheManager.getSubmanagerInfoCache()).willReturn(submanagerCache);
        SubmanagerDto expectedDto = new SubmanagerDto(groupId, "token", serverId, secretKey);
        given(submanagerCache.get(eq(groupId), any())).willReturn(Optional.of(expectedDto));

        Optional<SubmanagerDto> result = submanagerService.getOptionalSubmanager(groupId);
        assertThat(result).contains(expectedDto);
        verify(submanagerRepository, never()).findById(anyLong());
    }

    @Test
    void shouldLoadFromRepositoryWhenCacheMiss() {
        given(cacheManager.getSubmanagerInfoCache()).willReturn(submanagerCache);

        long absGroupId = Math.abs(groupId);
        SubmanagerEntity entity = new SubmanagerEntity(absGroupId, "token", serverId, secretKey);
        SubmanagerDto expectedDto = new SubmanagerDto(absGroupId, "token", serverId, secretKey);

        given(submanagerCache.get(eq(groupId), any())).willAnswer(invocation -> {
            Function<Long, Optional<SubmanagerDto>> loader = invocation.getArgument(1);
            return loader.apply(groupId);
        });
        given(submanagerRepository.findById(absGroupId)).willReturn(Optional.of(entity));
        given(submanagerMapper.toSubmanagerDto(entity)).willReturn(expectedDto);

        Optional<SubmanagerDto> result = submanagerService.getOptionalSubmanager(groupId);
        assertThat(result).contains(expectedDto);
        verify(submanagerRepository).findById(absGroupId);
        verify(submanagerMapper).toSubmanagerDto(entity);
    }

    @Test
    void shouldReturnEmptyWhenRepositoryReturnsEmpty() {
        given(cacheManager.getSubmanagerInfoCache()).willReturn(submanagerCache);

        long absGroupId = Math.abs(groupId);
        given(submanagerCache.get(eq(groupId), any())).willAnswer(invocation -> {
            Function<Long, Optional<SubmanagerDto>> loader = invocation.getArgument(1);
            return loader.apply(groupId);
        });
        given(submanagerRepository.findById(absGroupId)).willReturn(Optional.empty());

        Optional<SubmanagerDto> result = submanagerService.getOptionalSubmanager(groupId);
        assertThat(result).isEmpty();
        verify(submanagerMapper, never()).toSubmanagerDto(any());
    }

    @Test
    void shouldUseAbsoluteGroupIdInGetOptional() {
        given(cacheManager.getSubmanagerInfoCache()).willReturn(submanagerCache);

        long negativeGroupId = -456L;
        long absGroupId = 456L;
        given(submanagerCache.get(eq(absGroupId), any())).willAnswer(invocation -> {
            Function<Long, Optional<SubmanagerDto>> loader = invocation.getArgument(1);
            return loader.apply(absGroupId);
        });
        given(submanagerRepository.findById(absGroupId)).willReturn(Optional.empty());

        submanagerService.getOptionalSubmanager(negativeGroupId);
        verify(submanagerRepository).findById(absGroupId);
    }


    @Test
    void shouldReturnTrueWhenGroupActorDifferentGroupId() {
        GroupActor other = new GroupActor(999L, "someToken");
        boolean result = submanagerService.isSubmanager(other);
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenGroupActorIsMainBot() {
        GroupActor mainBot = new GroupActor(theMainBotId, "mainToken");
        boolean result = submanagerService.isSubmanager(mainBot);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueWhenGroupIdAndTokenBothDifferent() {
        GroupActor other = new GroupActor(999L, "someToken");
        boolean result = submanagerService.isSubmanager(other);
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenGroupIdSameButTokenDifferent() {
        GroupActor sameIdDifferentToken = new GroupActor(theMainBotId, "otherToken");
        boolean result = submanagerService.isSubmanager(sameIdDifferentToken);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenGroupIdDifferentButTokenSame() {
        GroupActor differentIdSameToken = new GroupActor(999L, "mainToken");
        boolean result = submanagerService.isSubmanager(differentIdSameToken);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenGroupIdAndTokenSame() {
        GroupActor sameAsMain = new GroupActor(theMainBotId, "mainToken");
        boolean result = submanagerService.isSubmanager(sameAsMain);
        assertThat(result).isFalse();
    }
}