package me.moonways.bridgenet.model.service.settings;

import me.moonways.bridgenet.api.util.ExceptionallyConsumer;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Интерфейс, отвечающий за шаблон поведения
 * пользовательской настройки относительно пользователя,
 * из которого мы ее получили.
 *
 * @param <T> - Тип настраиваемого объекта.
 */
public interface Setting<T> {

// ================================= // DISABLED VALUES // ========================================================== //

    Object DISABLED_OBJECT = null;
    Boolean DISABLED_BOOLEAN = false;
    Number DISABLED_NUMBER = -1;

// ================================================================================================================== //

    /**
     * Скопировать текущий объект настройки и вернуть его клон.
     */
    Setting<T> copy();

    /**
     * Идентификатор пользовательской настройки.
     */
    SettingID<T> id();

    /**
     * Получить актуальное значение пользовательской настройки.
     */
    T get();

    /**
     * Получить актуальное значение пользовательской настройки.
     * В случае же если она не найдена у пользователя, то функция
     * вернет fallback значение, прописанное в параметре.
     *
     * @param orElse - fallback значение.
     */
    T orElse(T orElse);

    /**
     * Получить актуальное значение пользовательской настройки.
     * В случае же если она не найдена у пользователя, то функция
     * вернет fallback значение, прописанное в параметре.
     *
     * @param orElse - fallback значение.
     */
    T orElse(Supplier<T> orElse);

    /**
     * Изменить актуальное значение пользовательской настройки на новое.
     *
     * @param value - новое значение.
     */
    Setting<T> set(T value);

    /**
     * Изменить актуальное значение пользовательской настройки на новое.
     *
     * @param value - новое значение.
     */
    Setting<T> set(Supplier<T> value);

    /**
     * Преобразовать значение пользовательской настройки в
     * другое.
     *
     * @param function - маппер значения.
     */
    <R> Setting<R> map(Function<T, R> function);

    /**
     * Данная функция применяет вводных консумер только в том случае,
     * если пользовательская настройка была включена относительно
     * нашего пользователя.
     *
     * @param enabledConsumer - консумер.
     */
    Setting<T> ifEnabled(Consumer<T> enabledConsumer);

    /**
     * Проверить, включена ли пользовательская настройка
     * относительно пользователя, из которого мы ее получили.
     */
    boolean isEnabled();

    /**
     * Подписка на изменения значения инстанса
     * данной настройки.
     *
     * @param subscriber - обработчик входящего изменения значения.
     */
    void onChanged(ExceptionallyConsumer<T> subscriber);
}
