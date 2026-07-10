package me.moonways.endpoint.players.player;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.audience.ComponentHolders;
import me.moonways.bridgenet.model.event.AudienceSendEvent;
import me.moonways.bridgenet.model.service.language.Language;
import me.moonways.bridgenet.model.service.language.LanguageServiceModel;
import me.moonways.bridgenet.model.service.language.Message;
import me.moonways.bridgenet.model.service.permissions.PermissionsServiceModel;
import me.moonways.bridgenet.model.service.permissions.group.PermissionGroup;
import me.moonways.bridgenet.model.service.permissions.permission.Permission;
import me.moonways.bridgenet.model.service.players.OfflinePlayer;
import me.moonways.endpoint.players.PlayerLevelingStub;
import me.moonways.endpoint.players.database.PlayerDescription;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public class OfflinePlayerStub implements OfflinePlayer {

    private static final String PLAYER_IS_OFFLINE = "player \"%s\" is offline";

    @ToString.Include
    private final UUID id;
    @ToString.Include
    private final String name;

    @Getter
    private final PlayerDescription description;

    @Inject
    protected PermissionsServiceModel permissionsServiceModel;
    @Inject
    protected LanguageServiceModel languageServiceModel;

    @Override
    public Set<Permission> getPermissions() {
        return permissionsServiceModel.getPermissions().getActivePermissions(getId());
    }

    @Override
    public PermissionGroup getGroup() {
        return permissionsServiceModel.getGroups().getPlayerGroup(getId()).get();
    }

    @Override
    public Language getLanguage() {
        return languageServiceModel.getPlayerLang(getId());
    }

    @Override
    public int getLevel() {
        return PlayerLevelingStub.getLevel(getTotalExperience());
    }

    @Override
    public int getTotalExperience() {
        return description.getTotalExperience();
    }

    @Override
    public int getExperience() {
        return getTotalExperience() - getExperienceToNextLevel();
    }

    @Override
    public int getExperienceToNextLevel() {
        return PlayerLevelingStub.getExperienceToNextLevel(getLevel());
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    protected Optional<AudienceSendEvent> doMessageSend(Component message, ComponentHolders holders) {
        // override me.
        return Optional.empty();
    }

    @Override
    public final Optional<AudienceSendEvent> sendMessage(@NotNull Component message) {
        if (!isOnline()) {
            throw new UnsupportedOperationException(String.format(PLAYER_IS_OFFLINE, getName()));
        }
        return sendMessage(message, ComponentHolders.begin());
    }

    @Override
    public final Optional<AudienceSendEvent> sendMessage(@NotNull Message message) {
        if (!isOnline()) {
            throw new UnsupportedOperationException(String.format(PLAYER_IS_OFFLINE, getName()));
        }
        return sendMessage(message, ComponentHolders.begin());
    }

    @Override
    public final Optional<AudienceSendEvent> sendMessage(@Nullable String message) {
        if (!isOnline()) {
            throw new UnsupportedOperationException(String.format(PLAYER_IS_OFFLINE, getName()));
        }
        return sendMessage(message, ComponentHolders.begin());
    }

    @Override
    public final Optional<AudienceSendEvent> sendMessage(@NotNull Component message, @NotNull ComponentHolders holders) {
        if (!isOnline()) {
            throw new UnsupportedOperationException(String.format(PLAYER_IS_OFFLINE, getName()));
        }
        return doMessageSend(message, holders);
    }

    @Override
    public final Optional<AudienceSendEvent> sendMessage(@NotNull Message message, @NotNull ComponentHolders holders) {
        if (!isOnline()) {
            throw new UnsupportedOperationException(String.format(PLAYER_IS_OFFLINE, getName()));
        }
        Component component = languageServiceModel.message(getLanguage(), message);
        return sendMessage(component, holders);
    }

    @Override
    public final Optional<AudienceSendEvent> sendMessage(@Nullable String message, @NotNull ComponentHolders holders) {
        if (!isOnline()) {
            throw new UnsupportedOperationException(String.format(PLAYER_IS_OFFLINE, getName()));
        }
        return sendMessage(message != null ? Component.text(message) : Component.empty(), holders);
    }

    @Override
    public boolean hasPermission(@NotNull Permission permission) {
        return permissionsServiceModel.getPermissions().hasPermission(id, permission);
    }

    @Override
    public boolean hasPermission(@NotNull String permissionName) {
        return permissionsServiceModel.getPermissions().hasPermission(id, permissionName);
    }
}
