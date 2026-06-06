package com.example.my_bot.exception.message;

import com.example.my_bot.exception.member.MemberException;
import com.vk.api.sdk.objects.messages.Message;

import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

public class FindingMessageIntervalOutOfBoundsException extends MessageException {


    public FindingMessageIntervalOutOfBoundsException() {

        super("Период, за который можно просматривать сообщения, выходит за рамки дозволенного.");
    }
}
