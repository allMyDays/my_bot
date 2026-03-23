package com.example.my_bot.service;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.limit.RoleLimitDto;
import com.example.my_bot.entity.RoleLimitEntity;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.limit.LimitPeriodOutOfBoundsException;
import com.example.my_bot.exception.limit.LimitUsageOutOfBoundsException;
import com.example.my_bot.exception.limit.LimitWithThatCommandAndRoleAlreadyExistsException;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.LimitMapper;
import com.example.my_bot.repository.RoleLimitRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleLimitService {

    private CommandRegistry commandRegistry;

    private final MemberService memberService;

    private final RoleService roleService;

    private final CaffeineCacheManager cacheManager;

    private final CommandAccessService commandService;

    private final LimitMapper limitMapper;

    private final RoleLimitRepository roleLimitRepository;

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
    public RoleLimitEntity createCommandLimit(long chatId, long fromId, @NonNull String userCommand, int rolePriority, int maxUsage, int periodInSeconds, boolean isPersonal){

        if(periodInSeconds< MIN_LIMIT_PERIOD_IN_SECONDS ||periodInSeconds> MAX_LIMIT_PERIOD_IN_SECONDS){
            throw new LimitPeriodOutOfBoundsException(MIN_LIMIT_PERIOD_IN_SECONDS, MAX_LIMIT_PERIOD_IN_SECONDS);
        }if(maxUsage<MIN_LIMIT_USAGE||maxUsage>MAX_LIMIT_USAGE) {
            throw new LimitUsageOutOfBoundsException(MIN_LIMIT_USAGE, MAX_LIMIT_USAGE);
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
        ImmutableMap<Integer, RoleLimitDto> currentCommandRoleLimits = getCachedCustomRoleLimits(chatId).get(mainCommandName.get());

        if(currentCommandRoleLimits!=null&&currentCommandRoleLimits.get(rolePriority)!=null){
           throw new LimitWithThatCommandAndRoleAlreadyExistsException(mainCommandName.get(), foundRole.getRoleName());
        }
        RoleLimitEntity savedLimit = roleLimitRepository.save(new RoleLimitEntity(
                chatId, mainCommandName.get(), rolePriority, isPersonal, maxUsage, periodInSeconds
                ));

        invalidateCommandLimitCache(chatId);
        return savedLimit;
    }
    @Transactional
    public RoleLimitEntity createCommandLimit(long chatId, long fromId, @NonNull String userCommand, @NonNull String roleName, int maxUsage, int periodInSeconds, boolean isPersonal){
        int rolePriority = roleService.getRoleByNameIgnoreCase(chatId, roleName.trim())
                .orElseThrow(RoleNotFoundException::new).getRolePriority();

        return createCommandLimit(chatId, fromId, userCommand, rolePriority,maxUsage, periodInSeconds,isPersonal);
    }

    @Transactional
    public void deleteCommandLimit(long chatId, @NonNull String userCommand, long fromId){







    }



    public Map<String, ImmutableMap<Integer, RoleLimitDto>> getCachedCustomRoleLimits(long chatId) {
        ConcurrentMap<String, ImmutableMap<Integer, RoleLimitDto>> map = cacheManager.getRoleLimitCache().get(chatId, id -> {

            List<RoleLimitEntity> entities = roleLimitRepository.findByChatId(id);

            Map<String, ImmutableMap.Builder<Integer, RoleLimitDto>> builders = new HashMap<>(); // временная карта

            for (RoleLimitEntity entity : entities) {
                ImmutableMap.Builder<Integer, RoleLimitDto> builder = builders.computeIfAbsent(entity.getCommandName(), k -> ImmutableMap.builder());
                builder.put(entity.getRolePriority(), limitMapper.toRoleLimitDto(entity));
            }

            // итоговая карта
            ConcurrentMap<String, ImmutableMap<Integer, RoleLimitDto>> result = new ConcurrentHashMap<>();
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
