package me.moonways.bridgenet.api.event;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class EventFollower<E extends Event> {

    private final Object lock = new Object();

    private Consumer<E> thenExecuteConsumer;
    @Getter
    private E completed;

    private void validateNull(Event event) {
        if (event == null) {
            throw new EventException("event is null");
        }
    }

    public boolean isCompleted() {
        return completed != null;
    }

    public void follow(@Nullable Consumer<E> eventConsumer) {
        if (eventConsumer == null)
            return;

        synchronized (lock) {
            if (isCompleted()) {
                eventConsumer.accept(completed);
                return;
            }

            if (thenExecuteConsumer == null)
                thenExecuteConsumer = eventConsumer;
            else
                thenExecuteConsumer = thenExecuteConsumer.andThen(eventConsumer);
        }
    }

    public void postComplete(@NotNull E event) {
        validateNull(event);
        completed = event;

        if (thenExecuteConsumer != null) {
            thenExecuteConsumer.accept(event);
        }
    }
}