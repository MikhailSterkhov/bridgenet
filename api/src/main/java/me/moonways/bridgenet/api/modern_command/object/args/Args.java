package me.moonways.bridgenet.api.modern_command.object.args;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Args {

    private final Arg[] args;
    private final String label;
}
