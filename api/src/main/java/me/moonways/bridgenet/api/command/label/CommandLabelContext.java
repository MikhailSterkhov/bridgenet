package me.moonways.bridgenet.api.command.label;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.exception.CommandException;
import me.moonways.bridgenet.api.command.uses.Command;
import me.moonways.bridgenet.api.util.ExceptionallyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Getter
public class CommandLabelContext {

    private final String label;
    private final String commandName;

    private final Arguments arguments;

    public static CommandLabelContext create(Command command, String label) {
        String commandName = command.getInfo().getName();
        int commandNameIndex = label.indexOf(commandName) + commandName.length();

        String[] parsedArgs = label.substring(commandNameIndex).trim().split(" ");
        Arguments arguments = Arguments.with(isEmptyArguments(parsedArgs) ? new String[0] : parsedArgs);

        return new CommandLabelContext(label, commandName, arguments);
    }

    private static boolean isEmptyArguments(String[] args) {
        return args.length == 1 && args[0].isEmpty();
    }

    @RequiredArgsConstructor
    public static class Arguments {

        private final String[] value;

        public static Arguments with(String[] args) {
            return new Arguments(args);
        }

        public Stream<String> stream() {
            return Arrays.stream(value);
        }

        public String[] toStringArray() {
            return value;
        }

        private Optional<String> lookup(int position) {
            if (isEmpty() || size() <= position) {
                return Optional.empty();
            }

            return Optional.ofNullable(value[position]);
        }

        public Optional<String> get(int position) {
            return lookup(position);
        }

        public <R> Optional<R> get(int position, ExceptionallyFunction<String, R> mapper) {
            try {
                return Optional.ofNullable(mapper.apply(lookup(position).orElse(null)));
            } catch (Throwable exception) {
                throw new CommandException(exception);
            }
        }

        public Optional<String> first() {
            return get(0);
        }

        public <R> Optional<R> first(ExceptionallyFunction<String, R> mapper) {
            return get(0, mapper);
        }

        public Optional<String> last() {
            if (size() - 1 < 0) {
                return Optional.empty();
            }
            return get(size() - 1);
        }

        public <R> Optional<R> last(ExceptionallyFunction<String, R> mapper) {
            if (size() - 1 < 0) {
                return Optional.empty();
            }
            return get(size() - 1, mapper);
        }

        public boolean isEmpty() {
            return value.length == 0;
        }

        public int size() {
            return value.length;
        }

        public void assertSize(int required) {
            if (!has(required)) {
                throw new AssertionError(String.format("Illegal command arguments size (%d < %d)", size(), required));
            }
        }

        public boolean has(int requiredSize) {
            return size() > requiredSize;
        }

        @NotNull
        public Iterator<String> iterator() {
            return Arrays.asList(value).iterator();
        }
    }

    private static class LabelParser {

        public static Arguments getArguments(String line) {
            String[] splitLine = line.split(" ");

            return Arguments.with(Arrays.copyOfRange(splitLine, 1, splitLine.length));
        }

        public static String first(String line) {
            return line.split(" ")[0];
        }
    }
}
