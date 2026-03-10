package com.example.my_bot.dto.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;


@Getter
@AllArgsConstructor
public class UserCommandValidationResult {

    private Set<String> notFoundCommands;

    private Set<String> normalizedCommands;

}
