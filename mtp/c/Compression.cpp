#include <jni.h>
#include <vector>
#include <zlib.h>
#include "me_moonways_bridgenet_mtp_jni_NativeByteCompressor.h"

using namespace std;

extern "C" {
JNIEXPORT jbyteArray JNICALL Java_me_moonways_bridgenet_mtp_transfer_jni_NativeByteCompression_compress
        (JNIEnv *env, jobject parent, jbyteArray bytes) {
    jsize dataSize = env->GetArrayLength(bytes);
    jbyte *byteArray = env->GetByteArrayElements(bytes, NULL);

    std::vector<unsigned char> inputData(dataSize);
    for (int i = 0; i < dataSize; ++i) {
        inputData[i] = static_cast<unsigned char>(byteArray[i]);
    }

    std::vector<unsigned char> compressedData;

    z_stream stream;
    stream.zalloc = Z_NULL;
    stream.zfree = Z_NULL;
    stream.opaque = Z_NULL;

    if (deflateInit(&stream, Z_DEFAULT_COMPRESSION) != Z_OK)
        return nullptr;

    stream.next_in = inputData.data();
    stream.avail_in = dataSize;

    const size_t chunkSize = 1024;
    unsigned char out[chunkSize];

    do {
        stream.next_out = out;
        stream.avail_out = chunkSize;

        if (deflate(&stream, Z_FINISH) == Z_STREAM_ERROR) {
            deflateEnd(&stream);
            return nullptr;
        }

        size_t compressedSize = chunkSize - stream.avail_out;
        compressedData.insert(compressedData.end(), out, out + compressedSize);

    } while (stream.avail_out == 0);

    deflateEnd(&stream);

    jbyteArray result = env->NewByteArray(compressedData.size());
    env->SetByteArrayRegion(result, 0, compressedData.size(), reinterpret_cast<jbyte *>(compressedData.data()));

    return result;
}

JNIEXPORT jbyteArray JNICALL Java_me_moonways_bridgenet_mtp_transfer_jni_NativeByteCompression_decompress
        (JNIEnv *env, jobject obj, jbyteArray compressedData, jint uncompressedSize) {
    jsize dataSize = env->GetArrayLength(compressedData);
    jbyte *byteArray = env->GetByteArrayElements(compressedData, NULL);

    vector<unsigned char> inputData(dataSize);
    for (int i = 0; i < dataSize; ++i) {
        inputData[i] = static_cast<unsigned char>(byteArray[i]);
    }

    vector<unsigned char> decompressedData(uncompressedSize);

    z_stream stream;
    stream.zalloc = Z_NULL;
    stream.zfree = Z_NULL;
    stream.opaque = Z_NULL;

    if (inflateInit(&stream) != Z_OK)
        return nullptr;

    stream.next_in = inputData.data();
    stream.avail_in = dataSize;

    stream.next_out = decompressedData.data();
    stream.avail_out = uncompressedSize;

    if (inflate(&stream, Z_FINISH) != Z_STREAM_END) {
        inflateEnd(&stream);
        return nullptr;
    }

    inflateEnd(&stream);

    jbyteArray result = env->NewByteArray(uncompressedSize);
    env->SetByteArrayRegion(result, 0, uncompressedSize, reinterpret_cast<jbyte *>(decompressedData.data()));

    return result;
}
}
