package com.example.my_bot.unit.service;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.entity.RoleRateLimitEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.limit.RateLimitPeriodOutOfBoundsException;
import com.example.my_bot.exception.limit.RateLimitUsageOutOfBoundsException;
import com.example.my_bot.exception.limit.RateLimitWithThatCommandAndRoleAlreadyExistsException;
import com.example.my_bot.exception.limit.TooManyRateLimitsException;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.RateLimitMapper;
import com.example.my_bot.repository.RoleRateLimitRepository;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleRateLimitService;
import com.example.my_bot.service.RoleService;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleRateLimitServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private RoleService roleService;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private CommandAccessService commandService;

    @Mock
    private RateLimitMapper rateLimitMapper;

    @Mock
    private RoleRateLimitRepository roleRateLimitRepository;

    @Mock
    private CommandRegistry commandRegistry;

    @Mock
    private Cache<Long, ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>>> roleLimitCache;

    @InjectMocks
    private RoleRateLimitService roleRateLimitService;

    private static final long CHAT_ID = 1L;
    private static final long FROM_ID = 100L;
    private static final int ROLE_PRIORITY = 50;
    private static final String ROLE_NAME = "Moderator";
    private static final String USER_COMMAND = "/ban";
    private static final String MAIN_COMMAND = "ban";
    private static final int MAX_USAGE = 5;
    private static final long PERIOD_SEC = 3600L;
    private static final boolean IS_PERSONAL = false;

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getRoleLimitCache()).thenReturn(roleLimitCache);

        lenient().when(roleLimitCache.get(anyLong(), any())).thenReturn(ImmutableMap.of());

        lenient().when(commandRegistry.getMainNameOfCommand(anyString()))
                .thenReturn(Optional.of(MAIN_COMMAND));

        lenient().when(commandService.checkCommandAuthorization(anyLong(), anyString(), anyInt(), anyLong()))
                .thenReturn(true);

        lenient().when(memberService.getMemberRolePriority(anyLong(), anyLong()))
                .thenReturn(DefaultRole.SENIOR_ADMINISTRATOR.getRolePriority());

        lenient().when(roleService.getRoleByPriority(eq(CHAT_ID), eq(ROLE_PRIORITY)))
                .thenReturn(Optional.of(new RoleDto(ROLE_NAME, ROLE_PRIORITY)));

        roleRateLimitService.setCommandRegistry(commandRegistry);
    }


    @Test
    void createCommandRateLimitByPriority_shouldThrowWhenTooManyLimits() {

        ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> existingLimits = createFilledLimits(20);
        when(roleLimitCache.get(eq(CHAT_ID), any())).thenReturn(existingLimits);

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(TooManyRateLimitsException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }

    @Test
    void createCommandRateLimitByPriority_shouldThrowWhenTimePeriodOutOfBounds() {
        long lowPeriod = 30L;
        long highPeriod = 100_000L;

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, lowPeriod, IS_PERSONAL))
                .isInstanceOf(RateLimitPeriodOutOfBoundsException.class);

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, highPeriod, IS_PERSONAL))
                .isInstanceOf(RateLimitPeriodOutOfBoundsException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }

    @Test
    void createCommandRateLimitByPriority_shouldThrowWhenUsageOutOfBounds() {
        int lowUsage = 0;
        int highUsage = 101;

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, lowUsage, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(RateLimitUsageOutOfBoundsException.class);

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, highUsage, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(RateLimitUsageOutOfBoundsException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }

    @Test
    void createCommandRateLimitByPriority_shouldThrowWhenRoleNotFound() {
        when(roleService.getRoleByPriority(eq(CHAT_ID), eq(ROLE_PRIORITY)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(RoleNotFoundException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }

    @Test
    void createCommandRateLimitByPriority_shouldThrowWhenCheckRoleInteractionFails() {
        doThrow(RoleAccessDeniedException.class)
                .when(roleService).checkRoleInteractionAbility(anyInt(), anyInt());

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(RoleAccessDeniedException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }

    @Test
    void createCommandRateLimitByPriority_shouldThrowWhenCommandNotFound() {
        when(commandRegistry.getMainNameOfCommand(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(UserCommandNotFoundException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }

    @Test
    void createCommandRateLimitByPriority_shouldThrowWhenCommandAccessDenied() {
        when(commandService.checkCommandAuthorization(eq(CHAT_ID), eq(MAIN_COMMAND), anyInt(), eq(FROM_ID)))
                .thenReturn(false);

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(CommandAccessDeniedException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }

    @Test
    void createCommandRateLimitByPriority_shouldThrowWhenLimitAlreadyExists() {
        ImmutableMap<Integer, RoleRateLimitDto> existingForCommand = ImmutableMap.of(
                ROLE_PRIORITY, mock(RoleRateLimitDto.class)
        );
        ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> existingLimits = ImmutableMap.of(
                MAIN_COMMAND, existingForCommand
        );
        when(roleLimitCache.get(eq(CHAT_ID), any())).thenReturn(existingLimits);

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(RateLimitWithThatCommandAndRoleAlreadyExistsException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }

    @Test
    void createCommandRateLimitByPriority_shouldSaveAndReturnEntity_whenAllValid() {
        RoleRateLimitEntity entityToSave = new RoleRateLimitEntity(
                CHAT_ID, MAIN_COMMAND, ROLE_PRIORITY, IS_PERSONAL, MAX_USAGE, (int) PERIOD_SEC
        );
        when(roleRateLimitRepository.save(any(RoleRateLimitEntity.class))).thenReturn(entityToSave);

        RoleRateLimitEntity result = roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_PRIORITY, MAX_USAGE, PERIOD_SEC, IS_PERSONAL);

        assertThat(result).isEqualTo(entityToSave);

        ArgumentCaptor<RoleRateLimitEntity> captor = ArgumentCaptor.forClass(RoleRateLimitEntity.class);
        verify(roleRateLimitRepository).save(captor.capture());
        RoleRateLimitEntity saved = captor.getValue();
        assertThat(saved.getChatId()).isEqualTo(CHAT_ID);
        assertThat(saved.getCommandName()).isEqualTo(MAIN_COMMAND);
        assertThat(saved.getRolePriority()).isEqualTo(ROLE_PRIORITY);
        assertThat(saved.isPersonal()).isEqualTo(IS_PERSONAL);
        assertThat(saved.getMaxUsage()).isEqualTo(MAX_USAGE);
        assertThat(saved.getTimePeriodSec()).isEqualTo((int) PERIOD_SEC);

        verify(roleLimitCache).invalidate(CHAT_ID);
    }


    @Test
    void createCommandRateLimitByName_shouldDelegateToPriorityMethod() {
        when(roleService.getRoleByNameIgnoreCase(eq(CHAT_ID), eq(ROLE_NAME)))
                .thenReturn(Optional.of(new RoleDto(ROLE_NAME, ROLE_PRIORITY)));

        when(roleRateLimitRepository.save(any())).thenReturn(mock(RoleRateLimitEntity.class));

        roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_NAME, MAX_USAGE, PERIOD_SEC, IS_PERSONAL);

        ArgumentCaptor<RoleRateLimitEntity> captor = ArgumentCaptor.forClass(RoleRateLimitEntity.class);
        verify(roleRateLimitRepository).save(captor.capture());
        assertThat(captor.getValue().getRolePriority()).isEqualTo(ROLE_PRIORITY);
    }

    @Test
    void createCommandRateLimitByName_shouldThrowWhenRoleNotFound() {
        when(roleService.getRoleByNameIgnoreCase(eq(CHAT_ID), eq(ROLE_NAME)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleRateLimitService.createCommandRateLimit(
                CHAT_ID, FROM_ID, USER_COMMAND, ROLE_NAME, MAX_USAGE, PERIOD_SEC, IS_PERSONAL))
                .isInstanceOf(RoleNotFoundException.class);

        verify(roleRateLimitRepository, never()).save(any());
    }


    @Test
    void deleteLimit_shouldThrowWhenRoleInteractionFails() {
        RoleRateLimitDto dto = new RoleRateLimitDto();
        dto.setRolePriority(ROLE_PRIORITY);
        dto.setCommandName(MAIN_COMMAND);

        doThrow(RoleAccessDeniedException.class)
                .when(roleService).checkRoleInteractionAbility(anyInt(), anyInt());

        assertThatThrownBy(() -> roleRateLimitService.deleteLimit(dto, CHAT_ID, FROM_ID))
                .isInstanceOf(RoleAccessDeniedException.class);

        verify(roleRateLimitRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteLimit_shouldThrowWhenCommandAccessDenied() {
        RoleRateLimitDto dto = new RoleRateLimitDto();
        dto.setRolePriority(ROLE_PRIORITY);
        dto.setCommandName(MAIN_COMMAND);

        when(commandService.checkCommandAuthorization(eq(CHAT_ID), eq(MAIN_COMMAND), anyInt(), eq(FROM_ID)))
                .thenReturn(false);

        assertThatThrownBy(() -> roleRateLimitService.deleteLimit(dto, CHAT_ID, FROM_ID))
                .isInstanceOf(CommandAccessDeniedException.class);

        verify(roleRateLimitRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteLimit_shouldDeleteAndInvalidateCache_whenAllValid() {
        RoleRateLimitDto dto = new RoleRateLimitDto();
        dto.setEntityId(123L);
        dto.setRolePriority(ROLE_PRIORITY);
        dto.setCommandName(MAIN_COMMAND);

        when(commandService.checkCommandAuthorization(eq(CHAT_ID), eq(MAIN_COMMAND), anyInt(), eq(FROM_ID)))
                .thenReturn(true);

        roleRateLimitService.deleteLimit(dto, CHAT_ID, FROM_ID);

        verify(roleRateLimitRepository).deleteById(123L);
        verify(roleLimitCache).invalidate(CHAT_ID);
    }


    @Test
    void getRoleLimitsSortedByEntityId_shouldReturnSortedList() {
        RoleRateLimitDto dto1 = new RoleRateLimitDto();
        dto1.setEntityId(10L);
        RoleRateLimitDto dto2 = new RoleRateLimitDto();
        dto2.setEntityId(5L);
        RoleRateLimitDto dto3 = new RoleRateLimitDto();
        dto3.setEntityId(7L);

        ImmutableMap<Integer, RoleRateLimitDto> map1 = ImmutableMap.of(1, dto1);
        ImmutableMap<Integer, RoleRateLimitDto> map2 = ImmutableMap.of(2, dto2);
        ImmutableMap<Integer, RoleRateLimitDto> map3 = ImmutableMap.of(3, dto3);

        ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> limits = ImmutableMap.of(
                "cmd1", map1,
                "cmd2", map2,
                "cmd3", map3
        );
        when(roleLimitCache.get(eq(CHAT_ID), any())).thenReturn(limits);

        List<RoleRateLimitDto> result = roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID);

        assertThat(result).containsExactly(dto2, dto3, dto1);
    }


    @Test
    void getCachedCustomRoleLimits_shouldLoadFromRepositoryWhenCacheMiss() {
        RoleRateLimitEntity entity1 = new RoleRateLimitEntity();
        entity1.setId(1L);
        entity1.setChatId(CHAT_ID);
        entity1.setCommandName("ban");
        entity1.setRolePriority(10);
        entity1.setMaxUsage(3);
        entity1.setTimePeriodSec(60);
        entity1.setPersonal(false);

        RoleRateLimitEntity entity2 = new RoleRateLimitEntity();
        entity2.setId(2L);
        entity2.setChatId(CHAT_ID);
        entity2.setCommandName("kick");
        entity2.setRolePriority(20);
        entity2.setMaxUsage(5);
        entity2.setTimePeriodSec(120);
        entity2.setPersonal(true);

        when(roleRateLimitRepository.findByChatId(CHAT_ID))
                .thenReturn(Arrays.asList(entity1, entity2));

        RoleRateLimitDto dto1 = new RoleRateLimitDto();
        dto1.setEntityId(1L);
        dto1.setCommandName("ban");
        dto1.setRolePriority(10);
        RoleRateLimitDto dto2 = new RoleRateLimitDto();
        dto2.setEntityId(2L);
        dto2.setCommandName("kick");
        dto2.setRolePriority(20);

        when(rateLimitMapper.toRoleLimitDto(entity1)).thenReturn(dto1);
        when(rateLimitMapper.toRoleLimitDto(entity2)).thenReturn(dto2);

        when(roleLimitCache.get(eq(CHAT_ID), any())).thenAnswer(invocation -> {
            Function<Long, ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>>> loader =
                    invocation.getArgument(1);
            return loader.apply(CHAT_ID);
        });

        ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> result =
                roleRateLimitService.getCachedCustomRoleLimits(CHAT_ID);

        assertThat(result).containsKeys("ban", "kick");
        assertThat(result.get("ban")).containsEntry(10, dto1);
        assertThat(result.get("kick")).containsEntry(20, dto2);

        verify(roleRateLimitRepository).findByChatId(CHAT_ID);
    }

    private ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> createFilledLimits(int count) {
        ImmutableMap.Builder<String, ImmutableMap<Integer, RoleRateLimitDto>> builder = ImmutableMap.builder();
        for (int i = 0; i < count; i++) {
            ImmutableMap<Integer, RoleRateLimitDto> inner = ImmutableMap.of(i, mock(RoleRateLimitDto.class));
            builder.put("cmd" + i, inner);
        }
        return builder.build();
    }
}
