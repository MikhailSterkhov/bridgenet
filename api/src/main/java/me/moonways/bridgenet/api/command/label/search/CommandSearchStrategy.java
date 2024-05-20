package me.moonways.bridgenet.api.command.label.search;

import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.command.uses.Command;
import me.moonways.bridgenet.api.command.process.registration.CommandRegistry;

import java.util.*;

@Autobind
public final class CommandSearchStrategy {

    @Inject
    private CommandRegistry registry;

    public Optional<Command> search(String label) {
        String[] splitLabel = label.split(" ");
        String commandName = splitLabel[0];

        if (splitLabel.length < 2) {
            if (contains(commandName)) {
                return Optional.of(get(commandName));
            } else {
                return Optional.empty();
            }
        }

        ListIterator<String> iterator = new ArrayList<>(Arrays.asList(splitLabel))
                .listIterator();

        StringBuilder combinedWords = new StringBuilder();

        while (iterator.hasNext()) {
            String nextWord = iterator.next();

            combinedWords.append(nextWord);
            Optional<Command> commandOptional = Optional.empty();

            if (!contains(nextWord)) {
                String completedCombinedWords = combinedWords.toString();

                if (contains(completedCombinedWords)) {
                    commandOptional = Optional.of(get(completedCombinedWords));
                }
            } else {
                commandOptional = Optional.of(get(nextWord));
            }

            combinedWords.append(iterator.hasNext() ? " " : "");

            if (commandOptional.isPresent()) {
                return commandOptional;
            }
        }

        return Optional.ofNullable(get(commandName));
    }

    private boolean contains(String name) {
        return registry.contains(UUID.nameUUIDFromBytes(name.getBytes()));
    }

    private Command get(String commandName) {
        return registry.get(UUID.nameUUIDFromBytes(commandName.getBytes()));
    }
}
