package me.moonways.endpoint.settings;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.jdbc.entity.EntityRepository;
import me.moonways.bridgenet.jdbc.entity.EntityRepositoryFactory;
import me.moonways.bridgenet.model.service.settings.Setting;
import me.moonways.bridgenet.model.service.settings.SettingID;

import java.util.Map;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public class PlayerSettings {

    private final Map<SettingID<?>, Setting<?>> settings;
    private final UUID playerID;

    @Inject
    private EntityRepositoryFactory entityRepositoryFactory;

    @SuppressWarnings("unchecked")
    public <T> Setting<T> get(SettingID<T> id) {
        if (settings.containsKey(id)) {
            return (Setting<T>) settings.get(id);
        }
        return createAndInsert(id);
    }

    private <T> Setting<T> create(SettingID<T> id) {
        Setting<T> setting = new SettingStub<>(id);
        settings.put(id, setting);

        setting.onChanged(value -> updateEntity(setting));

        return setting;
    }

    private <T> Setting<T> createAndInsert(SettingID<T> id) {
        Setting<T> setting = create(id);
        insertEntity(setting);
        return setting;
    }

    public void loadAll() {
        EntityRepository<EntitySetting> repository
                = entityRepositoryFactory.fromEntityType(EntitySetting.class);
        repository.search(
                        repository.beginCriteria()
                                .andEquals(EntitySetting::getPlayerId, playerID))
                .subscribeEach(this::loadEntity); // todo работает или нет ПРОВЕРЬ ???
    }

    private void loadEntity(EntitySetting entitySetting) {
        //noinspection unchecked
        SettingID<Object> settingID = (SettingID<Object>) SettingID.fromUuid(entitySetting.getSettingId())
                .orElseThrow(() -> new SettingsEndpointException("setting by id - " + entitySetting.getSettingId() + " is not found"));
        Setting<Object> setting = create(settingID);
        setting.set(entitySetting.getValue());
    }

    private void updateEntity(Setting<?> setting) {
        EntitySetting entitySetting = EntitySetting.fromSetting(playerID, setting);
        EntityRepository<EntitySetting> repository
                = entityRepositoryFactory.fromEntityType(entitySetting);

        repository.delete(entitySetting);

        insertEntity(setting);
    }

    private <T> void insertEntity(Setting<?> setting) {
        EntitySetting entitySetting = EntitySetting.fromSetting(playerID, setting);
        EntityRepository<EntitySetting> repository
                = entityRepositoryFactory.fromEntityType(entitySetting);

        repository.insert(entitySetting);
    }
}
