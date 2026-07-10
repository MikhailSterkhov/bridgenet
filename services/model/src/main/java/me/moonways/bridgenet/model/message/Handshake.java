package me.moonways.bridgenet.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.mtp.message.encryption.EncryptedMessage;
import me.moonways.bridgenet.mtp.message.persistence.ClientMessage;
import me.moonways.bridgenet.mtp.message.persistence.ServerMessage;
import me.moonways.bridgenet.mtp.transfer.ByteTransfer;
import me.moonways.bridgenet.mtp.transfer.provider.ToEnumProvider;
import me.moonways.bridgenet.mtp.transfer.provider.ToPropertiesProvider;
import me.moonways.bridgenet.mtp.transfer.provider.ToUUIDProvider;

import java.util.Properties;
import java.util.UUID;

@Getter
@ToString
@EncryptedMessage
@ClientMessage
@AllArgsConstructor
@NoArgsConstructor(onConstructor_ = @Inject)
public class Handshake {
    public enum Type {SERVER, PLAYER}

    @ByteTransfer(provider = ToEnumProvider.class)
    private Type type;

    @ByteTransfer(provider = ToPropertiesProvider.class)
    private Properties properties;

    @ByteTransfer
    private int protocolVersion;

    public interface Result {
        UUID getKey();

        default void onSuccess(Runnable runnable) {
            if (this instanceof Success) {
                runnable.run();
            }
        }

        default void onFailure(Runnable runnable) {
            if (this instanceof Failure) {
                runnable.run();
            }
        }
    }

    @Getter
    @ToString
    @ServerMessage
    @EncryptedMessage
    @AllArgsConstructor
    @NoArgsConstructor(onConstructor_ = @Inject)
    public static class Success implements Result {

        @ByteTransfer(provider = ToUUIDProvider.class)
        private UUID key;
    }

    @Getter
    @ToString
    @ServerMessage
    @EncryptedMessage
    @AllArgsConstructor
    @NoArgsConstructor(onConstructor_ = @Inject)
    public static class Failure implements Result {

        @ByteTransfer(provider = ToUUIDProvider.class)
        private UUID key;
    }
}
