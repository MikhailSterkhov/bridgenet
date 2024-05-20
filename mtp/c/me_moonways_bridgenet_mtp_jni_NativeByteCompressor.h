#include <jni.h>

#ifndef _Included_me_moonways_bridgenet_mtp_transfer_jni_NativeByteCompression
#define _Included_me_moonways_bridgenet_mtp_transfer_jni_NativeByteCompression
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jbyteArray JNICALL Java_me_moonways_bridgenet_mtp_transfer_jni_NativeByteCompression_compress
        (JNIEnv *env, jobject parent, jbyteArray bytes);

JNIEXPORT jbyteArray JNICALL Java_me_moonways_bridgenet_mtp_transfer_jni_NativeByteCompression_decompress
        (JNIEnv *env, jobject obj, jbyteArray compressedData, jint uncompressedSize);

#ifdef __cplusplus
}
#endif
#endif