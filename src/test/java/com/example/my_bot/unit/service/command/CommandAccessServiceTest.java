package com.example.my_bot.unit.service.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.ChatCommand;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandAuthorizationResult;
import com.example.my_bot.dto.command.UserCommandValidationResult;
import com.example.my_bot.dto.cooldown.CooldownResult;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.service.*;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.permission.MemberPermissionService;
import com.example.my_bot.service.permission.RolePermissionService;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandAccessServiceTest {

    @Mock
    private CommandRegistry commandRegistry;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private MemberPermissionService memberPermissionService;

    @Mock
    private RoleRateLimitService roleRateLimitService;

    @InjectMocks
    private CommandAccessService commandAccessService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final int userRolePriority = 30;
    private final String commandName = "testCmd";
    private final String normalizedCommand = "testCmd";
    private final Set<String> commands = Set.of(commandName);

    // ---------------------- checkCommandAuthorization ---------------------------

    @Test
    void shouldAllowCommandWhenPersonalPermissionTrue() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of(
                normalizedCommand, ImmutableMap.of(fromId, true)
        ));
        given(rolePermissionService.getCachedCustomRolePermissions(chatId)).willReturn(ImmutableMap.of());
        given(commandRegistry.getMainNameOfCommand(commandName)).willReturn(Optional.of(normalizedCommand));

        boolean result = commandAccessService.checkCommandAuthorization(chatId, commandName, userRolePriority, fromId);
        assertThat(result).isTrue();
        verify(commandRegistry, never()).getCommandAnnotation(anyString());
    }

    @Test
    void shouldDenyCommandWhenPersonalPermissionFalse() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of(
                normalizedCommand, ImmutableMap.of(fromId, false)
        ));
        given(rolePermissionService.getCachedCustomRolePermissions(chatId)).willReturn(ImmutableMap.of());
        given(commandRegistry.getMainNameOfCommand(commandName)).willReturn(Optional.of(normalizedCommand));

        boolean result = commandAccessService.checkCommandAuthorization(chatId, commandName, userRolePriority, fromId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldAllowWhenCustomRolePriorityLessOrEqual() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of());
        given(rolePermissionService.getCachedCustomRolePermissions(chatId))
                .willReturn(ImmutableMap.of(normalizedCommand, 20));
        given(commandRegistry.getMainNameOfCommand(commandName)).willReturn(Optional.of(normalizedCommand));

        boolean result = commandAccessService.checkCommandAuthorization(chatId, commandName, userRolePriority, fromId);
        assertThat(result).isTrue();
    }

    @Test
    void shouldDenyWhenCustomRolePriorityGreater() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of());
        given(rolePermissionService.getCachedCustomRolePermissions(chatId))
                .willReturn(ImmutableMap.of(normalizedCommand, 40));
        given(commandRegistry.getMainNameOfCommand(commandName)).willReturn(Optional.of(normalizedCommand));

        boolean result = commandAccessService.checkCommandAuthorization(chatId, commandName, userRolePriority, fromId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldUseDefaultRoleWhenNoCustomPermissions() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of());
        given(rolePermissionService.getCachedCustomRolePermissions(chatId)).willReturn(ImmutableMap.of());
        given(commandRegistry.getMainNameOfCommand(commandName)).willReturn(Optional.of(normalizedCommand));
        Command cmd = mock(Command.class);
        given(cmd.defaultRole()).willReturn(DefaultRole.ADMINISTRATOR);
        given(commandRegistry.getCommandAnnotation(normalizedCommand)).willReturn(Optional.of(cmd));

        boolean result = commandAccessService.checkCommandAuthorization(chatId, commandName, 100, fromId);
        assertThat(result).isTrue();
    }

    @Test
    void shouldDenyWhenDefaultRoleHigher() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of());
        given(rolePermissionService.getCachedCustomRolePermissions(chatId)).willReturn(ImmutableMap.of());
        given(commandRegistry.getMainNameOfCommand(commandName)).willReturn(Optional.of(normalizedCommand));
        Command cmd = mock(Command.class);
        given(cmd.defaultRole()).willReturn(DefaultRole.SENIOR_ADMINISTRATOR);
        given(commandRegistry.getCommandAnnotation(normalizedCommand)).willReturn(Optional.of(cmd));

        boolean result = commandAccessService.checkCommandAuthorization(chatId, commandName, userRolePriority, fromId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenAnnotationMissing() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of());
        given(rolePermissionService.getCachedCustomRolePermissions(chatId)).willReturn(ImmutableMap.of());
        given(commandRegistry.getMainNameOfCommand(commandName)).willReturn(Optional.of(normalizedCommand));
        given(commandRegistry.getCommandAnnotation(normalizedCommand)).willReturn(Optional.empty());

        boolean result = commandAccessService.checkCommandAuthorization(chatId, commandName, userRolePriority, fromId);
        assertThat(result).isFalse();
    }

    @Test
    void shouldAuthorizeMultipleCommands() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of(
                "cmd1", ImmutableMap.of(fromId, true),
                "cmd2", ImmutableMap.of(fromId, false)
        ));
        given(rolePermissionService.getCachedCustomRolePermissions(chatId)).willReturn(ImmutableMap.of());
        given(commandRegistry.getMainNamesOfRequiredCommands(Set.of("cmd1", "cmd2")))
                .willReturn(new UserCommandValidationResult(Set.of(), Set.of("cmd1", "cmd2")));

        CommandAuthorizationResult result = commandAccessService.checkCommandsAuthorization(
                chatId, Set.of("cmd1", "cmd2"), userRolePriority, fromId
        );
        assertThat(result.getAllowed()).containsExactly("cmd1");
        assertThat(result.getForbidden()).containsExactly("cmd2");
    }

    @Test
    void shouldHandleCustomRolePermissionsForMultipleCommands() {
        given(memberPermissionService.getCachedCustomMemberPermissions(chatId)).willReturn(ImmutableMap.of());
        given(rolePermissionService.getCachedCustomRolePermissions(chatId))
                .willReturn(ImmutableMap.of("cmd1", 20, "cmd2", 40));
        given(commandRegistry.getMainNamesOfRequiredCommands(Set.of("cmd1", "cmd2")))
                .willReturn(new UserCommandValidationResult(Set.of(), Set.of("cmd1", "cmd2")));

        CommandAuthorizationResult result = commandAccessService.checkCommandsAuthorization(
                chatId, Set.of("cmd1", "cmd2"), 30, fromId
        );
        assertThat(result.getAllowed()).containsExactly("cmd1");
        assertThat(result.getForbidden()).containsExactly("cmd2");
    }

    @Test
    void shouldThrowWhenCommandNotFoundInBulk() {
        given(commandRegistry.getMainNamesOfRequiredCommands(anySet()))
                .willThrow(new UserCommandNotFoundException(Set.of("unknown")));

        assertThatThrownBy(() -> commandAccessService.checkCommandsAuthorization(
                chatId, Set.of("unknown"), userRolePriority, fromId
        )).isInstanceOf(UserCommandNotFoundException.class);
    }

    @Test
    void shouldAllowWhenNoCooldown() {
        ChatCommand chatCommand = mock(ChatCommand.class);
        Command cmd = mock(Command.class);
        given(cmd.mainCommandName()).willReturn(commandName);
        CommandCooldown defaultCooldown = new CommandCooldown(60, 5);
        given(chatCommand.getCooldown()).willReturn(defaultCooldown);
        given(commandRegistry.getCommandWithTheAnnotation(commandName))
                .willReturn(Optional.of(Map.entry(chatCommand, cmd)));

        given(roleRateLimitService.getCachedCustomRoleLimits(chatId)).willReturn(null);

        CooldownResult result = commandAccessService.checkCommandRateLimit(chatId, commandName, userRolePriority, fromId);
        assertThat(result.canExecuteCommand()).isTrue();
        assertThat(result.getLeftCDSeconds()).isZero();
    }

    @Test
    void shouldApplyCustomRoleLimitWhenPresent() throws InterruptedException {
        ChatCommand chatCommand = mock(ChatCommand.class);
        Command cmd = mock(Command.class);
        given(cmd.mainCommandName()).willReturn(commandName);
        CommandCooldown defaultCooldown = new CommandCooldown(60, 10);
        given(chatCommand.getCooldown()).willReturn(defaultCooldown);
        given(commandRegistry.getCommandWithTheAnnotation(commandName))
                .willReturn(Optional.of(Map.entry(chatCommand, cmd)));

        RoleRateLimitDto roleLimit = new RoleRateLimitDto(1L, commandName, userRolePriority, true, 2, 5);
        ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> allLimits =
                ImmutableMap.of(commandName, ImmutableMap.of(userRolePriority, roleLimit));
        given(roleRateLimitService.getCachedCustomRoleLimits(chatId)).willReturn(allLimits);

        for (int i = 0; i < 2; i++) {
            CooldownResult result = commandAccessService.checkCommandRateLimit(chatId, commandName, userRolePriority, fromId);
            assertThat(result.canExecuteCommand()).isTrue();
        }

        CooldownResult result = commandAccessService.checkCommandRateLimit(chatId, commandName, userRolePriority, fromId);
        assertThat(result.canExecuteCommand()).isFalse();
        assertThat(result.getLeftCDSeconds()).isGreaterThan(0);

        Thread.sleep(6000);
        result = commandAccessService.checkCommandRateLimit(chatId, commandName, userRolePriority, fromId);
        assertThat(result.canExecuteCommand()).isTrue();
    }

    @Test
    void shouldThrowWhenCommandCooldownMissing() {
        ChatCommand chatCommand = mock(ChatCommand.class);
        given(chatCommand.getCooldown()).willReturn(null);
        given(commandRegistry.getCommandWithTheAnnotation(commandName))
                .willReturn(Optional.of(Map.entry(chatCommand, mock(Command.class))));

        assertThatThrownBy(() -> commandAccessService.checkCommandRateLimit(chatId, commandName, userRolePriority, fromId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenCommandNotFoundForRateLimit() {
        given(commandRegistry.getCommandWithTheAnnotation(commandName)).willReturn(Optional.empty());
        assertThatThrownBy(() -> commandAccessService.checkCommandRateLimit(chatId, commandName, userRolePriority, fromId))
                .isInstanceOf(UserCommandNotFoundException.class);
    }
}