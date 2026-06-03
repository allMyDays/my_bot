package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.cooldown.CooldownResult;
import com.example.my_bot.exception.command.ForbiddenCommandForCurrentModeException;
import com.example.my_bot.exception.command.UnknownCommandException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.*;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;


import static com.example.my_bot.utils.TextUtils.*;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;


@Component
@Slf4j
@RequiredArgsConstructor
public class CommandDispatcher {
    private final ChatService chatService;
    private final MemberService memberService;
    private final VkChatClient vkChatClient;
    private final CommandRegistry commandRegistry;
    private final CommandAccessService commandAccessService;
    private final GlobalUserService userService;
    private final MessageMapper messageMapper;


    public void dispatch(CommandMessageDto messageDto) throws ClientException, ApiException {

             long fromId = messageDto.getFromId();

             if(ChatUtils.isGroupId(fromId)||userService.getOrCreateUser(fromId).isBanned()){
                 return;
             }

             Optional<String> commandOptional = messageDto.getCommand();
             if(commandOptional.isEmpty()) return;
             String commandName = commandOptional.get();

             long chatId = messageDto.getChatId();

             Optional<Character> chatPrefix = chatService.getChatPrefix(chatId);

             if(!messageDto.isEventOrTimerMode()){   // в событиях/таймерах все команды обязаны быть без префикса
                 boolean mustCutPrefix=true;
                 if(chatPrefix.isPresent()){
                   if(commandName.charAt(0)!=chatPrefix.get()) return;
                 }else{
                    if(commandName.charAt(0)!=ChatUtils.DEFAULT_CHAT_PREFIX){
                     mustCutPrefix=false;
                    }
                 }
                 if(mustCutPrefix){
                   commandName = commandName.substring(1);
                 }
             }

        final String finalCommandName = commandName;
        Optional<ChatCommand> cmdOptional = commandRegistry.getCommand(finalCommandName);
        if(cmdOptional.isEmpty()){
            if(messageDto.isEventOrTimerMode()){
                throw new UnknownCommandException(finalCommandName);
            }
        }
        else {
            ChatCommand mainCommand = cmdOptional.get();
            Command cmdAnnotation = commandRegistry.getCommandAnnotation(finalCommandName)
                    .orElseThrow(()->new RuntimeException("Cannot find required init-annotation @Command for "+ finalCommandName));
            if(messageDto.isEventOrTimerMode()&&!cmdAnnotation.eventable()){
                throw new ForbiddenCommandForCurrentModeException(finalCommandName);
            }

            int userRolePriority = memberService.getMemberRolePriority(chatId, fromId);

            boolean canExecute = commandAccessService.checkCommandAuthorization(
                    chatId, cmdAnnotation.mainCommandName(),userRolePriority,fromId);

            SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, messageDto);
            if(canExecute){
                CooldownResult cooldownResult = commandAccessService.checkCommandRateLimit(
                        chatId, cmdAnnotation.mainCommandName(),userRolePriority,fromId);
                if(!cooldownResult.canExecuteCommand()){
                  if(cooldownResult.canSendCDMessageToUser()){
                    sendMessage.setText(
                            "Нельзя так часто использовать эту команду. Она станет вновь доступна %s(Вам) через %s"
                            .formatted(createMention(fromId), formatDurationFromSeconds(cooldownResult.getLeftCDSeconds(), true))
                    );
                    vkChatClient.sendText(sendMessage);
                  }return;
                }
                mainCommand.execute(messageDto);
            }else{
                if(!chatService.isSilentRestriction(chatId)){
                sendMessage.setText(
                        "Команда «%s» недоступна %s(Вам) для использования."
                                .formatted(cmdAnnotation.mainCommandName(),createMention(fromId))
                );
                vkChatClient.sendText(sendMessage);
                }
            }
        }
    }

}
