package me.moonways.rmap.transport;

public class RmapTransportException extends RuntimeException {
    public RmapTransportException(String message) { super(message); }
    public RmapTransportException(String message, Throwable cause) { super(message, cause); }
}
