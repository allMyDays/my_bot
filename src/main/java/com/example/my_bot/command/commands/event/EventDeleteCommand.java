package com.example.my_bot.command.commands.event;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.event.EventService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "удалитьсобытие", alternativeCommandNames = {"удалитьивент", "remevent"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class EventDeleteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final EventService eventService;

    private final MessageMapper messageMapper;


    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        if(args.length<1){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(!isValidInteger(args[0])){
            sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;

        }List<EventDto> events = eventService.getEventsSortedByIdInIncreasingOrder(chatId);

        int outerEventId = Integer.parseInt(args[0]);

        if(outerEventId<1||outerEventId>events.size()){
            sendMessage.setText("Не найдено события с таким ID.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        try{
            eventService.deleteEventById(events.get(outerEventId-1).getId(), commandMessage.getFromId());
        }catch (EventException | RoleException | MemberException e){
          sendMessage.setText(e.getMessage());
          vkChatClient.sendText(sendMessage);
          return;
        }
        sendMessage.setText("✅Событие с ID %d было успешно удалёно.".formatted(outerEventId));

        vkChatClient.sendText(sendMessage);
    }
}
