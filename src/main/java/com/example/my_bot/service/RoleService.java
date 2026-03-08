package com.example.my_bot.service;

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
    private final int MAX_ROLES_COUNT = 10;



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


        int createdRoles=0;
        for(RoleEntity existingRole: roleRepository.findByChatId(chatId)){
            existingPriorities.add(existingRole.getRolePriority());
            existingNames.add(existingRole.getRoleName().toLowerCase());
            if(!isDefaultRole(existingRole.getRolePriority())){
                createdRoles++;

            }
        }
        if(createdRoles>=MAX_ROLES_COUNT){
            throw new RoleCreationLimitReachedException();
        }

        if(existingPriorities.contains(rolePriority)||isDefaultRole(rolePriority)){
            throw new DuplicateRolePriorityException(rolePriority);
        }
        if(existingNames.contains(roleName.toLowerCase())||isDefaultRole(roleName)){
            throw new DuplicateRoleNameException("Роль с названием «%s» уже существует.".formatted(roleName));
        }

        return roleRepository.save(new RoleEntity(chatId, rolePriority, roleName));

    }
    @Transactional
    public RoleDto renameRole(long chatId, long fromId, int existingRolePriority, @NonNull String newRoleName){

        if(newRoleName.length()<MIN_CREATABLE_ROLE_NAME_LENGTH||newRoleName.length()>MAX_CREATABLE_ROLE_NAME_LENGTH){
            throw new RoleNameLengthOutOfBoundsException(MIN_CREATABLE_ROLE_NAME_LENGTH, MAX_CREATABLE_ROLE_NAME_LENGTH);
        }
        if(EmojiManager.containsEmoji(newRoleName)){
            throw new RoleNameCannotContainEmojiException();
        }
        checkRoleInteractionAbility(existingRolePriority, chatId, fromId);

        RoleDto roleToEdit = getExistingChatRole(chatId, existingRolePriority)
                .orElseThrow(RoleNotFoundException::new);

        if(roleToEdit.getRoleName().equalsIgnoreCase(newRoleName)){
            throw new DuplicateRoleNameException("Данная роль с приоритетом %d уже имеет указанное название."
                    .formatted(existingRolePriority));

        }
        Optional<DefaultRole> systemRole = DefaultRole.getRoleByName(newRoleName.toLowerCase());

        if(systemRole.isPresent()&&systemRole.get().getRolePriority()!=existingRolePriority){
            // роль нельзя переименовать в системную, даже если эта системная роль переименована, за исключением если системную роль переименовывают в своё изначальное имя
            throw new DuplicateRoleNameException("Роль с названием «%s» уже существует.".formatted(newRoleName));
        }

        HashSet<RoleDto> existingRoles = getAllRolesWithNoSorting(chatId);


        for(RoleDto roleDto: existingRoles){
            if(roleDto.getRoleName().equalsIgnoreCase(newRoleName)){
                throw new DuplicateRoleNameException("Роль с названием «%s» уже существует.".formatted(newRoleName));
            }
        }

        if(!roleToEdit.isRoleInDataBase()){
            RoleEntity roleToSave = new RoleEntity(chatId, roleToEdit.getRolePriority(),newRoleName);
            roleRepository.save(roleToSave);
            return roleMapper.toDto(roleToSave,true);
        }
        int updatedRows = roleRepository.updateRoleName(chatId, existingRolePriority, newRoleName);
        if(updatedRows==0){
            throw new RoleNotFoundException();
        }
        return new RoleDto(newRoleName,existingRolePriority, true);

    }
    @Transactional
    public RoleDto renameRole(long chatId, long fromId, @NonNull String existingRoleName, @NonNull String newRoleName){

           Integer rolePriority = getRolePriority(chatId, existingRoleName)
                .orElseThrow(RoleNotFoundException::new);

        return renameRole(chatId, fromId, rolePriority, newRoleName);

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

        RoleDto currentTempRoleDto = new RoleDto(null, inputRolePriority,false);

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
        sortedRoleSet.addAll(roleMapper.toDto(roleRepository.findByChatId(chatId),true));


        for(DefaultRole defRole: DefaultRole.values()){
            sortedRoleSet.add(roleMapper.toDto(defRole,false));   // дубликаты не добавятся из-за equals & hashcode по priorityRole

        } return sortedRoleSet;

    }

    public HashSet<RoleDto> getAllRolesWithNoSorting(long chatId){

        HashSet<RoleDto> roles = new HashSet<>(roleMapper.toDto(roleRepository.findByChatId(chatId),true));
        for(DefaultRole defRole: DefaultRole.values()){
            roles.add(roleMapper.toDto(defRole,false));   // дубликаты не добавятся из-за equals & hashcode по priorityRole

        } return roles;

    }



    public Optional<String> getRoleName(long chatId, int rolePriority){

       return getExistingChatRole(chatId, rolePriority).map(RoleDto::getRoleName);

    }
    public Optional<Integer> getRolePriority(long chatId, @NonNull String roleName){

        return getExistingChatRole(chatId, roleName).map(RoleDto::getRolePriority);

    }

    public Optional<RoleDto> getExistingChatRole(long chatId, int rolePriority){

        Optional<RoleEntity> optionalRole = roleRepository.findByChatIdAndRolePriority(chatId, rolePriority);

        if(optionalRole.isEmpty()){
            Optional<String> defRole = DefaultRole.getRoleNameByPriority(rolePriority);
            return defRole.map(s -> new RoleDto(s, rolePriority, false));

        }return Optional.of(roleMapper.toDto(optionalRole.get(), true));

    }

    public Optional<RoleDto> getExistingChatRole(long chatId, @NonNull String roleName){

        roleName=roleName.trim();
        Optional<RoleEntity> optionalRole = roleRepository.findByChatIdAndRoleName(chatId, roleName);

        if(optionalRole.isEmpty()){
            Optional<DefaultRole> defRole = DefaultRole.getRoleByName(roleName);
            return defRole.map(r -> roleMapper.toDto(r,false));

        }return Optional.of(roleMapper.toDto(optionalRole.get(), true));

    }


    private void checkRoleInteractionAbility(int rolePriority, long chatId, long fromId){
        if(rolePriority>memberService.getCachedMemberRolePriority(chatId, fromId)){
            throw new RolePriorityAccessDeniedException();
        }

    }














}
