package me.moonways.endpoint.settings;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.api.util.ExceptionallyConsumer;
import me.moonways.bridgenet.model.service.settings.Setting;
import me.moonways.bridgenet.model.service.settings.SettingID;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettingStub<T> implements Setting<T> {

    @ToString.Include
    private final SettingID<T> id;
    @ToString.Include
    private T value;

    private ExceptionallyConsumer<T> subscriber = ((t) -> {
    });

    @Override
    public Setting<T> copy() {
        return new SettingStub<>(id, value, subscriber);
    }

    @Override
    public SettingID<T> id() {
        return id;
    }

    @Override
    public T get() {
        checkValidValue();
        return value;
    }

    @Override
    public boolean isEnabled() {
        return !SettingStub.isDisabled(this);
    }

    @Override
    public void onChanged(ExceptionallyConsumer<T> subscriber) {
        this.subscriber = this.subscriber.andThen(subscriber);
    }

    @Override
    public T orElse(T orElse) {
        return Optional.ofNullable(get()).orElse(orElse);
    }

    @Override
    public T orElse(Supplier<T> orElse) {
        return Optional.ofNullable(get()).orElseGet(orElse);
    }

    @Override
    public Setting<T> set(T value) {
        this.value = value;
        try {
            this.subscriber.accept(value);
        } catch (Throwable exception) {
            throw new SettingsEndpointException(exception);
        }
        return this;
    }

    @Override
    public Setting<T> set(Supplier<T> value) {
        return set(value.get());
    }

    @Override
    public Setting<T> ifEnabled(Consumer<T> consumer) {
        if (isEnabled()) {
            consumer.accept(value);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> Setting<R> map(Function<T, R> function) {
        return new SettingStub<R>((SettingID<R>) id).set(function.apply(value));
    }

    private boolean isBoolean() {
        return id.getType().equals(Boolean.class) || id.getType().equals(boolean.class);
    }

    private boolean isNumber() {
        return (id.getType().isPrimitive() && !isBoolean()) || Number.class.isAssignableFrom(id.getType());
    }

    @SuppressWarnings("unchecked")
    private void checkValidValue() {
        if (value == null) {
            if (isNumber()) {
                value = (T) DISABLED_NUMBER;
            }
            if (isBoolean()) {
                value = (T) DISABLED_BOOLEAN;
            }
        }
    }

    public static boolean isDisabled(SettingStub<?> settingStub) {
        if (settingStub.isBoolean()) {
            return Objects.equals(settingStub.get(), DISABLED_BOOLEAN);
        }
        if (settingStub.isNumber()) {
            return Objects.equals(settingStub.get(), DISABLED_NUMBER);
        }

        return Objects.equals(settingStub.get(), DISABLED_OBJECT);
    }
}
