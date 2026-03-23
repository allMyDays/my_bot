package com.example.my_bot.service;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.command.UserCommandValidationResult;
import com.example.my_bot.dto.command.CommandAuthorizationResult;
import com.example.my_bot.dto.permission.MemberPermissionSettingResult;
import com.example.my_bot.entity.MemberPermissionEntity;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.repository.MemberPermissionRepository;
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
public class MemberPermissionService {

    private CommandRegistry commandRegistry;

    private final MemberService memberService;

    private final MemberPermissionRepository memberPermissionRepository;

    private final CaffeineCacheManager cacheManager;

    private final static int MAX_CUSTOM_MEMBER_PERMISSIONS_COUNT = 20;

    private final CommandAccessService commandService;


    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }


    @Transactional
    public MemberPermissionSettingResult allowOrForbidCommandForMember(long chatId, long fromId, @NonNull Set<String> userCommands, long targetUserId, boolean allow){

        if(fromId==targetUserId){
            throw new CannotApplyThisCommandToYourselfException();
        }

        int callerRole = memberService.getCachedMemberRolePriority(chatId,fromId);

        if(memberService.getCachedMemberRolePriority(chatId, targetUserId)>callerRole){
            throw new MemberAccessDeniedException(targetUserId, fromId);
        }
        MemberPermissionSettingResult result = new MemberPermissionSettingResult();
        UserCommandValidationResult commandNormalizationResult = commandRegistry.getMainNamesOfRequiredCommands(userCommands);
        result.setNotFound(commandNormalizationResult.getNotFoundCommands());
        if(userCommands.size()==result.getNotFound().size()){
            return result;
        }

        CommandAuthorizationResult commandAuthorizationResult =
                commandService.checkCommandsAuthorization(chatId, commandNormalizationResult.getNormalizedCommands(), callerRole, fromId, false);
        result.setForbiddenToEdit(commandAuthorizationResult.getForbidden());

        Map<String, ImmutableMap<Long, Boolean>> existingMemberPermissions = getCachedCustomMemberPermissions(chatId);

        int totalMemberPermissionSize = existingMemberPermissions.values().stream()
                .mapToInt(ImmutableMap::size)
                .sum();

        int newPermissionsAvailableSize = MAX_CUSTOM_MEMBER_PERMISSIONS_COUNT - totalMemberPermissionSize;

        Set<String> commandsToUpdate = new HashSet<>();
        Set<MemberPermissionEntity> commandsToSave = new HashSet<>();

        for(String currentCommand: commandAuthorizationResult.getAllowed()){
            ImmutableMap<Long, Boolean> currentCommandPermissions = existingMemberPermissions.get(currentCommand);
            Boolean requiredPermission = (currentCommandPermissions==null?null:currentCommandPermissions.get(targetUserId));
            if(requiredPermission==null){
                    if(newPermissionsAvailableSize<=0){
                    result.getNotEnoughSpaceToAddNew().add(currentCommand);
                    continue;
                }
                commandsToSave.add(new MemberPermissionEntity(chatId,currentCommand, targetUserId,allow));
                newPermissionsAvailableSize--;
            }else if(!requiredPermission.equals(allow)){
                    commandsToUpdate.add(currentCommand);
            }else{
                result.getHasRequiredPermissionAlready().add(currentCommand);
                continue;
            }result.getAccepted().add(currentCommand);
        }
        if(!commandsToSave.isEmpty()){
            memberPermissionRepository.saveAll(commandsToSave);}
        if(!commandsToUpdate.isEmpty()){
            memberPermissionRepository.updateUserPermissionsForRequiredCommands(chatId, commandsToUpdate, targetUserId, allow);
        }
        invalidateMemberPermissionCache(chatId);  // обновляю кеш разрешений

        return result;

    }

    @Transactional
    public void deleteCustomMemberPermission(long chatId, @NonNull String userCommand, long targetUserId, long fromId){

        if(fromId==targetUserId){
            throw new CannotApplyThisCommandToYourselfException();
        }

        String mainCommandName = commandRegistry.getMainNameOfCommand(userCommand)
                .orElseThrow(()->new UserCommandNotFoundException(userCommand));
        int callerRole = memberService.getCachedMemberRolePriority(chatId, fromId);

        if(!commandService.checkCommandAuthorization(chatId, mainCommandName, callerRole, fromId, false)){
            throw new CommandAccessDeniedException(fromId, mainCommandName);
        }if(memberService.getCachedMemberRolePriority(chatId, targetUserId)>callerRole){
            throw new MemberAccessDeniedException(targetUserId, fromId);
        }
        memberPermissionRepository.deleteMemberPermissionForOneCommand(chatId, mainCommandName, targetUserId);

       invalidateMemberPermissionCache(chatId);

    }


    public Map<String, ImmutableMap<Long, Boolean>> getCachedCustomMemberPermissions(long chatId) {
        ConcurrentMap<String, ImmutableMap<Long, Boolean>> map = cacheManager.getMemberPermissionCache().get(chatId, id -> {

            List<MemberPermissionEntity> entities = memberPermissionRepository.findByChatId(id);

            Map<String, ImmutableMap.Builder<Long, Boolean>> builders = new HashMap<>(); // временная карта

            for (MemberPermissionEntity entity : entities) {
                String command = entity.getCommandName();
                Long userId = entity.getUserId();
                Boolean allowed = entity.isAllowed();

                ImmutableMap.Builder<Long, Boolean> builder = builders.computeIfAbsent(command, k -> ImmutableMap.builder());
                builder.put(userId, allowed);
            }

            // итоговая карта
            ConcurrentMap<String, ImmutableMap<Long, Boolean>> result = new ConcurrentHashMap<>();
            builders.forEach((key, value) -> result.put(key, value.build()));
            return result;
        });

        return Collections.unmodifiableMap(map);
    }

    private void invalidateMemberPermissionCache(long chatId){
        cacheManager.getMemberPermissionCache().invalidate(chatId);
    }
    public static int getMaxCustomMemberPermissionsCount() {
        return MAX_CUSTOM_MEMBER_PERMISSIONS_COUNT;
    }

}
