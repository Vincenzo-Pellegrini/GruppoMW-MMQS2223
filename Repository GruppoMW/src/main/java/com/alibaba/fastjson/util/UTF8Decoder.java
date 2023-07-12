package com.alibaba.fastjson.util;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

/*  Legal UTF-8 Byte Sequences
 *
 * #    Code Points      Bits   Bit/Byte pattern
 * 1                     7      0xxxxxxx
 *      U+0000..U+007F          00..7F
 *
 * 2                     11     110xxxxx    10xxxxxx
 *      U+0080..U+07FF          C2..DF      80..BF
 *
 * 3                     16     1110xxxx    10xxxxxx    10xxxxxx
 *      U+0800..U+0FFF          E0          A0..BF      80..BF
 *      U+1000..U+FFFF          E1..EF      80..BF      80..BF
 *
 * 4                     21     11110xxx    10xxxxxx    10xxxxxx    10xxxxxx
 *     U+10000..U+3FFFF         F0          90..BF      80..BF      80..BF
 *     U+40000..U+FFFFF         F1..F3      80..BF      80..BF      80..BF
 *    U+100000..U10FFFF         F4          80..8F      80..BF      80..BF
 *
 */
public class UTF8Decoder extends CharsetDecoder {

    private static final Charset charset = StandardCharsets.UTF_8;

    public UTF8Decoder(){
        super(charset, 1.0f, 1.0f);
    }

    private static boolean isNotContinuation(int b) {
        return (b & 0xc0) != 0x80;
    }

    private static CoderResult lookupN(ByteBuffer src, int n) {
        for (int i = 1; i < n; i++) {
            if (isNotContinuation(src.get())) return CoderResult.malformedForLength(i);
        }
        return CoderResult.malformedForLength(n);
    }

    private CoderResult decodeArrayLoop(ByteBuffer src, CharBuffer dst) {
        byte[] srcArray = src.array();
        int srcPosition = src.arrayOffset() + src.position();
        int srcLength = src.arrayOffset() + src.limit();
        char[] destArray = dst.array();
        int destPosition = dst.arrayOffset() + dst.position();
        int destLength = dst.arrayOffset() + dst.limit();
        int destLengthASCII = destPosition + Math.min(srcLength - srcPosition, destLength - destPosition);
         destPosition = decodeASCIILoop(srcArray, srcPosition, destArray, destPosition, destLengthASCII);

        if (srcPosition >= srcLength) {
            return xflow(src, srcPosition, srcLength, dst, destPosition, 0);
        }

        while (srcPosition < srcLength) {
            int b1 = srcArray[srcPosition];
            if (b1 >= 0) {
                destPosition = decodeOneByteChar(src, srcPosition, dst, destPosition, destLength);
            } else if ((b1 >> 5) == -2) {
                destPosition = decodeTwoByteChar(src, srcPosition, dst, destPosition, destLength);
            }else {
                if(malformed(src, srcPosition, dst, destPosition, 1)!=null){
                    return malformed(src, srcPosition, dst, destPosition, 1);
                }
            }
        }
        return xflow(src, srcPosition, srcLength, dst, destPosition, 0);
    }

    public static CoderResult malformedN(ByteBuffer src, int nb) {
        switch (nb) {
            case 1:
                int b1 = src.get();
                return malformedNLoop(b1,src);
            case 2: // always 1
                return CoderResult.malformedForLength(1);
            case 3:
                b1 = src.get();
                int b2 = src.get(); // no need to lookup b3
                return CoderResult.malformedForLength(malformedForLengthBoolean(b1,b2));
            case 4: // we don't care the speed here
                b1 = src.get() & 0xff;
                b2 = src.get() & 0xff;
                if (malformedForLengthBoolean2(b1,b2)) return CoderResult.malformedForLength(1);
                if (isNotContinuation(src.get())) return CoderResult.malformedForLength(2);
                return CoderResult.malformedForLength(3);
            default:
                throw new IllegalStateException();
        }
    }

    public static int malformedForLengthBoolean(int b1,int b2){
        if((b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) || isNotContinuation(b2)){
            return 1;
        }
        else{
            return 2;
        }
    }

    public static boolean malformedForLengthBoolean2(int b1, int b2){
        return (b1 > 0xf4 || (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) || (b1 == 0xf4 && (b2 & 0xf0) != 0x80) || isNotContinuation(b2));
    }

    public static CoderResult malformedNLoop(int b1,ByteBuffer src){
        if ((b1 >> 2) == -2) {
            // 5 bytes 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
            if (src.remaining() < 4) return CoderResult.UNDERFLOW;
            return lookupN(src, 5);
        }
        if ((b1 >> 1) == -2) {
            // 6 bytes 1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
            if (src.remaining() < 5) {
                return CoderResult.UNDERFLOW;
            }
            return lookupN(src, 6);
        }
        return null;
    }

    private static CoderResult malformed(ByteBuffer src, int sp, CharBuffer dst, int dp, int nb) {
        src.position(sp - src.arrayOffset());
        CoderResult cr = malformedN(src, nb);
        src.position(sp);
        dst.position(dp);
        return cr;
    }

    private static CoderResult xflow(Buffer src, int sp, int sl, Buffer dst, int dp, int nb) {
        src.position(sp);
        dst.position(dp);
        return (nb == 0 || sl - sp < nb) ? CoderResult.UNDERFLOW : CoderResult.OVERFLOW;
    }

    private int decodeASCIILoop(byte[] srcArray, int srcPosition, char[] destArray, int destPosition, int destLengthASCII) {
        while (destPosition < destLengthASCII && srcArray[srcPosition] >= 0) {
            destArray[destPosition++] = (char) srcArray[srcPosition++];
        }
        return destPosition;
    }

    private int decodeOneByteChar(ByteBuffer src, int srcPosition, CharBuffer dst, int destPosition, int destLength) {
        if (destPosition >= destLength) {
            return xflow(src, srcPosition, src.limit(), dst, destPosition, 1).length();
        }
        return destPosition + 1;
    }

    private int decodeTwoByteChar(ByteBuffer src, int srcPosition, CharBuffer dst, int destPosition, int destLength) {
        if (src.limit() - srcPosition < 2 || destPosition >= destLength) {
            return xflow(src, srcPosition, src.limit(), dst, destPosition, 2).length();
        }
        return destPosition + 1;
    }

    protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
        return decodeArrayLoop(src, dst);
    }

}
