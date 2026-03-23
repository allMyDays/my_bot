package com.example.my_bot.service;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.entity.RoleRateLimitEntity;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.limit.RateLimitPeriodOutOfBoundsException;
import com.example.my_bot.exception.limit.RateLimitUsageOutOfBoundsException;
import com.example.my_bot.exception.limit.RateLimitWithThatCommandAndRoleAlreadyExistsException;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.RateLimitMapper;
import com.example.my_bot.repository.RoleRateLimitRepository;
import com.google.common.collect.ImmutableMap;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleRateLimitService {

    private CommandRegistry commandRegistry;

    private final MemberService memberService;

    private final RoleService roleService;

    private final CaffeineCacheManager cacheManager;

    private final CommandAccessService commandService;

    private final RateLimitMapper rateLimitMapper;

    private final RoleRateLimitRepository roleRateLimitRepository;

    private final static int MAX_CUSTOM_LIMITS = 20;

    private final static int MIN_LIMIT_PERIOD_IN_SECONDS = 60;

    private final static int MAX_LIMIT_PERIOD_IN_SECONDS = 86_400;

    private final static int MIN_LIMIT_USAGE = 1;

    private final static int MAX_LIMIT_USAGE = 100;


    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }


    @Transactional
    public RoleRateLimitEntity createCommandLimit(long chatId, long fromId, @NonNull String userCommand, int rolePriority, int maxUsage, int periodInSeconds, boolean isPersonal){

        if(periodInSeconds< MIN_LIMIT_PERIOD_IN_SECONDS ||periodInSeconds> MAX_LIMIT_PERIOD_IN_SECONDS){
            throw new RateLimitPeriodOutOfBoundsException(MIN_LIMIT_PERIOD_IN_SECONDS, MAX_LIMIT_PERIOD_IN_SECONDS);
        }if(maxUsage<MIN_LIMIT_USAGE||maxUsage>MAX_LIMIT_USAGE) {
            throw new RateLimitUsageOutOfBoundsException(MIN_LIMIT_USAGE, MAX_LIMIT_USAGE);
        }
        RoleDto foundRole = roleService.getRoleByPriority(chatId, rolePriority)
                .orElseThrow(RoleNotFoundException::new);

        int userRolePriority = memberService.getCachedMemberRolePriority(chatId, fromId);
        if(rolePriority>userRolePriority){
            throw new RoleAccessDeniedException();
        }Optional<String> mainCommandName =  commandRegistry.getMainNameOfCommand(userCommand.trim());
        if(mainCommandName.isEmpty()){
            throw new UserCommandNotFoundException(userCommand);
        }boolean abilityCommandInteraction = commandService.checkCommandAuthorization(
                chatId,mainCommandName.get(), userRolePriority,fromId, false);
        if(!abilityCommandInteraction){
            throw new CommandAccessDeniedException(fromId, mainCommandName.get());
        }
        ImmutableMap<Integer, RoleRateLimitDto> currentCommandRoleLimits = getCachedCustomRoleLimits(chatId).get(mainCommandName.get());

        if(currentCommandRoleLimits!=null&&currentCommandRoleLimits.get(rolePriority)!=null){
           throw new RateLimitWithThatCommandAndRoleAlreadyExistsException(mainCommandName.get(), foundRole.getRoleName());
        }
        RoleRateLimitEntity savedLimit = roleRateLimitRepository.save(new RoleRateLimitEntity(
                chatId, mainCommandName.get(), rolePriority, isPersonal, maxUsage, periodInSeconds
                ));

        invalidateCommandLimitCache(chatId);
        return savedLimit;
    }
    @Transactional
    public RoleRateLimitEntity createCommandLimit(long chatId, long fromId, @NonNull String userCommand, @NonNull String roleName, int maxUsage, int periodInSeconds, boolean isPersonal){
        int rolePriority = roleService.getRoleByNameIgnoreCase(chatId, roleName.trim())
                .orElseThrow(RoleNotFoundException::new).getRolePriority();

        return createCommandLimit(chatId, fromId, userCommand, rolePriority,maxUsage, periodInSeconds,isPersonal);
    }

    @Transactional
    public void deleteLimit(RoleRateLimitDto limitDto, long chatId, long fromId){

        int userRolePriority = memberService.getCachedMemberRolePriority(chatId, fromId);

        if(limitDto.getRolePriority()>userRolePriority){
            throw new RoleAccessDeniedException();
        }
        boolean abilityCommandInteraction = commandService.checkCommandAuthorization(
                chatId, limitDto.getCommandName(),userRolePriority,fromId, false);
        if(!abilityCommandInteraction){
            throw new CommandAccessDeniedException(fromId, limitDto.getCommandName());
        }
        roleRateLimitRepository.deleteById(limitDto.getEntityId());
        invalidateCommandLimitCache(chatId);
    }

    public List<RoleRateLimitDto> getRoleLimitsSortedByEntityId(long chatId){

        Map<String, ImmutableMap<Integer, RoleRateLimitDto>> roleLimits =  getCachedCustomRoleLimits(chatId);

        return roleLimits.values().stream()
                .flatMap(map -> map.values().stream())
                .sorted(Comparator.comparing(RoleRateLimitDto::getEntityId))
                .collect(Collectors.toList());
    }

    public Map<String, ImmutableMap<Integer, RoleRateLimitDto>> getCachedCustomRoleLimits(long chatId) {
        ConcurrentMap<String, ImmutableMap<Integer, RoleRateLimitDto>> map = cacheManager.getRoleLimitCache().get(chatId, id -> {

            List<RoleRateLimitEntity> entities = roleRateLimitRepository.findByChatId(id);

            Map<String, ImmutableMap.Builder<Integer, RoleRateLimitDto>> builders = new HashMap<>(); // временная карта

            for (RoleRateLimitEntity entity : entities) {
                ImmutableMap.Builder<Integer, RoleRateLimitDto> builder = builders.computeIfAbsent(entity.getCommandName(), k -> ImmutableMap.builder());
                builder.put(entity.getRolePriority(), rateLimitMapper.toRoleLimitDto(entity));
            }

            // итоговая карта
            ConcurrentMap<String, ImmutableMap<Integer, RoleRateLimitDto>> result = new ConcurrentHashMap<>();
            builders.forEach((key, value) -> result.put(key, value.build()));
            return result;
        });

        return Collections.unmodifiableMap(map);
    }

    private void invalidateCommandLimitCache(long chatId){
        cacheManager.getRoleLimitCache().invalidate(chatId);
    }
    public static int getMaxCustomLimits() {
        return MAX_CUSTOM_LIMITS;
    }

}
