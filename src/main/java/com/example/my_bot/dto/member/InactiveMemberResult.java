package com.example.my_bot.dto.member;

import com.example.my_bot.dto.RoleDto;
import jakarta.annotation.Nullable;
import lombok.*;

import java.time.Instant;
import java.util.Optional;

@NoArgsConstructor
public class InactiveMemberResult{

    @Setter
    @Getter
    long userId;

    Instant lastMessage;

    public InactiveMemberResult(long userId, @Nullable Instant lastMessage) {
        this.userId = userId;
        this.lastMessage = lastMessage;
    }

    public void setLastMessage(@Nullable Instant lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Optional<Instant> getLastMessage(){
        return Optional.ofNullable(lastMessage);
    }
}
