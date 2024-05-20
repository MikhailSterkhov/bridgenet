package me.moonways.bridgenet.api.modern_command.persistance;

import me.moonways.bridgenet.api.modern_command.CommandElementType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandElement {

    CommandElementType value();
}
