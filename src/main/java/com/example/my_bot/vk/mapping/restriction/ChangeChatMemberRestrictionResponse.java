package com.example.my_bot.vk.mapping.restriction;

import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.Validable;
import lombok.Getter;

import java.util.List;

@Getter
public class ChangeChatMemberRestrictionResponse implements Validable{

        @SerializedName("failed_member_ids")
        private List<Long> failedMemberIds;

}
