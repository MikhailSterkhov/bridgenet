package me.moonways.bridgenet.mtp.transfer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.mtp.jni.NativeByteCompressor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

@Log4j2
@UtilityClass
public class ByteCompression {

    private static final byte HAS_COMPRESSION_STATE = 1;
    private static final byte NO_COMPRESSION_STATE = 0;

    private final NativeByteCompressor nativeCompressor = new NativeByteCompressor();

    public byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

        deflater.finish();
        byte[] buffer = new byte[1024];

        while (!deflater.finished()) {

            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        outputStream.close();
        return outputStream.toByteArray();
    }

    public byte[] decompress(byte[] data) throws IOException, DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];

        while (!inflater.finished()) {

            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        outputStream.close();
        return outputStream.toByteArray();
    }

    public void write(byte[] array, ByteBuf byteBuf) throws IOException {
        byte[] compressedBytes = nativeCompressor.compress(array);

        boolean hasCompression = array.length > compressedBytes.length;

        byteBuf.writeByte(hasCompression ? HAS_COMPRESSION_STATE : NO_COMPRESSION_STATE);
        byteBuf.writeBytes(hasCompression ? compressedBytes : array);
    }

    public ByteBuf read(ByteBuf byteBuf) throws IOException, DataFormatException {
        byte state = byteBuf.readByte();
        byte[] array = new byte[byteBuf.readableBytes()];

        byteBuf.readBytes(array);
        ByteBuf buffer = Unpooled.buffer();

        return state == HAS_COMPRESSION_STATE ? buffer.writeBytes(nativeCompressor.decompress(array)) : buffer.writeBytes(array);
    }
}