package me.moonways.rmap.codec.builtin;

import me.moonways.rmap.codec.RmapInput;
import me.moonways.rmap.codec.RmapOutput;
import me.moonways.rmap.codec.ValueCodec;

import java.sql.Timestamp;

public final class TimestampCodec implements ValueCodec<Timestamp> {

    public Class<Timestamp> type() {
        return Timestamp.class;
    }

    public void write(RmapOutput out, Timestamp v) {
        out.writeLong(v.getTime());
    }

    public Timestamp read(RmapInput in) {
        return new Timestamp(in.readLong());
    }
}
