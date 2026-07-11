package me.moonways.rmap.codec.builtin;

import me.moonways.rmap.codec.RmapInput;
import me.moonways.rmap.codec.RmapOutput;
import me.moonways.rmap.codec.ValueCodec;

import java.util.Locale;

public final class LocaleCodec implements ValueCodec<Locale> {

    public Class<Locale> type() {
        return Locale.class;
    }

    public void write(RmapOutput out, Locale v) {
        out.writeString(v.toLanguageTag());
    }

    public Locale read(RmapInput in) {
        return Locale.forLanguageTag(in.readString());
    }
}
