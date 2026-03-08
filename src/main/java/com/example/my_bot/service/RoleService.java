package com.example.my_bot.service;

import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.exception.role.RolePriorityAccessDeniedException;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.exception.role.*;
import com.example.my_bot.mapper.RoleMapper;
import com.example.my_bot.repository.RoleRepository;
import com.vdurmont.emoji.EmojiManager;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    private final RoleMapper roleMapper;

    private final MemberService memberService;

    private final int MIN_CREATABLE_ROLE_PRIORITY = -100;
    private final int MAX_CREATABLE_ROLE_PRIORITY =  99;
    private final int MIN_CREATABLE_ROLE_NAME_LENGTH = 3;
    private final int MAX_CREATABLE_ROLE_NAME_LENGTH = 30;



    public RoleEntity createRole(long chatId, long fromId, int rolePriority, @NonNull String roleName){

        roleName=roleName.trim();

        if(rolePriority<MIN_CREATABLE_ROLE_PRIORITY||rolePriority>MAX_CREATABLE_ROLE_PRIORITY){
            throw new RolePriorityOutOfBoundsException(MIN_CREATABLE_ROLE_PRIORITY, MAX_CREATABLE_ROLE_PRIORITY);
        }
        if(roleName.length()<MIN_CREATABLE_ROLE_NAME_LENGTH||roleName.length()>MAX_CREATABLE_ROLE_NAME_LENGTH){
            throw new RoleNameLengthOutOfBoundsException(MIN_CREATABLE_ROLE_NAME_LENGTH, MAX_CREATABLE_ROLE_NAME_LENGTH);
        }
        if(EmojiManager.containsEmoji(roleName)){
            throw new RoleNameCannotContainEmojiException();
        }
        checkRoleInteractionAbility(rolePriority, chatId, fromId);

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

    @Transactional
    public RoleDto deleteCustomRole(long chatId, long fromId, int rolePriority){

        if(isDefaultRole(rolePriority)){
            throw new CannotDeleteDefaultRoleException();
        }
        RoleEntity roleToDelete = roleRepository.findByChatIdAndRolePriority(chatId, rolePriority)
                .orElseThrow(RoleNotFoundException::new);

        checkRoleInteractionAbility(rolePriority, chatId, fromId);

        RoleDto roleToReAssign = findTheNearestLowestRole(chatId, roleToDelete.getRolePriority());

        memberService.updateRolePriorityForMembers(chatId, roleToDelete.getRolePriority(), roleToReAssign.getRolePriority());

        roleRepository.delete(roleToDelete);

        return roleToReAssign;

    }

    @Transactional
    public RoleDto deleteCustomRole(long chatId, long fromId, @NonNull String roleName){

        if(isDefaultRole(roleName)){
            throw new CannotDeleteDefaultRoleException();
        }
        RoleEntity roleToDelete = roleRepository.findByChatIdAndRoleName(chatId, roleName)
                .orElseThrow(RoleNotFoundException::new);

        checkRoleInteractionAbility(roleToDelete.getRolePriority(), chatId, fromId);

        RoleDto roleToReAssign = findTheNearestLowestRole(chatId, roleToDelete.getRolePriority());

        memberService.updateRolePriorityForMembers(chatId, roleToDelete.getRolePriority(), roleToReAssign.getRolePriority());

        roleRepository.delete(roleToDelete);

        return roleToReAssign;

    }

    public RoleDto findTheNearestLowestRole(long chatId, int inputRolePriority){

        TreeSet<RoleDto> allSortedRoles = getAllRolesSortedInDescendingOrder(chatId);

        RoleDto currentTempRoleDto = new RoleDto(null, inputRolePriority);

        RoleDto foundRole = allSortedRoles.higher(currentTempRoleDto);

        if(foundRole==null){
            foundRole = allSortedRoles.lower(currentTempRoleDto);
            if(foundRole==null){
                throw new RoleNotFoundException();
            }
        }
        return foundRole;
    }


    public TreeSet<RoleDto> getAllRolesSortedInDescendingOrder(long chatId){

        TreeSet<RoleDto> sortedRoleSet = new TreeSet<>(
                Comparator.comparingInt(RoleDto::getRolePriority).reversed()
        );
        sortedRoleSet.addAll(roleMapper.toDto(getRoles(chatId)));


        for(DefaultRole defRole: DefaultRole.values()){
            sortedRoleSet.add(roleMapper.toDto(defRole));   // дубликаты не добавятся из-за equals & hashcode по priorityRole

        } return sortedRoleSet;

    }

    public Optional<String> getRoleName(long chatId, int rolePriority){

        Optional<RoleEntity> optionalRole = roleRepository.findByChatIdAndRolePriority(chatId, rolePriority);

        if(optionalRole.isEmpty()){
            return DefaultRole.getRoleNameByPriority(rolePriority);
        }return Optional.of(optionalRole.get().getRoleName());

    }


    private List<RoleEntity> getRoles(long chatId){
        return roleRepository.findByChatId(chatId);

    }

    private void checkRoleInteractionAbility(int rolePriority, long chatId, long fromId){
        if(rolePriority>memberService.getCachedMemberRolePriority(chatId, fromId)){
            throw new RolePriorityAccessDeniedException();
        }

    }














}
