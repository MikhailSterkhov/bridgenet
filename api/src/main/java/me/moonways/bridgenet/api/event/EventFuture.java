package me.moonways.bridgenet.api.event;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public class EventFuture<E extends Event> {

    private final EventFollower<E> follower = new EventFollower<>();

// ----------------------------------------------------------------------------------- //

    private final ExecutorService executorService;

    private final EventPriority priority;

    private final boolean isAsync;
    private final boolean isCancellable;

// ----------------------------------------------------------------------------------- //

    private void validateNull(E event) {
        if (event == null) {
            throw new EventException("event is null");
        }
    }

    private void validateNull(Consumer<E> eventConsumer) {
        if (eventConsumer == null) {
            throw new EventException("additional event-executor is null");
        }
    }

    private void validateTimeout(long timeout) {
        if (timeout <= 0) {
            throw new EventException("timeout value must be > 0");
        }
    }

    public EventFuture<E> follow(@NotNull Consumer<E> eventConsumer) {
        validateNull(eventConsumer);
        follower.follow(eventConsumer);

        return this;
    }

    public EventFuture<E> setTimeout(long timeout, @Nullable Runnable timeoutRunnable) {
        validateTimeout(timeout);

        TimeoutHandler timeoutHandler = new TimeoutHandler(timeout, timeoutRunnable);
        timeoutHandler.beginAwait();
        return follow(__ -> timeoutHandler.breakTimeout());
    }

    public EventFuture<E> setTimeout(long timeout) {
        return setTimeout(timeout, null);
    }

    public void complete(@NotNull E event) {
        validateNull(event);
        follower.postComplete(event);
    }

    @AllArgsConstructor
    private class TimeoutHandler {

        private long timeout;
        private final Runnable handler;

        public void breakTimeout() {
            timeout = -1;
        }

        public void beginAwait() {
            if (timeout <= 0)
                return;

            long totalTimeoutMillis = System.currentTimeMillis() + timeout;
            executorService.execute(() -> {

                while (timeout > 0 && System.currentTimeMillis() - totalTimeoutMillis > 0) ;

                if (timeout <= 0) {
                    if (handler != null) {
                        handler.run();
                    }

                    throw new EventException("Timeout");
                }
            });
        }
    }
}
