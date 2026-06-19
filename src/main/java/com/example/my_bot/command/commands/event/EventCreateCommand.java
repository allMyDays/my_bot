package com.example.my_bot.command.commands.event;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.EventEntity;
import com.example.my_bot.enumeration.event.EventArgumentType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
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
import static com.example.my_bot.utils.ChatUtils.CHAT_MANAGER_ROLE_PRIORITY;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "событие", alternativeCommandNames = {"ивент","addevent"}, defaultRole = SENIOR_MODERATOR, eventable = false)
public class EventCreateCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private final EventService eventService;

    private final MessageMapper messageMapper;

    private VkChatClient vkChatClient;

    private RoleService roleService;

    private final static String DELETE_PARAMETER = "&delete";
    private final static Pattern DELETE_PARAMETER_PATTERN =  Pattern.compile(DELETE_PARAMETER, Pattern.CASE_INSENSITIVE);

    private final static String REPLY_PARAMETER = "&reply";
    private final static Pattern REPLY_PARAMETER_PATTERN =  Pattern.compile(REPLY_PARAMETER, Pattern.CASE_INSENSITIVE);

    private final static String SILENT_PARAMETER = "&silent";
    private final static Pattern SILENT_PARAMETER_PATTERN =  Pattern.compile(SILENT_PARAMETER, Pattern.CASE_INSENSITIVE);



    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        String[] args = commandMessage.getFirstRowArguments();
        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        long fromId = commandMessage.getFromId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",commandMessage);

        if(args.length<3) {    //самый минимум: "!ивент приглашение модератор бан" (тип, роль, команда)
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        MyEventType foundEventType = MyEventType.findByCyrillicType(args[0]).orElse(null);
        if(foundEventType==null){
            sendMessage.setText("Вы ввели несуществующий тип события.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        String eventRole;
        String fullCommand;
        String eventArgument=null;

        if(foundEventType.getArgumentType()!= EventArgumentType.NONE){
            // событие нуждается в обязательном аргументе, самый минимум: "!ивент эмоджи 50 модератор бан"
            if(args.length<4) {
                sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
                vkChatClient.sendText(sendMessage);
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
        boolean reply = false;
        boolean silent = false;

        Matcher deleteMatcher = DELETE_PARAMETER_PATTERN.matcher(fullCommand);
        if(deleteMatcher.find()){
            delete = true;
            fullCommand = deleteMatcher.replaceAll("");
        }
        Matcher replyMatcher = REPLY_PARAMETER_PATTERN.matcher(fullCommand);
        if(replyMatcher.find()){
            reply = true;
            fullCommand = replyMatcher.replaceAll("");
        }
        Matcher silentMatcher = SILENT_PARAMETER_PATTERN.matcher(fullCommand);
        if(silentMatcher.find()){
            silent = true;
            fullCommand = silentMatcher.replaceAll("");
        }

        fullCommand=fullCommand.trim();
        fullCommand = fullCommand.isEmpty()?null:fullCommand;

        try{
            if(isValidInteger(eventRole)){
                createdEvent = eventService.createNewEvent(chatId, foundEventType, Integer.parseInt(eventRole), eventArgument, fullCommand, fromId, delete,reply,silent);
            }else{
                // предполагается что ввели название роли
                createdEvent = eventService.createNewEvent(chatId, foundEventType, eventRole, eventArgument, fullCommand, fromId, delete,reply,silent);
            }
        } catch (RoleException | EventException | CommandException e) {
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }

        String roleName = roleService.getRoleName(chatId, createdEvent.getRolePriority()).orElse("unknown role");
        MyEventType type = createdEvent.getType();

        boolean isEventCommand = createdEvent.getRolePriority()==CHAT_MANAGER_ROLE_PRIORITY;

        String message = "✅ %s(Вы) успешно создали %s.\n"
                .formatted(createMention(fromId), isEventCommand?"новую команду-событие":"новое событие") +
                "&#128218; Тип: %s (%s).\n".formatted(type.getCyrillicType(), type.getDescription());
                if(createdEvent.getArgument()!=null){
                    String arg = createdEvent.getArgument();
                    message+="&#128204; Аргумент: %s\n".formatted((type==WITH_SUBSCRIPTION||type==WITHOUT_SUBSCRIPTION)?createMention(Long.parseLong(arg)):arg);
                }
                if(!isEventCommand){
                    message+="&#128081; Воздействует на роль «%s» и ниже.\n".formatted(roleName);
                }
                if(createdEvent.getFullCommand()!=null){
                    message+="&#8618; Применяется команда: %s".formatted(createdEvent.getFullCommand());
                }
                if(!isEventCommand){
                    if(type.getAdvancedEventConfig().isCanBeAdvancedEvent()){
                        message+="\n\n❓Событие реагирует только на одно сообщение от участника. Если хотите больше сообщений, событие нужно расширить.";
                    }
                }else{
                    message+="\n\n❓Команда, которую вы указали, будет активироваться от имени того, кто спровоцировал событие, а не от имени создателя события (как в обычном событии).";

                }
        sendMessage.setText(message);
        vkChatClient.sendText(sendMessage);


    }

    @Autowired
    public void setRoleService(RoleService roleService) {
        this.roleService = roleService;
    }
}
