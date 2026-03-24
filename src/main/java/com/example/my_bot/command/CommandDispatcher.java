package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.cooldown.CooldownResult;
import com.example.my_bot.entity.UserEntity;
import com.example.my_bot.service.*;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.utils.ChatUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.formatDuration;


@Component
@Slf4j
@RequiredArgsConstructor
public class CommandDispatcher {
    private final ChatService chatService;
    private final MemberService memberService;
    private final VkChatClient vkChatClient;
    private final CommandRegistry commandRegistry;
    private final CommandAccessService commandAccessService;
    private final UserService userService;




    public void dispatch(CommandMessageDto commandMessage) throws ClientException, ApiException {

             long fromId = commandMessage.getFromId();

             UserEntity userEntity = userService.getOrCreateUser(fromId);

             if(userEntity.isBanned()){
                 return;
             }

             Optional<String> commandOptional = commandMessage.getCommand();
             if(commandOptional.isEmpty()) return;
             String commandName = commandOptional.get();

             long chatId = commandMessage.getChatId();

             ChatDetailsDto chatDetails = chatService.getCachedChatDetails(chatId, true);

             Optional<Character> chatPrefix = chatDetails.getOptionalPrefix();

             boolean mustCutPrefix=true;

             if(chatPrefix.isPresent()){
                 if(commandName.charAt(0)!=chatPrefix.get()) return;
             }else{
                 if(commandName.charAt(0)!=DEFAULT_CHAT_PREFIX){
                     mustCutPrefix=false;

                 }
             }
              if(mustCutPrefix){
                  commandName = commandName.substring(1);
              }


        Optional<ChatCommand> cmdOptional = commandRegistry.getCommand(commandName);
        if (cmdOptional.isPresent()) {
            ChatCommand mainCommand = cmdOptional.get();
            Command cmdAnnotation = commandRegistry.getCommandAnnotation(commandName)
                    .orElseThrow(()->new RuntimeException("Cannot find required init-annotation @Command"));

            int userRolePriority = memberService.getCachedMemberRolePriority(chatId, fromId);

            boolean canExecute = commandAccessService.checkCommandAuthorization(
                    chatId, cmdAnnotation.mainCommandName(),userRolePriority,fromId,false);

            if(canExecute){
                CooldownResult cooldownResult = commandAccessService.checkCommandRateLimit(
                        chatId, cmdAnnotation.mainCommandName(),userRolePriority,fromId);
                if(!cooldownResult.canExecuteCommand()){
                  if(cooldownResult.canSendCDMessageToUser()){
                    vkChatClient.sendText(chatId,
                            "Нельзя так часто использовать эту команду. Она станет вновь доступна %s(Вам) через %s"
                                    .formatted(createMention(fromId),formatDuration(cooldownResult.getLeftCDSeconds(), true)),
                            true);
                  }return;
                }
                mainCommand.execute(commandMessage);
            }else{
                if(!chatDetails.isSilentRestriction()){
                vkChatClient.sendText(
                        chatId,
                        "Команда «%s» недоступна %s(Вам) для использования.".formatted(cmdAnnotation.mainCommandName(),createMention(fromId)),
                        false);
                }
            }
        }
    }

}
