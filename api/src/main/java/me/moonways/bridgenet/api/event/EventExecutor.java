package me.moonways.bridgenet.api.event;

import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.event.cancellation.Cancellable;
import me.moonways.bridgenet.api.event.exception.EventException;
import org.jetbrains.annotations.NotNull;

import java.rmi.RemoteException;
import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
public final class EventExecutor {

    private final ExecutorService executorService;

    private final EventRegistry eventRegistry;

    private void validateNull(Event event) {
        if (event == null) {
            throw new EventException("event is null");
        }
    }

    private void validateNotCancellations(Event event) {
        if (event instanceof Cancellable) {
            throw new EventException("async event is not be assign Cancellable");
        }
    }

    @NotNull
    public <E extends Event> EventFuture<E> fireEvent(E event) {
        validateNull(event);

        if (event instanceof AsyncEvent) {
            return supplyAsynchronous(event);
        }

        EventFuture<E> eventFuture = createFuture(event, false);
        fireEventNaturally(event, eventFuture);

        return eventFuture;
    }

    private <E extends Event> EventFuture<E> supplyAsynchronous(E event) {
        validateNotCancellations(event);

        EventFuture<E> eventFuture = createFuture(event, true);
        executorService.submit(() -> fireEventNaturally(event, eventFuture));

        return eventFuture;
    }

    private boolean canCancellations(Event event) {
        boolean result = (event instanceof Cancellable);
        if (result) {
            try {
                ((Cancellable) event).makeNotCancelled();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        return result;
    }

    private  <E extends Event> void fireEventNaturally(E event, EventFuture<E> eventFuture) {
        eventRegistry.findInvokersByPriority(event.getClass())
                        .forEach(invoker -> invoker.invoke(event));

        eventFuture.complete(event);
    }

    private <E extends Event> EventFuture<E> createFuture(E event, boolean isAsync) {
        boolean isCancellable = canCancellations(event);

        return new EventFuture<>(executorService, EventPriority.NORMAL, isAsync, isCancellable);
    }
}