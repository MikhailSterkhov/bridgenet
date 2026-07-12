package me.moonways.rmap.codec.builtin;

import me.moonways.rmap.codec.RmapInput;
import me.moonways.rmap.codec.RmapOutput;
import me.moonways.rmap.codec.ValueCodec;

import java.net.InetSocketAddress;

public final class InetSocketAddressCodec implements ValueCodec<InetSocketAddress> {

    public Class<InetSocketAddress> type() {
        return InetSocketAddress.class;
    }

    public void write(RmapOutput out, InetSocketAddress v) {
        out.writeString(v.getHostString());
        out.writeInt(v.getPort());
    }

    public InetSocketAddress read(RmapInput in) {
        return InetSocketAddress.createUnresolved(in.readString(), in.readInt());
    }
}
