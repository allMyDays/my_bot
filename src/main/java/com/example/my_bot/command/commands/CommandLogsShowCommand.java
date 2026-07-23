package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.CommandLogEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.command.CommandLogService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "логи", alternativeCommandNames = {"logs"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
@RequiredArgsConstructor
public class CommandLogsShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);
    private final CommandLogService commandLogService;
    private final MessageMapper messageMapper;

    private final ChatService chatService;
    private final GlobalUserService globalUserService;
    private VkChatClient vkChatClient;

    private final static int MAX_LOGS_AT_ONE_USAGE = 100;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException{

        String[] args = commandMessage.getFirstRowArguments();
        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        int logQuantity=0;

        if(args.length>=1&&isValidInteger(args[0])){
            logQuantity = Integer.parseInt(args[0]);
        }
        if(args.length==0||logQuantity> MAX_LOGS_AT_ONE_USAGE){
            logQuantity = MAX_LOGS_AT_ONE_USAGE;
        }

        List<CommandLogEntity> commandLogs = commandLogService.getLastNCommandLogs(dataBaseChatId, logQuantity);

        Map<Long, String> memberNamesMap = globalUserService.getUserFullNamesInRequiredCase(
                commandLogs.stream()
                        .map(CommandLogEntity::getFromId)
                        .collect(Collectors.toSet()),
                NameCase.NOMINATIVE
        );

        TimeZoneType chatTimeZone = chatService.getChatTimeZone(dataBaseChatId);

        StringBuilder sb = new StringBuilder("Последние %d команд, используемых в данном чате:\n".formatted(commandLogs.size()));

        int counter = 1;
        for(CommandLogEntity log: commandLogs){
            sb.append(
                    "%d. %s — %s(%s) [%s]\n"
                            .formatted(counter++, log.getCommandName(), createMention(log.getFromId()), memberNamesMap.get(log.getFromId()), TimeUtils.getFormattedStringDateTime(log.getCreatedAt(), chatTimeZone))
            );
        }
        if(!commandLogs.isEmpty()){
            sb.append("\nВремя использования команд указано по ").append(chatTimeZone.getStringType());
        }

        sendMessage.setText(sb.toString());
        vkChatClient.sendText(sendMessage);

        return SUCCESS;
    }
}
