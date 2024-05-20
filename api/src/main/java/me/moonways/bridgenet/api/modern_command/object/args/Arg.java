package me.moonways.bridgenet.api.modern_command.object.args;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class Arg {

    private final String value;

    public static Arg create(String value) {
        return new Arg(value);
    }
}
