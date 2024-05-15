package me.moonways.bridgenet.test.data;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.connector.BridgenetConnector;
import me.moonways.bridgenet.connector.BridgenetServerSync;
import me.moonways.bridgenet.connector.description.DeviceDescription;
import me.moonways.bridgenet.model.bus.message.Handshake;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;

import java.util.List;

@Log4j2
public class ExampleConnector extends BridgenetConnector {

    public static final DeviceDescription DEVICE_DESCRIPTION = DeviceDescription.builder()
            .name("Test-01")
            .host("127.0.0.1")
            .port(25565)
            .build();

    @Override
    protected DeviceDescription createDescription() {
        return DEVICE_DESCRIPTION;
    }

    @Override
    public void onConnected(BridgenetNetworkChannel channel) {
        log.info("§d§nSUCCESSFUL CONNECTED TO BRIDGENET SERVER!");
    }

    @Override
    public void onHandshake(Handshake.Result result) {
        result.onSuccess(() -> log.info("§d§nHANDSHAKE SUCCESS EXCHANGED"));
        result.onFailure(() -> log.info("§d§nHANDSHAKE HAS FAILED"));
    }

    public Handshake.Result retryHandshakeExchanging() {
        BridgenetServerSync bridgenet = getBridgenetServerSync();
        return bridgenet.exportDeviceHandshake(DEVICE_DESCRIPTION);
    }

    public List<String> lookupBridgenetRegisteredComamndsList() {
        BridgenetServerSync bridgenet = getBridgenetServerSync();
        return bridgenet.lookupServerCommandsList();
    }
}