package com.alibaba.fastjson.util;

import java.util.Arrays;
public class Base64 {

    private Base64() {
        throw new IllegalStateException("Utility class");
    }
    protected static final char[] CA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    protected static final int[]  IA = new int[256];
    static {
        Arrays.fill(IA, -1);
        for (int i = 0, iS = CA.length; i < iS; i++)
            IA[CA[i]] = i;
        IA['='] = 0;
    }

    /**
     * Decodes a BASE64 encoded char array that is known to be resonably well formatted. The method is about twice as
     * fast as #decode(char[]). The preconditions are:<br>
     * + The array must have a line length of 76 chars OR no line separators at all (one line).<br>
     * + Line separator must be "\r\n", as specified in RFC 2045 + The array must not contain illegal characters within
     * the encoded string<br>
     * + The array CAN have illegal characters at the beginning and end, those will be dealt with appropriately.<br>
     *
     * @param chars The source array. Length 0 will return an empty array. <code>null</code> will throw an exception.
     * @return The decoded array of bytes. May be of length 0.
     */
    public static byte[] decodeFast(char[] chars, int offset, int charsLen) {
        // Check special case
        if (charsLen == 0) {
            return new byte[0];
        }

        int sIx = offset;
        int eIx = offset + charsLen - 1; // Start and end index after trimming.

        // Trim illegal chars from start
        while (sIx < eIx && IA[chars[sIx]] < 0)
            sIx++;

        // Trim illegal chars from end
        while (eIx > 0 && IA[chars[eIx]] < 0)
            eIx--;

        // get the padding count (=) (0, 1 or 2)
        int pad = returnPad(chars,eIx);
        int cCnt = eIx - sIx + 1; // Content count including possible separators
        int sepCnt = returnSepCnt(charsLen,chars,cCnt);

        int len = ((cCnt - sepCnt) * 6 >> 3) - pad; // The number of decoded bytes
        byte[] bytes = new byte[len]; // Preallocate byte[] of exact length

        // Decode all but the last 0 - 2 bytes.
        int d = 0;
        for (int cc = 0, eLen = (len / 3) * 3; d < eLen;) {
            // Assemble three bytes into an int from four "valid" characters.
            int i = IA[chars[sIx++]] << 18 | IA[chars[sIx++]] << 12 | IA[chars[sIx++]] << 6 | IA[chars[sIx++]];

            // Add the bytes
            bytes[d++] = (byte) (i >> 16);
            bytes[d++] = (byte) (i >> 8);
            bytes[d++] = (byte) i;

            // If line separator, jump over it.
            if (sepCnt > 0 && cc == 19) {
                sIx += 2;
            }
        }

        return bytes;
    }

    public static byte[] decodeFast(String chars, int offset, int charsLen) {
        // Check special case
        if (charsLen == 0) {
            return new byte[0];
        }

        int sIx = offset;
        int eIx = offset + charsLen - 1; // Start and end index after trimming.

        // Trim illegal chars from start
        while (sIx < eIx && IA[chars.charAt(sIx)] < 0)
            sIx++;

        // Trim illegal chars from end
        while (eIx > 0 && IA[chars.charAt(eIx)] < 0)
            eIx--;

        // get the padding count (=) (0, 1 or 2)
        int pad = returnPad(chars.toCharArray(),eIx);
        int cCnt = eIx - sIx + 1; // Content count including possible separators
        int sepCnt = returnSepCnt(charsLen,chars.toCharArray(),cCnt);

        int len = ((cCnt - sepCnt) * 6 >> 3) - pad; // The number of decoded bytes
        byte[] bytes = new byte[len]; // Preallocate byte[] of exact length

        // Decode all but the last 0 - 2 bytes.
        int d = 0;
        for (int cc = 0, eLen = (len / 3) * 3; d < eLen;) {
            // Assemble three bytes into an int from four "valid" characters.
            int i = IA[chars.charAt(sIx++)] << 18 | IA[chars.charAt(sIx++)] << 12 | IA[chars.charAt(sIx++)] << 6 | IA[chars.charAt(sIx++)];

            // Add the bytes
            bytes[d++] = (byte) (i >> 16);
            bytes[d++] = (byte) (i >> 8);
            bytes[d++] = (byte) i;

            // If line separator, jump over it.
            if (sepCnt > 0 && cc == 19) {
                sIx += 2;
            }
        }

        return bytes;
    }

    public static int returnPad(char[] chars  , int eIx){
        if(chars[eIx] == '='){
            if((chars[eIx - 1] == '=' )){
                return 2;
            }
            else{
                return 1;
            }
        }
        else{
            return 0;
        }
    }

    public static int returnSepCnt(int charsLen , char[] chars,  int cCnt){
        if(charsLen>76){
            if(chars[76] == '\r'){
                return cCnt/78 << 1;
            }
            else{
                return 0;
            }
        }
        else{
            return 0;
        }
    }
    /**
     * Decodes a BASE64 encoded string that is known to be resonably well formatted. The method is about twice as fast
     * as decode(String). The preconditions are:<br>
     * + The array must have a line length of 76 chars OR no line separators at all (one line).<br>
     * + Line separator must be "\r\n", as specified in RFC 2045 + The array must not contain illegal characters within
     * the encoded string<br>
     * + The array CAN have illegal characters at the beginning and end, those will be dealt with appropriately.<br>
     *
     * @param s The source string. Length 0 will return an empty array. <code>null</code> will throw an exception.
     * @return The decoded array of bytes. May be of length 0.
     */
    public static byte[] decodeFast(String s) {
        // Check special case
        int sLen = s.length();
        if (sLen == 0) {
            return new byte[0];
        }

        int sIx = 0;
        int eIx = sLen - 1; // Start and end index after trimming.

        // Trim illegal chars from start
        while (sIx < eIx && IA[s.charAt(sIx) & 0xff] < 0)
            sIx++;

        // Trim illegal chars from end
        while (eIx > 0 && IA[s.charAt(eIx) & 0xff] < 0)
            eIx--;

        // get the padding count (=) (0, 1 or 2)
        int pad = returnPad(s.toCharArray(),eIx);
        int cCnt = eIx - sIx + 1; // Content count including possible separators
        int sepCnt = returnSepCnt(sLen,s.toCharArray(),cCnt);

        int len = ((cCnt - sepCnt) * 6 >> 3) - pad; // The number of decoded bytes
        byte[] dArr = new byte[len]; // Preallocate byte[] of exact length

        // Decode all but the last 0 - 2 bytes.
        int d = 0;
        for (int cc = 0, eLen = (len / 3) * 3; d < eLen;) {
            // Assemble three bytes into an int from four "valid" characters.
            int i = IA[s.charAt(sIx++)] << 18 | IA[s.charAt(sIx++)] << 12 | IA[s.charAt(sIx++)] << 6
                    | IA[s.charAt(sIx++)];

            // Add the bytes
            dArr[d++] = (byte) (i >> 16);
            dArr[d++] = (byte) (i >> 8);
            dArr[d++] = (byte) i;

            // If line separator, jump over it.
            if (sepCnt > 0 && cc == 19) {
                sIx += 2;
            }
        }

        return dArr;
    }
}