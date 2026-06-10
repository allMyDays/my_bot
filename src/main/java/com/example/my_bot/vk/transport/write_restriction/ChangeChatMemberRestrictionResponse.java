package com.example.my_bot.vk.transport.write_restriction;

import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.Validable;
import com.vk.api.sdk.objects.annotations.Required;
import lombok.Getter;

import java.util.List;

@Getter
public class ChangeChatMemberRestrictionResponse implements Validable{

        @SerializedName("failed_member_ids")
        private List<Long> failedMemberIds;

}
