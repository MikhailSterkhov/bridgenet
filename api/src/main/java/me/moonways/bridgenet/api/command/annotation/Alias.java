package me.moonways.bridgenet.api.command.annotation;

import me.moonways.bridgenet.api.command.annotation.repeatable.RepeatableCommandAliases;

import java.lang.annotation.*;

@Repeatable(RepeatableCommandAliases.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Alias {

    String value();
}
