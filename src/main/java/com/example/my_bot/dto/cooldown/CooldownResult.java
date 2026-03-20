package com.example.my_bot.dto.cooldown;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class CooldownResult {

    private boolean canExecuteCommand =false;

    private long leftCooldownSeconds = 0;

    private boolean canSendCDMessageToUser =false;




}
