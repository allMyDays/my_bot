package com.example.my_bot.mapper;

import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.mapstruct.Mapper;
import com.vk.api.sdk.objects.messages.Message;

import static com.example.my_bot.utils.ChatUtils.extractConversationId;

@Mapper(componentModel = "spring")
public abstract class CommandMapper {



    public CommandMessageDto toCommandMessageDto(long chatId, @NonNull Message message){

        CommandMessageDto commandMessageDto = toCommandMessageDto(chatId, message.getFromId(), message.getText(),false);
        commandMessageDto.setPeerId(message.getPeerId());
        if(message.getReplyMessage()!=null){
            commandMessageDto.setReplyMessageFromId(message.getReplyMessage().getFromId());
        }if(message.getFwdMessages()!=null){
            commandMessageDto.setFwdMessagesFromIds(
                    message.getFwdMessages().stream()
                     .map(ForeignMessage::getFromId)
                     .toList()
            );
        } return commandMessageDto;

    }
    public CommandMessageDto toCommandMessageDto(long chatId, long fromId, @Nullable String fullMessage, boolean isTimerMode){

        CommandMessageDto commandMessageDto = new CommandMessageDto();
        commandMessageDto.setUserMessage(fullMessage);
        commandMessageDto.setFromId(fromId);
        commandMessageDto.setChatId(chatId);
        commandMessageDto.setPeerId(ChatUtils.convertToPeerId(chatId));
        commandMessageDto.setTimerMode(isTimerMode);

        if(fullMessage!=null){
            fullMessage = fullMessage.trim();
            String[] rows = fullMessage.split("\\n+");
            String[] commandAndArgs = UserInputResolver.splitFullCommand(rows[0]);
            commandMessageDto.setCommand(commandAndArgs[0].toLowerCase().trim());
            if(commandAndArgs.length==2){
               commandMessageDto.setFirstRowArguments(commandAndArgs[1].split(" +"));
            }
            commandMessageDto.setAllRows(rows);
        }
        return commandMessageDto;

    }




}
