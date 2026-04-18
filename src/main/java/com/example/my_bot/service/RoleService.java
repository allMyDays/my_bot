package com.example.my_bot.service;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.exception.role.*;
import com.example.my_bot.mapper.RoleMapper;
import com.example.my_bot.repository.RoleRepository;
import com.google.common.collect.ImmutableMap;
import com.vdurmont.emoji.EmojiManager;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    private final RoleMapper roleMapper;

    private final MemberService memberService;

    private final CaffeineCacheManager cacheManager;

    private final int MIN_CREATABLE_ROLE_PRIORITY = -100;
    private final int MAX_CREATABLE_ROLE_PRIORITY =  99;
    private final int MIN_CREATABLE_ROLE_NAME_LENGTH = 3;
    private final int MAX_CREATABLE_ROLE_NAME_LENGTH = 30;
    private final int MAX_CUSTOM_ROLES_COUNT = 10;


    @Transactional
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
        checkRoleInteractionAbility(rolePriority, memberService.getMemberRolePriority(chatId, fromId));

        if(isDefaultRole(rolePriority)){
            throw new DuplicateRolePriorityException(rolePriority);
        }if(isDefaultRole(roleName.toLowerCase())){
            throw new DuplicateRoleNameException(
                    "Данное Название является зарезервированным для системной роли, вы не можете создать роль с таким названием."
            );
        }

        int createdRolesCounter=0;
        for(Map.Entry<Integer, String> entry: getAllRolesWithNoSorting(chatId).entrySet()){
            if(!isDefaultRole(entry.getKey())){
                createdRolesCounter++;
            }if(entry.getKey()==rolePriority){
                throw new DuplicateRolePriorityException(rolePriority);
            }if(entry.getValue().equalsIgnoreCase(roleName)){
                throw new DuplicateRoleNameException("Роль с названием «%s» уже существует.".formatted(roleName));
            }
        }
        if(createdRolesCounter>= MAX_CUSTOM_ROLES_COUNT){
            throw new RoleCreationLimitReachedException();
        }
        RoleEntity savedRole = roleRepository.save(new RoleEntity(chatId, rolePriority, roleName));
        invalidateRoleCache(chatId);
        return savedRole;

    }
    @Transactional
    public RoleDto renameRole(long chatId, long fromId, int existingRolePriority, @NonNull String newRoleName){

        newRoleName=newRoleName.trim();

        if(newRoleName.length()<MIN_CREATABLE_ROLE_NAME_LENGTH||newRoleName.length()>MAX_CREATABLE_ROLE_NAME_LENGTH){
            throw new RoleNameLengthOutOfBoundsException(MIN_CREATABLE_ROLE_NAME_LENGTH, MAX_CREATABLE_ROLE_NAME_LENGTH);
        }
        if(EmojiManager.containsEmoji(newRoleName)){
            throw new RoleNameCannotContainEmojiException();
        }
        checkRoleInteractionAbility(existingRolePriority, memberService.getMemberRolePriority(chatId, fromId));

        Map<Integer, String> allRoles = getAllRolesWithNoSorting(chatId);

        String currentRoleName = allRoles.get(existingRolePriority);

        if(currentRoleName==null){
            throw new RoleNotFoundException();
        }

        if(currentRoleName.equals(newRoleName)){    // можно изменить регистр роли
            throw new DuplicateRoleNameException("Данная роль с приоритетом %d уже имеет точно такое же название."
                    .formatted(existingRolePriority));

        }
        Optional<DefaultRole> systemRole = DefaultRole.getRoleByName(newRoleName.toLowerCase());

        if(systemRole.isPresent()&&systemRole.get().getRolePriority()!=existingRolePriority){
            // роль нельзя переименовать в системную, даже если эта системная роль переименована, за исключением если системную роль переименовывают в своё изначальное имя
            throw new DuplicateRoleNameException("Название «%s» является зарезервированным для системной роли, вы не можете переименовать роль в такое название."
                    .formatted(systemRole.get().getRoleName()));
        }

        for(Map.Entry<Integer, String> entry: allRoles.entrySet()){
            if(entry.getValue().equalsIgnoreCase(newRoleName)&&entry.getKey()!=existingRolePriority){
                // можно изменить регистр названия существующей роли, но нельзя переименовать роль в название другой роли, даже если регистр различается
                throw new DuplicateRoleNameException("Роль с названием «%s» уже существует.".formatted(newRoleName));
            }
        }

        Optional<RoleEntity> currentRole =  roleRepository.findByChatIdAndRolePriority(chatId, existingRolePriority);
        RoleEntity roleToSave = currentRole.orElseGet(() ->
                new RoleEntity(chatId, existingRolePriority));
        roleToSave.setRoleName(newRoleName);
        RoleDto roleToReturn = roleMapper.toDto(roleRepository.save(roleToSave));
        invalidateRoleCache(chatId);
        return roleToReturn;

    }
    @Transactional
    public RoleDto renameRole(long chatId, long fromId, @NonNull String existingRoleName, @NonNull String newRoleName){

           int rolePriority = getRoleByNameIgnoreCase(chatId, existingRoleName)
                .orElseThrow(RoleNotFoundException::new).getRolePriority();

        return renameRole(chatId, fromId, rolePriority, newRoleName);

    }

    @Transactional
    public RoleDto deleteCustomRole(long chatId, long fromId, int rolePriority){

        if(isDefaultRole(rolePriority)){
            throw new CannotDeleteDefaultRoleException();
        }
        if(!getCreatedOrModifiedRoles(chatId).containsKey(rolePriority)){
            throw new RoleNotFoundException();
        }

        checkRoleInteractionAbility(rolePriority, memberService.getMemberRolePriority(chatId, fromId));

        RoleDto roleToReAssign = findTheNearestLowestRole(chatId, rolePriority, true);

        memberService.reAssignRequiredMembersMassively(chatId, rolePriority, roleToReAssign.getRolePriority());

        int deletedRows = roleRepository.deleteByChatIdAndRolePriority(chatId, rolePriority);
        if(deletedRows==0){
            log.error("chat {} error: could not delete role by chat id and priority {}", chatId, rolePriority);
            throw new RoleNotFoundException();
        }
        invalidateRoleCache(chatId);
        return roleToReAssign;

    }

    @Transactional
    public RoleDto deleteCustomRole(long chatId, long fromId, @NonNull String roleName){

        RoleDto role = getRoleByNameIgnoreCase(chatId, roleName)
                .orElseThrow(RoleNotFoundException::new);

        return deleteCustomRole(chatId, fromId, role.getRolePriority());
    }

    public RoleDto findTheNearestLowestRole(long chatId, int rolePriority, boolean findHigherIfAbsents){

        TreeMap<Integer, String> allSortedRoles = getAllRolesSortedInDescendingOrder(chatId);

        Map.Entry<Integer, String> foundRole = allSortedRoles.higherEntry(rolePriority);

        if(foundRole==null){
            if(findHigherIfAbsents){
                foundRole = allSortedRoles.lowerEntry(rolePriority);
            }
            if(foundRole==null){
                throw new RoleNotFoundException();
            }
        }
        return new RoleDto(foundRole.getValue(), foundRole.getKey());
    }
    public RoleDto findTheNearestHighestRole(long chatId, int rolePriority){

        TreeMap<Integer, String> allSortedRoles = getAllRolesSortedInDescendingOrder(chatId);

        Map.Entry<Integer, String> foundRole = allSortedRoles.lowerEntry(rolePriority);

        if(foundRole==null){
                throw new RoleNotFoundException();
        }
        return new RoleDto(foundRole.getValue(), foundRole.getKey());
    }



    public TreeMap<Integer, String> getAllRolesSortedInDescendingOrder(long chatId){
        TreeMap<Integer, String> sortedReverse = new TreeMap<>(Comparator.reverseOrder());
        sortedReverse.putAll(getAllRolesWithNoSorting(chatId));
        return sortedReverse;


    }

    public Map<Integer, String> getAllRolesWithNoSorting(long chatId){

        Map<Integer, String> resultMap = new HashMap<>(getCreatedOrModifiedRoles(chatId));

        for(DefaultRole defaultRole: DefaultRole.values()){
            resultMap.putIfAbsent(defaultRole.getRolePriority(), defaultRole.getRoleName());

        } return resultMap;

    }

    public Optional<String> getRoleName(long chatId, int rolePriority){

       return getRoleByPriority(chatId, rolePriority).map(RoleDto::getRoleName);

    }


    public Optional<RoleDto> getRoleByPriority(long chatId, int rolePriority){

        Map<Integer, String> allRoles = getAllRolesWithNoSorting(chatId);
        return Optional.ofNullable(allRoles.get(rolePriority))
                .map(r->new RoleDto(r,rolePriority));

    }

    public boolean roleExistsByPriority(long chatId, int rolePriority){

        if(isDefaultRole(rolePriority)){
            return true;
        }
        return getRoleByPriority(chatId,rolePriority).isPresent();

    }

    public Optional<RoleDto> getRoleByNameIgnoreCase(long chatId, @NonNull String roleName){
        roleName=roleName.trim();

       Map<Integer, String> roles = getAllRolesWithNoSorting(chatId);

       for(Map.Entry<Integer, String> role: roles.entrySet()){
           if(role.getValue().equalsIgnoreCase(roleName)){
               return Optional.of(new RoleDto(role.getValue(), role.getKey()));
           }
       }return Optional.empty();

    }

    public void checkRoleInteractionAbility(int roleToEdit, int userRole){

        if(roleToEdit>userRole||(userRole==roleToEdit&&userRole<SENIOR_ADMINISTRATOR.getRolePriority())){
            // никому нельзя редактировать роль выше своей, но можно редактировать свою роль, если ты ст.админ и выше
            throw new RoleAccessDeniedException();
        }
    }
    public ImmutableMap<Integer, String> getCreatedOrModifiedRoles(long chatId){

        return cacheManager.getDbRoleCache().get(chatId,
                k-> {
                    ImmutableMap.Builder<Integer, String> map = new ImmutableMap.Builder<>();
                    List<RoleEntity> roleEntities = roleRepository.findByChatId(chatId);
                    for(RoleEntity role: roleEntities){
                        map.put(role.getRolePriority(), role.getRoleName());
                    } return map.build();
                });

    }

    private void invalidateRoleCache(long chatId){
        cacheManager.getDbRoleCache().invalidate(chatId);

    }

}
