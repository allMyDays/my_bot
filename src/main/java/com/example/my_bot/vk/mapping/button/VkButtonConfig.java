package com.example.my_bot.vk.mapping.button;

import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;


@Getter
@AllArgsConstructor
public class VkButtonConfig {

    private final String text;

    private final KeyboardButtonColor color;

    private final String payload;

}
