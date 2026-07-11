package com.example.my_bot.mapper;

import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.vk.mapping.message.VkMessage;
import com.vk.api.sdk.client.actors.GroupActor;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class MessageMapper {



    public CommandMessageDto toCommandMessageDto(@NonNull CommandRoutingData commandRoutingData, @NonNull VkMessage message, boolean replyToMessageId){

        CommandMessageDto commandMessageDto = toCommandMessageDto(commandRoutingData, message.getFromId(), message.getText(), message.getConversationMessageId(),replyToMessageId,false);
        if(message.getReplyMessage()!=null){
            commandMessageDto.setReplyOrFwdMessages(List.of(message.getReplyMessage()));
        }
        else if(message.getFwdMessages()!=null){
            commandMessageDto.setReplyOrFwdMessages(message.getFwdMessages());
        }
        return commandMessageDto;

    }
    public CommandMessageDto toCommandMessageDto(@NonNull CommandRoutingData commandRoutingData, long fromId, @Nullable String fullMessage, int conversationMessageId, boolean replyToMessageId, boolean eventOrTimerMode){
        return toCommandMessageDto(commandRoutingData, fromId, fullMessage, conversationMessageId, replyToMessageId, eventOrTimerMode, false);
    }

    public CommandMessageDto toCommandMessageDto(@NonNull CommandRoutingData commandRoutingData, long fromId, @Nullable String fullMessage, int conversationMessageId, boolean replyToMessageId, boolean eventOrTimerMode, boolean doNotSendTheMessage){

        CommandMessageDto commandMessageDto = new CommandMessageDto();
        commandMessageDto.setUserText(fullMessage);
        commandMessageDto.setFromId(fromId);
        commandMessageDto.setCommandRoutingData(commandRoutingData);
        commandMessageDto.setEventOrTimerMode(eventOrTimerMode);
        commandMessageDto.setConversationMessageId(conversationMessageId);
        commandMessageDto.setReplyToMessageId(replyToMessageId);
        commandMessageDto.setDoNotSendTheMessage(doNotSendTheMessage);

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
    @Mapping(target = "responsePeerId", source = "commandMessageDto.commandRoutingData.responsePeerId")
    @Mapping(target = "responderBot", source = "commandMessageDto.commandRoutingData.responderBot")
    @Mapping(target = "dataBaseChatId", source = "commandMessageDto.commandRoutingData.dataBaseChatId")
    public abstract SendMessageDto toSendMessageDto(@NonNull String text, @NonNull CommandMessageDto commandMessageDto);

    public SendMessageDto toSendMessageDto(@NonNull CommandMessageDto commandMessageDto){
        return toSendMessageDto("", commandMessageDto);
    }

    @Mapping(target = "ableMentions", source = "ableMentions")
    @Mapping(target = "text", source = "text")
    @Mapping(target = "responsePeerId", source = "commandMessageDto.commandRoutingData.responsePeerId")
    @Mapping(target = "responderBot", source = "commandMessageDto.commandRoutingData.responderBot")
    @Mapping(target = "dataBaseChatId", source = "commandMessageDto.commandRoutingData.dataBaseChatId")
    public abstract SendMessageDto toSendMessageDto(@NonNull String text, boolean ableMentions, @NonNull CommandMessageDto commandMessageDto);

    public SendMessageDto toSendMessageDto(boolean ableMentions, @NonNull CommandMessageDto commandMessageDto){
        return toSendMessageDto("", ableMentions, commandMessageDto);
    }

    @Mapping(target = "text", source = "text")
    @Mapping(target = "responsePeerId", source = "commandRoutingData.responsePeerId")
    @Mapping(target = "responderBot", source = "commandRoutingData.responderBot")
    @Mapping(target = "dataBaseChatId", source = "commandRoutingData.dataBaseChatId")
    public abstract SendMessageDto toSendMessageDto(@NonNull String text, @NonNull CommandRoutingData commandRoutingData);

    @Mapping(target = "text", source = "text")
    @Mapping(target = "responsePeerId", source = "commandRoutingData.responsePeerId")
    @Mapping(target = "responderBot", source = "commandRoutingData.responderBot")
    @Mapping(target = "dataBaseChatId", source = "commandRoutingData.dataBaseChatId")
    public abstract SendMessageDto toSendMessageDto(@NonNull String text, @NonNull CommandRoutingData commandRoutingData, Integer conversationMessageId, boolean replyToMessageId);

    @Mapping(target = "text", source = "text")
    @Mapping(target = "responsePeerId", source = "responsePeerId")
    @Mapping(target = "responderBot", source = "responderBot")
    @Mapping(target = "dataBaseChatId", source = "dataBaseChatId")
    public abstract SendMessageDto toSendMessageDto(@NonNull String text, long responsePeerId, @Nullable Long dataBaseChatId, @NonNull GroupActor responderBot);


}
