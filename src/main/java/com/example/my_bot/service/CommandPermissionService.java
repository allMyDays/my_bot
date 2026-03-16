package com.example.my_bot.service;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.UserCommandValidationResult;
import com.example.my_bot.dto.permission.AbilityEditRolePermissionsResult;
import com.example.my_bot.dto.permission.RolePermissionDto;
import com.example.my_bot.dto.permission.SetCommandPermissionResult;
import com.example.my_bot.entity.CommandPermissionEntity;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.permission.RolePermissionAccessDeniedException;
import com.example.my_bot.exception.permission.PermissionCreationLimitReachedException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.mapper.CommandPermissionMapper;
import com.example.my_bot.repository.CommandPermissionRepository;
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

import static com.example.my_bot.enumeration.CacheKeyBuilder.ROLE_CMD_PERMISSION;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommandPermissionService {

    private CommandRegistry commandRegistry;

    private final MemberService memberService;

    private final RoleService roleService;

    private final CommandPermissionRepository permissionRepository;

    private final CaffeineCacheManager cacheManager;

    private final int MAX_CUSTOM_PERMISSIONS_COUNT = 20;


    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }


    @Transactional
    public SetCommandPermissionResult allowCommandForRole(long chatId, long fromId, @NonNull Set<String> userCommands, int rolePriority){

        RoleDto foundRole = roleService.getRoleByPriority(chatId, rolePriority)
                .orElseThrow(RoleNotFoundException::new);

        int userRolePriority = memberService.getCachedMemberRolePriority(chatId, fromId);

        if(rolePriority>userRolePriority){
            throw new RoleAccessDeniedException();
        }
        SetCommandPermissionResult result = new SetCommandPermissionResult();
        result.setRoleDto(foundRole);

        UserCommandValidationResult commandNormalizationResult = commandRegistry.getMainNamesOfRequiredCommands(userCommands);
        result.setNotFound(commandNormalizationResult.getNotFoundCommands());

        if(userCommands.size()==result.getNotFound().size()){
            return result;
        }

        AbilityEditRolePermissionsResult commandEditingResult =
                abilityToEditRolePermissions(chatId, commandNormalizationResult.getNormalizedCommands(), userRolePriority);
        result.setForbiddenToEdit(commandEditingResult.getForbidden());
        

        Map<String, Integer> existingCustomPermissions = getCachedCustomRolePermissions(chatId);

        int availableSize = MAX_CUSTOM_PERMISSIONS_COUNT-existingCustomPermissions.size();

        if(availableSize<=0){
            throw new PermissionCreationLimitReachedException();
        }

        Set<String> commandsToUpdate = new HashSet<>();
        Set<CommandPermissionEntity> commandsToSave = new HashSet<>();

        int processedCommands = 0;
        for(String currentCommand: commandEditingResult.getAllowed()){
            if(processedCommands==availableSize){
                break;
            }
            Integer permissionRolePriority = existingCustomPermissions.get(currentCommand);
            if(permissionRolePriority==null){
                commandsToSave.add(new CommandPermissionEntity(chatId, currentCommand, null, rolePriority, true));
            }else if(permissionRolePriority!=rolePriority){
                    commandsToUpdate.add(currentCommand);
            }else{
                result.getHasRequiredPermissionAlready().add(currentCommand);
                continue;
            }

            result.getChanged().add(currentCommand);
            processedCommands++;

        }
        if(!commandsToSave.isEmpty()){
            permissionRepository.saveAll(commandsToSave);}
        if(!commandsToUpdate.isEmpty()){
            permissionRepository.updateRolePermissionForRequiredCommands(chatId, commandsToUpdate, rolePriority);
        }
        putRolePermissionsInTheCache(chatId, result.getChanged(), rolePriority);  // обновляю кеш разрешений

        return result;

    }
    @Transactional
    public SetCommandPermissionResult allowCommandForRole(long chatId, long fromId, @NonNull Set<String> userCommands, @NonNull String roleName){
        int rolePriority = roleService.getRoleByNameIgnoreCase(chatId, roleName)
                .orElseThrow(RoleNotFoundException::new).getRolePriority();

        return allowCommandForRole(chatId, fromId, userCommands, rolePriority);
    }

    @Transactional
    public void deleteCustomRolePermission(long chatId, @NonNull String userCommand, long fromId){

        String mainCommandName = commandRegistry.getMainNameOfCommand(userCommand)
                .orElseThrow(()->new UserCommandNotFoundException(userCommand));

        int userRolePriority = memberService.getCachedMemberRolePriority(chatId, fromId);

        if(!abilityToEditRolePermission(chatId, userCommand, userRolePriority)){
            throw new RolePermissionAccessDeniedException(fromId);
        }

        permissionRepository.deleteRolePermissionForOneCommand(chatId, mainCommandName);

        deleteRolePermissionFromTheCache(chatId, mainCommandName);

    }

    private AbilityEditRolePermissionsResult abilityToEditRolePermissions(long chatId, @NonNull Set<String> normalizedCommands, int userRolePriority){

        Map<String, Integer> customPermissions = getCachedCustomRolePermissions(chatId);

        Set<String> allowed = new HashSet<>();
        Set<String> forbidden = new HashSet<>();

        for(String normalizedCommand: normalizedCommands){

            if(customPermissions.containsKey(normalizedCommand)){
                if(customPermissions.get(normalizedCommand)>userRolePriority){
                    forbidden.add(normalizedCommand);
                }else{
                    allowed.add(normalizedCommand);
                }
            }else{
                Optional<Command> annotationOptional = commandRegistry.getCommandAnnotation(normalizedCommand);
                if(annotationOptional.isEmpty()){
                    log.error("could not find required @Command annotation for normalized string command: {}", normalizedCommand);
                    continue;
                }Command annotation = annotationOptional.get();
                if(annotation.defaultRole().getRolePriority()>userRolePriority){
                    forbidden.add(normalizedCommand);
                }else{
                    allowed.add(normalizedCommand);
                }
             }
          }

        return new AbilityEditRolePermissionsResult(allowed, forbidden);

    }
    private boolean abilityToEditRolePermission(long chatId, @NonNull String normalizedCommand, int userRolePriority){

        Map<String, Integer> customPermissions = getCachedCustomRolePermissions(chatId);

            if(customPermissions.containsKey(normalizedCommand)){
                return customPermissions.get(normalizedCommand) <= userRolePriority;
            }else{
                Optional<Command> annotationOptional = commandRegistry.getCommandAnnotation(normalizedCommand);
                if(annotationOptional.isEmpty()){
                    log.error("could not find required @Command annotation for normalized string command: {}", normalizedCommand);
                    return false;
                }Command annotation = annotationOptional.get();
                return annotation.defaultRole().getRolePriority() <= userRolePriority;
            }

    }


    public Map<String, Integer> getCachedCustomRolePermissions(long chatId) {
        ConcurrentMap<String, Integer> map =  cacheManager.getRolePermissionCache().get(chatId, id ->
                permissionRepository.findByChatIdAndUserIdIsNull(id).stream()
                        .collect(Collectors.toConcurrentMap(
                                CommandPermissionEntity::getCommandName,
                                CommandPermissionEntity::getRolePriority,
                                (existing, replacement) -> existing
                        ))
        );

        return Collections.unmodifiableMap(map);
    }

    private void putRolePermissionInTheCache(long chatId, String normalizedCommand, int newRolePriority) {
        normalizedCommand = normalizedCommand.trim();
        ConcurrentMap<String, Integer> map = cacheManager.getRolePermissionCache().get(chatId, k -> new ConcurrentHashMap<>());
        map.put(normalizedCommand, newRolePriority);
    }

    private void putRolePermissionsInTheCache(long chatId, @NonNull Set<String> normalizedCommands, int newRolePriority){

        ConcurrentMap<String, Integer> map = cacheManager.getRolePermissionCache().get(chatId, k -> new ConcurrentHashMap<>());
        for(String command:normalizedCommands){
            if(command==null) continue;
            map.put(command.trim(), newRolePriority);
        }
    } private void deleteRolePermissionFromTheCache(long chatId, @NonNull String normalizedCommand){

        ConcurrentMap<String, Integer> map = cacheManager.getRolePermissionCache().get(chatId, k -> new ConcurrentHashMap<>());
        map.remove(normalizedCommand.trim());
    }


}
