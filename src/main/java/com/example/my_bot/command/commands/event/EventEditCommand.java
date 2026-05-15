package com.example.my_bot.command.commands.event;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TextUtils.isValidInteger;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

@Slf4j
@Command(mainCommandName = "редивент", alternativeCommandNames = {"editevent"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class EventEditCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60);

    private final VkChatClient vkChatClient;

    private final EventService eventService;

    private final ChatService chatService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService globalUserService;

    private final MessageMapper messageMapper;

    private final static String REMOVE_ARGUMENT = "удалить";

    private final static String ACTION_LIMIT_ARGUMENT = "лимитдействия";

    private final static String WORK_TIME_ARGUMENT = "времяработы";

    private final static String COOLDOWN_ARGUMENT = "кулдаун";

    private final static String EXCEPTIONAL_ARGUMENT = "исключение";

    private final static String NEW_MEMBERS_ARGUMENT = "дляновичков";

    private final static String PERSONAL_EVENT_ARGUMENT = "толькодля";

    private final static Pattern WORK_TIME_PATTERN = Pattern.compile("(([01][0-9]|2[0-3]):[0-5][0-9])-(([01][0-9]|2[0-3]):[0-5][0-9])");


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long fromId = messageDto.getFromId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",messageDto);

        if(notEnoughArgs(args,3,sendMessage)) return;

        Integer outerEventId = parseInt(args[0], sendMessage);
        if(outerEventId == null) return;

        List<EventDto> events = eventService.getEventsSortedByIdInIncreasingOrder(chatId);

        if(outerEventId<1||outerEventId>events.size()){
            send(sendMessage, "Не найдено события с таким ID.");
            return;
        }
        long eventEntityId = events.get(outerEventId-1).getId();

        EventDto editedEvent;

        switch(args[1].toLowerCase()){
            case WORK_TIME_ARGUMENT -> {  // !редивент 1 времяработы 23:00-08:00
                Matcher matcher = WORK_TIME_PATTERN.matcher(args[2]);
                if(matcher.find()){
                    LocalTime start = TimeUtils.parseTimeOfDay(matcher.group(1)).orElse(null);
                    LocalTime end = TimeUtils.parseTimeOfDay(matcher.group(3)).orElse(null);
                    try{
                        editedEvent = eventService.setDailyWorkTime(eventEntityId, start, end,fromId);
                    }catch (EventException | RoleException | MemberException e){
                        send(sendMessage, e.getMessage());
                        return;
                    }
                    send(sendMessage, "✅Теперь событие №%d («%s») будет работать каждый день с %s до %s %s."
                                    .formatted(outerEventId,editedEvent.getType().getDescription(), editedEvent.getStartDayTime(),editedEvent.getEndDayTime(),chatService.getChatTimeZone(chatId).getStringType())
                    );
                }else{
                    send(sendMessage, "Вы ввели некорректный аргумент диапазона, пример: 23:00-08:00");
                }
            }
            case EXCEPTIONAL_ARGUMENT -> {
                // !редивент 1 исключение @durov
                // !редивент 1 исключение удалить @durov

                boolean remove = args[2].equalsIgnoreCase(REMOVE_ARGUMENT);
                if(remove&&notEnoughArgs(args,4,sendMessage)) return;

                Long memberId = parseMember(args[remove?3:2],sendMessage);
                if(memberId==null) return;
                try{
                    if(remove){
                        editedEvent = eventService.removeMemberFromExceptional(eventEntityId, memberId,fromId);
                    }else{
                        editedEvent = eventService.addMemberToExceptional(eventEntityId, memberId,fromId);
                    }
                }catch (EventException | RoleException | CommandException | MemberException e){
                    send(sendMessage, e.getMessage());
                    return;
                }
                send(sendMessage,
                        ("✅Вы успешно "+(remove
                                ? "удалили данного участника из исключения события  №%d («%s»)"
                                : "добавили данного участника в исключения для события  №%d («%s»). Теперь событие не будет на него активироваться ни при каком условии. "
                        )).formatted(outerEventId, editedEvent.getType().getDescription())
                );
            }
            case PERSONAL_EVENT_ARGUMENT -> {     // !редивент 1 толькодля @durov

                Long memberId = parseMember(args[2],sendMessage);
                if(memberId==null) return;
                try{
                    editedEvent = eventService.makeEventPersonal(eventEntityId, memberId, fromId);
                }catch (EventException | RoleException | CommandException | MemberException e){
                    send(sendMessage, e.getMessage());
                    return;
                }
                Long memberToTrigger = editedEvent.getMemberToTrigger();
                send(sendMessage,
                        "✅Вы успешно сделали событие №%d («%s») персональным для %s(%s). \n❓Теперь событие будет реагировать только на этого участника."
                        .formatted(outerEventId, editedEvent.getType().getDescription(),createMention(memberToTrigger),globalUserService.getUserNameInRequiredCase(memberToTrigger, NameCase.GENITIVE).orElse("этого участника")));
            }
            case ACTION_LIMIT_ARGUMENT -> {      // !редивент 1 лимитдействия 100 2 часа
                if(notEnoughArgs(args,5,sendMessage)) return;
                Integer maxUsage = parseInt(args[2], sendMessage);
                if(maxUsage == null) return;
                Long timePeriod = parsePeriod(args[3],args[4], sendMessage);
                if (timePeriod==null) return;
                try{
                    editedEvent = eventService.setAETimePeriodAndMaxUsage(eventEntityId,timePeriod,maxUsage,fromId);
                }catch (EventException | RoleException | MemberException e){
                    send(sendMessage, e.getMessage());
                    return;
                }
                send(sendMessage,
                        "✅Вы успешно добавили событию №%d («%s») лимит действия в %d за %s\n❓Теперь команда, указанная в этом событии, будет активироваться только по достижении участником данного лимита за данный период времени (для события «%s»)."
                         .formatted(outerEventId,editedEvent.getType().getDescription(), editedEvent.getAEMaxUsage(),formatDurationFromSeconds(editedEvent.getAEPeriodSec(),true),editedEvent.getType().getDescription())
                );

            }
            case COOLDOWN_ARGUMENT -> {  // !редивент 1 кулдаун 1 час
                if(notEnoughArgs(args,4,sendMessage)) return;
                Long timePeriod = parsePeriod(args[2],args[3], sendMessage);
                if(timePeriod==null) return;

                try{
                    editedEvent = eventService.setCDTimePeriod(eventEntityId,timePeriod,fromId);
                }catch (EventException | RoleException | MemberException e){
                    send(sendMessage, e.getMessage());
                    return;
                }
                int cdPeriod = editedEvent.getCDPeriodSec();
                String eventDesc = editedEvent.getType().getDescription();

                send(sendMessage,"✅Вы успешно "+
                        (cdPeriod==0
                                ? "отключили кулдаун срабатывания для события №%d («%s»).".formatted(outerEventId,eventDesc)
                                : "добавили событию №%d («%s») кулдаун срабатывания.\n❓Теперь команда, указанная в событии, будет активироваться на одного участника максимум один раз в %s".formatted(outerEventId,eventDesc,formatDurationFromSeconds(editedEvent.getCDPeriodSec(),true)
                        ))
                );
            }
            case NEW_MEMBERS_ARGUMENT -> {  // !редивент 1 дляновичков 26 часов

                if(notEnoughArgs(args,4,sendMessage)) return;
                Long timePeriod = parsePeriod(args[2],args[3], sendMessage);
                if(timePeriod==null) return;
                try{
                    editedEvent = eventService.setNewMembersTimePeriod(eventEntityId,timePeriod,fromId);
                }catch (EventException | RoleException e){
                    send(sendMessage, e.getMessage());
                    return;
                }
                send(sendMessage,
                        "✅Теперь событие №%d («%s») будет срабатывать только на новых участников, впервые появившихся в чате менее чем %s назад."
                                .formatted(outerEventId,editedEvent.getType().getDescription(),formatDurationFromSeconds(editedEvent.getNewMembersPeriodSec(),true)));
            }
            default -> {
                send(sendMessage, "Вы ввели несуществующий тип для редактирования.");
            }
        }
    }

    private void send(SendMessageDto dto, String text) throws ClientException, ApiException{
        dto.setText(text);
        vkChatClient.sendText(dto);
    }

    private boolean notEnoughArgs(String[] args, int required, SendMessageDto dto) throws ClientException, ApiException{
        if(args.length<required){
            send(dto, NOT_ENOUGH_ARGUMENTS_MESSAGE);
            return true;
        }
        return false;
    }
    private Integer parseInt(String value, SendMessageDto dto) throws ClientException, ApiException{
        if(!isValidInteger(value)){
            send(dto, NOT_VALID_INTEGER_MESSAGE);
            return null;
        }
        return Integer.parseInt(value);
    }

    private Long parsePeriod(String value, String type, SendMessageDto dto) throws ClientException, ApiException {
        Optional<Long> period = TimeUtils.toSecondsFromString(value, type);
        if(period.isEmpty()){
            send(dto, INVALID_TIME_PERIOD_MESSAGE);
            return null;
        }
        return period.get();
    }

    private Long parseMember(String input, SendMessageDto dto) throws ClientException, ApiException{
        Optional<Long> member = userInputResolver.getMemberIdByStringInput(input);
        if(member.isEmpty()){
            send(dto, MEMBER_LINK_IS_NOT_CORRECT);
            return null;
        }
        return member.get();
    }

}


