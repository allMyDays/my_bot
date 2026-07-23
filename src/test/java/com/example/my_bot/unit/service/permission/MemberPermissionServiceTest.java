package com.example.my_bot.unit.service.permission;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.command.CommandAuthorizationResult;
import com.example.my_bot.dto.command.UserCommandValidationResult;
import com.example.my_bot.dto.permission.MemberPermissionSettingResult;
import com.example.my_bot.entity.MemberPermissionEntity;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.repository.permission.MemberPermissionRepository;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.permission.MemberPermissionService;
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
class MemberPermissionServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private MemberPermissionRepository memberPermissionRepository;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private CommandAccessService commandService;

    @Mock
    private CommandRegistry commandRegistry;

    @Mock
    private Cache<Long, ImmutableMap<String, ImmutableMap<Long, Boolean>>> permissionCache;

    @InjectMocks
    private MemberPermissionService memberPermissionService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final long targetUserId = 200L;
    private final Set<String> userCommands = Set.of("/cmd1", "/cmd2");
    private final Set<String> normalizedCommands = Set.of("cmd1", "cmd2");
    private final int callerRole = 30;

    @BeforeEach
    void setUp() {
        memberPermissionService.setCommandRegistry(commandRegistry);
        given(cacheManager.getMemberPermissionCache()).willReturn(permissionCache);

        given(permissionCache.get(eq(chatId), any())).willReturn(ImmutableMap.of());
    }


    @Nested
    class AllowOrForbidCommandForMemberTest {

        @Test
        void shouldThrowWhenTargetIsSelf() {
            assertThatThrownBy(() -> memberPermissionService.allowOrForbidCommandForMember(chatId, fromId, userCommands, fromId, true))
                    .isInstanceOf(CannotApplyThisCommandToYourselfException.class);
            verifyNoInteractions(memberService);
        }

        @Test
        void shouldReturnNotFoundWhenCommandsNotRegistered() {
            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of("cmd1", "cmd2"), Set.of());
            validationResult.setNormalizedCommands(Set.of());
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            MemberPermissionSettingResult result = memberPermissionService.allowOrForbidCommandForMember(chatId, fromId, userCommands, targetUserId, true);

            assertThat(result.getAccepted()).isEmpty();
            assertThat(result.getNotFound()).containsExactlyInAnyOrder("cmd1", "cmd2");
            assertThat(result.getForbiddenToEdit()).isEmpty();
            assertThat(result.getHasRequiredPermissionAlready()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).isEmpty();
            verify(memberPermissionRepository, never()).saveAll(any());
            verify(memberPermissionRepository, never()).updateUserPermissionsForRequiredCommands(anyLong(), anySet(), anyLong(), anyBoolean());
        }

        @Test
        void shouldReturnForbiddenWhenUserHasNoAccessToEditCommand() {
            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of(), normalizedCommands);
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            CommandAuthorizationResult authResult = new CommandAuthorizationResult();
            authResult.setAllowed(Set.of());
            authResult.setForbidden(Set.of("cmd1", "cmd2"));
            given(commandService.checkCommandsAuthorization(chatId, normalizedCommands, callerRole, fromId)).willReturn(authResult);

            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(callerRole);
            // void метод – используем doNothing()
            doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, targetUserId, true);

            MemberPermissionSettingResult result = memberPermissionService.allowOrForbidCommandForMember(chatId, fromId, userCommands, targetUserId, true);

            assertThat(result.getAccepted()).isEmpty();
            assertThat(result.getForbiddenToEdit()).containsExactlyInAnyOrder("cmd1", "cmd2");
            assertThat(result.getNotFound()).isEmpty();
            assertThat(result.getHasRequiredPermissionAlready()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).isEmpty();
            verify(memberPermissionRepository, never()).saveAll(any());
            verify(memberPermissionRepository, never()).updateUserPermissionsForRequiredCommands(anyLong(), anySet(), anyLong(), anyBoolean());
        }

        @Test
        void shouldAddNewPermissionsWhenSpaceAvailable() {
            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of(), normalizedCommands);
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            CommandAuthorizationResult authResult = new CommandAuthorizationResult();
            authResult.setAllowed(normalizedCommands);
            authResult.setForbidden(Set.of());
            given(commandService.checkCommandsAuthorization(chatId, normalizedCommands, callerRole, fromId)).willReturn(authResult);

            given(permissionCache.get(eq(chatId), any())).willReturn(ImmutableMap.of());

            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(callerRole);
            doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, targetUserId, true);

            MemberPermissionSettingResult result = memberPermissionService.allowOrForbidCommandForMember(chatId, fromId, userCommands, targetUserId, true);

            assertThat(result.getAccepted()).containsExactlyInAnyOrder("cmd1", "cmd2");
            assertThat(result.getNotFound()).isEmpty();
            assertThat(result.getForbiddenToEdit()).isEmpty();
            assertThat(result.getHasRequiredPermissionAlready()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).isEmpty();

            ArgumentCaptor<Set<MemberPermissionEntity>> captor = ArgumentCaptor.forClass(Set.class);
            verify(memberPermissionRepository).saveAll(captor.capture());
            Set<MemberPermissionEntity> saved = captor.getValue();
            assertThat(saved).hasSize(2);
            for (MemberPermissionEntity entity : saved) {
                assertThat(entity.getChatId()).isEqualTo(chatId);
                assertThat(entity.getUserId()).isEqualTo(targetUserId);
                assertThat(entity.isAllowed()).isTrue();
                assertThat(entity.getCommandName()).isIn("cmd1", "cmd2");
            }
            verify(memberPermissionRepository, never()).updateUserPermissionsForRequiredCommands(anyLong(), anySet(), anyLong(), anyBoolean());
            verify(permissionCache).invalidate(chatId);
        }

        @Test
        void shouldUpdateExistingPermissionWhenValueDiffers() {
            ImmutableMap<String, ImmutableMap<Long, Boolean>> existing = ImmutableMap.of(
                    "cmd1", ImmutableMap.of(targetUserId, true),
                    "cmd2", ImmutableMap.of(targetUserId, false)
            );
            given(permissionCache.get(eq(chatId), any())).willReturn(existing);

            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of(), normalizedCommands);
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            CommandAuthorizationResult authResult = new CommandAuthorizationResult();
            authResult.setAllowed(normalizedCommands);
            authResult.setForbidden(Set.of());
            given(commandService.checkCommandsAuthorization(chatId, normalizedCommands, callerRole, fromId)).willReturn(authResult);

            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(callerRole);
            doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, targetUserId, true);

            MemberPermissionSettingResult result = memberPermissionService.allowOrForbidCommandForMember(chatId, fromId, userCommands, targetUserId, true);

            assertThat(result.getAccepted()).containsExactly("cmd2");   // только изменённая команда
            assertThat(result.getHasRequiredPermissionAlready()).containsExactly("cmd1"); // уже имеет разрешение
            assertThat(result.getNotFound()).isEmpty();
            assertThat(result.getForbiddenToEdit()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).isEmpty();

            verify(memberPermissionRepository, never()).saveAll(any());
            verify(memberPermissionRepository).updateUserPermissionsForRequiredCommands(chatId, Set.of("cmd2"), targetUserId, true);
            verify(permissionCache).invalidate(chatId);
        }

        @Test
        void shouldNotAddWhenNoSpaceLeft() {
            ImmutableMap.Builder<String, ImmutableMap<Long, Boolean>> builder = ImmutableMap.builder();
            for (int i = 0; i < 20; i++) {
                // Используем разных пользователей (не targetUserId), чтобы записи были, но не для targetUserId
                builder.put("cmd" + i, ImmutableMap.of((long)(i + 1), true));
            }
            ImmutableMap<String, ImmutableMap<Long, Boolean>> existing = builder.build();
            given(permissionCache.get(eq(chatId), any())).willReturn(existing);

            UserCommandValidationResult validationResult = new UserCommandValidationResult(Set.of(), normalizedCommands);
            given(commandRegistry.getMainNamesOfRequiredCommands(userCommands)).willReturn(validationResult);

            CommandAuthorizationResult authResult = new CommandAuthorizationResult();
            authResult.setAllowed(normalizedCommands);
            authResult.setForbidden(Set.of());
            given(commandService.checkCommandsAuthorization(chatId, normalizedCommands, callerRole, fromId)).willReturn(authResult);

            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(callerRole);
            doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, targetUserId, true);

            MemberPermissionSettingResult result = memberPermissionService.allowOrForbidCommandForMember(chatId, fromId, userCommands, targetUserId, true);

            assertThat(result.getAccepted()).isEmpty();
            assertThat(result.getNotEnoughSpaceToAddNew()).containsExactlyInAnyOrder("cmd1", "cmd2");
            assertThat(result.getHasRequiredPermissionAlready()).isEmpty();
            assertThat(result.getNotFound()).isEmpty();
            assertThat(result.getForbiddenToEdit()).isEmpty();

            verify(memberPermissionRepository, never()).saveAll(any());
            verify(memberPermissionRepository, never()).updateUserPermissionsForRequiredCommands(anyLong(), anySet(), anyLong(), anyBoolean());
            verify(permissionCache).invalidate(chatId);
        }
    }


    @Nested
    class DeleteCustomMemberPermissionTest {

        @Test
        void shouldThrowWhenTargetIsSelf() {
            assertThatThrownBy(() -> memberPermissionService.deleteCustomMemberPermission(chatId, "/cmd", fromId, fromId))
                    .isInstanceOf(CannotApplyThisCommandToYourselfException.class);
            verifyNoInteractions(memberService);
        }

        @Test
        void shouldThrowWhenCommandNotFound() {
            given(commandRegistry.getMainNameOfCommand("/cmd")).willReturn(Optional.empty());
            assertThatThrownBy(() -> memberPermissionService.deleteCustomMemberPermission(chatId, "/cmd", targetUserId, fromId))
                    .isInstanceOf(UserCommandNotFoundException.class);
            verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
        }

        @Test
        void shouldThrowWhenUserHasNoAccessToCommand() {
            given(commandRegistry.getMainNameOfCommand("/cmd")).willReturn(Optional.of("cmd"));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(callerRole);
            given(commandService.checkCommandAuthorization(chatId, "cmd", callerRole, fromId)).willReturn(false);

            assertThatThrownBy(() -> memberPermissionService.deleteCustomMemberPermission(chatId, "/cmd", targetUserId, fromId))
                    .isInstanceOf(CommandAccessDeniedException.class)
                    .hasMessageContaining("cmd");
            verify(memberPermissionRepository, never()).deleteMemberPermissionForOneCommand(anyLong(), anyString(), anyLong());
        }

        @Test
        void shouldDeleteSuccessfully() {
            given(commandRegistry.getMainNameOfCommand("/cmd")).willReturn(Optional.of("cmd"));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(callerRole);
            given(commandService.checkCommandAuthorization(chatId, "cmd", callerRole, fromId)).willReturn(true);
            doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, targetUserId, true);

            memberPermissionService.deleteCustomMemberPermission(chatId, "/cmd", targetUserId, fromId);

            verify(memberPermissionRepository).deleteMemberPermissionForOneCommand(chatId, "cmd", targetUserId);
            verify(permissionCache).invalidate(chatId);
        }
    }

    @Test
    void shouldGetCachedPermissionsAndBuildMap() {
        List<MemberPermissionEntity> entities = Arrays.asList(
                new MemberPermissionEntity(chatId, "cmd1", 101L, true),
                new MemberPermissionEntity(chatId, "cmd1", 102L, false),
                new MemberPermissionEntity(chatId, "cmd2", 101L, true)
        );
        given(memberPermissionRepository.findByChatId(chatId)).willReturn(entities);

        given(permissionCache.get(eq(chatId), any())).willAnswer(invocation -> {
            java.util.function.Function<Long, ImmutableMap<String, ImmutableMap<Long, Boolean>>> loader =
                    invocation.getArgument(1);
            return loader.apply(chatId);
        });

        ImmutableMap<String, ImmutableMap<Long, Boolean>> result =
                memberPermissionService.getCachedCustomMemberPermissions(chatId);

        assertThat(result).hasSize(2);
        assertThat(result.get("cmd1")).containsEntry(101L, true).containsEntry(102L, false);
        assertThat(result.get("cmd2")).containsEntry(101L, true);
        verify(memberPermissionRepository).findByChatId(chatId);
    }


    @Test
    void shouldReturnMaxCount() {
        assertThat(MemberPermissionService.getMaxCustomMemberPermissionsCount()).isEqualTo(20);
    }
}