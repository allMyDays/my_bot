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

    private String userText;

    @Getter
    private int conversationMessageId;

    @Getter
    private Long chatId;

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
    private long peerId;

    @Getter
    private boolean replyToMessageId;

    @Getter
    private boolean doNotSendMessage;

    @Getter
    private boolean eventOrTimerMode = false;
    


    public Optional<String> getUserText() {
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
