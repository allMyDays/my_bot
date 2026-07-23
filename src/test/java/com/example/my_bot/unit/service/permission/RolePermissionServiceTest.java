package com.example.my_bot.unit.service.permission;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandAuthorizationResult;
import com.example.my_bot.dto.command.UserCommandValidationResult;
import com.example.my_bot.dto.permission.RolePermissionSettingResult;
import com.example.my_bot.entity.RolePermissionEntity;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.repository.permission.RolePermissionRepository;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.permission.RolePermissionService;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePermissionServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private RoleService roleService;

    @Mock
    private RolePermissionRepository permissionRepository;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private CommandAccessService commandService;

    @Mock
    private CommandRegistry commandRegistry;

    @Mock
    private Cache<Long, ImmutableMap<String, Integer>> rolePermissionCache;

    @InjectMocks
    private RolePermissionService rolePermissionService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final Set<String> userCommands = Set.of("/cmd1", "/cmd2");
    private final Set<String> normalizedCommands = Set.of("cmd1", "cmd2");
    private final int rolePriority = 20;
    private final int userRolePriority = 30;
    private final RoleDto roleDto = new RoleDto("moderator", rolePriority);

    @BeforeEach
    void setUp() {
        rolePermissionService.setCommandRegistry(commandRegistry);
        given(cacheManager.getRolePermissionCache()).willReturn(rolePermissionCache);
        given(rolePermissionCache.get(eq(chatId), any())).willReturn(ImmutableMap.of());
    }

    @Nested
    class AllowCommandForRoleWithPriorityTest {

        @Test
        void shouldThrowWhenRoleNotFound() {
            given(roleService.getRoleByPriority(chatId, rolePriority)).willReturn(Optional.empty());
            assertThatThrownBy(() -> rolePermissionService.allowCommandForRole(chatId, fromId, userCommands, rolePriority))
                    .isInstanceOf(RoleNotFoundException.class);
            verifyNoInteractions(commandRegistry, commandService, permissionRepository);
        }

        @Test
        void shouldReturnNotFoundWhenCommandsNotRegistered() {
            given(roleService.getRoleByPriority(chatId, rolePriority)).willReturn(Optional.of(roleDto));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(userRolePriority);
            doNothing().when(roleService).checkRoleInteractionAbility(rolePriority, userRolePriority);

            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of("cmd1", "cmd2"), Set.of());
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            RolePermissionSettingResult result = rolePermissionService.allowCommandForRole(chatId, fromId, userCommands, rolePriority);

            assertThat(result.getAccepted()).isEmpty();
            assertThat(result.getNotFound()).containsExactlyInAnyOrder("cmd1", "cmd2");
            assertThat(result.getForbiddenToEdit()).isEmpty();
            assertThat(result.getHasRequiredPermissionAlready()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).isEmpty();
            assertThat(result.getRoleDto()).isEqualTo(roleDto);
            verify(permissionRepository, never()).saveAll(any());
            verify(permissionRepository, never()).updateRolePermissionForRequiredCommands(anyLong(), anySet(), anyInt());
            verify(rolePermissionCache, never()).invalidate(anyLong());
        }

        @Test
        void shouldReturnForbiddenWhenUserHasNoAccessToEditCommand() {
            given(roleService.getRoleByPriority(chatId, rolePriority)).willReturn(Optional.of(roleDto));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(userRolePriority);
            doNothing().when(roleService).checkRoleInteractionAbility(rolePriority, userRolePriority);

            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of(), normalizedCommands);
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            CommandAuthorizationResult authResult = new CommandAuthorizationResult();
            authResult.setAllowed(Set.of());
            authResult.setForbidden(Set.of("cmd1", "cmd2"));
            given(commandService.checkCommandsAuthorization(chatId, normalizedCommands, userRolePriority, fromId)).willReturn(authResult);

            RolePermissionSettingResult result = rolePermissionService.allowCommandForRole(chatId, fromId, userCommands, rolePriority);

            assertThat(result.getAccepted()).isEmpty();
            assertThat(result.getForbiddenToEdit()).containsExactlyInAnyOrder("cmd1", "cmd2");
            assertThat(result.getNotFound()).isEmpty();
            assertThat(result.getHasRequiredPermissionAlready()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).isEmpty();
            assertThat(result.getRoleDto()).isEqualTo(roleDto);
            verify(permissionRepository, never()).saveAll(any());
            verify(permissionRepository, never()).updateRolePermissionForRequiredCommands(anyLong(), anySet(), anyInt());
            verify(rolePermissionCache).invalidate(chatId);
        }

        @Test
        void shouldAddNewPermissionsWhenSpaceAvailable() {
            given(roleService.getRoleByPriority(chatId, rolePriority)).willReturn(Optional.of(roleDto));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(userRolePriority);
            doNothing().when(roleService).checkRoleInteractionAbility(rolePriority, userRolePriority);

            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of(), normalizedCommands);

            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            CommandAuthorizationResult authResult = new CommandAuthorizationResult();
            authResult.setAllowed(normalizedCommands);
            authResult.setForbidden(Set.of());
            given(commandService.checkCommandsAuthorization(chatId, normalizedCommands, userRolePriority, fromId)).willReturn(authResult);

            given(rolePermissionCache.get(eq(chatId), any())).willReturn(ImmutableMap.of());

            RolePermissionSettingResult result = rolePermissionService.allowCommandForRole(chatId, fromId, userCommands, rolePriority);

            assertThat(result.getAccepted()).containsExactlyInAnyOrder("cmd1", "cmd2");
            assertThat(result.getNotFound()).isEmpty();
            assertThat(result.getForbiddenToEdit()).isEmpty();
            assertThat(result.getHasRequiredPermissionAlready()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).isEmpty();
            assertThat(result.getRoleDto()).isEqualTo(roleDto);

            ArgumentCaptor<Set<RolePermissionEntity>> captor = ArgumentCaptor.forClass(Set.class);
            verify(permissionRepository).saveAll(captor.capture());
            Set<RolePermissionEntity> saved = captor.getValue();
            assertThat(saved).hasSize(2);
            for (RolePermissionEntity entity : saved) {
                assertThat(entity.getChatId()).isEqualTo(chatId);
                assertThat(entity.getRolePriority()).isEqualTo(rolePriority);
                assertThat(entity.getCommandName()).isIn("cmd1", "cmd2");
            }
            verify(permissionRepository, never()).updateRolePermissionForRequiredCommands(anyLong(), anySet(), anyInt());
            verify(rolePermissionCache).invalidate(chatId);
        }

        @Test
        void shouldUpdateExistingPermissionWhenPriorityDiffers() {
            given(roleService.getRoleByPriority(chatId, rolePriority)).willReturn(Optional.of(roleDto));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(userRolePriority);
            doNothing().when(roleService).checkRoleInteractionAbility(rolePriority, userRolePriority);

            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of(), normalizedCommands);
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            CommandAuthorizationResult authResult = new CommandAuthorizationResult();
            authResult.setAllowed(normalizedCommands);
            authResult.setForbidden(Set.of());
            given(commandService.checkCommandsAuthorization(chatId, normalizedCommands, userRolePriority, fromId)).willReturn(authResult);

            ImmutableMap<String, Integer> existing = ImmutableMap.of(
                    "cmd1", 10,
                    "cmd2", rolePriority
            );
            given(rolePermissionCache.get(eq(chatId), any())).willReturn(existing);

            RolePermissionSettingResult result = rolePermissionService.allowCommandForRole(chatId, fromId, userCommands, rolePriority);

            assertThat(result.getAccepted()).containsExactly("cmd1");
            assertThat(result.getHasRequiredPermissionAlready()).containsExactly("cmd2");
            assertThat(result.getNotFound()).isEmpty();
            assertThat(result.getForbiddenToEdit()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).isEmpty();

            verify(permissionRepository, never()).saveAll(any());
            verify(permissionRepository).updateRolePermissionForRequiredCommands(chatId, Set.of("cmd1"), rolePriority);
            verify(rolePermissionCache).invalidate(chatId);
        }

        @Test
        void shouldNotAddWhenNoSpaceLeft() {
            given(roleService.getRoleByPriority(chatId, rolePriority)).willReturn(Optional.of(roleDto));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(userRolePriority);
            doNothing().when(roleService).checkRoleInteractionAbility(rolePriority, userRolePriority);

            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of(), normalizedCommands);
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            CommandAuthorizationResult authResult = new CommandAuthorizationResult();
            authResult.setAllowed(normalizedCommands);
            authResult.setForbidden(Set.of());
            given(commandService.checkCommandsAuthorization(chatId, normalizedCommands, userRolePriority, fromId)).willReturn(authResult);

            ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
            for (int i = 0; i < 40; i++) {
                builder.put("other" + i, 10);
            }
            ImmutableMap<String, Integer> existing = builder.build();
            given(rolePermissionCache.get(eq(chatId), any())).willReturn(existing);

            RolePermissionSettingResult result = rolePermissionService.allowCommandForRole(chatId, fromId, userCommands, rolePriority);

            assertThat(result.getAccepted()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).containsExactlyInAnyOrder("cmd1", "cmd2");
            assertThat(result.getHasRequiredPermissionAlready()).isEmpty();
            assertThat(result.getNotFound()).isEmpty();
            assertThat(result.getForbiddenToEdit()).isEmpty();

            verify(permissionRepository, never()).saveAll(any());
            verify(permissionRepository, never()).updateRolePermissionForRequiredCommands(anyLong(), anySet(), anyInt());
            verify(rolePermissionCache).invalidate(chatId);
        }
    }

    @Nested
    class AllowCommandForRoleWithNameTest {

        @Test
        void shouldCallOverloadedMethodWithPriority() {
            String roleName = "moderator";
            given(roleService.getRoleByNameIgnoreCase(chatId, roleName)).willReturn(Optional.of(roleDto));

            RolePermissionService spy = spy(rolePermissionService);
            doReturn(new RolePermissionSettingResult()).when(spy).allowCommandForRole(chatId, fromId, userCommands, rolePriority);

            spy.allowCommandForRole(chatId, fromId, userCommands, roleName);

            verify(spy).allowCommandForRole(chatId, fromId, userCommands, rolePriority);
        }

        @Test
        void shouldThrowWhenRoleNameNotFound() {
            given(roleService.getRoleByNameIgnoreCase(chatId, "unknown")).willReturn(Optional.empty());
            assertThatThrownBy(() -> rolePermissionService.allowCommandForRole(chatId, fromId, userCommands, "unknown"))
                    .isInstanceOf(RoleNotFoundException.class);
        }
    }


    @Nested
    class DeleteCustomRolePermissionTest {

        @Test
        void shouldThrowWhenCommandNotFound() {
            given(commandRegistry.getMainNameOfCommand("cmd")).willReturn(Optional.empty());
            assertThatThrownBy(() -> rolePermissionService.deleteCustomRolePermission(chatId, "/cmd", fromId))
                    .isInstanceOf(UserCommandNotFoundException.class);
            verify(memberService, never()).getMemberRolePriority(anyLong(), anyLong());
            verify(permissionRepository, never()).deleteRolePermissionForOneCommand(anyLong(), anyString());
        }

        @Test
        void shouldThrowWhenUserHasNoAccessToCommand() {
            given(commandRegistry.getMainNameOfCommand(anyString())).willReturn(Optional.of("cmd"));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(userRolePriority);
            given(commandService.checkCommandAuthorization(chatId, "cmd", userRolePriority, fromId)).willReturn(false);

            assertThatThrownBy(() -> rolePermissionService.deleteCustomRolePermission(chatId, "/cmd", fromId))
                    .isInstanceOf(CommandAccessDeniedException.class)
                    .hasMessageContaining("cmd");
            verify(permissionRepository, never()).deleteRolePermissionForOneCommand(anyLong(), anyString());
        }

        @Test
        void shouldDeleteSuccessfully() {
            given(commandRegistry.getMainNameOfCommand(anyString())).willReturn(Optional.of("cmd"));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(userRolePriority);
            given(commandService.checkCommandAuthorization(chatId, "cmd", userRolePriority, fromId)).willReturn(true);

            rolePermissionService.deleteCustomRolePermission(chatId, "/cmd", fromId);

            verify(permissionRepository).deleteRolePermissionForOneCommand(chatId, "cmd");
            verify(rolePermissionCache).invalidate(chatId);
        }
    }


    @Test
    void shouldGetCachedPermissionsAndBuildMap() {
        List<RolePermissionEntity> entities = Arrays.asList(
                new RolePermissionEntity(chatId, "cmd1", 10),
                new RolePermissionEntity(chatId, "cmd2", 20)
        );
        given(permissionRepository.findByChatId(chatId)).willReturn(entities);

        given(rolePermissionCache.get(eq(chatId), any())).willAnswer(invocation -> {
            java.util.function.Function<Long, ImmutableMap<String, Integer>> loader =
                    invocation.getArgument(1);
            return loader.apply(chatId);
        });

        ImmutableMap<String, Integer> result = rolePermissionService.getCachedCustomRolePermissions(chatId);

        assertThat(result).hasSize(2);
        assertThat(result.get("cmd1")).isEqualTo(10);
        assertThat(result.get("cmd2")).isEqualTo(20);
        verify(permissionRepository).findByChatId(chatId);
    }


    @Test
    void shouldReturnMaxCount() {
        assertThat(RolePermissionService.getMaxCustomRolePermissionsCount()).isEqualTo(40);
    }
}