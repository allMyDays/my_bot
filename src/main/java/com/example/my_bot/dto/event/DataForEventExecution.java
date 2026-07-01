package com.example.my_bot.dto.event;

import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.event.ReactionType;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.vk.mapping.action.VkAction;
import com.example.my_bot.vk.mapping.attachment.VkMessageAttachment;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import static com.example.my_bot.utils.ChatUtils.convertToPeerId;


@Getter
public class DataForEventExecution {

    private final long dataBaseChatId;
    private final long fromId;
    private final int conversationMessageId;
    private final VkAction action;
    private final List<VkMessageAttachment> attachments;
    private final String userText;
    private final List<ForeignMessage> fwMessages;
    private final ForeignMessage replyMessage;
    private final ReactionType userReaction;
    private final Long fwdMessageOwnerId;
    private final boolean isSelfDestructingMessage;
    private final CommandRoutingData commandRoutingData;
    @Setter
    private boolean theMessageBeenDeleted;


    public DataForEventExecution(long dataBaseChatId, long fromId, int conversationMessageId, @Nullable VkAction action, @Nullable List<VkMessageAttachment> attachments, @Nullable String userText, @Nullable List<ForeignMessage> fwMessages, @Nullable ForeignMessage replyMessage, @Nullable ReactionType userReaction, boolean isSelfDestructingMessage, @Nullable CommandRoutingData commandRoutingData) {
        this.dataBaseChatId = dataBaseChatId;
        this.fromId = fromId;
        this.conversationMessageId = conversationMessageId;
        this.action = action;
        this.attachments = attachments;
        this.userText = userText!=null?userText.trim():null;
        this.fwMessages = fwMessages;
        this.replyMessage = replyMessage;
        this.userReaction = userReaction;
        this.isSelfDestructingMessage = isSelfDestructingMessage;

        if(commandRoutingData!=null){
            this.commandRoutingData = new CommandRoutingData(commandRoutingData);
            this.commandRoutingData.setResponderBot(this.commandRoutingData.getExecutorBot());
            this.commandRoutingData.setResponsePeerId(convertToPeerId(this.commandRoutingData.getVkApiChatId()));
        }
        else{
            this.commandRoutingData=null;
        }

        this.fwdMessageOwnerId= replyMessage!=null
                ? replyMessage.getFromId()
                : (fwMessages!=null&&!fwMessages.isEmpty()) ? fwMessages.get(0).getFromId() : null;

    }
}
