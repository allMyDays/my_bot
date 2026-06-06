package com.example.my_bot.mapper;

import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.vk.VkMessage;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class MessageMapper {



    public CommandMessageDto toCommandMessageDto(long chatId, @NonNull VkMessage message, boolean replyToMessageId){

        CommandMessageDto commandMessageDto = toCommandMessageDto(chatId, message.getFromId(), message.getText(), message.getConversationMessageId(),replyToMessageId,false);
        commandMessageDto.setPeerId(message.getPeerId());
        if(message.getReplyMessage()!=null){
            commandMessageDto.setReplyOrFwdMessages(List.of(message.getReplyMessage()));
        }else if(message.getFwdMessages()!=null){
            commandMessageDto.setReplyOrFwdMessages(message.getFwdMessages());
        } return commandMessageDto;

    }
    public CommandMessageDto toCommandMessageDto(long chatId, long fromId, @Nullable String fullMessage, int conversationMessageId, boolean replyToMessageId, boolean eventOrTimerMode){

        CommandMessageDto commandMessageDto = new CommandMessageDto();
        commandMessageDto.setUserText(fullMessage);
        commandMessageDto.setFromId(fromId);
        commandMessageDto.setChatId(chatId);
        commandMessageDto.setPeerId(ChatUtils.convertToPeerId(chatId));
        commandMessageDto.setEventOrTimerMode(eventOrTimerMode);
        commandMessageDto.setConversationMessageId(conversationMessageId);
        commandMessageDto.setReplyToMessageId(replyToMessageId);

        if(fullMessage!=null){
            fullMessage = fullMessage.trim();
            String[] rows = fullMessage.split("\\n+");
            String[] commandAndArgs = UserInputResolver.splitFullCommandIntoTwoElements(rows[0]);
            commandMessageDto.setCommand(commandAndArgs[0].toLowerCase().trim());
            if(commandAndArgs.length==2){
               commandMessageDto.setFirstRowArguments(commandAndArgs[1].split(" +"));
            }
            commandMessageDto.setAllRows(rows);
        }
        return commandMessageDto;

    }

    @Mapping(target = "text", source = "text")
    public abstract SendMessageDto toSendMessageDto(String text, @NonNull CommandMessageDto commandMessageDto);

    @Mapping(target = "ableMentions", source = "ableMentions")
    @Mapping(target = "text", source = "text")
    public abstract SendMessageDto toSendMessageDto(String text, boolean ableMentions, @NonNull CommandMessageDto commandMessageDto);

    @Mapping(target = "text", source = "text")
    @Mapping(target = "peerId", source = "peerId")
    public abstract SendMessageDto toSendMessageDto(String text, long peerId);








}
