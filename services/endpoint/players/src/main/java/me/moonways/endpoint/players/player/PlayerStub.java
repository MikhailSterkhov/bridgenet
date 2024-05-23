package me.moonways.endpoint.players.player;

import lombok.Getter;
import me.moonways.bridgenet.model.audience.ComponentHolders;
import me.moonways.bridgenet.model.audience.MessageDirection;
import me.moonways.bridgenet.model.event.AudienceSendEvent;
import me.moonways.bridgenet.model.message.SendMessage;
import me.moonways.bridgenet.model.message.SendTitle;
import me.moonways.bridgenet.model.service.language.Language;
import me.moonways.bridgenet.model.service.language.Message;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.model.service.players.component.PlayerConnection;
import me.moonways.bridgenet.model.service.players.component.PlayerStore;
import me.moonways.bridgenet.model.service.players.component.statistic.ActivityStatistics;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.model.util.Title;
import me.moonways.bridgenet.model.util.TitleFade;
import me.moonways.endpoint.players.database.PlayerDescription;
import net.kyori.adventure.text.Component;

import java.rmi.RemoteException;
import java.util.Optional;
import java.util.UUID;

@Getter
public class PlayerStub extends OfflinePlayerStub implements Player {

    private final ActivityStatistics statistics;
    private final PlayerConnection connection;

    private final PlayerStore playerStore;

    public PlayerStub(UUID id, String name, PlayerDescription description, PlayerStore playerStore) {
        super(id, name, description);

        this.statistics = new ActivityStatisticsStub();
        this.connection = new PlayerConnectionStub(this);

        this.playerStore = playerStore;
    }

    @Override
    public boolean isOnline() throws RemoteException {
        return playerStore.get(getId()).isPresent();
    }

    private Optional<AudienceSendEvent> writeAudienceMessage(Object message, MessageDirection direction, Component[] components) throws RemoteException {
        return !writeServerChannel(message) ? Optional.empty() :
                Optional.of(
                        AudienceSendEvent.builder()
                                .entity(this)
                                .direction(direction)
                                .components(components)
                                .build());
    }

    @Override
    protected Optional<AudienceSendEvent> doMessageSend(Component message, ComponentHolders holders) throws RemoteException {
        SendMessage sendMessage = createSendMessage(SendMessage.ChatType.CHAT, message, holders);

        return writeAudienceMessage(sendMessage, MessageDirection.MESSAGE,
                new Component[]{Component.text(sendMessage.getMessage())});
    }

    @Override
    public Optional<AudienceSendEvent> sendActionbar(Component message) throws RemoteException {
        return sendActionbar(message, ComponentHolders.begin());
    }

    @Override
    public Optional<AudienceSendEvent> sendActionbar(Component message, ComponentHolders holders) throws RemoteException {
        SendMessage sendMessage = createSendMessage(SendMessage.ChatType.ACTION_BAR, message, holders);

        return writeAudienceMessage(sendMessage, MessageDirection.ACTIONBAR,
                new Component[]{Component.text(sendMessage.getMessage())});
    }

    @Override
    public Optional<AudienceSendEvent> sendActionbar(Message message) throws RemoteException {
        return sendActionbar(message, ComponentHolders.begin());
    }

    @Override
    public Optional<AudienceSendEvent> sendActionbar(Message message, ComponentHolders holders) throws RemoteException {
        return sendActionbar(languageServiceModel.message(getLanguage(), message));
    }

    @Override
    public Optional<AudienceSendEvent> sendActionbar(String message) throws RemoteException {
        return sendMessage(message, ComponentHolders.begin());
    }

    @Override
    public Optional<AudienceSendEvent> sendActionbar(String message, ComponentHolders holders) throws RemoteException {
        return sendMessage(Component.text(message), holders);
    }

    @Override
    public Optional<AudienceSendEvent> sendComponentTitle(Title<Component> title) throws RemoteException {
        return sendComponentTitle(title, ComponentHolders.begin());
    }

    @Override
    public Optional<AudienceSendEvent> sendComponentTitle(Title<Component> title, ComponentHolders holders) throws RemoteException {
        SendTitle sendTitle = createSendTitle(title, holders);
        return writeAudienceMessage(sendTitle, MessageDirection.TITLE,
                new Component[]
                        {
                                Component.text(sendTitle.getTitle()),
                                Component.text(sendTitle.getSubtitle())
                        });
    }

    @Override
    public Optional<AudienceSendEvent> sendTranslatedTitle(Title<Message> title) throws RemoteException {
        return sendTranslatedTitle(title, ComponentHolders.begin());
    }

    @Override
    public Optional<AudienceSendEvent> sendTranslatedTitle(Title<Message> title, ComponentHolders holders) throws RemoteException {
        Message messageTitle = Optional.ofNullable(title.getTitle()).orElse(Message.empty());
        Message messageSubtitle = Optional.ofNullable(title.getSubtitle()).orElse(Message.empty());

        Language language = getLanguage();
        return sendComponentTitle(
                Title.<Component>builder()
                        .title(languageServiceModel.message(language, messageTitle))
                        .subtitle(languageServiceModel.message(language, messageSubtitle))
                        .fade(title.getFade())
                        .build(),
                holders);
    }

    @Override
    public Optional<AudienceSendEvent> sendTitle(Title<String> title) throws RemoteException {
        return sendTitle(title, ComponentHolders.begin());
    }

    @Override
    public Optional<AudienceSendEvent> sendTitle(Title<String> title, ComponentHolders holders) throws RemoteException {
        return sendComponentTitle(
                Title.<Component>builder()
                        .title(Component.text(title.getTitle()))
                        .subtitle(Component.text(title.getSubtitle()))
                        .fade(title.getFade())
                        .build(),
                holders);
    }

    private SendMessage createSendMessage(SendMessage.ChatType chatType, Component message, ComponentHolders holders) {
        Component holdenComponent = holders.apply(this, message);
        return new SendMessage(getId(), holdenComponent.toString(), chatType);
    }

    private SendTitle createSendTitle(Title<Component> title, ComponentHolders holders) {
        Component componentTitle = Optional.ofNullable(title.getTitle())
                .orElse(Component.empty());

        Component componentSubtitle = Optional.ofNullable(title.getSubtitle())
                .orElse(Component.empty());

        TitleFade fade = Optional.ofNullable(title.getFade()).orElseGet(TitleFade::defaults);

        return new SendTitle(getId(),
                holders.apply(this, componentTitle).toString(),
                holders.apply(this, componentSubtitle).toString(),
                fade.getFadeIn(),
                fade.getStay(),
                fade.getFadeOut()
        );
    }

    private boolean writeServerChannel(Object message) throws RemoteException {
        Optional<EntityServer> serverOptional = connection.getServer();

        if (serverOptional.isPresent()) {
            EntityServer entityServer = serverOptional.get();
            entityServer.getChannel().send(message);

            return true;
        }

        return false;
    }
}
