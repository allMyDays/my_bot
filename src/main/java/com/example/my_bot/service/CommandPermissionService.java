package com.example.my_bot.service;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.dto.permission.RoleCommandPermissionDto;
import com.example.my_bot.entity.CommandPermissionEntity;
import com.example.my_bot.enumeration.RedisKeyBuilder;
import com.example.my_bot.exception.command.NoUserCommandsFoundException;
import com.example.my_bot.exception.permission.PermissionCreationLimitReachedException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.mapper.CommandPermissionMapper;
import com.example.my_bot.mapper.json.CommandPermissionJsonMapper;
import com.example.my_bot.repository.CommandPermissionRepository;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.RedisKeyBuilder.ROLE_CMD_PERMISSION;

@Service
@RequiredArgsConstructor
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
    public Set<String> allowCommandForRole(long chatId, long fromId, @NonNull Set<String> userCommands, int rolePriority){

        if(!roleService.roleExistsByPriority(chatId, rolePriority)){
            throw new RoleNotFoundException();
        }

        if(rolePriority>memberService.getCachedMemberRolePriority(chatId, fromId)){
            throw new RoleAccessDeniedException();
        }

        Set<String> mainUserCmdNames = commandRegistry.getMainNamesOfRequiredCommands(userCommands);
        if(mainUserCmdNames.isEmpty()){
            throw new NoUserCommandsFoundException();
        }

        Map<String, RoleCommandPermissionDto> existingCustomPermissions = getCachedCustomRolePermissions(chatId);

        int availableSize = MAX_CUSTOM_PERMISSIONS_COUNT-existingCustomPermissions.size();

        if(availableSize<=0){
            throw new PermissionCreationLimitReachedException();
        }

        Set<String> commandsToUpdate = new HashSet<>();
        Set<String> commandsToSave = new HashSet<>();

        int processedCommands = 0;
        for(String currentCommand: mainUserCmdNames){
            if(processedCommands==availableSize){
                break;
            }
            RoleCommandPermissionDto commandDto = existingCustomPermissions.get(currentCommand);
            if(commandDto==null){
                commandsToSave.add(currentCommand);
                processedCommands++;
            }else if(commandDto.getRolePriority()!=rolePriority){
                    commandsToUpdate.add(currentCommand);
                    processedCommands++;
            }
        }

        permissionRepository.saveAll(
                commandsToSave.stream()
                        .map(c->new CommandPermissionEntity(chatId, c, null, rolePriority, true))
                        .toList()
        );
        if(!commandsToUpdate.isEmpty()){
         permissionRepository.updateRolePriorityForRoleCommands(chatId, commandsToUpdate, rolePriority);
        }

        redisService.delete(ROLE_CMD_PERMISSION.buildKey(chatId));  // удаляю кеш разрешений
        commandsToSave.addAll(commandsToUpdate);
        return commandsToSave;


    }

    public Map<String, RoleCommandPermissionDto> getCachedCustomRolePermissions(long chatId){

        Map<String, String> hash = redisService.getHash(ROLE_CMD_PERMISSION.buildKey(chatId));

        if(!hash.isEmpty()){
            return hash.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry-> permissionJsonMapper.fromJson(entry.getValue()))
                    );
        }

        List<RoleCommandPermissionDto> rolePermissionDTOs = permissionMapper.toPermissionDtoList(
                permissionRepository.findByChatIdAndUserIdIsNull(chatId)
        );

        Map<String, RoleCommandPermissionDto> mapToReturn = new HashMap<>();
        Map<String, String> mapToSave = new HashMap<>();
        for (RoleCommandPermissionDto dto : rolePermissionDTOs) {
            mapToReturn.put(dto.getCommandName(), dto);
            mapToSave.put(dto.getCommandName(), permissionJsonMapper.toJson(dto));
        }

        redisService.setHash(ROLE_CMD_PERMISSION.buildKey(chatId), mapToSave, PERMISSIONS_CACHE_TTL_SECONDS);

        return mapToReturn;

    }


















}
