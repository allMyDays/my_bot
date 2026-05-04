package com.example.my_bot.exception.member;

import lombok.NonNull;

import java.util.List;
import java.util.stream.Collectors;

import static com.example.my_bot.utils.TextUtils.createMention;

public class UserNeverBeenInChatException extends MemberException {
    public UserNeverBeenInChatException(long userId) {
        super(createMention(userId)+"(Данный пользователь) не является текущим или бывшим участником чата.");
    }public UserNeverBeenInChatException(@NonNull List<Long> userIds) {
        super("Следующие пользователи не являются текущими или бывшими участниками чата: %s "
                .formatted(String.join(", ", userIds.stream()
                              .map(String::valueOf)
                              .collect(Collectors.joining(", ")))
                ));
    }
}
