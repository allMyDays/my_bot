package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.cooldown.CooldownResult;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.command.HandleAdminChatCommandStatus;
import com.example.my_bot.exception.command.ForbiddenCommandForCurrentModeException;
import com.example.my_bot.exception.command.UnknownCommandException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.*;
import com.example.my_bot.service.chat.AdminChatActionService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.example.my_bot.enumeration.command.HandleAdminChatCommandStatus.MUST_BE_EXECUTED_IN_ADMIN_CHAT;
import static com.example.my_bot.enumeration.command.HandleAdminChatCommandStatus.NOT_ADMIN_CHAT;
import static com.example.my_bot.utils.ChatUtils.DEFAULT_CHAT_PREFIX;
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
    private final AdminChatActionService adminChatActionService;


    public void dispatch(CommandMessageDto commandMessage) throws ClientException, ApiException {

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);
        long fromId = commandMessage.getFromId();

        if(ChatUtils.isGroupId(fromId)||userService.getOrCreateUser(fromId).isBanned()) return;

        String commandName = commandMessage.getCommand().orElse(null);
        if(commandName == null) return;

        Long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        Optional<Character> chatPrefix = chatId==null
                ? Optional.of(DEFAULT_CHAT_PREFIX)
                : chatService.getChatPrefix(chatId);

        if(!commandMessage.isEventOrTimerMode()){   // в событиях/таймерах все команды обязаны быть без префикса
            commandName = validateAndCutCommandPrefix(chatPrefix.orElse(null), commandName).orElse(null);
            if(commandName==null) return;
        }

        Optional<Map.Entry<ChatCommand, Command>> commandData = commandRegistry.getCommandWithTheAnnotation(commandName);
        if(commandData.isEmpty()){
            if(commandMessage.isEventOrTimerMode()){
                throw new UnknownCommandException(commandName);
            }
        }
        else{
            ChatCommand mainCommand = commandData.get().getKey();
            Command cmdAnnotation = commandData.get().getValue();

            HandleAdminChatCommandStatus handleAdminChatCommandStatus=
                    adminChatActionService.handleAdminChatCommand(cmdAnnotation, UserInputResolver.replaceCommand(commandMessage.getUserText(), cmdAnnotation.mainCommandName()),commandMessage.getCommandRoutingData(), commandMessage.isEventOrTimerMode());

            if(handleAdminChatCommandStatus==NOT_ADMIN_CHAT||handleAdminChatCommandStatus==MUST_BE_EXECUTED_IN_ADMIN_CHAT){

                if(commandMessage.isEventOrTimerMode()&&!cmdAnnotation.eventable()){
                    throw new ForbiddenCommandForCurrentModeException(commandName);
                }
                if(cmdAnnotation.onlyForConversations()&&chatId==null){
                    sendMessage.setText(
                            "Команду «%s» можно использовать только в конференциях.".formatted(cmdAnnotation.mainCommandName())
                    );
                    vkChatClient.sendText(sendMessage);
                    return;
                }

                int userRolePriority = chatId==null
                        ? DefaultRole.MEMBER.getRolePriority()
                        : memberService.getMemberRolePriority(chatId, fromId);

                boolean canExecute = (chatId==null||
                        commandAccessService.checkCommandAuthorization(chatId, cmdAnnotation.mainCommandName(),userRolePriority,fromId));

                if(canExecute){
                    CooldownResult cooldownResult =
                            commandAccessService.checkCommandRateLimit(chatId, cmdAnnotation.mainCommandName(),userRolePriority,fromId);
                    if(!cooldownResult.canExecuteCommand()){
                        if(cooldownResult.canSendCDMessageToUser()){
                            sendMessage.setText(
                                    "Нельзя так часто использовать эту команду. Она станет вновь доступна %s(Вам) через %s"
                                            .formatted(createMention(fromId), formatDurationFromSeconds(cooldownResult.getLeftCDSeconds(), true))
                            );
                            vkChatClient.sendText(sendMessage);
                        }return;
                    }
                    mainCommand.execute(commandMessage);
                }
                else{
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

    private Optional<String> validateAndCutCommandPrefix(@Nullable Character chatPrefix, @NonNull String userCommand){

        boolean mustCutPrefix=true;
        if(chatPrefix!=null){
            // префикс включён - строгое соответствие префиксу
            if(userCommand.charAt(0)!=chatPrefix) return Optional.empty();
        }
        else{
            // префикс отключен - либо дефолтный префикс, либо без префикса
            if(userCommand.charAt(0)!= DEFAULT_CHAT_PREFIX){
                mustCutPrefix=false;
            }
        }
        if(mustCutPrefix){
            userCommand = userCommand.substring(1);
        }
        return Optional.of(userCommand);
    }

}
