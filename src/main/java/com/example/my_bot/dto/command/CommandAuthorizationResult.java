package com.example.my_bot.dto.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;


@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class CommandAuthorizationResult {

    private Set<String> allowed=new HashSet<>();

    private Set<String> forbidden=new HashSet<>();
}
