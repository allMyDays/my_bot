package com.example.my_bot.service;

import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.exception.ChatEntityNotFoundException;
import com.example.my_bot.exception.role.DuplicateRoleNameException;
import com.example.my_bot.exception.role.DuplicateRolePriorityException;
import com.example.my_bot.exception.role.RoleNameLengthOutOfBoundsException;
import com.example.my_bot.exception.role.RolePriorityOutOfBoundsException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.mapper.json.ChatJsonMapper;
import com.example.my_bot.repository.ChatRepository;
import com.example.my_bot.repository.RoleRepository;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;
import static com.example.my_bot.enumeration.RedisKeyBuilder.CHAT;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    private final int MIN_CREATABLE_ROLE_PRIORITY = -100;
    private final int MAX_CREATABLE_ROLE_PRIORITY =  99;
    private final int MIN_CREATABLE_ROLE_NAME_LENGTH = 3;
    private final int MAX_CREATABLE_ROLE_NAME_LENGTH = 20;



    public RoleEntity createRole(long chatId, int rolePriority, @NonNull String roleName){

        roleName=roleName.trim();

        if(rolePriority<MIN_CREATABLE_ROLE_PRIORITY||rolePriority>MAX_CREATABLE_ROLE_PRIORITY){
            throw new RolePriorityOutOfBoundsException(MIN_CREATABLE_ROLE_PRIORITY, MAX_CREATABLE_ROLE_PRIORITY);
        }
        if(roleName.length()<MIN_CREATABLE_ROLE_NAME_LENGTH||roleName.length()>MAX_CREATABLE_ROLE_NAME_LENGTH){
            throw new RoleNameLengthOutOfBoundsException(MIN_CREATABLE_ROLE_NAME_LENGTH, MAX_CREATABLE_ROLE_NAME_LENGTH);
        }

        Set<Integer> existingPriorities = new HashSet<>();
        Set<String> existingNames = new HashSet<>();

        for(RoleEntity existingRole: getRoles(chatId)){
            existingPriorities.add(existingRole.getRolePriority());
            existingNames.add(existingRole.getRoleName().toLowerCase());
        }

        if(isDefaultRole(rolePriority)||existingPriorities.contains(rolePriority)){
            throw new DuplicateRolePriorityException(rolePriority);
        }
        if(isDefaultRole(roleName)||existingNames.contains(roleName.toLowerCase())){
            throw new DuplicateRoleNameException(roleName);
        }

        return roleRepository.save(new RoleEntity(chatId, rolePriority, roleName));

    }

    public List<RoleEntity> getRoles(long chatId){
        return roleRepository.findByChatId(chatId);

    }














}
