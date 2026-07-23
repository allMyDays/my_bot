package com.example.my_bot.unit.service.chat;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.chat.AdminChatDto;
import com.example.my_bot.entity.AdminChatEntity;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.exception.chat.AdminChatException;
import com.example.my_bot.exception.chat.AdminChatNotFoundException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.repository.chat.AdminChatRepository;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.AdminChatService;
import com.example.my_bot.service.chat.ChatService;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminChatServiceTest {

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private AdminChatRepository adminChatRepository;

    @Mock
    private ChatMapper chatMapper;

    @Mock
    private ChatService chatService;

    @Mock
    private MemberService memberService;

    @Mock
    private Cache<Long, Optional<AdminChatDto>> adminChatCache;

    @Mock
    private Cache<Long, Optional<Long>> adminChatIdByBoundChatCache;

    private AdminChatService adminChatService;

    private final long chatId = 123L;
    private final long adminChatId = 456L;
    private final long targetChatId = 789L;
    private final long fromId = 100L;
    private final String chatCode = "abc123";

    @BeforeEach
    void setUp() {
        adminChatService = new AdminChatService(
                cacheManager,
                adminChatRepository,
                chatMapper,
                chatService,
                memberService
        );
    }

    @Test
    void shouldReturnAdminChatDtoWhenPresent() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        AdminChatDto dto = new AdminChatDto();
        given(adminChatCache.get(eq(chatId), any())).willReturn(Optional.of(dto));
        Optional<AdminChatDto> result = adminChatService.getAdminChatData(chatId);
        assertThat(result).contains(dto);
        verify(adminChatRepository, never()).findById(anyLong());
    }

    @Test
    void shouldLoadFromRepositoryWhenCacheMiss() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        AdminChatEntity entity = new AdminChatEntity();
        entity.setChatId(chatId);
        AdminChatDto dto = new AdminChatDto();
        given(adminChatCache.get(eq(chatId), any())).willAnswer(invocation -> {
            java.util.function.Function<Long, Optional<AdminChatDto>> loader = invocation.getArgument(1);
            return loader.apply(chatId);
        });
        given(adminChatRepository.findById(chatId)).willReturn(Optional.of(entity));
        given(chatMapper.toAdminChatDto(entity)).willReturn(dto);

        Optional<AdminChatDto> result = adminChatService.getAdminChatData(chatId);
        assertThat(result).contains(dto);
        verify(adminChatRepository).findById(chatId);
        verify(chatMapper).toAdminChatDto(entity);
    }

    @Test
    void shouldReturnEmptyWhenNotInRepo() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        given(adminChatCache.get(eq(chatId), any())).willAnswer(invocation -> {
            java.util.function.Function<Long, Optional<AdminChatDto>> loader = invocation.getArgument(1);
            return loader.apply(chatId);
        });
        given(adminChatRepository.findById(chatId)).willReturn(Optional.empty());

        Optional<AdminChatDto> result = adminChatService.getAdminChatData(chatId);
        assertThat(result).isEmpty();
        verify(chatMapper, never()).toAdminChatDto(any());
    }

    @Test
    void shouldUnbindChatAndRemoveAdminChatWhenOnlyOneBoundChat() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        given(cacheManager.getAdminChatIdByBoundChatIdCache()).willReturn(adminChatIdByBoundChatCache);
        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(new ChatEntity(chatId));

        AdminChatEntity adminChat = new AdminChatEntity(adminChatId);
        adminChat.setBoundChats(new HashSet<>(Set.of(chatId)));
        given(adminChatRepository.findById(adminChatId)).willReturn(Optional.of(adminChat));

        ConcurrentMap<Long, Optional<AdminChatDto>> adminMap = new ConcurrentHashMap<>();
        given(adminChatCache.asMap()).willReturn(adminMap);

        adminChatService.unBindChatFromAdminChat(adminChatId, chatCode);

        verify(adminChatRepository).deleteByChatId(adminChatId);
        assertThat(adminMap.get(adminChatId)).isEmpty();
        verify(adminChatIdByBoundChatCache).invalidate(chatId);
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldUnbindChatAndUpdateCacheWhenMultipleBoundChats() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        given(cacheManager.getAdminChatIdByBoundChatIdCache()).willReturn(adminChatIdByBoundChatCache);
        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(new ChatEntity(chatId));

        AdminChatEntity adminChat = new AdminChatEntity(adminChatId);
        adminChat.setBoundChats(new HashSet<>(Set.of(chatId, targetChatId)));
        given(adminChatRepository.findById(adminChatId)).willReturn(Optional.of(adminChat));

        ConcurrentMap<Long, Optional<AdminChatDto>> adminMap = new ConcurrentHashMap<>();
        given(adminChatCache.asMap()).willReturn(adminMap);

        AdminChatDto updatedDto = new AdminChatDto();
        given(chatMapper.toAdminChatDto(adminChat)).willReturn(updatedDto);

        adminChatService.unBindChatFromAdminChat(adminChatId, chatCode);

        assertThat(adminChat.getBoundChats()).doesNotContain(chatId);
        verify(adminChatRepository, never()).deleteByChatId(anyLong());
        assertThat(adminMap.get(adminChatId)).isEqualTo(Optional.of(updatedDto));
        verify(adminChatIdByBoundChatCache).invalidate(chatId);
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenChatNotBound() {
        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(new ChatEntity(chatId));
        AdminChatEntity adminChat = new AdminChatEntity(adminChatId);
        adminChat.setBoundChats(new HashSet<>(Set.of(targetChatId)));
        given(adminChatRepository.findById(adminChatId)).willReturn(Optional.of(adminChat));

        assertThatThrownBy(() -> adminChatService.unBindChatFromAdminChat(adminChatId, chatCode))
                .isInstanceOf(AdminChatException.class)
                .hasMessageContaining("не привязан указанный вами чат");
        verify(adminChatRepository, never()).deleteByChatId(anyLong());
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAdminChatNotFound() {
        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(new ChatEntity(chatId));
        given(adminChatRepository.findById(adminChatId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> adminChatService.unBindChatFromAdminChat(adminChatId, chatCode))
                .isInstanceOf(AdminChatNotFoundException.class);
    }

    @Test
    void shouldRemoveAdminChatAndInvalidateCaches() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        given(cacheManager.getAdminChatIdByBoundChatIdCache()).willReturn(adminChatIdByBoundChatCache);

        AdminChatEntity adminChat = new AdminChatEntity(adminChatId);
        adminChat.setBoundChats(new HashSet<>(Set.of(chatId, targetChatId)));
        given(adminChatRepository.findById(adminChatId)).willReturn(Optional.of(adminChat));

        ConcurrentMap<Long, Optional<AdminChatDto>> adminMap = new ConcurrentHashMap<>();
        given(adminChatCache.asMap()).willReturn(adminMap);

        adminChatService.removeAdminChat(adminChatId);

        verify(adminChatRepository).deleteByChatId(adminChatId);
        assertThat(adminMap.get(adminChatId)).isEmpty();
        verify(adminChatIdByBoundChatCache).invalidate(chatId);
        verify(adminChatIdByBoundChatCache).invalidate(targetChatId);
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAdminChatNotFoundForRemove() {
        given(adminChatRepository.findById(adminChatId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> adminChatService.removeAdminChat(adminChatId))
                .isInstanceOf(AdminChatNotFoundException.class);
        verify(adminChatRepository, never()).deleteByChatId(anyLong());
    }

    @Test
    void shouldSetAdminChatSuccessfully() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        given(cacheManager.getAdminChatIdByBoundChatIdCache()).willReturn(adminChatIdByBoundChatCache);

        ChatEntity currentChat = new ChatEntity();
        currentChat.setChatId(chatId);
        ChatEntity targetChat = new ChatEntity();
        targetChat.setChatId(targetChatId);

        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(currentChat);
        given(chatService.findByChatIdOrThrow(targetChatId)).willReturn(targetChat);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(SENIOR_ADMINISTRATOR.getRolePriority());
        given(adminChatCache.get(eq(chatId), any())).willReturn(Optional.empty());
        given(adminChatRepository.findById(targetChatId)).willReturn(Optional.empty());

        AdminChatEntity newAdminChat = new AdminChatEntity(targetChatId);
        newAdminChat.setBoundChats(new HashSet<>(Set.of(chatId)));
        given(adminChatRepository.save(any(AdminChatEntity.class))).willReturn(newAdminChat);

        ConcurrentMap<Long, Optional<AdminChatDto>> adminMap = new ConcurrentHashMap<>();
        given(adminChatCache.asMap()).willReturn(adminMap);
        ConcurrentMap<Long, Optional<Long>> boundMap = new ConcurrentHashMap<>();
        given(adminChatIdByBoundChatCache.asMap()).willReturn(boundMap);

        AdminChatDto dto = new AdminChatDto();
        given(chatMapper.toAdminChatDto(any(AdminChatEntity.class))).willReturn(dto);

        adminChatService.setAdminChat(chatCode, targetChatId, fromId);

        verify(adminChatRepository).save(any(AdminChatEntity.class));
        assertThat(adminMap.get(targetChatId)).isEqualTo(Optional.of(dto));
        assertThat(boundMap.get(chatId)).isEqualTo(Optional.of(targetChatId));
    }

    @Test
    void shouldThrowWhenCurrentChatEqualsTargetChat() {
        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(chat);
        given(chatService.findByChatIdOrThrow(chatId)).willReturn(chat);
        assertThatThrownBy(() -> adminChatService.setAdminChat(chatCode, chatId, fromId))
                .isInstanceOf(AdminChatException.class)
                .hasMessageContaining("тем же самым чатом");
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenRoleTooLow() {
        ChatEntity currentChat = new ChatEntity();
        currentChat.setChatId(chatId);
        ChatEntity targetChat = new ChatEntity();
        targetChat.setChatId(targetChatId);

        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(currentChat);
        given(chatService.findByChatIdOrThrow(targetChatId)).willReturn(targetChat);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(10);

        assertThatThrownBy(() -> adminChatService.setAdminChat(chatCode, targetChatId, fromId))
                .isInstanceOf(AdminChatException.class)
                .hasMessageContaining("Ваша роль в указанном чате недостаточно высока");
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCurrentChatIsAlreadyAdminChat() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        ChatEntity currentChat = new ChatEntity();
        currentChat.setChatId(chatId);
        ChatEntity targetChat = new ChatEntity();
        targetChat.setChatId(targetChatId);

        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(currentChat);
        given(chatService.findByChatIdOrThrow(targetChatId)).willReturn(targetChat);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(SENIOR_ADMINISTRATOR.getRolePriority());

        given(adminChatCache.get(eq(chatId), any())).willReturn(Optional.of(new AdminChatDto()));

        assertThatThrownBy(() -> adminChatService.setAdminChat(chatCode, targetChatId, fromId))
                .isInstanceOf(AdminChatException.class)
                .hasMessageContaining("уже является админ-чатом");
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenBoundChatAlreadyExists() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        ChatEntity currentChat = new ChatEntity();
        currentChat.setChatId(chatId);
        ChatEntity targetChat = new ChatEntity();
        targetChat.setChatId(targetChatId);

        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(currentChat);
        given(chatService.findByChatIdOrThrow(targetChatId)).willReturn(targetChat);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(SENIOR_ADMINISTRATOR.getRolePriority());
        given(adminChatCache.get(eq(chatId), any())).willReturn(Optional.empty());

        AdminChatEntity existingAdminChat = new AdminChatEntity(targetChatId);
        existingAdminChat.setBoundChats(new HashSet<>(Set.of(chatId)));
        given(adminChatRepository.findById(targetChatId)).willReturn(Optional.of(existingAdminChat));

        assertThatThrownBy(() -> adminChatService.setAdminChat(chatCode, targetChatId, fromId))
                .isInstanceOf(AdminChatException.class)
                .hasMessageContaining("уже привязан указанный вами чат");
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenMaxBoundChatsReached() {
        given(cacheManager.getAdminChatCache()).willReturn(adminChatCache);
        ChatEntity currentChat = new ChatEntity();
        currentChat.setChatId(chatId);
        ChatEntity targetChat = new ChatEntity();
        targetChat.setChatId(targetChatId);

        given(chatService.findByChatCodeOrThrow(chatCode)).willReturn(currentChat);
        given(chatService.findByChatIdOrThrow(targetChatId)).willReturn(targetChat);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(SENIOR_ADMINISTRATOR.getRolePriority());
        given(adminChatCache.get(eq(chatId), any())).willReturn(Optional.empty());

        AdminChatEntity existingAdminChat = new AdminChatEntity(targetChatId);
        Set<Long> boundChats = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            boundChats.add((long) i);
        }
        existingAdminChat.setBoundChats(boundChats);
        given(adminChatRepository.findById(targetChatId)).willReturn(Optional.of(existingAdminChat));

        assertThatThrownBy(() -> adminChatService.setAdminChat(chatCode, targetChatId, fromId))
                .isInstanceOf(AdminChatException.class)
                .hasMessageContaining("максимально возможное количество чатов");
        verify(adminChatRepository, never()).save(any());
    }

    @Test
    void shouldReturnAdminChatIdFromCacheWhenPresent() {
        given(cacheManager.getAdminChatIdByBoundChatIdCache()).willReturn(adminChatIdByBoundChatCache);
        given(adminChatIdByBoundChatCache.get(eq(chatId), any())).willReturn(Optional.of(adminChatId));
        Optional<Long> result = adminChatService.findLatestAdminChatIdByBoundChatId(chatId);
        assertThat(result).contains(adminChatId);
        verify(adminChatRepository, never()).findTopByBoundChatsContainingOrderByChatIdDesc(anyLong());
    }


    @Test
    void shouldReturnEmptyWhenNotFoundInRepo() {
        given(cacheManager.getAdminChatIdByBoundChatIdCache()).willReturn(adminChatIdByBoundChatCache);
        given(adminChatIdByBoundChatCache.get(eq(chatId), any())).willAnswer(invocation -> {
            java.util.function.Function<Long, Optional<Long>> loader = invocation.getArgument(1);
            return loader.apply(chatId);
        });
        given(adminChatRepository.findTopByBoundChatsContainingOrderByChatIdDesc(chatId))
                .willReturn(Optional.empty());

        Optional<Long> result = adminChatService.findLatestAdminChatIdByBoundChatId(chatId);
        assertThat(result).isEmpty();
    }
}
