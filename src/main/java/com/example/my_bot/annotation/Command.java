package com.example.my_bot.annotation;

import com.example.my_bot.enumeration.DefaultRole;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Command {
    String[] commands();
    DefaultRole defaultRole();
    boolean eventable();
}