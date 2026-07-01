package com.example.my_bot.dto.command;

import com.vk.api.sdk.objects.messages.ForeignMessage;
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

    @Getter
    private String userText;

    @Getter
    private int conversationMessageId;

    @Getter
    private long fromId;

    @Getter
    private List<ForeignMessage> replyOrFwdMessages=new ArrayList<>();

    @Getter
    private String[] firstRowArguments = new String[0];

    @Getter
    private String[] allRows = new String[0];

    private String command;

    @Getter
    private boolean replyToMessageId;

    @Getter
    private boolean doNotSendTheMessage;

    @Getter
    private boolean eventOrTimerMode = false;

    @Getter
    private CommandRoutingData commandRoutingData;
    



    public Optional<String> getOptionalUserText() {
        return Optional.ofNullable(userText);
    }

    public boolean hasAnyText(){
        return userText !=null&&!userText.trim().isEmpty();
    }

    public Optional<String> getCommand() {
        if(command==null||command.trim().isEmpty()){
            return Optional.empty();
        }
        return Optional.of(command);

    }



}
