package com.example.my_bot.command.commands.event;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.exception.timer.TimerException;
import com.example.my_bot.service.event.EventService;
import com.example.my_bot.service.timer.TimerService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.ChatUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "удалитьсобытие", alternativeCommandNames = {"удалитьивент", "remevent"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class EventDeleteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final EventService eventService;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long peerId = messageDto.getPeerId();

        if(args.length<1){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId,true);
            return;
        }if(!isValidInteger(args[0])){
            vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId,true);
            return;

        } List<EventDto> events = eventService.getEventsSortedByIdInIncreasingOrder(chatId);

        int outerEventId = Integer.parseInt(args[0]);

        if(outerEventId<1||outerEventId>events.size()){
            vkChatClient.sendText("Не найдено таймера с таким ID.",peerId,true);
            return;
        }
        try{
            eventService.deleteEventById(events.get(outerEventId-1).getId(), messageDto.getFromId());
        }catch (EventException | RoleException e){
          vkChatClient.sendText(e.getMessage(), peerId,true);
          return;
        } vkChatClient.sendText("✅Событие с ID %d было успешно удалёно.".formatted(outerEventId),peerId, true);
    }
}
