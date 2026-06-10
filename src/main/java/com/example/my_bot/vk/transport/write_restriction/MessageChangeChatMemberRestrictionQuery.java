package com.example.my_bot.vk.transport.write_restriction;

import com.example.my_bot.vk.enumeration.WriteRestrictionAction;
import com.vk.api.sdk.client.AbstractQueryBuilder;
import com.vk.api.sdk.client.Utils;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.objects.annotations.ApiParam;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

public class MessageChangeChatMemberRestrictionQuery extends AbstractQueryBuilder<MessageChangeChatMemberRestrictionQuery, ChangeChatMemberRestrictionResponse> {
    public MessageChangeChatMemberRestrictionQuery(VkApiClient client, GroupActor actor) {
        super(client, "messages.changeConversationMemberRestrictions", ChangeChatMemberRestrictionResponse.class);
        this.accessToken(actor.getAccessToken());
        this.groupId(actor.getGroupId());
    }

    public MessageChangeChatMemberRestrictionQuery(VkApiClient client, UserActor actor) {
        super(client, "messages.changeConversationMemberRestrictions", ChangeChatMemberRestrictionResponse.class);
        this.accessToken(actor.getAccessToken());
    }

    @ApiParam("group_id")
    public MessageChangeChatMemberRestrictionQuery groupId(Long value) {
        return (MessageChangeChatMemberRestrictionQuery)this.unsafeParam("group_id", value);
    }

    @ApiParam("peer_id")
    public MessageChangeChatMemberRestrictionQuery peerId(Long value) {
        return (MessageChangeChatMemberRestrictionQuery)this.unsafeParam("peer_id", value);
    }

    @ApiParam("member_ids")
    public MessageChangeChatMemberRestrictionQuery memberIds(List<Long> value) {
        return (MessageChangeChatMemberRestrictionQuery)this.unsafeParam("member_ids", value);
    }

    @ApiParam("for")
    public MessageChangeChatMemberRestrictionQuery timePeriodSec(int seconds) {
        return (MessageChangeChatMemberRestrictionQuery)this.unsafeParam("for", seconds);
    }

    @ApiParam("action")
    public MessageChangeChatMemberRestrictionQuery action(WriteRestrictionAction value) {
        return (MessageChangeChatMemberRestrictionQuery)this.unsafeParam("action", value.getVkType());
    }

    protected MessageChangeChatMemberRestrictionQuery getThis() {
        return this;
    }

    protected List<String> essentialKeys() {
        return Arrays.asList("access_token");
    }
}
