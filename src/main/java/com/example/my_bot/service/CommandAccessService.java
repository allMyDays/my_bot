package com.example.my_bot.service;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.UserCommandValidationResult;
import com.example.my_bot.dto.command.CommandAuthorizationResult;
import com.example.my_bot.dto.cooldown.CooldownResult;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.service.permission.MemberPermissionService;
import com.example.my_bot.service.permission.RolePermissionService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.google.common.collect.ImmutableMap;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.example.my_bot.enumeration.key.CooldownCacheKeyBuilder.CUSTOM_ROLE_COOLDOWN;
import static com.example.my_bot.enumeration.key.CooldownCacheKeyBuilder.DEFAULT_COOLDOWN;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandAccessService {

    private CommandRegistry commandRegistry;

    private RolePermissionService rolePermissionService;

    private MemberPermissionService memberPermissionService;

    private RoleRateLimitService roleRateLimitService;

    private final static int MILLISECONDS_BETWEEN_SENDING_COOLDOWN_MESSAGE_TO_USER = 30_000;

    private final Cache<String, CooldownData> cooldownCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfter(new Expiry<String, CooldownData>() {
                @Override
                public long expireAfterCreate(String key, CooldownData data, long currentTime) {
                    return TimeUnit.SECONDS.toNanos(data.cdSeconds);
                }

                @Override
                public long expireAfterUpdate(String key, CooldownData data, long currentTime, long currentDuration) {
                    // при добавлении вызова сбрасываю ttl
                    return TimeUnit.SECONDS.toNanos(data.cdSeconds);
                }

                @Override
                public long expireAfterRead(String key, CooldownData data, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();


    @Autowired
    @Lazy
    public void setRolePermissionService(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }
    @Autowired
    @Lazy
    public void setMemberPermissionService(MemberPermissionService memberPermissionService) {
        this.memberPermissionService = memberPermissionService;
    }
    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }
    @Autowired
    @Lazy
    public void setRoleRateLimitService(RoleRateLimitService roleRateLimitService) {
        this.roleRateLimitService = roleRateLimitService;
    }

    public CommandAuthorizationResult checkCommandsAuthorization(
            long chatId, @NonNull Set<String> userCommands, int userRolePriority, long fromId, boolean normalizeCommandsAndThrowIfNotExist){

        Set<String> normalizedCommands=(normalizeCommandsAndThrowIfNotExist? normalizeCommands(userCommands):userCommands);

        ImmutableMap<String, Integer> customRolePermissions = rolePermissionService.getCachedCustomRolePermissions(chatId);
        ImmutableMap<String, ImmutableMap<Long, Boolean>> customMemberPermissions = memberPermissionService.getCachedCustomMemberPermissions(chatId);

        Set<String> allowed = new HashSet<>();
        Set<String> forbidden = new HashSet<>();

        for(String normalizedCommand: normalizedCommands){

            ImmutableMap<Long, Boolean> currentCmdPersonalPermissions = customMemberPermissions.get(normalizedCommand);
            if(currentCmdPersonalPermissions!=null){
                Boolean currentUserPersonalPermission = currentCmdPersonalPermissions.get(fromId);
                if(currentUserPersonalPermission!=null){
                    if(currentUserPersonalPermission){
                        allowed.add(normalizedCommand);
                    }else{
                        forbidden.add(normalizedCommand);
                    }continue;
                }
            }
            Integer roleToExecute = customRolePermissions.get(normalizedCommand);
            if(roleToExecute!=null){
                if(roleToExecute>userRolePriority){
                    forbidden.add(normalizedCommand);
                }else{
                    allowed.add(normalizedCommand);
                }
            }else{
                Optional<Command> annotationOptional = commandRegistry.getCommandAnnotation(normalizedCommand);
                if(annotationOptional.isEmpty()){
                    log.error("chat {} error: could not find required @Command annotation for normalized string command: {}",chatId, normalizedCommand);
                    forbidden.add(normalizedCommand);
                    continue;
                }Command annotation = annotationOptional.get();
                if(annotation.defaultRole().getRolePriority()>userRolePriority){
                    forbidden.add(normalizedCommand);
                }else{
                    allowed.add(normalizedCommand);
                }
            }
        }

        return new CommandAuthorizationResult(allowed, forbidden);

    }
    public boolean checkCommandAuthorization(
            long chatId, @NonNull String userCommand, int userRolePriority, long fromId, boolean normalizeCommandAndThrowIfNotExist){

        String normalizedCommand = (normalizeCommandAndThrowIfNotExist?normalizeCommand(userCommand):userCommand);

        ImmutableMap<String, Integer> customRolePermissions = rolePermissionService.getCachedCustomRolePermissions(chatId);
        ImmutableMap<String, ImmutableMap<Long, Boolean>> customMemberPermissions = memberPermissionService.getCachedCustomMemberPermissions(chatId);

        ImmutableMap<Long, Boolean> currentCmdPersonalPermissions = customMemberPermissions.get(normalizedCommand);
        if(currentCmdPersonalPermissions!=null){
            Boolean currentUserPersonalPermission = currentCmdPersonalPermissions.get(fromId);
            if(currentUserPersonalPermission!=null){
                return currentUserPersonalPermission;
            }
        }

        Integer roleToExecute = customRolePermissions.get(normalizedCommand);
        if(roleToExecute!=null){
            return roleToExecute <= userRolePriority;
        }else{
            Optional<Command> annotationOptional = commandRegistry.getCommandAnnotation(normalizedCommand);
            if(annotationOptional.isEmpty()){
                log.error("chat {} error: could not find required @Command annotation for normalized string command: {}",chatId, normalizedCommand);
                return false;
            }
            return annotationOptional.get().defaultRole().getRolePriority() <= userRolePriority;
        }

    }

    public CooldownResult checkCommandRateLimit(long chatId, String userCommand, int userRolePriority, long fromId) {

        ChatCommand chatCommand = commandRegistry.getCommand(userCommand)
                .orElseThrow(() -> new UserCommandNotFoundException(userCommand));

        String normalizedCommand = commandRegistry.getCommandAnnotation(userCommand)
                .orElseThrow(() -> new UserCommandNotFoundException(userCommand))
                .mainCommandName();

        CommandCooldown defaultCooldown = chatCommand.getCooldown();
        if (defaultCooldown == null) {
            throw new IllegalStateException("CommandCooldown not found for " + normalizedCommand);
        }

        long now = System.currentTimeMillis();

        String defaultKey = DEFAULT_COOLDOWN.buildDefaultCDKey(chatId, fromId, normalizedCommand);
        CooldownInfo defaultInfo = probeCooldown(defaultKey, defaultCooldown.getSeconds(), defaultCooldown.getMaxUses(), now);

        Optional<RoleRateLimitDto> roleLimit = getRoleLimit(chatId, normalizedCommand, userRolePriority);
        CooldownInfo customInfo = null;
        String customKey = null;

        if (roleLimit.isPresent()) {
            customKey = roleLimit.get().isPersonal()
                    ? CUSTOM_ROLE_COOLDOWN.buildRolePersonalKey(chatId, fromId, normalizedCommand, roleLimit.get().getEntityId())
                    : CUSTOM_ROLE_COOLDOWN.buildRoleKey(chatId, normalizedCommand, userRolePriority, roleLimit.get().getEntityId());

            customInfo = probeCooldown(customKey, roleLimit.get().getPeriodInSeconds(), roleLimit.get().getMaxUsage(), now);
        }

        boolean defaultFull = defaultInfo.full;
        boolean customFull = customInfo != null && customInfo.full;

        if (!defaultFull && !customFull) {
            addCall(defaultKey, defaultCooldown.getSeconds(), now);

            if (customInfo != null) {
                addCall(customKey, roleLimit.get().getPeriodInSeconds(), now);
            }

            CooldownResult result = new CooldownResult();
            result.setCanExecuteCommand(true);
            return result;
        }

        CooldownResult result = new CooldownResult();
        result.setCanExecuteCommand(false);

        if (defaultFull && !customFull) {
            result.setLeftCDSeconds(defaultInfo.leftSeconds);
            result.setCanSendCDMessageToUser(defaultInfo.canSendMessage);
            return result;
        }

        if (!defaultFull && customFull) {
            result.setLeftCDSeconds(customInfo.leftSeconds);
            result.setCanSendCDMessageToUser(customInfo.canSendMessage);
            return result;
        }

        result.setLeftCDSeconds(Math.max(defaultInfo.leftSeconds, customInfo.leftSeconds));
        result.setCanSendCDMessageToUser(defaultInfo.canSendMessage || customInfo.canSendMessage);
        return result;
    }

    private CooldownInfo probeCooldown(String key, long cdSeconds, int maxUses, long now) {
        long cdMillis = cdSeconds * 1000L;

        return cooldownCache.asMap().compute(key, (k, existing) -> {
            CooldownData data = (existing != null) ? existing : new CooldownData(cdSeconds);
            Deque<Long> calls = data.calls;

            while (!calls.isEmpty() && calls.peekFirst() < now - cdMillis) {
                calls.pollFirst();
            }

            boolean full = calls.size() >= maxUses;
            long leftSeconds = 0;
            boolean canSendMessage = false;

            if (full) {
                Long oldest = calls.peekFirst();
                if (oldest != null) {
                    leftSeconds = Math.max(0, (oldest + cdMillis - now) / 1000);
                }

                long lastMsg = data.lastSentCDMessageInMillis;
                if (lastMsg == 0 || now - lastMsg > MILLISECONDS_BETWEEN_SENDING_COOLDOWN_MESSAGE_TO_USER) {
                    data.lastSentCDMessageInMillis = now;
                    canSendMessage = true;
                }
            }

            data.probeFull = full;
            data.probeLeftSeconds = leftSeconds;
            data.probeCanSendMessage = canSendMessage;
            return data;
        }).toInfo();
    }

    private void addCall(String key, long cdSeconds, long now) {
        long cdMillis = cdSeconds * 1000L;

        cooldownCache.asMap().compute(key, (k, existing) -> {
                    CooldownData data = (existing != null) ? existing : new CooldownData(cdSeconds);
                    Deque<Long> calls = data.calls;

                    while (!calls.isEmpty() && calls.peekFirst() < now - cdMillis) {
                        calls.pollFirst();
                    }

                    calls.addLast(now);
                    return data;
                });
    }

    private Optional<RoleRateLimitDto> getRoleLimit(long chatId, String command, int rolePriority) {
        ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> allLimits = roleRateLimitService.getCachedCustomRoleLimits(chatId);
        if (allLimits == null) {
            return Optional.empty();
        }

        ImmutableMap<Integer, RoleRateLimitDto> commandLimits = allLimits.get(command);
        if (commandLimits == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(commandLimits.get(rolePriority));
    }

    private record CooldownInfo(boolean full, long leftSeconds, boolean canSendMessage) {}


    private static class CooldownData {
        final long cdSeconds;
        final Deque<Long> calls = new ArrayDeque<>();
        long lastSentCDMessageInMillis;

        boolean probeFull;
        long probeLeftSeconds;
        boolean probeCanSendMessage;

        CooldownData(long cdSeconds) {
            this.cdSeconds = cdSeconds;
        }

        CooldownInfo toInfo() {
            return new CooldownInfo(probeFull, probeLeftSeconds, probeCanSendMessage);
        }
    }

    private Set<String> normalizeCommands(Set<String> userCommands){
        UserCommandValidationResult validationResult = commandRegistry.getMainNamesOfRequiredCommands(userCommands);
        if(!validationResult.getNotFoundCommands().isEmpty()){
            throw new UserCommandNotFoundException(validationResult.getNotFoundCommands());
        }
        return validationResult.getNormalizedCommands();
    }
    private String normalizeCommand(String userCommand){
        Optional<String> validationResult = commandRegistry.getMainNameOfCommand(userCommand);
        if(validationResult.isEmpty()){
            throw new UserCommandNotFoundException(userCommand);
        }
        return validationResult.get();
    }

}
