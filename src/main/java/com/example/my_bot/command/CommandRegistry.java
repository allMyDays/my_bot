package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CommandRegistry {
    private final Map<String, ChatCommand> allCommands = new HashMap<>();
    private final Map<ChatCommand, Command> commandAnnotations = new HashMap<>();

    @Autowired
    public CommandRegistry(List<ChatCommand> chatCommandList) {
        for (ChatCommand chatCommand : chatCommandList) {
            Command annotation = chatCommand.getClass().getAnnotation(Command.class);
            if (annotation != null) {
                commandAnnotations.put(chatCommand, annotation);
                allCommands.put(annotation.mainCommandName().toLowerCase(), chatCommand);
                for (String altCmd : annotation.alternativeCommandNames()) {
                    allCommands.put(altCmd.toLowerCase(), chatCommand);
                }
            } else {
                log.error("Command class {} does not have required @Command init-annotation", chatCommand.getClass().getName());
            }
        }
    }

    public boolean commandExists(@NonNull String commandName) {
        return allCommands.containsKey(commandName.toLowerCase().trim());
    }

    public Optional<ChatCommand> getCommand(@NonNull String commandName) {
        return Optional.ofNullable(allCommands.get(commandName.toLowerCase().trim()));
    }

    public Optional<Command> getCommandAnnotation(@NonNull String commandName) {
        return getCommand(commandName.toLowerCase().trim()).map(commandAnnotations::get);
    }

    public Set<String> getAllCommandNames() {
        return Collections.unmodifiableSet(allCommands.keySet());
    }
    public Set<String> getMainNamesOfAllCommands() {

        return commandAnnotations.values().stream()
                .map(Command::mainCommandName)
                .collect(Collectors.toUnmodifiableSet());

    }

    public Optional<String> getMainNameOfCommand(@NonNull String commandName) {
        return getCommandAnnotation(commandName.toLowerCase().trim()).map(Command::mainCommandName);
    }

    public Set<String> getMainNamesOfRequiredCommands(Set<String> userCommands) {

        Set<String> mainNames = new HashSet<>();

        for(String userCommand: userCommands){
            Optional<ChatCommand> cmd = getCommand(userCommand);
            cmd.ifPresent(c-> mainNames.add(commandAnnotations.get(c).mainCommandName()));
        } return mainNames;
    }




}
