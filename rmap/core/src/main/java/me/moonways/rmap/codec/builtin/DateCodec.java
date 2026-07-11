package me.moonways.rmap.codec.builtin;

import me.moonways.rmap.codec.RmapInput;
import me.moonways.rmap.codec.RmapOutput;
import me.moonways.rmap.codec.ValueCodec;

import java.util.Date;

public final class DateCodec implements ValueCodec<Date> {

    public Class<Date> type() {
        return Date.class;
    }

    public void write(RmapOutput out, Date v) {
        out.writeLong(v.getTime());
    }

    public Date read(RmapInput in) {
        return new Date(in.readLong());
    }
}
