package me.moonways.bridgenet.model.service.commands.arg;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Args {

    private final Arg[] args;
    private final String label;
}