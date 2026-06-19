package com.example.my_bot.vk;

import com.example.my_bot.vk.transport.write_restriction.MessageChangeChatMemberRestrictionQuery;
import com.vk.api.sdk.actions.Messages;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.annotations.ApiMethod;

public class CustomMessages extends Messages {
    public CustomMessages(VkApiClient client) {
        super(client);
    }

    @ApiMethod("messages.send")
    public MessageChangeChatMemberRestrictionQuery changeChatMemberRestrictions (GroupActor actor) {
        return new MessageChangeChatMemberRestrictionQuery(this.getClient(), actor);
    }



}
