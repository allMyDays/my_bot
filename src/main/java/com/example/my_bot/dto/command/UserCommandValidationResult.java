package com.example.my_bot.dto.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;


@Getter
@AllArgsConstructor
@Setter
public class UserCommandValidationResult {

    private Set<String> notFoundCommands;

    private Set<String> normalizedCommands;

}
