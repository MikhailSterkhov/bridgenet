package me.moonways.bridgenet.api.modern_command.object.label;

import lombok.Getter;
import me.moonways.bridgenet.api.modern_command.object.WrappedEntityCommand;
import me.moonways.bridgenet.api.modern_command.object.args.Args;
import me.moonways.bridgenet.api.modern_command.process.splitter.LabelSplitterUtil;

import java.util.Optional;

public class Label {

    @Getter
    private final String value;
    private final Args args;

    public Label(String value, WrappedEntityCommand entityCommand) {
        this.value = value;
        this.args = LabelSplitterUtil.split(entityCommand.getName(), value);
    }

    public String toUpper() {
        return value.toUpperCase();
    }

    public String toLower() {
        return value.toLowerCase();
    }

    public int getLength() {
        return value.length();
    }

    public String trim() {
        return value.trim();
    }

    public char[] toCharArray() {
        return value.toCharArray();
    }

    public Optional<Args> getArgs() {
        return Optional.ofNullable(args);
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }
}
