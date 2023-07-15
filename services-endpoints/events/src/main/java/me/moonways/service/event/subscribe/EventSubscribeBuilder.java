package me.moonways.service.event.subscribe;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.moonways.services.api.events.event.Event;
import me.moonways.service.event.EventFollowerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public final class EventSubscribeBuilder<E extends Event> {

    public static <E extends Event> EventSubscribeBuilder<E> newBuilder(Class<E> eventType) {
        return new EventSubscribeBuilder<>(eventType);
    }

    private final Class<E> eventType;

    private EventSubscribeExpiration expiration;
    private EventSubscribePredication<E> predication;

    private final EventFollowerImpl<E> follower = new EventFollowerImpl<>();

    public EventSubscribeBuilder<E> expiration(@NotNull EventSubscribeExpiration expiration) {
        this.expiration = expiration;
        return this;
    }

    public EventSubscribeBuilder<E> expiration(long timeout, @NotNull TimeUnit unit) {
        this.expiration = new EventSubscribeExpiration(System.currentTimeMillis(), timeout, unit);
        return this;
    }

    @Deprecated
    public EventSubscribeBuilder<E> predication(@NotNull EventSubscribePredication<E> predication) {
        this.predication = predication;
        return this;
    }

    @Deprecated
    public EventSubscribeBuilder<E> predication(@NotNull Predicate<E> predicate) {
        this.predication = EventSubscribePredication.create(predicate);
        return this;
    }

    public EventSubscribeBuilder<E> follow(@NotNull Consumer<E> eventConsumer) {
        follower.follow(eventConsumer);
        return this;
    }

    public EventSubscriptionImpl<E> build() {
        return new EventSubscriptionImpl<>(eventType, expiration, predication, follower);
    }
}