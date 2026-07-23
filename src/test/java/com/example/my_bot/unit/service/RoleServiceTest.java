package com.example.my_bot.unit.service;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.exception.role.*;
import com.example.my_bot.mapper.RoleMapper;
import com.example.my_bot.repository.RoleRepository;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.google.common.collect.ImmutableMap;

import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.entity.RoleEntity;
import com.github.benmanes.caffeine.cache.Cache;
import com.vdurmont.emoji.EmojiManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private MemberService memberService;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private Cache<Long, ImmutableMap<Integer, String>> dbRoleCache;

    @InjectMocks
    private RoleService roleService;

    private static final long CHAT_ID = 1L;
    private static final long FROM_ID = 100L;
    private static final int VALID_PRIORITY = 50;
    private static final String VALID_NAME = "Moderator";
    private static final String VALID_NAME_LOWERCASE = "moderator";

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getDbRoleCache()).thenReturn(dbRoleCache);

        lenient().when(dbRoleCache.get(anyLong(), any())).thenReturn(ImmutableMap.of());

        lenient().when(memberService.getMemberRolePriority(anyLong(), anyLong()))
                .thenReturn(DefaultRole.SENIOR_ADMINISTRATOR.getRolePriority());
    }


    @Test
    void createRole_shouldThrowWhenPriorityOutOfBounds() {
        int lowPriority = RoleService.MIN_CREATABLE_ROLE_PRIORITY - 1;
        int highPriority = RoleService.MAX_CREATABLE_ROLE_PRIORITY + 1;

        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, lowPriority, VALID_NAME))
                .isInstanceOf(RolePriorityOutOfBoundsException.class);

        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, highPriority, VALID_NAME))
                .isInstanceOf(RolePriorityOutOfBoundsException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_shouldThrowWhenNameLengthOutOfBounds() {
        String shortName = "ab";
        String longName = "a".repeat(31);

        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, VALID_PRIORITY, shortName))
                .isInstanceOf(RoleNameLengthOutOfBoundsException.class);

        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, VALID_PRIORITY, longName))
                .isInstanceOf(RoleNameLengthOutOfBoundsException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_shouldThrowWhenNameContainsEmoji() {
        try (MockedStatic<EmojiManager> emojiManager = mockStatic(EmojiManager.class)) {
            emojiManager.when(() -> EmojiManager.containsEmoji(anyString())).thenReturn(true);

            assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, VALID_PRIORITY, VALID_NAME))
                    .isInstanceOf(RoleNameCannotContainEmojiException.class);

            verify(roleRepository, never()).save(any());
        }
    }

    @Test
    void createRole_shouldThrowWhenRolePriorityIsDefault() {
        int defaultPriority = DefaultRole.ADMINISTRATOR.getRolePriority(); // например 90
        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, defaultPriority, VALID_NAME))
                .isInstanceOf(DuplicateRolePriorityException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_shouldThrowWhenNameMatchesDefaultRole() {
        String defaultName = DefaultRole.ADMINISTRATOR.getRoleName(); // "Administrator"
        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, VALID_PRIORITY, defaultName))
                .isInstanceOf(DuplicateRoleNameException.class);

        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, VALID_PRIORITY, defaultName.toLowerCase()))
                .isInstanceOf(DuplicateRoleNameException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_shouldThrowWhenPriorityAlreadyExistsInCustomRoles() {
        ImmutableMap<Integer, String> existingRoles = ImmutableMap.of(VALID_PRIORITY, "SomeRole");
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(existingRoles);

        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, VALID_PRIORITY, VALID_NAME))
                .isInstanceOf(DuplicateRolePriorityException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_shouldThrowWhenNameAlreadyExistsInCustomRoles() {
        ImmutableMap<Integer, String> existingRoles = ImmutableMap.of(10, VALID_NAME);
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(existingRoles);

        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, VALID_PRIORITY, VALID_NAME_LOWERCASE))
                .isInstanceOf(DuplicateRoleNameException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_shouldThrowWhenCustomRolesLimitReached() {

        ImmutableMap.Builder<Integer, String> builder = ImmutableMap.builder();
        for (int i = 1; i <= 10; i++) {
            builder.put(i, "Role" + i);
        }
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(builder.build());

        assertThatThrownBy(() -> roleService.createRole(CHAT_ID, FROM_ID, 21, VALID_NAME))
                .isInstanceOf(RoleCreationLimitReachedException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_shouldSaveAndReturnRole_whenAllValid() {

        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(ImmutableMap.of());

        RoleEntity entityToSave = new RoleEntity(CHAT_ID, VALID_PRIORITY, VALID_NAME);
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(entityToSave);

        RoleEntity result = roleService.createRole(CHAT_ID, FROM_ID, VALID_PRIORITY, VALID_NAME);

        assertThat(result).isEqualTo(entityToSave);
        verify(roleRepository).save(any(RoleEntity.class));
        verify(dbRoleCache).invalidate(CHAT_ID); // проверяем инвалидацию кэша
    }


    @Test
    void renameRoleByPriority_shouldThrowWhenNameLengthInvalid() {
        String shortName = "ab";
        String longName = "a".repeat(31);

        assertThatThrownBy(() -> roleService.renameRole(CHAT_ID, FROM_ID, VALID_PRIORITY, shortName))
                .isInstanceOf(RoleNameLengthOutOfBoundsException.class);

        assertThatThrownBy(() -> roleService.renameRole(CHAT_ID, FROM_ID, VALID_PRIORITY, longName))
                .isInstanceOf(RoleNameLengthOutOfBoundsException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void renameRoleByPriority_shouldThrowWhenNameContainsEmoji() {
        try (MockedStatic<EmojiManager> emojiManager = mockStatic(EmojiManager.class)) {
            emojiManager.when(() -> EmojiManager.containsEmoji(anyString())).thenReturn(true);

            assertThatThrownBy(() -> roleService.renameRole(CHAT_ID, FROM_ID, VALID_PRIORITY, VALID_NAME))
                    .isInstanceOf(RoleNameCannotContainEmojiException.class);

            verify(roleRepository, never()).save(any());
        }
    }

    @Test
    void renameRoleByPriority_shouldThrowWhenRoleNotFound() {
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(ImmutableMap.of());

        assertThatThrownBy(() -> roleService.renameRole(CHAT_ID, FROM_ID, VALID_PRIORITY, VALID_NAME))
                .isInstanceOf(RoleNotFoundException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void renameRoleByPriority_shouldThrowWhenNewNameExactlySameAsCurrent() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, VALID_NAME);
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        assertThatThrownBy(() -> roleService.renameRole(CHAT_ID, FROM_ID, VALID_PRIORITY, VALID_NAME))
                .isInstanceOf(DuplicateRoleNameException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void renameRoleByPriority_shouldThrowWhenNewNameMatchesDefaultRoleWithDifferentPriority() {

        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, VALID_NAME);
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        String defaultName = DefaultRole.ADMINISTRATOR.getRoleName();
        assertThatThrownBy(() -> roleService.renameRole(CHAT_ID, FROM_ID, VALID_PRIORITY, defaultName))
                .isInstanceOf(DuplicateRoleNameException.class);
    }

    @Test
    void renameRoleByPriority_shouldThrowWhenNewNameAlreadyUsedByAnotherCustomRole() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(
                VALID_PRIORITY, VALID_NAME,
                60, "Admin"
        );
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        assertThatThrownBy(() -> roleService.renameRole(CHAT_ID, FROM_ID, VALID_PRIORITY, "admin"))
                .isInstanceOf(DuplicateRoleNameException.class);
    }

    @Test
    void renameRoleByPriority_shouldRenameSuccessfully_whenAllValid() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, VALID_NAME);
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        String newName = "ModeratorNew";

        RoleEntity existingEntity = new RoleEntity(CHAT_ID, VALID_PRIORITY, VALID_NAME);
        when(roleRepository.findByChatIdAndRolePriority(CHAT_ID, VALID_PRIORITY))
                .thenReturn(Optional.of(existingEntity));

        RoleEntity savedEntity = new RoleEntity(CHAT_ID, VALID_PRIORITY, newName);
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(savedEntity);

        RoleDto expectedDto = new RoleDto(newName, VALID_PRIORITY);
        when(roleMapper.toDto(savedEntity)).thenReturn(expectedDto);

        RoleDto result = roleService.renameRole(CHAT_ID, FROM_ID, VALID_PRIORITY, newName);

        assertThat(result).isEqualTo(expectedDto);
        verify(roleRepository).save(any(RoleEntity.class));
        verify(dbRoleCache).invalidate(CHAT_ID);
    }


    @Test
    void renameRoleByName_shouldDelegateToPriorityMethod() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, VALID_NAME);
        lenient().when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);
        lenient().when(roleRepository.findByChatIdAndRolePriority(eq(CHAT_ID), eq(VALID_PRIORITY)))
                .thenReturn(Optional.of(new RoleEntity(CHAT_ID, VALID_PRIORITY, VALID_NAME)));
        RoleEntity saved = new RoleEntity(CHAT_ID, VALID_PRIORITY, "NewName");
        lenient().when(roleRepository.save(any(RoleEntity.class))).thenReturn(saved);
        RoleDto dto = new RoleDto("NewName", VALID_PRIORITY);
        lenient().when(roleMapper.toDto(saved)).thenReturn(dto);

        RoleDto result = roleService.renameRole(CHAT_ID, FROM_ID, VALID_NAME_LOWERCASE, "NewName");

        assertThat(result).isEqualTo(dto);
        verify(roleRepository).save(any(RoleEntity.class));
        verify(dbRoleCache).invalidate(CHAT_ID);
    }


    @Test
    void deleteCustomRoleByPriority_shouldThrowWhenTryingToDeleteDefaultRole() {
        int defaultPriority = DefaultRole.ADMINISTRATOR.getRolePriority();
        assertThatThrownBy(() -> roleService.deleteCustomRole(CHAT_ID, FROM_ID, defaultPriority))
                .isInstanceOf(CannotDeleteDefaultRoleException.class);

        verify(roleRepository, never()).deleteByChatIdAndRolePriority(anyLong(), anyInt());
    }

    @Test
    void deleteCustomRoleByPriority_shouldThrowWhenRoleNotFoundInCustom() {
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(ImmutableMap.of());

        assertThatThrownBy(() -> roleService.deleteCustomRole(CHAT_ID, FROM_ID, VALID_PRIORITY))
                .isInstanceOf(RoleNotFoundException.class);

        verify(roleRepository, never()).deleteByChatIdAndRolePriority(anyLong(), anyInt());
    }

    @Test
    void deleteCustomRoleByPriority_shouldThrowWhenCheckRoleInteractionFails() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, VALID_NAME);
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(DefaultRole.MEMBER.getRolePriority());

        assertThatThrownBy(() -> roleService.deleteCustomRole(CHAT_ID, FROM_ID, VALID_PRIORITY))
                .isInstanceOf(RoleAccessDeniedException.class);

        verify(roleRepository, never()).deleteByChatIdAndRolePriority(anyLong(), anyInt());
    }

    @Test
    void deleteCustomRoleByPriority_shouldDeleteSuccessfully_whenAllValid() {
        ImmutableMap<Integer, String> allRoles = ImmutableMap.of(
                VALID_PRIORITY, VALID_NAME,
                40, "LowerRole",
                60, "HigherRole"
        );
        lenient().when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(allRoles);

        lenient().when(roleRepository.deleteByChatIdAndRolePriority(CHAT_ID, VALID_PRIORITY)).thenReturn(1);

        doNothing().when(memberService).reAssignRequiredMembersMassively(CHAT_ID, VALID_PRIORITY, 40);

        lenient().when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(DefaultRole.SENIOR_ADMINISTRATOR.getRolePriority());

        RoleDto result = roleService.deleteCustomRole(CHAT_ID, FROM_ID, VALID_PRIORITY);

        assertThat(result.getRolePriority()).isEqualTo(40);
        assertThat(result.getRoleName()).isEqualTo("LowerRole");

        verify(memberService).reAssignRequiredMembersMassively(CHAT_ID, VALID_PRIORITY, 40);
        verify(roleRepository).deleteByChatIdAndRolePriority(CHAT_ID, VALID_PRIORITY);
        verify(dbRoleCache).invalidate(CHAT_ID);
    }

    @Test
    void deleteCustomRoleByName_shouldDelegateToPriorityMethod() {

        ImmutableMap<Integer, String> allRoles = ImmutableMap.of(
                VALID_PRIORITY, VALID_NAME,
                40, "LowerRole"
        );
        lenient().when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(allRoles);

        lenient().when(roleRepository.deleteByChatIdAndRolePriority(CHAT_ID, VALID_PRIORITY)).thenReturn(1);

        doNothing().when(memberService).reAssignRequiredMembersMassively(CHAT_ID, VALID_PRIORITY, 40);

        lenient().when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(DefaultRole.SENIOR_ADMINISTRATOR.getRolePriority());

        RoleDto result = roleService.deleteCustomRole(CHAT_ID, FROM_ID, VALID_NAME_LOWERCASE);

        assertThat(result.getRolePriority()).isEqualTo(40);
        assertThat(result.getRoleName()).isEqualTo("LowerRole");

        verify(roleRepository).deleteByChatIdAndRolePriority(CHAT_ID, VALID_PRIORITY);
        verify(dbRoleCache).invalidate(CHAT_ID);
        verify(memberService).reAssignRequiredMembersMassively(CHAT_ID, VALID_PRIORITY, 40);
    }


    @Test
    void findTheNearestLowestRole_shouldReturnLowerRole_whenExists() {
        // Добавляем все дефолтные роли, чтобы они не мешали
        ImmutableMap<Integer, String> roles = ImmutableMap.<Integer, String>builder()
                .put(10, "Role10")
                .put(30, "Role30")
                .put(50, "Role50")
                .put(0, DefaultRole.MEMBER.getRoleName())
                .put(20, DefaultRole.MODERATOR.getRoleName())
                .put(40, DefaultRole.SENIOR_MODERATOR.getRoleName())
                .put(60, DefaultRole.ADMINISTRATOR.getRoleName())
                .put(80, DefaultRole.SENIOR_ADMINISTRATOR.getRoleName())
                .put(100, DefaultRole.CHAT_CREATOR.getRoleName())
                .build();

        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        RoleDto result = roleService.findTheNearestLowestRole(CHAT_ID, 30, false);
        assertThat(result.getRolePriority()).isEqualTo(20);
        assertThat(result.getRoleName()).isEqualTo(DefaultRole.MODERATOR.getRoleName());
    }

    @Test
    void findTheNearestLowestRole_shouldReturnHigherRole_whenLowerNotFoundAndFindHigherIfAbsents() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(10, "Role10", 30, "Role30");
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        RoleDto result = roleService.findTheNearestLowestRole(CHAT_ID, 40, true);
        assertThat(result.getRolePriority()).isEqualTo(30);
        assertThat(result.getRoleName()).isEqualTo("Role30");
    }


    @Test
    void findTheNearestHighestRole_shouldReturnHigherRole_whenExists() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(10, "Role10", 30, "Role30", 50, "Role50");
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        RoleDto result = roleService.findTheNearestHighestRole(CHAT_ID, 29);
        assertThat(result.getRolePriority()).isEqualTo(30);
        assertThat(result.getRoleName()).isEqualTo("Role30");
    }

    @Test
    void findTheNearestHighestRole_shouldThrowWhenNotFound() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(10, "Role10");
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        assertThatThrownBy(() -> roleService.findTheNearestHighestRole(CHAT_ID, 100))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void getAllRolesSortedInDescendingOrder_shouldReturnTreeMapWithReverseOrder() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(10, "Role10", 30, "Role30", 50, "Role50");
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        TreeMap<Integer, String> result = roleService.getAllRolesSortedInDescendingOrder(CHAT_ID);

        assertThat(result).contains(
                Map.entry(50, "Role50"),
                Map.entry(30, "Role30"),
                Map.entry(10, "Role10")
        );
    }


    @Test
    void getAllRolesWithNoSorting_shouldMergeCustomAndDefaultRoles() {
        ImmutableMap<Integer, String> custom = ImmutableMap.of(10, "Custom10", 13, "Custom13");
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(custom);

        Map<Integer, String> result = roleService.getAllRolesWithNoSorting(CHAT_ID);

        assertThat(result).containsAllEntriesOf(custom);
        for (DefaultRole defaultRole : DefaultRole.values()) {
            assertThat(result).containsEntry(defaultRole.getRolePriority(), defaultRole.getRoleName());
        }
        assertThat(result.get(DefaultRole.ADMINISTRATOR.getRolePriority()))
                .isEqualTo(DefaultRole.ADMINISTRATOR.getRoleName());
    }


    @Test
    void getRoleName_shouldReturnOptionalName_whenRoleExists() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, VALID_NAME);
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        Optional<String> result = roleService.getRoleName(CHAT_ID, VALID_PRIORITY);
        assertThat(result).contains(VALID_NAME);
    }

    @Test
    void getRoleName_shouldReturnEmpty_whenRoleDoesNotExist() {
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(ImmutableMap.of());

        Optional<String> result = roleService.getRoleName(CHAT_ID, 999);
        assertThat(result).isEmpty();
    }


    @Test
    void getRoleByPriority_shouldReturnOptionalDto_whenRoleExists() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, VALID_NAME);
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        Optional<RoleDto> result = roleService.getRoleByPriority(CHAT_ID, VALID_PRIORITY);
        assertThat(result).isPresent();
        assertThat(result.get().getRolePriority()).isEqualTo(VALID_PRIORITY);
        assertThat(result.get().getRoleName()).isEqualTo(VALID_NAME);
    }

    @Test
    void getRoleByPriority_shouldReturnEmpty_whenRoleDoesNotExist() {
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(ImmutableMap.of());

        Optional<RoleDto> result = roleService.getRoleByPriority(CHAT_ID, 999);
        assertThat(result).isEmpty();
    }


    @Test
    void roleExistsByPriority_shouldReturnTrueForDefaultRole() {
        assertThat(roleService.roleExistsByPriority(CHAT_ID, DefaultRole.MEMBER.getRolePriority())).isTrue();
        verify(dbRoleCache, never()).get(anyLong(), any());
    }

    @Test
    void roleExistsByPriority_shouldReturnTrueForCustomRole() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, VALID_NAME);
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        assertThat(roleService.roleExistsByPriority(CHAT_ID, VALID_PRIORITY)).isTrue();
    }

    @Test
    void roleExistsByPriority_shouldReturnFalseForNonExistingCustomRole() {
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(ImmutableMap.of());

        assertThat(roleService.roleExistsByPriority(CHAT_ID, 999)).isFalse();
    }


    @Test
    void getRoleByNameIgnoreCase_shouldReturnDto_whenNameMatchesIgnoringCase() {
        ImmutableMap<Integer, String> roles = ImmutableMap.of(VALID_PRIORITY, "Moderator");
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(roles);

        Optional<RoleDto> result = roleService.getRoleByNameIgnoreCase(CHAT_ID, "moderator");
        assertThat(result).isPresent();
        assertThat(result.get().getRolePriority()).isEqualTo(VALID_PRIORITY);
        assertThat(result.get().getRoleName()).isEqualTo("Moderator");
    }

    @Test
    void getRoleByNameIgnoreCase_shouldReturnEmpty_whenNoMatch() {
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenReturn(ImmutableMap.of());

        Optional<RoleDto> result = roleService.getRoleByNameIgnoreCase(CHAT_ID, "nonexistent");
        assertThat(result).isEmpty();
    }


    @Test
    void checkRoleInteractionAbility_shouldThrowWhenRoleToEditHigherThanUserRole() {
        int userRole = DefaultRole.MEMBER.getRolePriority(); // 0
        int roleToEdit = DefaultRole.ADMINISTRATOR.getRolePriority(); // 90
        assertThatThrownBy(() -> roleService.checkRoleInteractionAbility(roleToEdit, userRole))
                .isInstanceOf(RoleAccessDeniedException.class);
    }

    @Test
    void checkRoleInteractionAbility_shouldThrowWhenRoleToEditEqualsUserRoleAndUserIsBelowSeniorAdmin() {
        int userRole = DefaultRole.ADMINISTRATOR.getRolePriority(); // 90
        int roleToEdit = DefaultRole.ADMINISTRATOR.getRolePriority();
        assertThatThrownBy(() -> roleService.checkRoleInteractionAbility(roleToEdit, userRole))
                .isInstanceOf(RoleAccessDeniedException.class);
    }

    @Test
    void checkRoleInteractionAbility_shouldNotThrowWhenRoleToEditEqualsUserRoleAndUserIsSeniorAdminOrAbove() {
        int userRole = DefaultRole.SENIOR_ADMINISTRATOR.getRolePriority();
        int roleToEdit = DefaultRole.SENIOR_ADMINISTRATOR.getRolePriority();
        roleService.checkRoleInteractionAbility(roleToEdit, userRole);
    }

    @Test
    void checkRoleInteractionAbility_shouldNotThrowWhenRoleToEditLowerThanUserRole() {
        int userRole = DefaultRole.ADMINISTRATOR.getRolePriority(); // 90
        int roleToEdit = DefaultRole.MEMBER.getRolePriority(); // 0
        roleService.checkRoleInteractionAbility(roleToEdit, userRole); // проходит
    }

    @Test
    void checkRoleInteractionAbility_withChatId_shouldDelegateAndThrowIfNeeded() {
        when(memberService.getMemberRolePriority(CHAT_ID, FROM_ID))
                .thenReturn(DefaultRole.MEMBER.getRolePriority());
        int roleToEdit = DefaultRole.ADMINISTRATOR.getRolePriority();

        assertThatThrownBy(() -> roleService.checkRoleInteractionAbility(CHAT_ID, roleToEdit, FROM_ID))
                .isInstanceOf(RoleAccessDeniedException.class);
    }


    @Test
    void getCreatedOrModifiedRoles_shouldUseCacheAndLoadFromRepositoryIfAbsent() {
        // подготовим список ролей из репозитория
        List<RoleEntity> entities = Arrays.asList(
                new RoleEntity(CHAT_ID, 10, "Role10"),
                new RoleEntity(CHAT_ID, 20, "Role20")
        );
        when(roleRepository.findByChatId(CHAT_ID)).thenReturn(entities);

        // замокаем кэш: при вызове get с функцией, она должна выполниться и вернуть ImmutableMap
        // но мы не можем легко проверить, что функция выполнилась, но можем проверить результат
        // используем ArgumentCaptor для функции? проще: вызовем метод, и проверим, что roleRepository.findByChatId вызван.
        // поскольку кэш мок, мы можем стабулить get так, чтобы он выполнял функцию.
        when(dbRoleCache.get(eq(CHAT_ID), any())).thenAnswer(invocation -> {
            Function<Long, ImmutableMap<Integer, String>> loader = invocation.getArgument(1);
            return loader.apply(CHAT_ID);
        });

        Map<Integer, String> result = roleService.getCreatedOrModifiedRoles(CHAT_ID);

        assertThat(result).containsEntry(10, "Role10");
        assertThat(result).containsEntry(20, "Role20");
        verify(roleRepository).findByChatId(CHAT_ID);
        verify(dbRoleCache).get(eq(CHAT_ID), any());
    }

}
