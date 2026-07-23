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
import com.example.my_bot.exception.limit.TooManyRateLimitsException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.RateLimitMapper;
import com.example.my_bot.repository.RoleRateLimitRepository;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.utils.TextUtils;
import com.google.common.collect.ImmutableMap;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
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

    @Getter
    private final static int MAX_LIMIT_TIME_PERIOD_SEC = 86_400;
    private final static int MIN_LIMIT_TIME_PERIOD_SEC = 60;
    private final static int MIN_LIMIT_USAGE = 1;
    private final static int MAX_LIMIT_USAGE = 100;
    private final static int MAX_CUSTOM_LIMITS = 20;


    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    @Transactional
    public RoleRateLimitEntity createCommandRateLimit(long chatId, long fromId, @NonNull String userCommand, int rolePriority, int maxUsage, long periodInSeconds, boolean isPersonal){

        int currentLimitSize = getCachedCustomRoleLimits(chatId).values().stream()
                .mapToInt(map -> map.values().size())
                .sum();
        if(currentLimitSize >= MAX_CUSTOM_LIMITS){
            throw new TooManyRateLimitsException();
        }
        if(periodInSeconds< MIN_LIMIT_TIME_PERIOD_SEC || periodInSeconds> MAX_LIMIT_TIME_PERIOD_SEC){
            throw new RateLimitPeriodOutOfBoundsException(MIN_LIMIT_TIME_PERIOD_SEC, MAX_LIMIT_TIME_PERIOD_SEC);
        }
        if(maxUsage<MIN_LIMIT_USAGE||maxUsage>MAX_LIMIT_USAGE){
            throw new RateLimitUsageOutOfBoundsException(MIN_LIMIT_USAGE, MAX_LIMIT_USAGE);
        }
        RoleDto foundRole = roleService.getRoleByPriority(chatId, rolePriority)
                .orElseThrow(RoleNotFoundException::new);

        int userRolePriority = memberService.getMemberRolePriority(chatId, fromId);
        roleService.checkRoleInteractionAbility(rolePriority, userRolePriority);

        String mainCommandName =  commandRegistry.getMainNameOfCommand(TextUtils.cutDefaultPrefix(userCommand))
                .orElseThrow(()->new UserCommandNotFoundException(userCommand));

        boolean abilityCommandInteraction = commandService.checkCommandAuthorization(
                chatId,mainCommandName, userRolePriority,fromId);
        if(!abilityCommandInteraction){
            throw new CommandAccessDeniedException(fromId, mainCommandName);
        }
        ImmutableMap<Integer, RoleRateLimitDto> currentCommandRoleLimits = getCachedCustomRoleLimits(chatId).get(mainCommandName);

        if(currentCommandRoleLimits!=null&&currentCommandRoleLimits.get(rolePriority)!=null){
           throw new RateLimitWithThatCommandAndRoleAlreadyExistsException(mainCommandName, foundRole.getRoleName());
        }
        RoleRateLimitEntity savedLimit = roleRateLimitRepository.save(
                new RoleRateLimitEntity(
                        chatId, mainCommandName, rolePriority, isPersonal, maxUsage, (int)periodInSeconds
                )
        );

        invalidateCommandLimitCache(chatId);
        return savedLimit;
    }

    @Transactional
    public RoleRateLimitEntity createCommandRateLimit(long chatId, long fromId, @NonNull String userCommand, @NonNull String roleName, int maxUsage, long periodInSeconds, boolean isPersonal){
        int rolePriority = roleService.getRoleByNameIgnoreCase(chatId, roleName.trim())
                .orElseThrow(RoleNotFoundException::new).getRolePriority();

        return createCommandRateLimit(chatId, fromId, userCommand, rolePriority,maxUsage, periodInSeconds,isPersonal);
    }

    @Transactional
    public void deleteLimit(RoleRateLimitDto limitDto, long chatId, long fromId){

        int userRolePriority = memberService.getMemberRolePriority(chatId, fromId);

        roleService.checkRoleInteractionAbility(limitDto.getRolePriority(), userRolePriority);

        boolean abilityCommandInteraction = commandService.checkCommandAuthorization(
                chatId, limitDto.getCommandName(),userRolePriority,fromId);
        if(!abilityCommandInteraction){
            throw new CommandAccessDeniedException(fromId, limitDto.getCommandName());
        }
        roleRateLimitRepository.deleteById(limitDto.getEntityId());
        invalidateCommandLimitCache(chatId);
    }

    public List<RoleRateLimitDto> getRoleLimitsSortedByEntityId(long chatId){

        ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> roleLimits =  getCachedCustomRoleLimits(chatId);

        return roleLimits.values().stream()
                .flatMap(map -> map.values().stream())
                .sorted(Comparator.comparing(RoleRateLimitDto::getEntityId))
                .collect(Collectors.toList());
    }

    public ImmutableMap<String, ImmutableMap<Integer, RoleRateLimitDto>> getCachedCustomRoleLimits(long chatId) {
        return cacheManager.getRoleLimitCache().get(chatId, id -> {

            List<RoleRateLimitEntity> entities = roleRateLimitRepository.findByChatId(id);

            Map<String, ImmutableMap.Builder<Integer, RoleRateLimitDto>> builders = new HashMap<>(); // временная карта

            for (RoleRateLimitEntity entity : entities) {
                ImmutableMap.Builder<Integer, RoleRateLimitDto> builder = builders.computeIfAbsent(entity.getCommandName(), k -> ImmutableMap.builder());
                builder.put(entity.getRolePriority(), rateLimitMapper.toRoleLimitDto(entity));
            }

            // итоговая карта
            ImmutableMap.Builder<String, ImmutableMap<Integer, RoleRateLimitDto>> result = new ImmutableMap.Builder<>();
            builders.forEach((key, value) -> result.put(key, value.build()));
            return result.build();
        });
    }

    private void invalidateCommandLimitCache(long chatId){
        cacheManager.getRoleLimitCache().invalidate(chatId);
    }

}
