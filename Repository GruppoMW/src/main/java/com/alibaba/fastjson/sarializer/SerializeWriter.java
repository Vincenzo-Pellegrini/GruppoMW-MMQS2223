/*
 * Copyright 1999-2018 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.fastjson.serializer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.util.IOUtils;
import com.alibaba.fastjson.util.RyuDouble;
import com.alibaba.fastjson.util.RyuFloat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

import static com.alibaba.fastjson.util.IOUtils.replaceChars;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public final class SerializeWriter extends Writer {
    private static final ThreadLocal<char[]> bufLocal        = new ThreadLocal<>();
    private static final ThreadLocal<byte[]> bytesBufLocal    = new ThreadLocal<>();
    private static final char[] VALUE_TRUE = ":true".toCharArray();
    private static final char[] VALUE_FALSE = ":false".toCharArray();
    private static int bufferThresholdVariabile = 1024 * 128;

    static {
        try {
            String prop = IOUtils.getStringProperty("fastjson.serializer_buffer_threshold");
            if (prop != null && prop.length() > 0) {
                int serializerBufferThresholdVariabile = Integer.parseInt(prop);
                if (serializerBufferThresholdVariabile >= 64 && serializerBufferThresholdVariabile <= 1024 * 64) {
                    bufferThresholdVariabile = serializerBufferThresholdVariabile * 1024;
                }
            }
        } catch (NumberFormatException error) {
            // skip
        }
    }

    private char[] buf;

    /**
     * The number of chars in the buffer.
     */
    private int                            count;

    int                            features;

    private final Writer                     writer;

    private boolean                        useSingleQuotes;
    boolean                        quoteFieldNames;
    boolean                        sortField;
    boolean                        disableCircularReferenceDetect;
    boolean                        beanToArray;
    boolean                        writeNonStringValueAsString;
    boolean                        notWriteDefaultValue;
    private boolean                        writeEnumUsingName;
    private boolean                        writeEnumUsingToString;
    boolean                        writeDirect;

    private char                           keySeperator;

    private long                           sepcialBits;

    private static final String WRITER_NOT_NULL = "writer not null" ;

    public SerializeWriter(){
        this((Writer) null);
    }

    public SerializeWriter(Writer writer){
        this(writer, JSON.DEFAULT_GENERATE_FEATURE, SerializerFeature.EMPTY);
    }

    public SerializeWriter(SerializerFeature... features){
        this(null, features);
    }

    public SerializeWriter(Writer writer, SerializerFeature... features){
        this(writer, 0, features);
    }

    /**
     * @since 1.2.9
     */
    public SerializeWriter(Writer writer, int defaultFeatures, SerializerFeature... features){
        this.writer = writer;
        bytesBufLocal.remove();
        buf = bufLocal.get();

        if (buf != null) {
            bufLocal.remove();
        } else {
            buf = new char[2048];
        }

        int featuresValue = defaultFeatures;
        for (SerializerFeature feature : features) {
            featuresValue |= feature.getMask();
        }
        this.features = featuresValue;

        computeFeatures();
    }

    public SerializeWriter(int initialSize){
        this(null, initialSize);
    }

    public SerializeWriter(Writer writer, int initialSize){
        this.writer = writer;

        if (initialSize <= 0) {
            throw new IllegalArgumentException("Negative initial size: " + initialSize);
        }
        buf = new char[initialSize];

        computeFeatures();
    }

    public void config(SerializerFeature feature, boolean state) {
        if (state) {
            features |= feature.getMask();
            // 由于枚举序列化特性WRITE_ENUM_USING_TO_STRING和WRITE_ENUM_USING_NAME不能共存，需要检查
            if (feature == SerializerFeature.WRITE_ENUM_USING_TO_STRING) {
                features &= ~SerializerFeature.WRITE_ENUM_USING_NAME.getMask();
            } else if (feature == SerializerFeature.WRITE_ENUM_USING_NAME) {
                features &= ~SerializerFeature.WRITE_ENUM_USING_TO_STRING.getMask();
            }
        } else {
            features &= ~feature.getMask();
        }

        computeFeatures();
    }

    static final int NON_DIRECT_FEATURES = getAnInt()
            ;

    private static int getAnInt() {
        return 0 //
                | SerializerFeature.USE_SINGLE_QUOTES.mask //
                | SerializerFeature.BROWSER_COMPATIBLE.mask //
                | SerializerFeature.PRETTY_FORMAT.mask //
                | SerializerFeature.WRITE_ENUM_USING_TO_STRING.mask
                | SerializerFeature.WRITE_NON_STRING_VALUE_AS_STRING.mask
                | SerializerFeature.WRITE_SLASH_AS_SPECIAL.mask
                | SerializerFeature.IGNORE_ERROR_GETTER.mask
                | SerializerFeature.WRITE_CLASS_NAME.mask
                | SerializerFeature.NOT_WRITE_DEFAULT_VALUE.mask;
    }

    private void computeFeatures() {
        quoteFieldNames = (this.features & SerializerFeature.QUOTE_FIELD_NAMES.mask) != 0;
        useSingleQuotes = (this.features & SerializerFeature.USE_SINGLE_QUOTES.mask) != 0;
        sortField = (this.features & SerializerFeature.SORT_FIELD.mask) != 0;
        disableCircularReferenceDetect = (this.features & SerializerFeature.DISABLE_CIRCULAR_REFERENCE_DETECT.mask) != 0;
        beanToArray = (this.features & SerializerFeature.BEAN_TO_ARRAY.mask) != 0;
        writeNonStringValueAsString = (this.features & SerializerFeature.WRITE_NON_STRING_VALUE_AS_STRING.mask) != 0;
        notWriteDefaultValue = (this.features & SerializerFeature.NOT_WRITE_DEFAULT_VALUE.mask) != 0;
        writeEnumUsingName = (this.features & SerializerFeature.WRITE_ENUM_USING_NAME.mask) != 0;
        writeEnumUsingToString = (this.features & SerializerFeature.WRITE_ENUM_USING_TO_STRING.mask) != 0;

        writeDirect = quoteFieldNames //
                && (this.features & NON_DIRECT_FEATURES) == 0 //
                && (beanToArray || writeEnumUsingName)
        ;

        keySeperator = useSingleQuotes ? '\'' : '"';

        boolean browserSecure = (this.features & SerializerFeature.BROWSER_SECURE.mask) != 0;

        final long S0 = 0x4FFFFFFFFL;
        final long S1 = 0x8004FFFFFFFFL;
        final long S2 = 0x50000304ffffffffL;

        long l = (features & SerializerFeature.WRITE_SLASH_AS_SPECIAL.mask) != 0 ? S1 : S0;
        sepcialBits = browserSecure
                ? S2
                : l;
    }

    public boolean isEnabled(SerializerFeature feature) {
        return (this.features & feature.mask) != 0;
    }

    public boolean isEnabled(int feature) {
        return (this.features & feature) != 0;
    }

    /**
     * Writes a character to the buffer.
     */
    @Override
    public void write(int c) {
        int newcount = count + 1;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                flush();
                newcount = 1;
            }
        }
        buf[count] = (char) c;
        count = newcount;
    }

    /**
     * Writes characters to the buffer.
     *
     * @param c the data to be written
     * @param off the start offset in the data
     * @param len the number of chars that are written
     */
    public void write(char[] c, int off, int len) {
        if (off < 0 //
                || off > c.length //
                || len < 0 //
                || off + len > c.length //
                || off + len < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        int newcount = count + len;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                do {
                    int rest = buf.length - count;
                    System.arraycopy(c, off, buf, count, rest);
                    count = buf.length;
                    flush();
                    len -= rest;
                    off += rest;
                } while (len > buf.length);
                newcount = len;
            }
        }
        System.arraycopy(c, off, buf, count, len);
        count = newcount;

    }

    public void expandCapacity(int minimumCapacity) {

        int newCapacity = buf.length + (buf.length >> 1) + 1;

        if (newCapacity < minimumCapacity) {
            newCapacity = minimumCapacity;
        }
        char[] newValue = new char[newCapacity];
        System.arraycopy(buf, 0, newValue, 0, count);

        if (buf.length < bufferThresholdVariabile) {
            char[] charsLocal = bufLocal.get();
            if (charsLocal == null || charsLocal.length < buf.length) {
                bufLocal.set(buf);
            }
        }

        buf = newValue;
    }
    @Override
    public SerializeWriter append(CharSequence csq) {
        String s = (csq == null ? "null" : csq.toString());
        write(s, 0, s.length());
        return this;
    }
    @Override
    public SerializeWriter append(CharSequence csq, int start, int end) {
        String s = (csq == null ? "null" : csq).subSequence(start, end).toString();
        write(s, 0, s.length());
        return this;
    }
    @Override
    public SerializeWriter append(char c) {
        write(c);
        return this;
    }

    /**
     * Write a portion of a string to the buffer.
     *
     * @param str String to be written from
     * @param off Offset from which to start reading characters
     * @param len Number of characters to be written
     */
    @Override
    public void write(String str, int off, int len) {
        int newcount = count + len;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                do {
                    int rest = buf.length - count;
                    str.getChars(off, off + rest, buf, count);
                    count = buf.length;
                    flush();
                    len -= rest;
                    off += rest;
                } while (len > buf.length);
                newcount = len;
            }
        }
        str.getChars(off, off + len, buf, count);
        count = newcount;
    }

    /**
     * Writes the contents of the buffer to another character stream.
     *
     * @param out the output stream to write to
     * @throws IOException If an I/O error occurs.
     */
    public void writeTo(Writer out) throws IOException {
        if (this.writer != null) {
            throw new UnsupportedOperationException(WRITER_NOT_NULL);
        }
        out.write(buf, 0, count);
    }

    public int writeToEx(OutputStream out, Charset charset) throws IOException {
        if (this.writer != null) {
            throw new UnsupportedOperationException(WRITER_NOT_NULL);
        }

        if (charset == IOUtils.UTF8) {
            return encodeToUTF8(out);
        } else {
            byte[] bytes = new String(buf, 0, count).getBytes(charset);
            out.write(bytes);
            return bytes.length;
        }
    }

    /**
     * Returns a copy of the input data.
     *
     * @return an array of chars copied from the input data.
     */
    public char[] toCharArray() {
        if (this.writer != null) {
            throw new UnsupportedOperationException(WRITER_NOT_NULL);
        }

        char[] newValue = new char[count];
        System.arraycopy(buf, 0, newValue, 0, count);
        return newValue;
    }

    /**
     * only for springwebsocket
     */
    public char[] toCharArrayForSpringWebSocket() {
        if (this.writer != null) {
            throw new UnsupportedOperationException(WRITER_NOT_NULL);
        }

        char[] newValue = new char[count - 2];
        System.arraycopy(buf, 1, newValue, 0, count - 2);
        return newValue;
    }

    public byte[] toBytes(Charset charset) {
        if (this.writer != null) {
            throw new UnsupportedOperationException(WRITER_NOT_NULL);
        }

        if (charset == IOUtils.UTF8) {
            return encodeToUTF8Bytes();
        } else {
            return new String(buf, 0, count).getBytes(charset);
        }
    }

    private int encodeToUTF8(OutputStream out) throws IOException {

        int bytesLength = (int) (count * (double) 3);
        byte[] bytes = bytesBufLocal.get();

        if (bytes == null) {
            bytes = new byte[1024 * 8];
            bytesBufLocal.set(bytes);
        }
        byte[] bytesLocal = bytes;

        if (bytes.length < bytesLength) {
            bytes = new byte[bytesLength];
        }

        int position = IOUtils.encodeUTF8(buf, 0, count, bytes);
        out.write(bytes, 0, position);

        if (bytes != bytesLocal && bytes.length <= bufferThresholdVariabile) {
            bytesBufLocal.set(bytes);
        }

        return position;
    }

    private byte[] encodeToUTF8Bytes() {
        int bytesLength = (int) (count * (double) 3);
        byte[] bytes = bytesBufLocal.get();

        if (bytes == null) {
            bytes = new byte[1024 * 8];
            bytesBufLocal.set(bytes);
        }
        byte[] bytesLocal = bytes;

        if (bytes.length < bytesLength) {
            bytes = new byte[bytesLength];
        }

        int position = IOUtils.encodeUTF8(buf, 0, count, bytes);
        byte[] copy = new byte[position];
        System.arraycopy(bytes, 0, copy, 0, position);

        if (bytes != bytesLocal && bytes.length <= bufferThresholdVariabile) {
            bytesBufLocal.set(bytes);
        }

        return copy;
    }

    public int size() {
        return count;
    }

    public String toString() {
        return new String(buf, 0, count);
    }

    /**
     * Close the stream. This method does not release the buffer, since its contents might still be required. Note:
     * Invoking this method in this class will have no effect.
     */
    public void close() {
        if (writer != null && count > 0) {
            flush();
        }
        if (buf.length <= bufferThresholdVariabile) {
            bufLocal.set(buf);
        }

        this.buf = null;
    }
    @Override
    public void write(String text) {
        if (text == null) {
            writeNull();
            return;
        }

        write(text, 0, text.length());
    }

    public void writeInt(int i) {
        if (i == Integer.MIN_VALUE) {
            write("-2147483648");
            return;
        }

        int size = (i < 0) ? IOUtils.stringSize(-i) + 1 : IOUtils.stringSize(i);

        int newcount = count + size;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                char[] chars = new char[size];
                IOUtils.getChars(i, size, chars);
                write(chars, 0, chars.length);
                return;
            }
        }

        IOUtils.getChars(i, newcount, buf);

        count = newcount;
    }

    public void writeByteArray(byte[] bytes) {
        if (isEnabled(SerializerFeature.WRITE_CLASS_NAME.mask)) {
            writeHex(bytes);
            return;
        }

        int bytesLen = bytes.length;
        final char quote = useSingleQuotes ? '\'' : '"';
        if (bytesLen == 0) {
            String emptyString = useSingleQuotes ? "''" : "\"\"";
            write(emptyString);
            return;
        }

        final char[] caVariabile = IOUtils.CA;

        // base64 algorithm author Mikael Grev
        int eLen = (bytesLen / 3) * 3; // Length of even 24-bits.
        int charsLen = ((bytesLen - 1) / 3 + 1) << 2; // base64 character count

        int offset = count;
        int newcount = count + charsLen + 2;

        bytesArrayRefactor(bytes,newcount,eLen,quote,caVariabile,bytesLen);

        count = newcount;
        buf[offset++] = quote;

        // Encode even 24-bits
        int s = 0 ;
        int d = offset;
        while (s < eLen) {
            // Copy next three bytes into lower 24 bits of int, paying attension to sign.
            int i = (bytes[s++] & 0xff) << 16 | (bytes[s++] & 0xff) << 8 | (bytes[s++] & 0xff);

            // Encode the int into four chars
            buf[d++] = caVariabile[(i >>> 18) & 0x3f];
            buf[d++] = caVariabile[(i >>> 12) & 0x3f];
            buf[d++] = caVariabile[(i >>> 6) & 0x3f];
            buf[d++] = caVariabile[i & 0x3f];

            s++;
        }

        // Pad and encode last bits if source isn't even 24 bits.
        int left = bytesLen - eLen; // 0 - 2.
        if (left > 0) {
            // Prepare the int
            int i = ((bytes[eLen] & 0xff) << 10) | (left == 2 ? ((bytes[bytesLen - 1] & 0xff) << 2) : 0);

            // Set last four chars
            buf[newcount - 5] = caVariabile[i >> 12];
            buf[newcount - 4] = caVariabile[(i >>> 6) & 0x3f];
            buf[newcount - 3] = left == 2 ? caVariabile[i & 0x3f] : '=';
            buf[newcount - 2] = '=';
        }
        buf[newcount - 1] = quote;
    }

    private void bytesArrayRefactor(byte[] bytes, int newcount, int eLen, char quote, char[] cA, int bytesLen){
        if (newcount > buf.length) {
            if (writer != null) {
                write(quote);

                int s = 0;
                while (s < eLen) {
                    // Copy next three bytes into lower 24 bits of int, paying attension to sign.
                    int i = (bytes[s++] & 0xff) << 16 | (bytes[s++] & 0xff) << 8 | (bytes[s++] & 0xff);

                    // Encode the int into four chars
                    write(cA[(i >>> 18) & 0x3f]);
                    write(cA[(i >>> 12) & 0x3f]);
                    write(cA[(i >>> 6) & 0x3f]);
                    write(cA[i & 0x3f]);
                    s++;
                }

                // Pad and encode last bits if source isn't even 24 bits.
                int left = bytesLen - eLen; // 0 - 2.
                if (left > 0) {
                    // Prepare the int
                    int i = iReturnedValue(bytes,left,eLen,bytesLen);

                    // Set last four chars
                    write(cA[i >> 12]);
                    write(cA[(i >>> 6) & 0x3f]);
                    write(left == 2 ? cA[i & 0x3f] : '=');
                    write('=');
                }

                write(quote);
            }
            expandCapacity(newcount);
        }
    }

    private int iReturnedValue(byte[] bytes, int left, int eLen, int bytesLen){
        return ((bytes[eLen] & 0xff) << 10) | (left == 2 ? ((bytes[bytesLen - 1] & 0xff) << 2) : 0);
    }

    public void writeHex(byte[] bytes) {
        int newcount = count + bytes.length * 2 + 3;
        newCountMajor(newcount);

        buf[count++] = 'x';
        buf[count++] = '\'';

        for (byte b : bytes) {
            int a = b & 0xFF;
            int b0 = a >> 4;
            int b1 = a & 0xf;

            buf[count++] = (char) (b0 + (b0 < 10 ? 48 : 55));
            buf[count++] = (char) (b1 + (b1 < 10 ? 48 : 55));
        }
        buf[count++] = '\'';
    }

    public void writeFloat(float value, boolean checkWriteClassName) {
        if (valueBoolean(value)) {
            writeNull();
        } else {
            int newcount = count + 15;
            if (newcount > buf.length) {
                if (writer == null) {
                    expandCapacity(newcount);
                } else {
                    String str = RyuFloat.toString(value);
                    write(str, 0, str.length());

                    if (checkWriteClassNameBoolean(checkWriteClassName)) {
                        write('F');
                    }
                    return;
                }
            }

            int len = RyuFloat.toString(value, buf, count);
            count += len;

            if (checkWriteClassName && isEnabled(SerializerFeature.WRITE_CLASS_NAME)) {
                write('F');
            }
        }
    }

    private static boolean valueBoolean(float value){
        return value == Float.POSITIVE_INFINITY || value == Float.NEGATIVE_INFINITY;
    }

    private boolean checkWriteClassNameBoolean(boolean checkWriteClassName){
        return checkWriteClassName && isEnabled(SerializerFeature.WRITE_CLASS_NAME);
    }

    public void writeDouble(double value, boolean checkWriteClassName) {
        if (Double.isNaN(value)
                || Double.isInfinite(value)) {
            writeNull();
            return;
        }

        int newcount = count + 24;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                String str = RyuDouble.toString(value);
                write(str, 0, str.length());

                if (checkWriteClassName && isEnabled(SerializerFeature.WRITE_CLASS_NAME)) {
                    write('D');
                }
                return;
            }
        }

        int len = RyuDouble.toString(value, buf, count);
        count += len;

        if (checkWriteClassName && isEnabled(SerializerFeature.WRITE_CLASS_NAME)) {
            write('D');
        }
    }

    public void writeEnum(Enum<?> value) {
        if (value == null) {
            writeNull();
            return;
        }

        String strVal = null;
        if (writeEnumUsingName && !writeEnumUsingToString) {
            strVal = value.name();
        } else if (writeEnumUsingToString) {
            strVal = value.toString();
        }

        if (strVal != null) {
            char quote = isEnabled(SerializerFeature.USE_SINGLE_QUOTES) ? '\'' : '"';
            write(quote);
            write(strVal);
            write(quote);
        } else {
            writeInt(value.ordinal());
        }
    }

    public void writeLong(long i) {

        needQuotationMarkWrite(i);

        int size = (i < 0) ? IOUtils.stringSize(-i) + 1 : IOUtils.stringSize(i);

        int newcount = count + size;
        if (needQuotationMark(i)) newcount += 2;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                char[] chars = new char[size];
                IOUtils.getChars(i, size, chars);
                if (needQuotationMark(i)) {
                    write('"');
                    write(chars, 0, chars.length);
                    write('"');
                } else {
                    write(chars, 0, chars.length);
                }
                return;
            }
        }

        if (needQuotationMark(i)) {
            buf[count] = '"';
            IOUtils.getChars(i, newcount - 1, buf);
            buf[newcount - 1] = '"';
        } else {
            IOUtils.getChars(i, newcount, buf);
        }

        count = newcount;
    }

    private boolean needQuotationMark(long i){
        return isEnabled(SerializerFeature.BROWSER_COMPATIBLE) //
                && (!isEnabled(SerializerFeature.WRITE_CLASS_NAME)) //
                && (i > 9007199254740991L || i < -9007199254740991L);
    }

    private void needQuotationMarkWrite(long i) {
        if (i == Long.MIN_VALUE) {
            if (needQuotationMark(i)) {
                write("\"-9223372036854775808\"");
            } else {
                write("-9223372036854775808");
            }
        }
    }

    public void writeNull() {
        write("null");
    }

    public void writeNull(SerializerFeature feature) {
        writeNull(0, feature.mask);
    }

    public void writeNull(int beanFeatures , int feature) {
        if ((beanFeatures & feature) == 0 //
                && (this.features & feature) == 0) {
            writeNull();
            return;
        }
        if ((beanFeatures & SerializerFeature.WRITE_MAP_NULL_VALUE.mask) != 0
                && (beanFeatures & ~SerializerFeature.WRITE_MAP_NULL_VALUE.mask
                & SerializerFeature.WRITE_MAP_NULL_FEATURES) == 0) {
            writeNull();
            return;
        }

        if (feature == SerializerFeature.WRITE_NULL_LIST_AS_EMPTY.mask) {
            write("[]");
        } else if (feature == SerializerFeature.WRITE_NULL_STRING_AS_EMPTY.mask) {
            writeString("");
        } else if (feature == SerializerFeature.WRITE_NULL_BOOLEAN_AS_FALSE.mask) {
            write("false");
        } else if (feature == SerializerFeature.WRITE_NULL_NUMBER_AS_ZERO.mask) {
            write('0');
        } else {
            writeNull();
        }
    }

    public void writeStringWithDoubleQuote(String text, final char seperator) {
        if (textIsNull(text, seperator)) return;

        int len = text.length();
        int newcount = count + len + 2;

        newcount = getNewcount(seperator, newcount);

        if (newCountMajor(text, seperator, newcount)) return;

        int start = count + 1;
        int end = start + len;

        buf[count] = '\"';
        text.getChars(0, len, buf, start);

        count = newcount;

        if (isEnabledBoolean(seperator, newcount, start, end)) return;

        int firstSpecialIndex = -1;

        for (int i = start; i < end; ++i) {
            char ch = buf[i];

            // 93
            if (isaBoolean(ch) && isaBoolean1(ch)) {
                firstSpecialIndex = getFirstSpecialIndex(firstSpecialIndex, i);

                newcount += 4;
            }
        }

        getBuf(seperator);
    }

    private static boolean isaBoolean1(char ch) {
        return ch >= 0x7F //
                && (ch == '\u2028' //
                || ch == '\u2029' //
                || ch < 0xA0);
    }

    private static boolean isaBoolean(char ch) {
        return ch >= ']';
    }

    private static int getNewcount(char seperator, int newcount) {
        if (seperator != 0) {
            newcount++;
        }
        return newcount;
    }

    private boolean newCountMajor(String text, char seperator, int newcount) {
        if (newcount > buf.length) {
            if (writer != null) {
                write('"');

                chValue(text, seperator);
                return true;
            }
            expandCapacity(newcount);
        }
        return false;
    }

    private boolean isEnabledBoolean(char seperator, int newcount, int start, int end) {
        if (isEnabled(SerializerFeature.BROWSER_COMPATIBLE)) {
            lastSpecialIndexVoid(seperator, newcount, start, end);
            return true;
        }
        return false;
    }

    private static int getFirstSpecialIndex(int firstSpecialIndex, int i) {
        if (firstSpecialIndex == -1) {
            firstSpecialIndex = i;
        }
        return firstSpecialIndex;
    }

    private void lastSpecialIndexVoid(char seperator, int newcount, int start, int end) {
        int lastSpecialIndex = -1;

        for (int i = start; i < end; ++i) {
            char ch = buf[i];

            if (ch == '"' //
                    || ch == '/' //
                    || ch == '\\') {
                lastSpecialIndex = i;
                newcount += 1;
            }

            if (ch == '\b' //
                    || ch == '\f' //
                    || ch == '\n' //
                    || ch == '\r' //
                    || ch == '\t') {
                lastSpecialIndex = i;
                newcount += 1;
            }

            if (ch < 32) {
                lastSpecialIndex = i;
                newcount += 5;
            }

            if (ch >= 127) {
                lastSpecialIndex = i;
                newcount += 5;
            }
        }

        newCountMajor(newcount);
        count = newcount;

        if(getBuf(start, end, lastSpecialIndex)!=null){
            buf = getBuf(start, end, lastSpecialIndex);
        }

        buf = getBuf(seperator);
    }

    private char[] getBuf(char seperator) {
        if (seperator != 0) {
            buf[count - 2] = '\"';
            buf[count - 1] = seperator;
        } else {
            buf[count - 1] = '\"';
        }
        return buf;
    }

    private char[] getBuf(int start, int end, int lastSpecialIndex) {
        for (int i = lastSpecialIndex; i >= start; --i) {
            char ch = buf[i];

            if (ch == '\b' //
                    || ch == '\f'//
                    || ch == '\n' //
                    || ch == '\r' //
                    || ch == '\t'
            ) {
                System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                buf[i] = '\\';
                buf[i + 1] = replaceChars[ch];
                end += 1;
            }

            if (ch == '"' //
                    || ch == '/' //
                    || ch == '\\'
            ) {
                System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                buf[i] = '\\';
                buf[i + 1] = ch;
                end += 1;
            }

            if (ch < 32) {
                System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                buf[i    ] = '\\';
                buf[i + 1] = 'u';
                buf[i + 2] = '0';
                buf[i + 3] = '0';
                buf[i + 4] = IOUtils.ASCII_CHARS[ch * 2];
                buf[i + 5] = IOUtils.ASCII_CHARS[ch * 2 + 1];
                end += 5;
            }

            if (ch >= 127) {
                System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                buf[i    ] = '\\';
                buf[i + 1] = 'u';
                buf[i + 2] = IOUtils.DIGITS[(ch >>> 12) & 15];
                buf[i + 3] = IOUtils.DIGITS[(ch >>> 8) & 15];
                buf[i + 4] = IOUtils.DIGITS[(ch >>> 4) & 15];
                buf[i + 5] = IOUtils.DIGITS[ch & 15];
                end += 5;
            }
        }
        return buf;
    }

    private void chValue(String text, char seperator) {
        for (int i = 0; i < text.length(); ++i) {
            isEnabledSerializerCh(text.charAt(i));
        }

        write('"');
        if (seperator != 0) {
            write(seperator);
        }
    }

    private boolean chMinusIOUtils(char ch) {
        if (ch < IOUtils.specicalFlags_doubleQuotes.length
                && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                || (ch == '/' && isEnabled(SerializerFeature.WRITE_SLASH_AS_SPECIAL))) {
            write('\\');
            if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                write('u');
                write(IOUtils.DIGITS[ch >>> 12 & 15]);
                write(IOUtils.DIGITS[ch >>> 8  & 15]);
                write(IOUtils.DIGITS[ch >>> 4  & 15]);
                write(IOUtils.DIGITS[ch & 15]);
            } else {
                write(IOUtils.replaceChars[ch]);
            }
            return true;
        }
        return false;
    }

    private boolean chEqualsValues(char ch) {
        if (ch == '\b' //
                || ch == '\f' //
                || ch == '\n' //
                || ch == '\r' //
                || ch == '\t' //
                || ch == '"' //
                || ch == '/' //
                || ch == '\\') {
            write('\\');
            write(replaceChars[ch]);
            return true;
        }

        if (ch < 32) {
            write('\\');
            write('u');
            write('0');
            write('0');
            write(IOUtils.ASCII_CHARS[ch * 2    ]);
            write(IOUtils.ASCII_CHARS[ch * 2 + 1]);
            return true;
        }

        if (ch >= 127) {
            write('\\');
            write('u');
            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
            write(IOUtils.DIGITS[(ch >>> 8 ) & 15]);
            write(IOUtils.DIGITS[(ch >>> 4 ) & 15]);
            write(IOUtils.DIGITS[ ch & 15]);
            return true;
        }
        return false;
    }

    private boolean isEnabledSerialize(char ch) {
        if (isEnabled(SerializerFeature.BROWSER_SECURE) && (ch == '(' || ch == ')' || ch == '<' || ch == '>')) {
            write('\\');
            write('u');
            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
            write(IOUtils.DIGITS[(ch >>> 8) & 15]);
            write(IOUtils.DIGITS[(ch >>> 4) & 15]);
            write(IOUtils.DIGITS[ch & 15]);
            return true;
        }
        return false;
    }

    private boolean textIsNull(String text, char seperator) {
        if (text == null) {
            writeNull();
            if (seperator != 0) {
                write(seperator);
            }
            return true;
        }
        return false;
    }

    private void newCountMajor(int newcount) {
        if (newcount > buf.length) {
            expandCapacity(newcount);
        }
    }

    private void isEnabledSerializerCh(char text) {

        if (isEnabledSerialize(text)) return;

        if (isEnabled(SerializerFeature.BROWSER_COMPATIBLE)) {
            if (chEqualsValues(text)) return;
        } else {
            if (chMinusIOUtils(text)) return;
        }

        write(text);
    }

    public void write(List<String> list) {
        if (list.isEmpty()) {
            write("[]");
            return;
        }

        int offset = count;
        for (int i = 0, list_size = list.size(); i < list_size; ++i) {
            String text = null;
            if(list.get(i)!=null){
                text = list.get(i);
            }

            int newcount = 0;
            if(text!=null){
                newcount = offset + text.length() + 3;
            }
            if (i == list.size() - 1) {
                newcount++;
            }
            if (newcount > buf.length) {
                count = offset;
                expandCapacity(newcount);
            }

            if (i == 0) {
                buf[offset++] = '[';
            } else {
                buf[offset++] = ',';
            }
            buf[offset++] = '"';
            if(text!=null){
                text.getChars(0, text.length(), buf, offset);
                offset += text.length();
            }
            buf[offset++] = '"';
        }
        buf[offset++] = ']';
        count = offset;
    }

    public void writeFieldValue(char seperator, String name, char value) {
        write(seperator);
        writeFieldName(name);
        if (value == 0) {
            writeString("\u0000");
        } else {
            writeString(Character.toString(value));
        }
    }

    public void writeFieldValue(char seperator, String name, boolean value) {
        if (!quoteFieldNames) {
            write(seperator);
            writeFieldName(name);
            write(value);
            return;
        }
        int intSize = value ? 4 : 5;

        int nameLen = name.length();
        int newcount = count + nameLen + 4 + intSize;
        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeString(name);
                write(':');
                write(value);
                return;
            }
            expandCapacity(newcount);
        }

        int start = count;
        count = newcount;

        buf[start] = seperator;

        int nameEnd = start + nameLen + 1;

        buf[start + 1] = keySeperator;

        name.getChars(0, nameLen, buf, start + 2);

        buf[nameEnd + 1] = keySeperator;

        if (value) {
            System.arraycopy(VALUE_TRUE, 0, buf, nameEnd + 2, 5);
        } else {
            System.arraycopy(VALUE_FALSE, 0, buf, nameEnd + 2, 6);
        }
    }

    public void write(boolean value) {
        if (value) {
            write("true");
        } else {
            write("false");
        }
    }

    public void writeFieldValue(char seperator, String name, int value) {
        if (value == Integer.MIN_VALUE || !quoteFieldNames) {
            write(seperator);
            writeFieldName(name);
            writeInt(value);
            return;
        }

        int intSize = (value < 0) ? IOUtils.stringSize(-value) + 1 : IOUtils.stringSize(value);

        int nameLen = name.length();
        int newcount = count + nameLen + 4 + intSize;
        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeFieldName(name);
                writeInt(value);
                return;
            }
            expandCapacity(newcount);
        }

        int start = count;
        count = newcount;

        buf[start] = seperator;

        int nameEnd = start + nameLen + 1;

        buf[start + 1] = keySeperator;

        name.getChars(0, nameLen, buf, start + 2);

        buf[nameEnd + 1] = keySeperator;
        buf[nameEnd + 2] = ':';

        IOUtils.getChars(value, count, buf);
    }

    public void writeFieldValue(char seperator, String name, long value) {
        if (value == Long.MIN_VALUE
                || !quoteFieldNames
                || isEnabled(SerializerFeature.BROWSER_COMPATIBLE.mask)
        ) {
            write(seperator);
            writeFieldName(name);
            writeLong(value);
            return;
        }

        int intSize = (value < 0) ? IOUtils.stringSize(-value) + 1 : IOUtils.stringSize(value);

        int nameLen = name.length();
        int newcount = count + nameLen + 4 + intSize;
        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeFieldName(name);
                writeLong(value);
                return;
            }
            expandCapacity(newcount);
        }

        int start = count;
        count = newcount;

        buf[start] = seperator;

        int nameEnd = start + nameLen + 1;

        buf[start + 1] = keySeperator;

        name.getChars(0, nameLen, buf, start + 2);

        buf[nameEnd + 1] = keySeperator;
        buf[nameEnd + 2] = ':';

        IOUtils.getChars(value, count, buf);
    }

    public void writeFieldValue(char seperator, String name, float value) {
        write(seperator);
        writeFieldName(name);
        writeFloat(value, false);
    }

    public void writeFieldValue(char seperator, String name, double value) {
        write(seperator);
        writeFieldName(name);
        writeDouble(value, false);
    }

    public void writeFieldValue(char seperator, String name, String value) {
        if (quoteFieldNames) {
            if (useSingleQuotes) {
                write(seperator);
                writeFieldName(name);
                if (value == null) {
                    writeNull();
                } else {
                    writeString(value);
                }
            } else {
                if (isEnabled(SerializerFeature.BROWSER_COMPATIBLE)) {
                    write(seperator);
                    writeStringWithDoubleQuote(name, ':');
                    writeStringWithDoubleQuote(value, (char) 0);
                } else {
                    writeFieldValueStringWithDoubleQuoteCheck(seperator, name, value);
                }
            }
        } else {
            write(seperator);
            writeFieldName(name);
            printValue(value);
        }
    }

    private void printValue(String value){
        if (value == null) {
            writeNull();
        } else {
            writeString(value);
        }
    }

    public void writeFieldValueStringWithDoubleQuoteCheck(char seperator, String name, String value) {
        int nameLen = name.length();
        int valueLen;

        int newcount = count;

        if (value == null) {
            newcount += nameLen + 8;
        } else {
            valueLen = value.length();
            newcount += nameLen + valueLen + 6;
        }

        if (newCountMajor1(seperator, name, value, newcount)) return;

        buf[count] = seperator;

        int nameStart = count + 2;
        int nameEnd = nameStart + nameLen;

        buf[count + 1] = '\"';
        name.getChars(0, nameLen, buf, nameStart);

        count = newcount;

        buf[nameEnd] = '\"';

        buf[count - 1] = '\"';
    }

    private boolean newCountMajor1(char seperator, String name, String value, int newcount) {
        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeStringWithDoubleQuote(name, ':');
                writeStringWithDoubleQuote(value, (char) 0);
                return true;
            }
            expandCapacity(newcount);
        }
        return false;
    }

    public void writeFieldValueStringWithDoubleQuote(char seperator, String name, String value) {
        int nameLen = name.length();
        int valueLen;

        int newcount = count;

        valueLen = value.length();
        newcount += nameLen + valueLen + 6;

        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeStringWithDoubleQuote(name, ':');
                writeStringWithDoubleQuote(value, (char) 0);
                return;
            }
            expandCapacity(newcount);
        }

        buf[count] = seperator;

        int nameStart = count + 2;
        int nameEnd = nameStart + nameLen;

        buf[count + 1] = '\"';
        name.getChars(0, nameLen, buf, nameStart);

        count = newcount;

        buf[nameEnd] = '\"';

        int index = nameEnd + 1;
        buf[index++] = ':';
        buf[index++] = '"';

        int valueStart = index;
        value.getChars(0, valueLen, buf, valueStart);
        buf[count - 1] = '\"';
    }



    public void writeFieldValue(char seperator, String name, Enum<?> value) {
        if (value == null) {
            write(seperator);
            writeFieldName(name);
            writeNull();
            return;
        }

        if (writeEnumUsingName && !writeEnumUsingToString) {
            writeEnumFieldValue(seperator, name, value.name());
        } else if (writeEnumUsingToString) {
            writeEnumFieldValue(seperator, name, value.toString());
        } else {
            writeFieldValue(seperator, name, value.ordinal());
        }
    }

    private void writeEnumFieldValue(char seperator, String name, String value) {
        if (useSingleQuotes) {
            writeFieldValue(seperator, name, value);
        } else {
            writeFieldValueStringWithDoubleQuote(seperator, name, value);
        }
    }

    public void writeFieldValue(char seperator, String name, BigDecimal value) {
        write(seperator);
        writeFieldName(name);
        if (value == null) {
            writeNull();
        } else {
            int scale = value.scale();
            write(isEnabled(SerializerFeature.WRITE_BIG_DECIMAL_AS_PLAIN) && scale >= -100 && scale < 100
                    ? value.toPlainString()
                    : value.toString()
            );
        }
    }

    public void writeString(String text, char seperator) {
        if (useSingleQuotes) {
            writeStringWithSingleQuote(text);
            write(seperator);
        } else {
            writeStringWithDoubleQuote(text, seperator);
        }
    }

    public void writeString(String text) {
        if (useSingleQuotes) {
            writeStringWithSingleQuote(text);
        } else {
            writeStringWithDoubleQuote(text, (char) 0);
        }
    }

    public void writeString(char[] chars) {
        if (useSingleQuotes) {
            writeStringWithSingleQuote(chars);
        } else {
            String text = new String(chars);
            writeStringWithDoubleQuote(text, (char) 0);
        }
    }

    void writeStringWithSingleQuote(String text) {
        if (textIsNull(text)) return;

        int len = text.length();
        int newcount = newCount2(text,len);

        int start = count + 1;

        buf[count] = '\'';
        text.getChars(0, len, buf, start);
        count = newcount;

        newCountMajor(newcount);
        count = newcount;

        buf[count - 1] = '\'';
    }

    private boolean textIsNull(String text) {
        if (text == null) {
            int newcount = count + 4;
            newCountMajor(newcount);
            "null".getChars(0, 4, buf, count);
            count = newcount;
            return true;
        }
        return false;
    }

    private int newCount2(String text, int len){
        int newcount = count + len + 2;
        if (newcount > buf.length) {
            if (writer != null) {
                write('\'');
                for (int i = 0; i < text.length(); ++i) {
                    char ch = text.charAt(i);
                    if (ch <= 13 || ch == '\\' || ch == '\'' //
                            || (ch == '/' && isEnabled(SerializerFeature.WRITE_SLASH_AS_SPECIAL))) {
                        write('\\');
                        write(replaceChars[ch]);
                    } else {
                        write(ch);
                    }
                }
                write('\'');
            }
            expandCapacity(newcount);
        }
        return newcount;
    }

    private void writeStringWithSingleQuote(char[] chars) {
        if (chars == null) {
            int newcount = count + 4;
            newCountMajor(newcount);
            "null".getChars(0, 4, buf, count);
            count = newcount;
            return;
        }

        int len = chars.length;
        int newcount = count + len + 2;

        newCount(newcount,chars);

        int start = count + 1;
        int end = start + len;

        buf[count] = '\'';

        System.arraycopy(chars, 0, buf, start, chars.length);
        count = newcount;

        int specialCount = 0;
        int lastSpecialIndex = -1;
        char lastSpecial = '\0';
        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch <= 13 || ch == '\\' || ch == '\'' //
                    || (ch == '/' && isEnabled(SerializerFeature.WRITE_SLASH_AS_SPECIAL))) {
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;
            }
        }

        newcount += specialCount;
        newCountMajor(newcount);
        count = newcount;

        specialCountRefactor(specialCount,lastSpecialIndex,start,end,lastSpecial);

        buf[count - 1] = '\'';
    }

    private void newCount(int newcount, char[] chars){
        if (newcount > buf.length) {
            if (writer != null) {
                write('\'');
                for (int i = 0; i < chars.length; ++i) {
                    char ch = chars[i];
                    if (ch <= 13 || ch == '\\' || ch == '\'' //
                            || (ch == '/' && isEnabled(SerializerFeature.WRITE_SLASH_AS_SPECIAL))) {
                        write('\\');
                        write(replaceChars[ch]);
                    } else {
                        write(ch);
                    }
                }
                write('\'');
                return;
            }
            expandCapacity(newcount);
        }
    }

    protected void specialCountRefactor(int specialCount, int lastSpecialIndex ,int start, int end ,char lastSpecial){
        if (specialCount == 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[lastSpecial];
        } else if (specialCount > 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[lastSpecial];
            end++;
            for (int i = lastSpecialIndex - 2; i >= start; --i) {
                char ch = buf[i];

                if (ch <= 13 || ch == '\\' || ch == '\'' //
                        || (ch == '/' && isEnabled(SerializerFeature.WRITE_SLASH_AS_SPECIAL))) {
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = replaceChars[ch];
                    end++;
                }
            }
        }
    }

    public void writeFieldName(String key) {
        writeFieldName2(key);
    }

    public void writeFieldName2(String key) {
        if (key == null) {
            write("null:");
            return;
        }

        if (useSingleQuotes) {
            if (quoteFieldNames) {
                writeStringWithSingleQuote(key);
                write(':');
            } else {
                writeKeyWithSingleQuoteIfHasSpecial(key);
            }
        } else {
            writeQuoteFieldNames(key);
        }
    }

    private void writeQuoteFieldNames(String key){
        if (quoteFieldNames) {
            writeStringWithDoubleQuote(key, ':');
        } else {
            boolean hashSpecial = key.length() == 0;
            for (int i = 0; i < key.length(); ++i) {
                char ch = key.charAt(i);
                boolean special = (ch < 64 && (sepcialBits & (1L << ch)) != 0) || ch == '\\';
                if (special) {
                    hashSpecial = true;
                    break;
                }
            }
            if (hashSpecial) {
                writeStringWithDoubleQuote(key, ':');
            } else {
                write(key);
                write(':');
            }
        }
    }

    private void writeKeyWithSingleQuoteIfHasSpecial(String text) {
        final byte[] specicalFlagsSingleQuotes = IOUtils.specicalFlags_singleQuotes;

        int len = text.length();
        int newcount = count + len + 1;
        if (newcount > buf.length) {
            if (writer != null) {
                if (len == 0) {
                    write('\'');
                    write('\'');
                    write(':');
                    return;
                }
                hasSpecialWrite(text,len,specicalFlagsSingleQuotes);

            }

            expandCapacity(newcount);
        }

        if (len == 0) {
            int newCount = count + 3;
            newCountMajor(newCount);
            buf[count++] = '\'';
            buf[count++] = '\'';
            buf[count++] = ':';
            return;
        }

        hasSpecialVoid2(text,len,newcount,specicalFlagsSingleQuotes);

        buf[newcount - 1] = ':';
    }

    public void hasSpecialVoid2(String text,int len,int newcount,final byte[] specicalFlagsSingleQuotes){
        int start = count;
        int end = start + len;

        text.getChars(0, len, buf, start);
        count = newcount;

        boolean hasSpecial = false;

        int i = start;
        while (i < end) {
            char ch = buf[i];
            if (specicalFlagsSingleQuotesBoolean(specicalFlagsSingleQuotes,ch)) {
                if (!hasSpecial) {
                    newcount += 3;
                    newCountMajor(newcount);
                    count = newcount;

                    System.arraycopy(buf, i + 1, buf, i + 3, end - i - 1);
                    System.arraycopy(buf, 0, buf, 1, i);
                    buf[start] = '\'';
                    buf[++i] = '\\';
                    buf[++i] = replaceChars[ch];
                    end += 2;
                    buf[count - 2] = '\'';

                    hasSpecial = true;
                } else {
                    newcount++;
                    newCountMajor(newcount);
                    count = newcount;

                    System.arraycopy(buf, i + 1, buf, i + 2, end - i);
                    buf[i] = '\\';
                    buf[++i] = replaceChars[ch];
                    end++;
                }
            }
            ++i;
        }
    }

    public boolean specicalFlagsSingleQuotesBoolean(final byte[] specicalFlagsSingleQuotes, char ch){
        return ch < specicalFlagsSingleQuotes.length && specicalFlagsSingleQuotes[ch] != 0;
    }

    public void hasSpecialWrite(String text,int len,final byte[] specicalFlagsSingleQuotes){
        boolean hasSpecial = false;
        for (int i = 0; i < len; ++i) {
            char ch = text.charAt(i);
            if (ch < specicalFlagsSingleQuotes.length && specicalFlagsSingleQuotes[ch] != 0) {
                hasSpecial = true;
            }
        }

        if (hasSpecial) {
            write('\'');
        }
        for (int i = 0; i < len; ++i) {
            char ch = text.charAt(i);
            if (ch < specicalFlagsSingleQuotes.length && specicalFlagsSingleQuotes[ch] != 0) {
                write('\\');
                write(replaceChars[ch]);
            } else {
                write(ch);
            }
        }
        if (hasSpecial) {
            write('\'');
        }
        write(':');
    }

    public void flush() {
        if (writer == null) {
            return;
        }

        try {
            writer.write(buf, 0, count);
            writer.flush();
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
        count = 0;
    }

    public void reset() {
        count = 0;
    }
}
