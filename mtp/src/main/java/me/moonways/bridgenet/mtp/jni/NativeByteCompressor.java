package me.moonways.bridgenet.mtp.jni;

public class NativeByteCompressor {

    static {
        System.loadLibrary("NativeCPPCompression");
    }

    public native byte[] compress(byte[] input);

    public native byte[] decompress(byte[] input);
}
