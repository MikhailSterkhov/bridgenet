package me.moonways.bridgenet.service.event.canсellation;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
public class EventCancellationWrapper implements Cancellable {

    private boolean cancelled;

    @Override
    public synchronized boolean isCancelled() {
        return cancelled;
    }

    @Override
    public synchronized void makeCancelled() {
        this.cancelled = Boolean.TRUE;
    }

    @Override
    public synchronized void makeNotCancelled() {
        this.cancelled = Boolean.FALSE;
    }
}
