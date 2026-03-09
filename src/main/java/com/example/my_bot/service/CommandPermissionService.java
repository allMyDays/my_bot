package com.example.my_bot.service;

import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.entity.CommandPermissionEntity;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.repository.CommandPermissionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommandPermissionService {

    private CommandRegistry commandRegistry;

    private final MemberService memberService;

    private final RoleService roleService;

    private final CommandPermissionRepository permissionRepository;


    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }


    public Set<String> allowCommandForRole(long chatId, long fromId, @NonNull Set<String> userCommands, int rolePriority){

        if(!roleService.roleExistsByPriority(chatId, rolePriority)){
            throw new RoleNotFoundException();
        }

        if(rolePriority>memberService.getCachedMemberRolePriority(chatId, fromId)){
            throw new RoleAccessDeniedException();

        }

        Set<String> mainCmdNames = commandRegistry.getMainNamesOfRequiredCommands(userCommands);
        if(mainCmdNames.isEmpty()){
            return mainCmdNames;
        }

        List<CommandPermissionEntity> permissionsToSave = mainCmdNames.stream()
                .map(name->new CommandPermissionEntity(chatId, name, null, rolePriority, true))
                .toList();

        permissionRepository.saveAll(permissionsToSave);

        return mainCmdNames;

    }


















}
