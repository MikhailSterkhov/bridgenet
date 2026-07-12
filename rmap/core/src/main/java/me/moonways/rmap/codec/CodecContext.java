package me.moonways.rmap.codec;

import java.util.Set;

/** Контекст encode/decode-прохода: interner (connection-scoped), whitelist, remote-refs (§5.2a, §10). */
public final class CodecContext {

    private final ClassInterner interner;
    private final Set<String> whitelist;
    private final RefContext refs;

    private CodecContext(ClassInterner interner, Set<String> whitelist, RefContext refs) {
        this.interner = interner;
        this.whitelist = whitelist;
        this.refs = refs;
    }

    public static CodecContext of(ClassInterner interner, Set<String> whitelist) {
        return new CodecContext(interner, whitelist, null);
    }

    public static CodecContext of(ClassInterner interner, Set<String> whitelist, RefContext refs) {
        return new CodecContext(interner, whitelist, refs);
    }

    public ClassInterner interner() { return interner; }
    public Set<String> whitelist() { return whitelist; }
    public RefContext refs() { return refs; }
}
