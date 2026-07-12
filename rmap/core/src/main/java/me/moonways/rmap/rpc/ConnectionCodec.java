package me.moonways.rmap.rpc;

import me.moonways.rmap.codec.ClassInterner;
import me.moonways.rmap.codec.CodecContext;
import me.moonways.rmap.codec.RefContext;
import me.moonways.rmap.codec.RmapByteWriter;
import me.moonways.rmap.codec.RmapCodec;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-connection кодек-обвязка: write-сторона — encode+enqueue атомарно под локом (§5.2a:
 * definition-кадр уходит в outbound раньше любого reference-кадра); read-сторона — контекст
 * для строго последовательного (per-connection SerialExecutor) декодирования в wire-порядке.
 */
public final class ConnectionCodec {

    private final RmapCodec codec;
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final ClassInterner writeInterner;
    private final ClassInterner readInterner;
    private final Object writeLock = new Object();
    private volatile RefContext refs; // задача 5; до неё null

    public ConnectionCodec(RmapCodec codec, Set<String> initialWhitelist) {
        this(codec, initialWhitelist, 4096);
    }

    public ConnectionCodec(RmapCodec codec, Set<String> initialWhitelist, int maxInternedClasses) {
        this.codec = codec;
        if (initialWhitelist != null) {
            this.whitelist.addAll(initialWhitelist);
        }
        this.writeInterner = new ClassInterner(maxInternedClasses);
        this.readInterner = new ClassInterner(maxInternedClasses);
    }

    public interface PayloadWriter {
        void write(RmapByteWriter out, CodecContext ctx);
    }

    /** Атомарно: payload-encode + постановка кадра в outbound (§5.2a). */
    public void encodeAndSend(RmapConnection conn, FrameType type, long callId, PayloadWriter w) {
        synchronized (writeLock) {
            RmapByteWriter out = new RmapByteWriter();
            w.write(out, CodecContext.of(writeInterner, null, refs));
            conn.send(new Frame(type, callId, out.toByteArray()));
        }
    }

    /** Read-контекст. Вызывать ТОЛЬКО из per-connection serial-decode задачи (wire-порядок §5.2a). */
    public CodecContext readCtx() {
        return CodecContext.of(readInterner, whitelist, refs);
    }

    public void addWhitelist(Collection<String> fqns) {
        whitelist.addAll(fqns);
    }

    public void setRefContext(RefContext refs) {
        this.refs = refs;
    }

    public RmapCodec codec() {
        return codec;
    }
}
