package com.example.my_bot.dto.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@NoArgsConstructor
@Setter
public class CommandMessageDto {

    private String userMessage;

    @Getter
    private long chatId;

    @Getter
    private long fromId;

    private Long replyMessageFromId;

    private List<Long> fwdMessagesFromIds = new ArrayList<>();

    @Getter
    private String[] firstRowArguments = new String[0];

    @Getter
    private String[] allRows = new String[0];

    private String command;

    @Getter
    private long peerId;

    @Getter
    private boolean eventOrTimerMode = false;


    public Optional<Long> getReplyMessageFromId() {
        return Optional.ofNullable(replyMessageFromId);
    }

    public List<Long> getFwdMessageFromIds() {
        return fwdMessagesFromIds;
    }

    public Optional<String> getUserMessage() {
        return Optional.ofNullable(userMessage);
    }

    public boolean hasAnyText(){
        return userMessage!=null&&!userMessage.trim().isEmpty();
    }

    public boolean hasReplyMessage(){
        return replyMessageFromId!=null;
    }
    public boolean hasFwdMessages(){
        return !fwdMessagesFromIds.isEmpty();
    }

    public Optional<String> getCommand() {
        if(command==null||command.trim().isEmpty()){
            return Optional.empty();
        }
        return Optional.of(command);

    }



}
