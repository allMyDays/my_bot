package com.example.my_bot.vk.mapping.post;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VkWallPostObject {

    @SerializedName("from_id")
    private long fromId;

    @SerializedName("id")
    private int postId;
}
