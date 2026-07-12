package me.moonways.rmap.auth;

import lombok.Value;
import me.moonways.rmap.codec.RmapByteWriter;
import me.moonways.rmap.codec.RmapCodecException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Взаимная HMAC-SHA256 аутентификация RMAP (спека §4.3). */
public final class HmacAuth {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte PREFIX_CLIENT = 'C';
    private static final byte PREFIX_SERVER = 'S';

    @Value
    public static class AuthInput {
        byte[] challenge;
        byte[] clientNonce;
        int protocolVersion;
        int codecSchemaVersion;
        String appVersion;
        String clientName;
    }

    private HmacAuth() {
    }

    public static byte[] randomBytes32() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return b;
    }

    public static byte[] clientMac(byte[] key, AuthInput in) {
        return mac(key, PREFIX_CLIENT, in);
    }

    public static byte[] serverMac(byte[] key, AuthInput in) {
        return mac(key, PREFIX_SERVER, in);
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b); // MessageDigest.isEqual — constant-time на JDK 6+
    }

    private static byte[] mac(byte[] key, byte prefix, AuthInput in) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            RmapByteWriter buf = new RmapByteWriter();
            buf.writeByte(prefix);
            buf.writeRaw(in.getChallenge());
            buf.writeRaw(in.getClientNonce());
            buf.writeInt(in.getProtocolVersion());
            buf.writeInt(in.getCodecSchemaVersion());
            byte[] app = in.getAppVersion().getBytes(StandardCharsets.UTF_8);
            byte[] name = in.getClientName().getBytes(StandardCharsets.UTF_8);
            buf.writeInt(app.length);
            buf.writeRaw(app);
            buf.writeInt(name.length);
            buf.writeRaw(name);
            return mac.doFinal(buf.toByteArray());
        } catch (java.security.GeneralSecurityException e) {
            throw new RmapCodecException("HMAC failure", e);
        }
    }
}
