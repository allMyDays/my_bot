package com.example.my_bot.mapper;

import com.example.my_bot.dto.command.CommandMessageDto;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import lombok.NonNull;
import org.mapstruct.Mapper;
import com.vk.api.sdk.objects.messages.Message;

import static com.example.my_bot.utils.VkChatUtils.extractConversationId;

@Mapper(componentModel = "spring")
public abstract class CommandMapper {

    public CommandMessageDto toCommandMessageDto(@NonNull Message message){

        CommandMessageDto commandMessageDto = new CommandMessageDto();
        commandMessageDto.setUserMessage(message.getText());
        commandMessageDto.setFromId(message.getFromId());
        commandMessageDto.setChatId(extractConversationId(message.getPeerId()));

        if(message.getText()!=null){
            String[] rows = message.getText().split("\\n+");
            String[] commandAndArgs = rows[0].split(" +", 2);
            commandMessageDto.setCommand(commandAndArgs[0].toLowerCase().trim());
            if(commandAndArgs.length==2){
              commandMessageDto.setFirstRowArguments(commandAndArgs[1].split(" +"));
            }
            commandMessageDto.setAllRows(rows);
        }
        if(message.getReplyMessage()!=null){
            commandMessageDto.setReplyMessageFromId(message.getReplyMessage().getFromId());
        }if(message.getFwdMessages()!=null){
            commandMessageDto.setFwdMessagesFromIds(message.getFwdMessages().stream()
                    .map(ForeignMessage::getFromId)
                    .toList()
            );
        } return commandMessageDto;

    }

}
