package com.example.my_bot.command.commands.event;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.EventEntity;
import com.example.my_bot.enumeration.event.EventArgumentType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.event.EventService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.event.MyEventType.WITHOUT_SUBSCRIPTION;
import static com.example.my_bot.enumeration.event.MyEventType.WITH_SUBSCRIPTION;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "событие", alternativeCommandNames = {"ивент","addevent"}, defaultRole = SENIOR_MODERATOR, eventable = false)
public class EventCreateCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private final EventService eventService;

    private VkChatClient vkChatClient;

    private RoleService roleService;

    private final static String DELETE_PARAMETER = "&delete";
    private final static Pattern DELETE_PARAMETER_PATTERN =  Pattern.compile(DELETE_PARAMETER, Pattern.CASE_INSENSITIVE);


    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        String[] args = messageDto.getFirstRowArguments();
        long chatId = messageDto.getChatId();
        long peerId = messageDto.getPeerId();
        long fromId = messageDto.getFromId();

        if(args.length<3) {    //самый минимум: "!ивент приглашение модератор бан" (тип, роль, команда)
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE, peerId, true);
            return;
        }
        MyEventType foundEventType = MyEventType.findByCyrillicType(args[0]).orElse(null);
        if(foundEventType==null){
            vkChatClient.sendText("Вы ввели несуществующий тип события.", peerId, true);
            return;
        }
        String eventRole;
        String fullCommand;
        String eventArgument=null;

        if(foundEventType.getArgumentType()!= EventArgumentType.NONE){
            // событие нуждается в обязательном аргументе, самый минимум: "!ивент эмоджи 50 модератор бан"
            if(args.length<4) {
                vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE, peerId, true);
                return;
            }
            eventArgument = args[1];
            eventRole = args[2];
            fullCommand = collectArgumentsSinceIndex(args, 3);
        } else{
            // аргумента нет, предполагается: !ивент приглашение модератор бан
            eventRole = args[1];
            fullCommand = collectArgumentsSinceIndex(args, 2);
        }

        EventEntity createdEvent;
        eventRole=eventRole.trim();

        boolean delete = false;

        Matcher deleteMatcher = DELETE_PARAMETER_PATTERN.matcher(fullCommand);
        if(deleteMatcher.find()){
            delete = true;
            fullCommand = deleteMatcher.replaceAll("");
        }
        fullCommand=fullCommand.trim();
        fullCommand = fullCommand.isEmpty()?null:fullCommand;

        try{
            if(isValidInteger(eventRole)){
                createdEvent = eventService.createNewEvent(chatId, foundEventType, Integer.parseInt(eventRole), eventArgument, fullCommand, fromId, delete);
            }else{
                // предполагается что ввели название роли
                createdEvent = eventService.createNewEvent(chatId, foundEventType, eventRole, eventArgument, fullCommand, fromId, delete);
            }

        } catch (RoleException | EventException | CommandException e) {
            vkChatClient.sendText(e.getMessage(), peerId, true);
            return;
        }

        String roleName = roleService.getRoleName(chatId, createdEvent.getRolePriority()).orElse("unknown role");
        MyEventType type = createdEvent.getType();

        String message = "✅ %s(Вы) успешно создали новое событие.\n".formatted(createMention(fromId)) +
                "&#128218; Тип: %s (%s).\n".formatted(type.getCyrillicType(), type.getDescription());
                if(createdEvent.getArgument()!=null){
                    String arg = createdEvent.getArgument();
                    message+="&#128204; Аргумент: %s\n".formatted((type==WITH_SUBSCRIPTION||type==WITHOUT_SUBSCRIPTION)?createMention(Long.parseLong(arg)):arg);
                }
                message+="&#128081; Воздействует на роль «%s» и ниже.\n".formatted(roleName);
                if(createdEvent.getFullCommand()!=null){
                    message+="&#8618; Применяется команда: %s".formatted(createdEvent.getFullCommand());
                }
                if(type.getAdvancedEventConfig().isCanBeAdvancedEvent()){
                    message+="\n\n❓Событие реагирует только на одно сообщение от участника. Если хотите больше сообщений, событие нужно расширить.";

                }


        vkChatClient.sendText(message, peerId, true);


    }

    @Autowired
    public void setRoleService(RoleService roleService) {
        this.roleService = roleService;
    }
}
