package me.moonways.bridgenet.rest.api.socket.codec.v1_0;

import me.moonways.bridgenet.rest.api.socket.codec.v1_1.HttpV1_1Encoder;
import me.moonways.bridgenet.rest.model.HttpProtocol;

import java.io.BufferedWriter;
import java.io.IOException;

public class HttpV1_0Encoder extends HttpV1_1Encoder {

    @Override
    protected void writeChunkedContent(BufferedWriter writer, String content) throws IOException {
        writer.write(content);
    }

    @Override
    public HttpProtocol protocol() {
        return HttpProtocol.HTTP_1_0;
    }
}
