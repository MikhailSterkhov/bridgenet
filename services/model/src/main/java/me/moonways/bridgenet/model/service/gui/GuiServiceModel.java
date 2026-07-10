package me.moonways.bridgenet.model.service.gui;

import me.moonways.bridgenet.model.service.gui.click.ClickAction;
import me.moonways.bridgenet.model.service.gui.item.Items;
import me.moonways.bridgenet.model.service.players.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Представляет собой модель сервиса для управления GUI интерфейсами в Minecraft.
 * Этот интерфейс определяет методы для получения списка элементов,
 * создания GUI, получения существующего GUI по его идентификатору,
 * а также для обработки событий кликов в GUI.
 */
public interface GuiServiceModel {

    /**
     * @return - Доступ к созданию предметов.
     */
    Items getItems();

    /**
     * Создать новый GUI по заданному типу.
     *
     * @param type - Тип GUI для создания.
     * @return - Созданный объект GUI.
     */
    Gui createGui(GuiType type);

    /**
     * Создать новый GUI с использованием заданного описания.
     *
     * @param description - Описание GUI для создания.
     * @return - Созданный объект GUI.
     */
    Gui createGui(GuiDescription description);

    /**
     * Получить существующий GUI по его идентификатору.
     *
     * @param id - Идентификатор GUI.
     * @return - Объект Optional, содержащий GUI, если он существует, в противном случае пустое значение.
     */
    Optional<Gui> getGui(UUID id);

    /**
     * Вызвать событие клика в указанном GUI с заданным событием клика.
     * @param clickAction - Описание объектов, которые взаимодействуют с воспроизводимым кликом.
     */
    void fireClickAction(ClickAction clickAction);

    /**
     * Вызвать событие закрытия текущего инвентаря
     * игрока.
     * @param player - игрок, для которого вызываем закрытие инвентаря.
     */
    void fireCloseGui(Player player);
}
