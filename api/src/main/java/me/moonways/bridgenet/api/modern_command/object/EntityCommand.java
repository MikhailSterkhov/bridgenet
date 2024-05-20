package me.moonways.bridgenet.api.modern_command.object;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class EntityCommand  {

    private final UUID id;
    private final CommandConfiguration configuration;

    private final List<EntityCommand> subElements;

    public EntityCommand lookupSubElement(String name) {
        return subElements.stream()
                .filter(element -> element.getConfiguration().getName().equalsIgnoreCase(name))
                .collect(Collectors.toList())
                .stream()
                .findFirst()
                .orElse(null);
    }

    public boolean hasSubElement(String name) {
        return (long) (int) subElements.stream()
                .filter(element -> element.getConfiguration().getName().equalsIgnoreCase(name)).count() > 0;
    }

    public boolean isEmptySubElements() {
        return subElements.isEmpty();
    }
}
