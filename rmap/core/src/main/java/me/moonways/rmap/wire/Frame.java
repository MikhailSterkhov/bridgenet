package me.moonways.rmap.wire;

import lombok.Value;

/** Кадр RMAP: тип + callId (§4.1) + сырой payload. */
@Value
public class Frame {
    FrameType type;
    long callId;
    byte[] payload;
}
