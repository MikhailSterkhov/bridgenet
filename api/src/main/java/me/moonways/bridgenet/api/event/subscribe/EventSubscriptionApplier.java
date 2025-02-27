package me.moonways.bridgenet.api.event.subscribe;

import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.event.*;

import java.util.Set;

@RequiredArgsConstructor
public final class EventSubscriptionApplier {

    private final EventService eventService;

    private final EventSubscriptionContainer container = new EventSubscriptionContainer();

    private void validateNull(EventSubscription<?> subscription) {
        if (subscription == null) {
            throw new EventException("subscription is null");
        }
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> void followSubscription(EventFuture<E> future) {
        future.follow(event -> {

            Set<EventSubscription<?>> subscriptionsAll = container.getSubscriptions(event.getClass());

            if (subscriptionsAll != null) {
                for (EventSubscription<?> eventSubscriptionImpl : subscriptionsAll) {

                    EventSubscription<E> genericSubscription = (EventSubscription<E>) eventSubscriptionImpl;
                    genericSubscription.followExpiration(eventService);

                    EventFollower<E> follower = genericSubscription.getFollower();
                    follower.postComplete(event);
                }
            }
        });
    }

    public void subscribe(EventSubscription<?> subscription) {
        validateNull(subscription);
        container.addSubscription(subscription);
    }

    public void unsubscribe(EventSubscription<?> subscription) {
        validateNull(subscription);
        container.removeSubscription(subscription);
    }
}
