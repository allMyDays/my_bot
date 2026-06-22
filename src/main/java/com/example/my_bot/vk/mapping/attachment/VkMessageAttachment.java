package com.example.my_bot.vk.mapping.attachment;

import com.example.my_bot.vk.enumeration.VkMessageAttachmentType;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.audio.Audio;
import com.vk.api.sdk.objects.base.Sticker;
import com.vk.api.sdk.objects.calls.Call;
import com.vk.api.sdk.objects.docs.Doc;
import com.vk.api.sdk.objects.gifts.Layout;
import com.vk.api.sdk.objects.market.MarketAlbum;
import com.vk.api.sdk.objects.market.MarketItem;
import com.vk.api.sdk.objects.messages.AudioMessage;
import com.vk.api.sdk.objects.messages.Graffiti;
import com.vk.api.sdk.objects.photos.Photo;
import com.vk.api.sdk.objects.polls.Poll;
import com.vk.api.sdk.objects.stories.Story;
import com.vk.api.sdk.objects.wall.WallComment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VkMessageAttachment {

    @SerializedName("type")
    private VkMessageAttachmentType type;

    @SerializedName("audio")
    private Audio audio;
    @SerializedName("audio_message")
    private AudioMessage audioMessage;
    @SerializedName("call")
    private Call call;
    @SerializedName("doc")
    private Doc doc;
    @SerializedName("gift")
    private Layout gift;
    @SerializedName("graffiti")
    private Graffiti graffiti;
    @SerializedName("market")
    private MarketItem market;
    @SerializedName("market_market_album")
    private MarketAlbum marketMarketAlbum;
    @SerializedName("photo")
    private Photo photo;
    @SerializedName("poll")
    private Poll poll;
    @SerializedName("sticker")
    private Sticker sticker;
    @SerializedName("story")
    private Story story;
    @SerializedName("wall_reply")
    private WallComment wallReply;
    @SerializedName("video")
    private Video video;
}
