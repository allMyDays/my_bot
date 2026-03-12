package com.example.my_bot.service;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
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
import com.example.my_bot.mapper.json.CommandPermissionJsonMapper;
import com.example.my_bot.repository.CommandPermissionRepository;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.RedisKeyBuilder.ROLE_CMD_PERMISSION;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommandPermissionService {

    private CommandRegistry commandRegistry;

    private final MemberService memberService;

    private final RoleService roleService;

    private final CommandPermissionRepository permissionRepository;

    private final RedisService redisService;

    private final CommandPermissionJsonMapper permissionJsonMapper;

    private final CommandPermissionMapper permissionMapper;

    private final static int PERMISSIONS_CACHE_TTL_SECONDS = 600;

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
        if(userCommands.size()==commandNormalizationResult.getNotFoundCommands().size()){
            result.setNotFound(userCommands);
            return result;
        }result.setNotFound(commandNormalizationResult.getNotFoundCommands());

        AbilityEditRolePermissionsResult commandEditingResult =
                editRolePermissionsAbility(chatId, commandNormalizationResult.getNormalizedCommands(), userRolePriority);
        result.setForbiddenToEdit(commandEditingResult.getForbidden());
        

        Map<String, RolePermissionDto> existingCustomPermissions = getCachedCustomRolePermissions(chatId);

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
            RolePermissionDto permissionDto = existingCustomPermissions.get(currentCommand);
            if(permissionDto==null){
                commandsToSave.add(new CommandPermissionEntity(chatId, currentCommand, null, rolePriority, true));
            }else if(permissionDto.getRolePriority()!=rolePriority){
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

        redisService.delete(ROLE_CMD_PERMISSION.buildKey(chatId));  // удаляю кеш разрешений

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

        AbilityEditRolePermissionsResult editResult = editRolePermissionsAbility(chatId, Set.of(userCommand), userRolePriority);

        if(editResult.getForbidden().contains(mainCommandName)){
            throw new RolePermissionAccessDeniedException(fromId);
        }

        permissionRepository.deleteRolePermissionForOneCommand(chatId, mainCommandName);

        redisService.delete(ROLE_CMD_PERMISSION.buildKey(chatId));

    }

    private AbilityEditRolePermissionsResult editRolePermissionsAbility(long chatId, @NonNull Set<String> normalizedCommands, int userRolePriority){

        Map<String, RolePermissionDto> customPermissions = getCachedCustomRolePermissions(chatId);

        Set<String> allowed = new HashSet<>();
        Set<String> forbidden = new HashSet<>();

        for(String normalizedCommand: normalizedCommands){

            if(customPermissions.containsKey(normalizedCommand)){
                if(customPermissions.get(normalizedCommand).getRolePriority()>userRolePriority){
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


    public Map<String, RolePermissionDto> getCachedCustomRolePermissions(long chatId){

        Map<String, String> hash = redisService.getHash(ROLE_CMD_PERMISSION.buildKey(chatId));

        if(!hash.isEmpty()){
            return hash.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry-> permissionJsonMapper.fromJson(entry.getValue()))
                    );
        }

        List<RolePermissionDto> rolePermissionDTOS = permissionMapper.toPermissionDtoList(
                permissionRepository.findByChatIdAndUserIdIsNull(chatId)
        );

        Map<String, RolePermissionDto> mapToReturn = new HashMap<>();
        Map<String, String> mapToSave = new HashMap<>();
        for (RolePermissionDto dto : rolePermissionDTOS) {
            mapToReturn.put(dto.getCommandName(), dto);
            mapToSave.put(dto.getCommandName(), permissionJsonMapper.toJson(dto));
        }

        redisService.setHash(ROLE_CMD_PERMISSION.buildKey(chatId), mapToSave, PERMISSIONS_CACHE_TTL_SECONDS);

        return mapToReturn;

    }


















}
