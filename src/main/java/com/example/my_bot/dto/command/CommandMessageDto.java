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
public class CommandMessageDto{

    private String userText;

    @Getter
    private int conversationMessageId;

    @Getter
    private long chatId;

    @Getter
    private long fromId;

    private Long replyMessageOwnerId;

    private List<Long> fwdMessagesOwnerIds = new ArrayList<>();

    @Getter
    private String[] firstRowArguments = new String[0];

    @Getter
    private String[] allRows = new String[0];

    private String command;

    @Getter
    private long peerId;

    @Getter
    private boolean replyToMessageId;

    @Getter
    private boolean eventOrTimerMode = false;
    
    


    public Optional<Long> getReplyMessageOwnerId() {
        return Optional.ofNullable(replyMessageOwnerId);
    }

    public List<Long> getFwdMessageOwnerIds() {
        return fwdMessagesOwnerIds;
    }

    public Optional<String> getUserText() {
        return Optional.ofNullable(userText);
    }

    public boolean hasAnyText(){
        return userText !=null&&!userText.trim().isEmpty();
    }

    public boolean hasReplyMessage(){
        return replyMessageOwnerId !=null;
    }
    public boolean hasFwdMessages(){
        return !fwdMessagesOwnerIds.isEmpty();
    }

    public Optional<String> getCommand() {
        if(command==null||command.trim().isEmpty()){
            return Optional.empty();
        }
        return Optional.of(command);

    }



}
