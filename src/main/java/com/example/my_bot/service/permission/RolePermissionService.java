package com.example.my_bot.service.permission;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.UserCommandValidationResult;
import com.example.my_bot.dto.command.CommandAuthorizationResult;
import com.example.my_bot.dto.permission.RolePermissionSettingResult;
import com.example.my_bot.entity.RolePermissionEntity;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.repository.permission.RolePermissionRepository;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.utils.TextUtils;
import com.google.common.collect.ImmutableMap;
import jakarta.transaction.Transactional;
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
public class RolePermissionService {

    private CommandRegistry commandRegistry;

    private final MemberService memberService;

    private final RoleService roleService;

    private final RolePermissionRepository permissionRepository;

    private final CaffeineCacheManager cacheManager;

    private final CommandAccessService commandService;

    private final static int MAX_CUSTOM_ROLE_PERMISSIONS_COUNT = 40;


    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }


    @Transactional
    public RolePermissionSettingResult allowCommandForRole(long chatId, long fromId, @NonNull Set<String> userCommands, int rolePriority){

        RoleDto foundRole = roleService.getRoleByPriority(chatId, rolePriority)
                .orElseThrow(RoleNotFoundException::new);

        int userRolePriority = memberService.getMemberRolePriority(chatId, fromId);

        roleService.checkRoleInteractionAbility(rolePriority,userRolePriority);

        RolePermissionSettingResult result = new RolePermissionSettingResult();
        result.setRoleDto(foundRole);

        userCommands = userCommands.stream()
                .map(TextUtils::cutDefaultPrefix)
                .collect(Collectors.toSet());

        UserCommandValidationResult commandNormalizationResult = commandRegistry.getMainNamesOfRequiredCommands(userCommands);
        result.setNotFound(commandNormalizationResult.getNotFoundCommands());

        if(userCommands.size()==result.getNotFound().size()){
            return result;
        }

        CommandAuthorizationResult commandAuthorizationResult =
                commandService.checkCommandsAuthorization(chatId, commandNormalizationResult.getNormalizedCommands(), userRolePriority,fromId);
        result.setForbiddenToEdit(commandAuthorizationResult.getForbidden());
        

        ImmutableMap<String, Integer> existingCustomPermissions = getCachedCustomRolePermissions(chatId);

        int newPermissionsAvailableSize = MAX_CUSTOM_ROLE_PERMISSIONS_COUNT -existingCustomPermissions.size();

        Set<String> commandsToUpdate = new HashSet<>();
        Set<RolePermissionEntity> commandsToSave = new HashSet<>();

        for(String currentCommand: commandAuthorizationResult.getAllowed()){
            Integer existingPermissionRolePriority = existingCustomPermissions.get(currentCommand);
            if(existingPermissionRolePriority==null){
                if(newPermissionsAvailableSize<=0){
                    result.getNotEnoughSpaceToAddNew().add(currentCommand);
                    continue;
                }
                commandsToSave.add(new RolePermissionEntity(chatId, currentCommand,rolePriority));
                newPermissionsAvailableSize--;
            }
            else if(existingPermissionRolePriority!=rolePriority){
                    commandsToUpdate.add(currentCommand);
            }
            else{
                result.getHasRequiredPermissionAlready().add(currentCommand);
                continue;
            }
            result.getAccepted().add(currentCommand);
        }
        if(!commandsToSave.isEmpty()){
            permissionRepository.saveAll(commandsToSave);
        }
        if(!commandsToUpdate.isEmpty()){
            permissionRepository.updateRolePermissionForRequiredCommands(chatId, commandsToUpdate, rolePriority);
        }
        invalidateRolePermissionCache(chatId);  // обновляю кеш разрешений

        return result;
    }

    @Transactional
    public RolePermissionSettingResult allowCommandForRole(long chatId, long fromId, @NonNull Set<String> userCommands, @NonNull String roleName){
        int rolePriority = roleService.getRoleByNameIgnoreCase(chatId, roleName)
                .orElseThrow(RoleNotFoundException::new).getRolePriority();

        return allowCommandForRole(chatId, fromId, userCommands, rolePriority);
    }

    @Transactional
    public void deleteCustomRolePermission(long chatId, @NonNull String userCommand, long fromId){

        String mainCommandName = commandRegistry.getMainNameOfCommand(TextUtils.cutDefaultPrefix(userCommand))
                .orElseThrow(()->new UserCommandNotFoundException(userCommand));

        int userRolePriority = memberService.getMemberRolePriority(chatId, fromId);

        if(!commandService.checkCommandAuthorization(chatId, mainCommandName, userRolePriority, fromId)){
            throw new CommandAccessDeniedException(fromId, mainCommandName);
        }

        permissionRepository.deleteRolePermissionForOneCommand(chatId, mainCommandName);

        invalidateRolePermissionCache(chatId);
    }

    @SuppressWarnings("ConstantConditions")
    public ImmutableMap<String, Integer> getCachedCustomRolePermissions(long chatId) {
        return cacheManager.getRolePermissionCache().get(chatId, id ->
                permissionRepository.findByChatId(id).stream()
                        .collect(ImmutableMap.toImmutableMap(
                                RolePermissionEntity::getCommandName,
                                RolePermissionEntity::getRolePriority,
                                (existing, replacement) -> existing))
        );
    }

    private void invalidateRolePermissionCache(long chatId){
        cacheManager.getRolePermissionCache().invalidate(chatId);
    }

    public static int getMaxCustomRolePermissionsCount() {
        return MAX_CUSTOM_ROLE_PERMISSIONS_COUNT;
    }

}
