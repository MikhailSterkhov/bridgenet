package me.moonways.bridgenet.api.command;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SubCommand {

    String[] value();
}
