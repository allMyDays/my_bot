package com.example.my_bot.service.event;
import static com.example.my_bot.enumeration.event.EventArgumentType.*;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.entity.EventEntity;
import com.example.my_bot.enumeration.event.ChatEventType;
import com.example.my_bot.enumeration.event.EventArgumentType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.event.*;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.EventMapper;
import com.example.my_bot.repository.EventRepository;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.utils.ChatUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final CommandAccessService commandAccessService;
    private final MemberService memberService;
    private final RoleService roleService;
    private CommandRegistry commandRegistry;
    private static final int MAX_EVENTS = 50;
    private final CaffeineCacheManager cacheManager;
    private final EventMapper eventMapper;

    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }



    public EventEntity createNewEvent(long chatId,
                               @NonNull MyEventType eventType,
                               int rolePriority,
                               @Nullable String userArgument,
                               @NonNull String fullCommand,
                               long fromId){

        userArgument = checkEventArgumentCorrectness(eventType,userArgument);

        if (eventRepository.countByChatId(chatId)>=MAX_EVENTS){
            throw new TooManyEventsException();
        }
        if(!roleService.roleExistsByPriority(chatId, rolePriority)){
            throw new RoleNotFoundException();
        }
        int callerRole = memberService.getMemberRolePriority(chatId, fromId);
        roleService.checkRoleInteractionAbility(rolePriority, callerRole);

        fullCommand = ChatUtils.cutDefaultPrefix(fullCommand);
        String userCommand = UserInputResolver.splitFullCommand(fullCommand)[0];
        Command annotation = commandRegistry.getCommandAnnotation(userCommand).orElseThrow(()->
                new UserCommandNotFoundException(userCommand));
        if(!annotation.eventable()){
            throw new CannotUseThisCommandForEventException(annotation.mainCommandName());
        }boolean executable = commandAccessService.checkCommandAuthorization(chatId, userCommand, callerRole, fromId);
        if(!executable){
            throw new CommandAccessDeniedException(fromId,userCommand);
        }
        EventEntity savedEvent = eventRepository.save(
                new EventEntity(chatId, eventType, rolePriority, userArgument, fromId, fullCommand)
        );
        invalidateEventsCache(chatId);
        return savedEvent;
    }

    public EventEntity createNewEvent(long chatId,
                                      @NonNull MyEventType eventType,
                                      @NonNull String roleName,
                                      @Nullable String userArgument,
                                      @NonNull String fullCommand,
                                      long fromId){
        RoleDto foundRole = roleService.getRoleByNameIgnoreCase(chatId, roleName)
                .orElseThrow(RoleNotFoundException::new);

        return createNewEvent(chatId, eventType, foundRole.getRolePriority(), userArgument, fullCommand, fromId);
    }


    private String checkEventArgumentCorrectness(@NonNull MyEventType eventType, @Nullable String userArgument){
        EventArgumentType eventArgType = eventType.getArgumentType();
        if(eventArgType== NONE){
            if(userArgument!=null){
                throw new EventTypeNotRequiresArgumentException(eventType);
            }
        }else{
            if(userArgument==null){
                throw new EventArgumentAbsentsException(eventType);
            }
            userArgument = userArgument.trim();
            int argMax = eventType.getArgMax();
            int argMin = eventType.getArgMin();

            if(eventArgType== INTEGER){
                int intArg;
                if(!ChatUtils.isValidInteger(userArgument)||((intArg=Integer.parseInt(userArgument))<argMin||intArg>argMax)){
                    throw new IncorrectEventArgumentException("Для данного типа события, аргумент должен быть валидным числом от %d до %d."
                            .formatted(argMin, argMax));
                }
            }else if(eventArgType == STRING){
                if(userArgument.length()<argMin||userArgument.length()>argMax){
                    throw new IncorrectEventArgumentException("Аргумент должен иметь длину символов от %d до %d для данного типа события."
                            .formatted(argMin, argMax));
                }
            }

        } return userArgument;

    }

    public ImmutableMap<ChatEventType, ImmutableSet<EventDto>> getEventsCache(long chatId){
        return cacheManager.getEventsCache().get(chatId, k->{
            List<EventEntity> entities = eventRepository.findByChatId(chatId);
            Map<ChatEventType, ImmutableSet.Builder<EventDto>> builders = new HashMap<>(); // временная карта

            for (EventEntity entity : entities) {
                    ImmutableSet.Builder<EventDto> builder = builders.computeIfAbsent(entity.getType().getChatEventType(), as -> ImmutableSet.builder());
                    builder.add(eventMapper.toEventDto(entity));
            }
            // итоговая карта
            ImmutableMap.Builder<ChatEventType, ImmutableSet<EventDto>> result = new ImmutableMap.Builder<>();
            builders.forEach((key, value) -> result.put(key, value.build()));
            return result.build();
        });

    }
   private void invalidateEventsCache(long chatId){
       cacheManager.getEventsCache().invalidate(chatId);
   }










}
