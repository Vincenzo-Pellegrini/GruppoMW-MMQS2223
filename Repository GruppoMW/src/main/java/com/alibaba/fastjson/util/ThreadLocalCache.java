package com.alibaba.fastjson.util;

import java.lang.ref.SoftReference;
import java.nio.charset.CharsetDecoder;

public class ThreadLocalCache {

    private ThreadLocalCache() {
        throw new IllegalStateException("Utility class");
    }


    public static final int                                 CHARS_CACH_INIT_SIZE_EXP = 10;
    public static final  int                                 CHARS_CACH_MAX_SIZE  = 1024 * 128;
    public static final int                                 CHARS_CACH_MAX_SIZE_EXP = 17;
    private static final ThreadLocal<SoftReference<char[]>> charsBufLocal        = new ThreadLocal<>();

    private static final ThreadLocal<CharsetDecoder>        decoderLocal         = new ThreadLocal<>();

    public static CharsetDecoder getUTF8Decoder() {
        CharsetDecoder decoder = decoderLocal.get();
        if (decoder == null) {
            decoder = new UTF8Decoder();
            decoderLocal.set(decoder);
            decoderLocal.remove();
        }
        return decoder;
    }

    public static void clearChars() {
        charsBufLocal.remove();
    }

    public static char[] getChars(int length) {
        SoftReference<char[]> ref = charsBufLocal.get();

        if (ref == null) {
            return allocate(length);
        }

        char[] chars = ref.get();

        if (chars == null) {
            return allocate(length);
        }

        if (chars.length < length) {
            chars = allocate(length);
        }

        return chars;
    }

    private static char[] allocate(int length) {
        if(length> CHARS_CACH_MAX_SIZE) {
            return new char[length];
        }

        int allocateLength = getAllocateLengthExp(CHARS_CACH_INIT_SIZE_EXP, CHARS_CACH_MAX_SIZE_EXP, length);
        char[] chars = new char[allocateLength];
        charsBufLocal.set(new SoftReference<>(chars));
        return chars;
    }

    private static int getAllocateLengthExp(int minExp, int maxExp, int length) {
        assert (1<<maxExp) >= length;


        int part = length >>> minExp;
        if(part <= 0) {
            return 1<< minExp;
        }
        return 1 << 32 - Integer.numberOfLeadingZeros(length-1);
    }

    // /////////
    public static final int                                 BYTES_CACH_INIT_SIZE = 1024;
    public static final int                                 BYTES_CACH_INIT_SIZE_EXP = 10;
    public static final  int                                 BYTES_CACH_MAX_SIZE  = 1024 * 128;
    public static final int                                 BYTES_CACH_MAX_SIZE_EXP = 17;
    private static final ThreadLocal<SoftReference<byte[]>> bytesBufLocal        = new ThreadLocal<>();

    public static void clearBytes() {
        bytesBufLocal.remove();
    }

    public static byte[] getBytes(int length) {
        SoftReference<byte[]> ref = bytesBufLocal.get();

        if (ref == null) {
            return allocateBytes(length);
        }

        byte[] bytes = ref.get();

        if (bytes == null) {
            return allocateBytes(length);
        }

        if (bytes.length < length) {
            bytes = allocateBytes(length);
        }

        return bytes;
    }

    private static byte[] allocateBytes(int length) {
        if(length > BYTES_CACH_MAX_SIZE) {
            return new byte[length];
        }

        int allocateLength = getAllocateLengthExp(BYTES_CACH_INIT_SIZE_EXP, BYTES_CACH_MAX_SIZE_EXP, length);
        byte[] chars = new byte[allocateLength];
        bytesBufLocal.set(new SoftReference<>(chars));
        return chars;
    }

}