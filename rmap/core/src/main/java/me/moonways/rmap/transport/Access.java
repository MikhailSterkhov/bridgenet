package me.moonways.rmap.transport;

import java.nio.charset.StandardCharsets;

/** Тип доступа RMAP: приватный (взаимный HMAC по ключу) или публичный (без auth). Спека §4.3. */
public final class Access {

    private final boolean isPrivate;
    private final byte[] key;

    private Access(boolean isPrivate, byte[] key) {
        this.isPrivate = isPrivate;
        this.key = key;
    }

    public static Access privateKey(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("access key must not be empty");
        }
        return new Access(true, secret.getBytes(StandardCharsets.UTF_8));
    }

    public static Access publicAccess() {
        return new Access(false, new byte[0]);
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public byte[] key() {
        return key;
    }
}
