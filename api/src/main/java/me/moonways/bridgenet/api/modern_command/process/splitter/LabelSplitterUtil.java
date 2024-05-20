package me.moonways.bridgenet.api.modern_command.process.splitter;

import lombok.experimental.UtilityClass;
import me.moonways.bridgenet.api.modern_command.object.args.Arg;
import me.moonways.bridgenet.api.modern_command.object.args.Args;

import java.util.Arrays;

@UtilityClass
public class LabelSplitterUtil {

    public Args split(String commandName, String value) {
        int Index = value.indexOf(commandName) + commandName.length();
        String[] parsedArgs = value.substring(Index).trim().split(" ");

        return Args.builder()
                .args(parsedArgs.length == 0 ? null : Arrays.stream(parsedArgs)
                        .map(Arg::create)
                        .toArray(Arg[]::new))
                .label(value)
                .build();
    }
}
