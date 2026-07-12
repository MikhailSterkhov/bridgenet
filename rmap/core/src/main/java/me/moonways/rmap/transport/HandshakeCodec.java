package me.moonways.rmap.transport;

import lombok.Value;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.codec.RmapByteWriter;

/** Кодирование payload'ов handshake-кадров RMAP (спека §4.2). */
public final class HandshakeCodec {

    private HandshakeCodec() {
    }

    @Value public static class Hello {
        int protocolVersion; String appVersion; int codecSchemaVersion; String clientName; byte[] clientNonce;
    }
    @Value public static class HelloAck {
        int protocolVersion; String appVersion; boolean authRequired; byte[] challenge;
    }

    public static byte[] encodeHello(Hello h) {
        RmapByteWriter w = new RmapByteWriter();
        w.writeInt(h.getProtocolVersion());
        w.writeStr(h.getAppVersion());
        w.writeInt(h.getCodecSchemaVersion());
        w.writeStr(h.getClientName());
        w.writeRaw(h.getClientNonce()); // 32 байта
        return w.toByteArray();
    }

    public static Hello decodeHello(byte[] payload) {
        RmapByteReader r = new RmapByteReader(payload, 0, payload.length);
        int pv = r.readInt();
        String app = r.readStr();
        int cs = r.readInt();
        String name = r.readStr();
        byte[] nonce = r.readRaw(32);
        return new Hello(pv, app, cs, name, nonce);
    }

    public static byte[] encodeHelloAck(HelloAck a) {
        RmapByteWriter w = new RmapByteWriter();
        w.writeInt(a.getProtocolVersion());
        w.writeStr(a.getAppVersion());
        w.writeBool(a.isAuthRequired());
        w.writeRaw(a.getChallenge()); // 32
        return w.toByteArray();
    }

    public static HelloAck decodeHelloAck(byte[] payload) {
        RmapByteReader r = new RmapByteReader(payload, 0, payload.length);
        return new HelloAck(r.readInt(), r.readStr(), r.readBool(), r.readRaw(32));
    }

    // AUTH_RESPONSE: bytes32 clientMac / AUTH_OK: bytes32 serverMac
    public static byte[] encodeMac32(byte[] mac) { return mac.clone(); }
    public static byte[] decodeMac32(byte[] payload) {
        RmapByteReader r = new RmapByteReader(payload, 0, payload.length);
        return r.readRaw(32);
    }

    // PING/PONG: int64 timestamp
    public static byte[] encodeTimestamp(long ts) {
        RmapByteWriter w = new RmapByteWriter();
        w.writeLong(ts);
        return w.toByteArray();
    }
    public static long decodeTimestamp(byte[] payload) {
        return new RmapByteReader(payload, 0, payload.length).readLong();
    }
}
