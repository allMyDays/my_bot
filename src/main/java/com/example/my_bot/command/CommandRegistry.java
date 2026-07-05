package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.dto.command.UserCommandValidationResult;
import com.example.my_bot.enumeration.key.ButtonPayloadKey;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CommandRegistry {

    private final Map<String, Map.Entry<ChatCommand, Command>> allCommandsWithTheAnnotations = new HashMap<>();
    private final Map<Class<?>, Command> annotationByClass = new HashMap<>();

    @Autowired
    public CommandRegistry(List<ChatCommand> chatCommandList) {

        for (ChatCommand chatCommand : chatCommandList) {

            Class<?> targetClass = AopUtils.getTargetClass(chatCommand);
            Command annotation = targetClass.getAnnotation(Command.class);
            if (annotation != null) {
                annotationByClass.put(targetClass, annotation);

                AbstractMap.SimpleEntry<ChatCommand, Command> value = new AbstractMap.SimpleEntry<>(chatCommand, annotation);
                allCommandsWithTheAnnotations.put(annotation.mainCommandName().toLowerCase(), value);

                for(String altCmd: annotation.alternativeCommandNames()){
                    allCommandsWithTheAnnotations.put(altCmd.toLowerCase(), value);
                }
            }
            else {
                log.error("Command class {} does not have required @Command init-annotation", chatCommand.getClass().getName());
            }
        }
    }

    public boolean commandExists(@NonNull String commandName) {
        return allCommandsWithTheAnnotations.containsKey(commandName.toLowerCase().trim());
    }

    public Optional<Map.Entry<ChatCommand, Command>> getCommandWithTheAnnotation(@NonNull String commandName){
        return Optional.ofNullable(allCommandsWithTheAnnotations.get(commandName));
    }

    public Optional<Command> getCommandAnnotation(@NonNull String commandName) {
        return getCommandWithTheAnnotation(commandName.toLowerCase().trim()).map(Map.Entry::getValue);
    }

    public Optional<Command> getCommandAnnotation(@NonNull Class<?> clazz) {
        return Optional.ofNullable(annotationByClass.get(clazz));
    }

    public Optional<String> getMainNameOfCommand(@NonNull String commandName) {
        return getCommandAnnotation(commandName.toLowerCase().trim()).map(Command::mainCommandName);
    }

    public UserCommandValidationResult getMainNamesOfRequiredCommands(@NonNull Set<String> userCommands) {

        Set<String> notFound = new HashSet<>();
        Set<String> normalizedNames = new HashSet<>();

        for(String userCommand: userCommands){
            Optional<Map.Entry<ChatCommand, Command>> cmd = getCommandWithTheAnnotation(userCommand);
            if(cmd.isEmpty()){
                notFound.add(userCommand);
            }else{
                normalizedNames.add(cmd.get().getValue().mainCommandName());
            }

        } return new UserCommandValidationResult(notFound, normalizedNames);
    }

}
