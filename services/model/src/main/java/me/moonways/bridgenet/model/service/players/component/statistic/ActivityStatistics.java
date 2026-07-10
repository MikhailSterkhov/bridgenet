package me.moonways.bridgenet.model.service.players.component.statistic;


public interface ActivityStatistics {

    /**
     * Сбросить все статистики активности игрока.
     */
    void reset();

    /**
     * Установить значение целочисленной статистики.
     *
     * @param statistic - Статистика, для которой нужно установить значение.
     * @param value     - Значение статистики.
     */
    void setInt(Statistic statistic, int value);

    /**
     * Установить значение длинной статистики.
     *
     * @param statistic - Статистика, для которой нужно установить значение.
     * @param value     - Значение статистики.
     */
    void setLong(Statistic statistic, long value);

    /**
     * Получить значение целочисленной статистики.
     *
     * @param statistic - Статистика, для которой нужно получить значение.
     * @return - Значение целочисленной статистики.
     */
    int getInt(Statistic statistic);

    /**
     * Получить значение длинной статистики.
     *
     * @param statistic - Статистика, для которой нужно получить значение.
     * @return - Значение длинной статистики.
     */
    long getLong(Statistic statistic);
}
