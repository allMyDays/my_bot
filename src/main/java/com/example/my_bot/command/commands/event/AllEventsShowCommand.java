package com.example.my_bot.command.commands.event;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.enumeration.event.EventArgumentType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.sql.Time;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.utils.ChatUtils.CHAT_MANAGER_ROLE_PRIORITY;
import static com.example.my_bot.utils.TextUtils.*;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "события", alternativeCommandNames = {"ивенты","events"}, defaultRole = MODERATOR, eventable = true)
public class AllEventsShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private final EventService eventService;

    private final ChatService chatService;

    private final GlobalUserService globalUserService;

    private final MessageMapper messageMapper;

    private VkChatClient vkChatClient;

    private RoleService roleService;


    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        String[] args = messageDto.getFirstRowArguments();
        StringBuilder sb = new StringBuilder();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",messageDto);

        if(args.length>=1&&args[0].equalsIgnoreCase("доступные")){
            sb.append("Вам доступно %d событий для создания:\n\n".formatted(MyEventType.values().length));
            int counter = 1;
            for(MyEventType myEventType: MyEventType.values()){
                sb.append("%d. (%s) — %s. %s.\n".formatted(
                        counter++, myEventType.getCyrillicType(), myEventType.getDescription(),myEventType.getArgumentType()==EventArgumentType.NONE?"Аргумент не требуется":"Требуется аргумент")
                );
            }
            sendMessage.setText(sb.toString());
            vkChatClient.sendText(sendMessage);
            return;
        }

        long chatId = messageDto.getChatId();
        String chatTimeZone = chatService.getChatTimeZone(chatId).getStringType();

        List<EventDto> events = eventService.getEventsSortedByIdInIncreasingOrder(chatId);
        sb.append("В чате установлено (%d/%d) событий:\n\n"
                .formatted(events.size(), eventService.getMaxEvents())
        );

        Map<Integer, EventDto> eventCommandsMap = new HashMap<>();
        int counter=0;
        for(EventDto currentEvent: events){
            counter++;
            if(eventService.isEventBeingACommandEvent(currentEvent)){
                // созданная команда-событие
                eventCommandsMap.put(counter, currentEvent);
                continue;
            }

            MyEventType type = currentEvent.getType();
            sb.append("%s(%d). ".formatted(createMention(currentEvent.getCreatorId()), counter));

            String forRoleOrMember;
            Long memberToTrigger = currentEvent.getMemberToTrigger();
            if(memberToTrigger!=null){  // личное событие
                forRoleOrMember = "%s(%s)"
                        .formatted(createMention(memberToTrigger), globalUserService.getUserNameInRequiredCase(memberToTrigger, NameCase.GENITIVE).orElse("этого участника"));
            }else{
                String roleName = roleService.getRoleName(chatId, currentEvent.getRolePriority()).orElse(null);
                forRoleOrMember = "роли %s и ниже".formatted(roleName!=null?"«"+roleName+"»":"с приоритетом "+currentEvent.getRolePriority());
            }

            String desc = currentEvent.getType().getDescription();
            String simpleOrAdvancedEvent = currentEvent.getAEMaxUsage()==null
                    ? "событии «%s»".formatted(desc)
                    : "достижении лимита действия «%s» в %d за %s".formatted(desc,currentEvent.getAEMaxUsage(),formatDurationFromSeconds(currentEvent.getAEPeriodSec(), true));

            sb.append(addRequiredActionEmojis(currentEvent));
            sb.append("Выполнение команды «%s» при %s для %s."
                    .formatted(currentEvent.getFullCommand()==null?"none":currentEvent.getFullCommand(),simpleOrAdvancedEvent, forRoleOrMember));

            if(currentEvent.getArgument()!=null){
                String argView = currentEvent.getArgument();

                switch (type){
                    case WITH_SUBSCRIPTION, WITHOUT_SUBSCRIPTION->{
                        argView = createMention(Long.parseLong(argView));
                    }
                    case SHORT_MESSAGE -> {
                        argView +=" (символов)";
                    }
                    case SHORT_VOICE_MESSAGE, LONG_VOICE_MESSAGE -> {
                        argView +=" (длительность в сек.)";
                    }
                    case REACTION_FILTER -> {
                        argView +=" (ID реакции)";
                    }
                }
                sb.append(" Аргумент: ").append(argView);
            } sb.append("\n");
            if(currentEvent.getStartDayTime()!=null){
                sb.append("\uD83D\uDD57Работает ежедневно с %s до %s %s.\n"
                        .formatted(currentEvent.getStartDayTime(), currentEvent.getEndDayTime(), chatTimeZone)
                );
            }
            Integer cd = currentEvent.getCDPeriodSec();
            if(cd!=null){
                sb.append("⏳Кулдаун: ").append(cd == 0
                        ? "отключён"
                        : "наказывает участника максимум 1 раз в %s".formatted(formatDurationFromSeconds(cd, true))
                ).append("\n");
            }
            if(!currentEvent.getExceptionalMembers().isEmpty()){
                AtomicInteger atomicInteger = new AtomicInteger();
                String members = currentEvent.getExceptionalMembers().stream()
                        .map(m->"%s(%s)".formatted(createMention(m), atomicInteger.incrementAndGet()))
                        .collect(Collectors.joining(", "));

                sb.append("❌Не реагирует на: ").append(members).append("\n");
            }
            if(currentEvent.getNewMembersPeriodSec()!=null){
                sb.append("⌚Только для новичков, находившихся в чате менее чем ")
                        .append(TimeUtils.formatDurationFromSeconds(currentEvent.getNewMembersPeriodSec(), true))
                        .append("\n");
            }
        }

        if(!eventCommandsMap.isEmpty()){
            sb.append("\n\nВ чате установлено (%d) специальных команд-событий:\n".formatted(eventCommandsMap.size()));
            eventCommandsMap.forEach((key,event)->{
                String command = event.getFullCommand();
                String arg = event.getArgument();
                sb.append("%d. %sВыполнение системной команды «%s» %s для события «%s».\n"
                        .formatted(
                                key,
                                addRequiredActionEmojis(event),
                                command==null?"none":command,
                                arg==null?"":"при аргументе «%s»".formatted(arg),
                                event.getType().getDescription()
                        )
                );
            });
        }
        sendMessage.setText(sb.toString());
        vkChatClient.sendText(sendMessage);

    }
    private String addRequiredActionEmojis(@NonNull EventDto eventDto){
        return (eventDto.isDelete()?"\uD83D\uDDD1":"")+
                (eventDto.isReply()?"↪":"")+
                (eventDto.isSilent()?"\uD83D\uDD15":"");
    }

    @Autowired
    public void setRoleService(RoleService roleService) {
        this.roleService = roleService;
    }
}
