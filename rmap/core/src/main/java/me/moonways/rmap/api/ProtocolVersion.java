package me.moonways.rmap.api;

/** Версии протокола/схемы кодека для fail-fast сверки в handshake (спека §4.3). */
public final class ProtocolVersion {

    public static final int PROTOCOL_VERSION = 1;
    public static final int CODEC_SCHEMA_VERSION = 1;

    private ProtocolVersion() {
    }
}
