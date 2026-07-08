package com.example.my_bot.aspect;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.CommandLogService;
import com.example.my_bot.service.chat.AdminChatActionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class CommandLoggingAspect {

    private final CommandLogService commandLogService;
    private final CommandRegistry commandRegistry;
    private final AdminChatActionService adminChatActionService;

    @Around("execution(* com.example.my_bot.command.ChatCommand.execute(..))")
    public Object logCommand(ProceedingJoinPoint joinPoint) throws Throwable {

        CommandMessageDto dto = (CommandMessageDto) joinPoint.getArgs()[0];
        Class<?> clazz = joinPoint.getTarget().getClass();
        Optional<Command> cmdAnnotation = commandRegistry.getCommandAnnotation(clazz);

        Long dataBaseChatId = dto.getCommandRoutingData().getDataBaseChatId();
        long fromId = dto.getFromId();

        Object result = joinPoint.proceed();

        if(cmdAnnotation.isEmpty()||dataBaseChatId==null){
            return result;
        }

        try{
            commandLogService.saveNewCommandLog(dataBaseChatId, cmdAnnotation.get(), fromId);
            adminChatActionService.sendMessageAboutAnUsedCommand(dataBaseChatId, cmdAnnotation.get(), fromId);
            return result;

        }catch (Exception e) {
            log.error("fail doing command {} actions", cmdAnnotation.get().mainCommandName(), e);
            throw e;
        }
    }

    @PostConstruct
    public void init() {
        log.info("CommandLoggingAspect initialized");
    }
}
