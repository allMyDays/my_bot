package com.example.my_bot.dto.cooldown;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@AllArgsConstructor
@NoArgsConstructor
@Setter

public class CooldownResult {

    private boolean canExecuteCommand =false;

    @Getter
    private long leftCDSeconds = 0;

    private boolean canSendCDMessageToUser =false;

    public boolean canExecuteCommand() {
        return canExecuteCommand;
    }

    public boolean canSendCDMessageToUser() {
        return canSendCDMessageToUser;
    }
}
