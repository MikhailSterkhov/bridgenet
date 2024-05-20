package me.moonways.bridgenet.api.command.label.regex;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CommandRegex {

    private final String value;
    private final String exceptionMsg;
}
