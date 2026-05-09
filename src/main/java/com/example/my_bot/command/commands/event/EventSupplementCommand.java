package com.example.my_bot.command.commands.event;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.resolver.UserInputResolver;
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
import static com.example.my_bot.utils.TextUtils.isValidInteger;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

@Slf4j
@Command(mainCommandName = "дополнитьивент", alternativeCommandNames = {"supplementevent"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class EventSupplementCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60);

    private final VkChatClient vkChatClient;

    private final EventService eventService;

    private final ChatService chatService;

    private final UserInputResolver userInputResolver;

    private final static String ACTION_LIMIT_ARGUMENT = "лимитдействия";

    private final static String WORK_TIME_ARGUMENT = "времяработы";

    private final static String COOLDOWN_ARGUMENT = "кулдаун";

    private final static String EXCEPTIONAL_ARGUMENT = "исключение";

    private final static String EXCEPTIONAL_REMOVE_ARGUMENT = "удалить";

    private final static Pattern WORK_TIME_PATTERN = Pattern.compile("(([01][0-9]|2[0-3]):[0-5][0-9])-(([01][0-9]|2[0-3]):[0-5][0-9])");


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long peerId = messageDto.getPeerId();
        long fromId = messageDto.getFromId();

        if(args.length<3){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId,true);
            return;
        }
        if(!isValidInteger(args[0])){
            vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId, true);
            return;
        }
        int outerEventId = Integer.parseInt(args[0]);
        List<EventDto> events = eventService.getEventsSortedByIdInIncreasingOrder(chatId);

        if(outerEventId<1||outerEventId>events.size()){
            vkChatClient.sendText("Не найдено события с таким ID.",peerId,true);
            return;
        }
        long eventEntityId = events.get(outerEventId-1).getId();


        EventDto editedEvent;
        String message;

        switch(args[1].toLowerCase()){
            case WORK_TIME_ARGUMENT -> {  // !дополнитьивент 1 времяработы 23:00-08:00
                Matcher matcher = WORK_TIME_PATTERN.matcher(args[2]);
                if(matcher.find()){
                    LocalTime start = TimeUtils.parseTimeOfDay(matcher.group(1)).orElse(null);
                    LocalTime end = TimeUtils.parseTimeOfDay(matcher.group(3)).orElse(null);
                    try{
                        editedEvent = eventService.setDailyWorkTime(eventEntityId, start, end,fromId);
                    }catch (EventException | RoleException e){
                        vkChatClient.sendText(e.getMessage(), peerId,true);
                        return;
                    }
                    message = "✅Теперь событие №%d («%s») будет работать каждый день с %s до %s %s."
                            .formatted(outerEventId,editedEvent.getType().getDescription(), editedEvent.getStartDayTime(),editedEvent.getEndDayTime(),chatService.getChatTimeZone(chatId).getStringType());

                    vkChatClient.sendText(message,peerId, true);
                }else{
                    vkChatClient.sendText("Вы ввели некорректный аргумент диапазона, пример: 23:00-08:00",peerId, true);
                }
            }
            case EXCEPTIONAL_ARGUMENT -> {
                // !дополнитьивент 1 исключение @durov
                // !дополнитьивент 1 исключение удалить @durov

                boolean remove = false;
                if(args[2].equalsIgnoreCase(EXCEPTIONAL_REMOVE_ARGUMENT)){
                    if(args.length<4){
                        vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId,true);
                        return;
                    } remove = true;
                }
                Optional<Long> memberId = userInputResolver.getMemberIdByStringInput(args[remove?3:2]);
                if(memberId.isEmpty()){
                    vkChatClient.sendText(MEMBER_LINK_IS_NOT_CORRECT, peerId, true);
                    return;
                }
                try{
                    if(remove){
                        editedEvent = eventService.removeMemberFromExceptional(eventEntityId, memberId.get(),fromId);
                    }else{
                        editedEvent = eventService.addMemberToExceptional(eventEntityId, memberId.get(),fromId);
                    }
                }catch (EventException | RoleException | CommandException | MemberException e){
                    vkChatClient.sendText(e.getMessage(), peerId,true);
                    return;
                }
                message = "✅Вы успешно "+(remove
                        ? "удалили данного участника из исключения события  №%d («%s»)"
                        : "добавили данного участника в исключения для события  №%d («%s»). Теперь событие не будет на него активироваться ни при каком условии. "
                );
                message = message.formatted(outerEventId, editedEvent.getType().getDescription());
                vkChatClient.sendText(message, peerId,true);
            }
            case ACTION_LIMIT_ARGUMENT -> {      // !дополнитьивент 1 лимитдействия 100 2 часа
                if(args.length<5){
                    vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId,true);
                    return;
                }if(!isValidInteger(args[2])){
                    vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId, true);
                    return;
                }
                Optional<Long> timePeriodInSeconds =  TimeUtils.toSecondsFromString(args[3],args[4]);
                if(timePeriodInSeconds.isEmpty()){
                    vkChatClient.sendText(INVALID_TIME_PERIOD_MESSAGE, peerId,true);
                    return;
                }
                try{
                    editedEvent = eventService.setAETimePeriodAndMaxUsage(eventEntityId,timePeriodInSeconds.get(),Integer.parseInt(args[2]),fromId);
                }catch (EventException | RoleException e){
                    vkChatClient.sendText(e.getMessage(), peerId,true);
                    return;
                }
                message = "✅Вы успешно добавили событию №%d («%s») лимит действия в %d за %s\n❓Теперь команда, указанная в этом событии, будет активироваться только по достижении участником данного лимита за данный период времени (для события «%s»)."
                        .formatted(outerEventId,editedEvent.getType().getDescription(), editedEvent.getAEMaxUsage(),formatDurationFromSeconds(editedEvent.getAEPeriodInSeconds(),true),editedEvent.getType().getDescription());

                vkChatClient.sendText(message,peerId, true);

            }
            case COOLDOWN_ARGUMENT -> {  // !дополнитьивент 1 кулдаун 1 час
                if(args.length<4){
                    vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId,true);
                    return;
                }
                Optional<Long> timePeriodInSeconds =  TimeUtils.toSecondsFromString(args[2],args[3]);
                if(timePeriodInSeconds.isEmpty()){
                    vkChatClient.sendText(INVALID_TIME_PERIOD_MESSAGE, peerId,true);
                    return;
                }
                try{
                    editedEvent = eventService.setCDTimePeriod(eventEntityId,timePeriodInSeconds.get(),fromId);
                }catch (EventException | RoleException e){
                    vkChatClient.sendText(e.getMessage(), peerId,true);
                    return;
                }
                int cdPeriod = editedEvent.getCDPeriodInSeconds();
                String eventDesc = editedEvent.getType().getDescription();
                if(cdPeriod==0){
                    message= "✅Вы успешно отключили кулдаун срабатывания для события №%d («%s»)."
                            .formatted(outerEventId,eventDesc);
                }else{
                    message = ("✅Вы успешно добавили событию №%d («%s») кулдаун срабатывания.\n❓Теперь команда, указанная в событии, будет активироваться на одного участника максимум один раз в %s")
                            .formatted(outerEventId,eventDesc,formatDurationFromSeconds(editedEvent.getCDPeriodInSeconds(),true));
                }
                vkChatClient.sendText(message,peerId, true);
            }
            default -> {
                vkChatClient.sendText("Вы ввели несуществующий тип для редактирования.", peerId,true);
            }
        }
    }
}
