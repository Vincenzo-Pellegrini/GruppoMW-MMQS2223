/*
 * Copyright 1999-2017 Alibaba Group.
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
package com.alibaba.fastjson.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.util.ASMUtils;
import com.alibaba.fastjson.util.IOUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

import static com.alibaba.fastjson.util.TypeUtils.FNV1A_64_MAGIC_HASHCODE;
import static com.alibaba.fastjson.util.TypeUtils.FNV1A_64_MAGIC_PRIME;

//这个类，为了性能优化做了很多特别处理，一切都是为了性能！！！

/**
 * @author wenshao[szujobs@hotmail.com]
 */

public final class JSONScanner extends JSONLexerBase {
    static final String          ILLEGAL_STR              = "illegal str, ";

    static final String UNCLOSED = "unclosed str";
    private final String text;
    private final int    len;

    public JSONScanner(String input){
        this(input, JSON.DEFAULT_PARSER_FEATURE);
    }

    public JSONScanner(String input, int features){
        super(features);

        text = input;
        len = text.length();
        bp = -1;

        next();
        if (ch == 65279) { // utf-8 bom
            next();
        }
    }

    public final char charAt(int index) {
        if (index >= len) {
            return EOI;
        }

        return text.charAt(index);
    }

    public final char next() {
        int index = ++bp;
        ch = (index >= this.len ? //
                EOI //
                : text.charAt(index));
        return ch;
    }

    public JSONScanner(char[] input, int inputLength){
        this(input, inputLength, JSON.DEFAULT_PARSER_FEATURE);
    }

    public JSONScanner(char[] input, int inputLength, int features){
        this(new String(input, 0, inputLength), features);
    }

    protected final void copyTo(int offset, int count, char[] dest) {
        text.getChars(offset, offset + count, dest, 0);
    }

    static boolean charArrayCompare(String src, int offset, char[] dest) {
        final int destLen = dest.length;
        if (destLen + offset > src.length()) {
            return false;
        }
        for (int i = 0; i < destLen; ++i) {
            if (dest[i] != src.charAt(offset + i)) {
                return false;
            }
        }
        return true;
    }

    public final boolean charArrayCompare(char[] chars) {
        return charArrayCompare(text, bp, chars);
    }

    public final int indexOf(char ch, int startIndex) {
        return text.indexOf(ch, startIndex);
    }

    public final String addSymbol(int offset, int len, int hash, final SymbolTable symbolTable) {
        return symbolTable.addSymbol(text, offset, len, hash);
    }

    public byte[] bytesValue() {
        if (token == JSONToken.HEX) {
            int start = np + 1;
            int len3 = sp;

            if (len3 % 2 != 0) {
                throw new JSONException("illegal state. " + len3);
            }

            byte[] bytes = new byte[len3 / 2];

            for (int i = 0; i < bytes.length; ++i) {
                char c0 = text.charAt(start + i * 2);
                char c1 = text.charAt(start + i * 2 + 1);
                int b0 = c0 - (c0 <= 57 ? 48 : 55);
                int b1 = c1 - (c1 <= 57 ? 48 : 55);
                bytes[i] = (byte) ((b0 << 4) | b1);
            }

            return bytes;
        }
        if (!hasSpecial) {

            return IOUtils.decodeBase64(text, np + 1, sp);

        } else {
            String escapedText = new String(sbuf, 0, sp);

            return IOUtils.decodeBase64(escapedText);
        }
    }

    /**
     * The value of a literal token, recorded as a string. For integers, leading 0x and 'l' suffixes are suppressed.
     */
    public final String stringVal() {
        if (!hasSpecial) {
            return this.subString(np + 1, sp);
        } else {
            return new String(sbuf, 0, sp);
        }
    }

    public final String subString(int offset, int count) {
        if (ASMUtils.IS_ANDROID) {
            if (count < sbuf.length) {
                text.getChars(offset, offset + count, sbuf, 0);
                return new String(sbuf, 0, count);
            } else {
                char[] chars = new char[count];

                text.getChars(offset, offset + count, chars, 0);
                return new String(chars);
            }
        } else {
            return text.substring(offset, offset + count);
        }
    }

    @Override
    protected char[] subChars(int offset, int count) {
        return new char[0];
    }

    public final char[] subCharsMetodo(int offset, int count) {
        if (ASMUtils.IS_ANDROID && count < sbuf.length) {
            text.getChars(offset, offset + count, sbuf, 0);
            return sbuf;
        } else {
            char[] chars = new char[count];

            text.getChars(offset, offset + count, chars, 0);
            return chars;
        }
    }

    public final String numberString() {
        char chLocal = charAt(np + sp - 1);
        int sp = this.sp;

        if (chLocal == 'L' || chLocal == 'S' || chLocal == 'B' || chLocal == 'F' || chLocal == 'D') {
            sp--;
        }
        return this.subString(np, sp);
    }

    public final BigDecimal decimalValue() {
        char chLocal = charAt(np + sp - 1);
        int sp = this.sp;

        if (chLocal == 'L' || chLocal == 'S' || chLocal == 'B' || chLocal == 'F' || chLocal == 'D') {
            sp--;
        }
        if (sp > 65535) {
            throw new JSONException("decimal overflow");
        }

        int offset = np;
        int count = sp;

        if (count < sbuf.length) {
            text.getChars(offset, offset + count, sbuf, 0);
            return new BigDecimal(sbuf, 0, count, MathContext.UNLIMITED);
        } else {
            char[] chars = new char[count];
            text.getChars(offset, offset + count, chars, 0);
            return new BigDecimal(chars, 0, chars.length, MathContext.UNLIMITED);
        }
    }

    public boolean scanISO8601DateIfMatch() {
        return scanISO8601DateIfMatch(true);
    }

    public boolean scanISO8601DateIfMatch(boolean strict) {
        int rest = len - bp;

        return scanISO8601DateIfMatch(strict, rest);
    }

    private boolean scanISO8601DateIfMatch(boolean strict, int rest) {
        if (rest < 8) {
            return false;
        }

        char c0 = charAt(bp);

        char c1 = charAt(bp + 1);

        char c2 = charAt(bp + 2);

        char c3 = charAt(bp + 3);

        char c4 = charAt(bp + 4);

        char c5 = charAt(bp + 5);

        char c6 = charAt(bp + 6);

        char c7 = charAt(bp + 7);

        if ((!strict) && rest > 13) {
            char cR0variabile = charAt(bp + rest - 1);

            char cR1variabile = charAt(bp + rest - 2);

            if (c0 == '/' && c1 == 'D' && c2 == 'a' && c3 == 't' && c4 == 'e' && c5 == '(' && cR0variabile == '/'
                    && cR1variabile == ')') {
                int plusIndex = -1;
                for (int i = 6; i < rest; ++i) {
                    char c = charAt(bp + i);
                    if (c == '+') {
                        plusIndex = i;
                    } else if (extracted35(c)) {
                        break;
                    }
                }
                if (plusIndex == -1) {
                    return false;
                }
                int offset = bp + 6;
                String numberText = this.subString(offset, bp + plusIndex - offset);
                long millis = Long.parseLong(numberText);
                calendar = Calendar.getInstance(timeZone, locale);
                calendar.setTimeInMillis(millis);
                token = JSONToken.LITERAL_ISO8601_DATE;
                return true;
            }
        }
        char c10;
        if (rest == 8
                || rest == 14
                || (rest == 16 && ((c10 = charAt(bp + 10)) == 'T' || c10 == ' '))
                || (rest == 17 && charAt(bp + 6) != '-')) {
            if (strict) {
                return false;
            }
            char y0;
            char y1;
            char y2;
            char y3;
            char m0variabile;
            char m1variabile;
            char d0;
            char d1;
            char c8 = charAt(bp + 8);
            final boolean c_47 = c4 == '-' && c7 == '-';
            final boolean sperate16 = c_47 && rest == 16;
            final boolean sperate17 = c_47 && rest == 17;
            if (sperate17 || sperate16) {
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;
                m0variabile = c5;
                m1variabile = c6;
                d0 = c8;
                d1 = charAt(bp + 9);
            } else if (c4 == '-' && c6 == '-') {
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;
                m0variabile = '0';
                m1variabile = c5;
                d0 = '0';
                d1 = c7;
            } else {
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;
                m0variabile = c4;
                m1variabile = c5;
                d0 = c6;
                d1 = c7;
            }

            if (!checkDate(y0, y1, y2, y3, m0variabile, m1variabile)) {
                return false;
            }

            setCalendar(y0, y1, y2, m0variabile, m1variabile, d0, d1);
            int hour;
            int minute;
            int seconds;
            int millis;

            if (rest != 8) {
                char c9 = charAt(bp + 9);
                c10 = charAt(bp + 10);
                char c11 = charAt(bp + 11);
                char c12 = charAt(bp + 12);
                char c13 = charAt(bp + 13);
                char h0;
                char h1;
                char m0;
                char m1;
                char s0;
                char s1;
                if ((sperate17 && c10 == 'T' && c13 == ':' && charAt(bp + 16) == 'Z')
                        || (sperate16 && (c10 == ' ' || c10 == 'T') && c13 == ':')) {
                    h0 = c11;
                    h1 = c12;
                    m0 = charAt(bp + 14);
                    m1 = charAt(bp + 15);
                    s0 = '0';
                    s1 = '0';
                } else {
                    h0 = c8;
                    h1 = c9;
                    m0 = c10;
                    m1 = c11;
                    s0 = c12;
                    s1 = c13;
                }

                if (!checkTime(h0, h1, m0, m1, s0, s1)) {
                    return false;
                }

                if (rest == 17 && !sperate17) {
                    char s0variabile = charAt(bp + 14);
                    char s1variabile = charAt(bp + 15);
                    char s2variabile = charAt(bp + 16);
                    if (extracted35(s0variabile)) {
                        return false;
                    }
                    if (extracted35(s1variabile)) {
                        return false;
                    }
                    if (extracted35(s2variabile)) {
                        return false;
                    }
                    millis = (s0variabile - '0') * 100 + (s1variabile - '0') * 10 + (s2variabile - '0');
                } else {
                    millis = 0;
                }
                hour = (h0 - '0') * 10 + (h1 - '0');
                minute = (m0 - '0') * 10 + (m1 - '0');
                seconds = (s0 - '0') * 10 + (s1 - '0');
            } else {
                hour = 0;
                minute = 0;
                seconds = 0;
                millis = 0;
            }
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, seconds);
            calendar.set(Calendar.MILLISECOND, millis);
            token = JSONToken.LITERAL_ISO8601_DATE;
            return true;
        }

        if (rest < 9) {
            return false;
        }
        char c8 = charAt(bp + 8);
        char c9 = charAt(bp + 9);
        int dateLenVariabile = 10;
        char y0;
        char y1;
        char y2;
        char y3;
        char m0variabile;
        char m1variabile;
        char d0;
        char d1;

        if ((c4 == '-' && c7 == '-') // cn
                ||  (c4 == '/' && c7 == '/') // tw yyyy/mm/dd
        ) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;
            m0variabile = c5;
            m1variabile = c6;
            if (c9 == ' ') {
                d0 = '0';
                d1 = c8;
                dateLenVariabile = 9;
            } else {
                d0 = c8;
                d1 = c9;
            }
        } else if ((c4 == '-' && c6 == '-') // cn yyyy-m-dd
        ) {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;
            m0variabile = '0';
            m1variabile = c5;
            if (c8 == ' ') {
                d0 = '0';
                d1 = c7;
                dateLenVariabile = 8;
            } else {
                d0 = c7;
                d1 = c8;
                dateLenVariabile = 9;
            }
        } else if ((extracted19(c2) && extracted19(c5)) // de dd.mm.yyyy
                || (c2 == '-' && c5 == '-') // in dd-mm-yyyy
        ) {
            d0 = c0;
            d1 = c1;
            m0variabile = c3;
            m1variabile = c4;
            y0 = c6;
            y1 = c7;
            y2 = c8;
            y3 = c9;
        } else if (c8 == 'T') {
            y0 = c0;
            y1 = c1;
            y2 = c2;
            y3 = c3;
            m0variabile = c4;
            m1variabile = c5;
            d0 = c6;
            d1 = c7;
            dateLenVariabile = 8;
        } else {
            if (c4 == '年' || c4 == '년') {
                y0 = c0;
                y1 = c1;
                y2 = c2;
                y3 = c3;
                if (c7 == '月' || c7 == '월') {
                    m0variabile = c5;
                    m1variabile = c6;
                    if (c9 == '日' || c9 == '일') {
                        d0 = '0';
                        d1 = c8;
                    } else if (charAt(bp + 10) == '日' || charAt(bp + 10) == '일'){
                        d0 = c8;
                        d1 = c9;
                        dateLenVariabile = 11;
                    } else {
                        return false;
                    }
                } else if (c6 == '月' || c6 == '월') {
                    m0variabile = '0';
                    m1variabile = c5;
                    if (c8 == '日' || c8 == '일') {
                        d0 = '0';
                        d1 = c7;
                    } else if (c9 == '日' || c9 == '일'){
                        d0 = c7;
                        d1 = c8;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        if (!checkDate(y0, y1, y2, y3, m0variabile, m1variabile)) {
            return false;
        }

        setCalendar(y0, y1, y2, m0variabile, m1variabile, d0, d1);
        char t = charAt(bp + dateLenVariabile);

        if (t == 'T' && rest == 16 && dateLenVariabile == 8 && charAt(bp + 15) == 'Z') {
            char h0 = charAt(bp + dateLenVariabile + 1);
            char h1 = charAt(bp + dateLenVariabile + 2);
            char m0 = charAt(bp + dateLenVariabile + 3);
            char m1 = charAt(bp + dateLenVariabile + 4);
            char s0 = charAt(bp + dateLenVariabile + 5);
            char s1 = charAt(bp + dateLenVariabile + 6);
            if (!checkTime(h0, h1, m0, m1, s0, s1)) {
                return false;
            }
            setTime(h0, h1, m0, m1, s0, s1);
            calendar.set(Calendar.MILLISECOND, 0);
            if (calendar.getTimeZone().getRawOffset() != 0) {
                String[] timeZoneIDs = TimeZone.getAvailableIDs(0);
                if (timeZoneIDs.length > 0) {
                    TimeZone timeZone = TimeZone.getTimeZone(timeZoneIDs[0]);
                    calendar.setTimeZone(timeZone);
                }
            }
            token = JSONToken.LITERAL_ISO8601_DATE;
            return true;
        } else if (t == 'T' || (t == ' ' && !strict)) {
            if (rest < dateLenVariabile + 9) { // "0000-00-00T00:00:00".length()
                return false;
            }
        } else if (t == '"' || t == EOI || t == '日' || t == '일') {
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            bp += dateLenVariabile;
            ch = charAt(bp);
            token = JSONToken.LITERAL_ISO8601_DATE;
            return true;
        } else if (t == '+' || t == '-') {
            if (len == dateLenVariabile + 6) {
                if (charAt(bp + dateLenVariabile + 3) != ':' //
                        || charAt(bp + dateLenVariabile + 4) != '0' //
                        || charAt(bp + dateLenVariabile + 5) != '0') {
                    return false;
                }
                setTime('0', '0', '0', '0', '0', '0');
                calendar.set(Calendar.MILLISECOND, 0);
                setTimeZone(t, charAt(bp + dateLenVariabile + 1), charAt(bp + dateLenVariabile + 2));
                return true;
            }
            return false;
        } else {
            return false;
        }

        if (charAt(bp + dateLenVariabile + 3) != ':') {
            return false;
        }

        if (charAt(bp + dateLenVariabile + 6) != ':') {
            return false;
        }

        char h0 = charAt(bp + dateLenVariabile + 1);
        char h1 = charAt(bp + dateLenVariabile + 2);
        char m0 = charAt(bp + dateLenVariabile + 4);
        char m1 = charAt(bp + dateLenVariabile + 5);
        char s0 = charAt(bp + dateLenVariabile + 7);
        char s1 = charAt(bp + dateLenVariabile + 8);

        if (!checkTime(h0, h1, m0, m1, s0, s1)) {
            return false;
        }

        setTime(h0, h1, m0, m1, s0, s1);
        char dot = charAt(bp + dateLenVariabile + 9);
        int millisLen = -1; // 有可能没有毫秒区域，没有毫秒区域的时候下一个字符位置有可能是'Z'、'+'、'-'
        int millis = 0;

        if (extracted19(dot)) { // 0000-00-00T00:00:00.000
            if (rest < dateLenVariabile + 11) {
                return false;
            }

            char s0variabile = charAt(bp + dateLenVariabile + 10);

            if (extracted35(s0variabile)) {
                return false;
            }
            millis = s0variabile - '0';
            millisLen = 1;

            if (rest > dateLenVariabile + 11) {
                char s1variabile = charAt(bp + dateLenVariabile + 11);
                if (extracted17(s1variabile)) {
                    millis = millis * 10 + (s1variabile - '0');
                    millisLen = 2;
                }
            }

            if (millisLen == 2) {
                char s2variabile = charAt(bp + dateLenVariabile + 12);

                if (extracted17(s2variabile)) {
                    millis = millis * 10 + (s2variabile - '0');
                    millisLen = 3;
                }
            }
        }
        calendar.set(Calendar.MILLISECOND, millis);
        int timzeZoneLength = 0;
        char timeZoneFlag = charAt(bp + dateLenVariabile + 10 + millisLen);

        if (timeZoneFlag == ' ') {
            millisLen++;
            timeZoneFlag = charAt(bp + dateLenVariabile + 10 + millisLen);
        }

        if (timeZoneFlag == '+' || timeZoneFlag == '-') {
            char t0 = charAt(bp + dateLenVariabile + 10 + millisLen + 1);

            if (t0 < '0' || t0 > '1') {
                return false;
            }
            char t1 = charAt(bp + dateLenVariabile + 10 + millisLen + 2);

            if (extracted35(t1)) {
                return false;
            }

            char t2 = charAt(bp + dateLenVariabile + 10 + millisLen + 3);
            char t3 = '0';
            char t4 = '0';

            if (t2 == ':') { // ThreeLetterISO8601TimeZone
                t3 = charAt(bp + dateLenVariabile + 10 + millisLen + 4);
                t4 = charAt(bp + dateLenVariabile + 10 + millisLen + 5);

                if(t3 == '4' && t4 == '5') {
                    // handle some special timezones like xx:45

                    if (t0 == '1' && (t1 == '2' || t1 == '3')) {
                        // NZ-CHAT          => +12:45
                        // Pacific/Chatham  => +12:45
                        // NZ-CHAT          => +13:45 (DST)
                        // Pacific/Chatham  => +13:45 (DST)
                    } else if (t0 == '0' && (t1 == '5' || t1 == '8')) {
                        // Asia/Kathmandu   => +05:45
                        // Asia/Katmandu    => +05:45
                        // Australia/Eucla  => +08:45
                    } else {
                        return false;
                    }
                } else {
                    //handle normal timezone like xx:00 and xx:30

                    if (t3 != '0' && t3 != '3') {
                        return false;
                    }
                    if (t4 != '0') {
                        return false;
                    }
                }
                timzeZoneLength = 6;
            } else if (t2 == '0') { // TwoLetterISO8601TimeZone
                t3 = charAt(bp + dateLenVariabile + 10 + millisLen + 4);

                if (t3 != '0' && t3 != '3') {
                    return false;
                }

                timzeZoneLength = 5;
            } else if (t2 == '3' && charAt(bp + dateLenVariabile + 10 + millisLen + 4) == '0') {
                t3 = '3';
                t4 = '0';
                timzeZoneLength = 5;
            } else if (t2 == '4' && charAt(bp + dateLenVariabile + 10 + millisLen + 4) == '5') {
                t3 = '4';
                t4 = '5';
                timzeZoneLength = 5;
            } else {
                timzeZoneLength = 3;
            }
            setTimeZone(timeZoneFlag, t0, t1, t3, t4);
        } else if (timeZoneFlag == 'Z') {// UTC
            timzeZoneLength = 1;

            if (calendar.getTimeZone().getRawOffset() != 0) {
                String[] timeZoneIDs = TimeZone.getAvailableIDs(0);

                if (timeZoneIDs.length > 0) {
                    TimeZone timeZone = TimeZone.getTimeZone(timeZoneIDs[0]);
                    calendar.setTimeZone(timeZone);
                }
            }
        }
        char end = charAt(bp + (dateLenVariabile + 10 + millisLen + timzeZoneLength));

        if (end != EOI && end != '"') {
            return false;
        }
        bp += (dateLenVariabile + 10 + millisLen + timzeZoneLength);
        ch = charAt(bp);
        token = JSONToken.LITERAL_ISO8601_DATE;
        return true;
    }

    protected void setTime(char h0, char h1, char m0, char m1, char s0, char s1) {
        int hour = (h0 - '0') * 10 + (h1 - '0');
        int minute = (m0 - '0') * 10 + (m1 - '0');
        int seconds = (s0 - '0') * 10 + (s1 - '0');

        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, seconds);
    }

    protected void setTimeZone(char timeZoneFlag, char t0, char t1) {
        setTimeZone(timeZoneFlag, t0, t1, '0', '0');
    }

    protected void setTimeZone(char timeZoneFlag, char t0, char t1, char t3, char t4) {
        int timeZoneOffset = ((t0 - '0') * 10 + (t1 - '0')) * 3600 * 1000;
        timeZoneOffset += ((t3 - '0') * 10 + (t4 - '0')) * 60 * 1000;

        if (timeZoneFlag == '-') {
            timeZoneOffset = -timeZoneOffset;
        }

        if (calendar.getTimeZone().getRawOffset() != timeZoneOffset) {
            calendar.setTimeZone(new SimpleTimeZone(timeZoneOffset, Integer.toString(timeZoneOffset)));
        }
    }

    private boolean checkTime(char h0, char h1, char m0, char m1, char s0, char s1) {
        boolean a = ((h0 == '0' && extracted35(h1)) || (h0 == '1' && extracted35(h1)) || (h0 == '2' && (h1 < '0' || h1 > '4')) || ((m0 >= '0' && m0 <= '5') && extracted35(m1)) || (m0 == '6' && m1 != '0') || ((s0 >= '0' && s0 <= '5') && extracted35(s1)) || (s0 == '6' && s1 != '0'));
        return !a;

    }

    private void setCalendar(char y0, char y1, char y2, char m0, char m1, char d0, char d1) {
        calendar = Calendar.getInstance(timeZone, locale);
        int year = (y0 - '0') * 1000 + (y1 - '0') * 100 + (y2 - '0') * 10;
        int month = (m0 - '0') * 10 + (m1 - '0') - 1;
        int day = (d0 - '0') * 10 + (d1 - '0');
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
    }

    static boolean checkDate(char y0, char y1, char y2, char y3, char m0, char m1) {
        if (extracted42(y0, y1, y2, y3)) {
            return false;
        }

        return !extracted45(m0, m1);
    }

    private static boolean extracted45(char m0, char m1) {
        return extracted43(m0, m1) || extracted44(m0, m1);
    }

    private static boolean extracted44(char m0, char m1) {
        return m0 == '1' && extracted37(m1);
    }

    private static boolean extracted43(char m0, char m1) {
        return m0 == '0' && extracted36(m1);
    }

    private static boolean extracted42(char y0, char y1, char y2, char y3) {
        return extracted35(y0) || extracted35(y1)|| extracted35(y2) || extracted35(y3);
    }

    private static boolean extracted37(char m1) {
        return m1 != '0' && m1 != '1' && m1 != '2';
    }

    private static boolean extracted36(char m1) {
        return m1 < '1' || m1 > '9';
    }

    private static boolean extracted35(char y0) {
        return y0 < '0' || y0 > '9';
    }

    @Override
    public boolean isEOF() {
        return bp == len || (ch == EOI && bp + 1 >= len);
    }

    @Override
    public int scanFieldInt(char[] fieldName) {
        matchStat = UNKNOWN;
        int startPos = this.bp;
        char startChar = this.ch;

        if (!charArrayCompare(text, bp, fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int index = bp + fieldName.length;
        char ch = charAt(index++);
        final boolean quote = ch == '"';

        if (quote) {
            ch = charAt(index++);
        }

        final boolean negative = ch == '-';

        if (negative) {
            ch = charAt(index++);
        }

        int value = 0;

        if (extracted17(ch)) {

            for (;;) {
                switch (ch) {
                    case ',':
                    case '}':
                        bp = index - 1;
                        break;
                    case ' ':
                    case '\b':
                    case '\t':
                        ch = charAt(index++);
                        continue;
                    default:
                        matchStat = NOT_MATCH;
                        return 0;
                }
            }
        } else {
            matchStat = NOT_MATCH;
        }
        if (ch == ',') {
            this.ch = charAt(++bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return extracted15(negative, value);
        }
        if (ch == '}') {
            bp = index - 1;
            ch = charAt(++bp);
            switch (ch) {
                case ',':
                    token = JSONToken.COMMA;
                    this.ch = charAt(++bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    this.ch = charAt(++bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    this.ch = charAt(++bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    break;
                case ' ':
                case '\b':
                case '\t':
                    charAt(++bp);
                    break;

                default:
                    this.bp = startPos;
                    this.ch = startChar;
                    matchStat = NOT_MATCH;
                    return 0;
            }

            matchStat = END;
        }
        return extracted15(negative, value);
    }

    @Override
    public String scanFieldString(char[] fieldName) {

        matchStat = UNKNOWN;
        int startPos = this.bp;
        char startChar = this.ch;

        for (;;) {
            if (charArrayCompare(text, bp, fieldName)) {
                break;
            }
            extracted34();
            matchStat = NOT_MATCH_NAME;
            return stringDefaultValue();
        }
        int index = bp + fieldName.length;
        int spaceCount = 0;
        char ch = charAt(index++);

        if (ch != '"') {
            while (isWhitespace(ch)) {
                spaceCount++;
                ch = charAt(index++);
            }

        }
        final String strVal;
        int startIndex = index;
        int endIndex = indexOf('"', startIndex);
        extracted24(endIndex);
        String stringVal = subString(startIndex, endIndex - startIndex);

        if (stringVal.indexOf('\\') != -1) {
            endIndex = extracted26(endIndex);
            int charsLenVariabile = endIndex - (bp + fieldName.length + 1 + spaceCount);
            char[] chars = subCharsMetodo(bp + fieldName.length + 1 + spaceCount, charsLenVariabile);
            stringVal = readString(chars, charsLenVariabile);
        }

        if ((this.features & Feature.TRIM_STRING_FIELD_VALUE.mask) != 0) {
            stringVal = stringVal.trim();
        }

        ch = charAt(endIndex + 1);
        for (;;) {
            strVal = stringVal;
            switch (ch) {
                case ',':
                case '}':
                    bp = endIndex + 1;
                    this.ch = ch;

                    break;
                case ' ':
                case '\b':
                case '\t':
                    endIndex++;
                    ch = charAt(endIndex + 1);
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return stringDefaultValue();
            }
            break;
        }

        if (ch == ',') {
            this.ch = charAt(++bp);
            matchStat = VALUE;
            return strVal;
        } else {
            //condition ch == '}' is always 'true'
            ch = charAt(++bp);
            switch (ch) {
                case ',':
                    token = JSONToken.COMMA;
                    this.ch = charAt(++bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    this.ch = charAt(++bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    this.ch = charAt(++bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    break;

                default:
                    this.bp = startPos;
                    this.ch = startChar;
                    matchStat = NOT_MATCH;
                    return stringDefaultValue();
            }

            matchStat = END;
        }
        return strVal;
    }

    private void extracted34() {
        while (isWhitespace(ch)) {
            next();
        }
    }

    @Override
    public java.util.Date scanFieldDate(char[] fieldName) {
        matchStat = UNKNOWN;
        int startPos = this.bp;
        char startChar = this.ch;
        int index = bp + fieldName.length;
        char ch = charAt(index++);
        final java.util.Date dateVal;

        if (ch == '"') {
            int startIndex = index;
            int endIndex = indexOf('"', startIndex);
            extracted24(endIndex);
            int rest = endIndex - startIndex;
            bp = index;

            if (!scanISO8601DateIfMatch(false, rest)) {
                bp = startPos;
                matchStat = NOT_MATCH;
                return null;
            }

            dateVal = calendar.getTime();
            ch = charAt(endIndex + 1);
            bp = startPos;

            for (; ; ) {
                switch (ch) {
                    case ',':
                    case '}':
                        bp = endIndex + 1;
                        this.ch = ch;
                        break;
                    case ' ':
                    case '\t':
                    case '\b':
                        endIndex++;
                        ch = charAt(endIndex + 1);
                        break;

                    default:
                        matchStat = NOT_MATCH;
                        return null;
                }
                break;
            }
        } else if (extracted49(ch)) {
            long millis = 0;

            millis = extracted33(ch,index, millis);

            dateVal = new java.util.Date(millis);
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if(ch ==  ',') {

            this.ch = charAt(++bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return dateVal;
        }else{
            ch = charAt(++bp);
            switch (ch) {
                case ',':
                    token = JSONToken.COMMA;
                    this.ch = charAt(++bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    this.ch = charAt(++bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    this.ch = charAt(++bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    break;

                default:
                    this.bp = startPos;
                    this.ch = startChar;
                    matchStat = NOT_MATCH;
                    return null;
            }

            matchStat = END;

        }
        return dateVal;
    }

    private long extracted33(char ch, int index, long millis) {
        if (extracted17(ch)) {
            millis = (long) ch - '0';
            extr8(ch, index, millis);
        }
        return millis;
    }

    private void extr8(char ch, int index, long millis){
        for(;;){
            if (extracted17(ch)) {
                extracted32(ch, millis);
            } else {
                extracted31(index, ch);
            }
            break;
        }
    }

    private long extracted32(char ch, long millis) {
        return millis * 10 + (ch - '0');
    }

    private void extracted31(int index, char ch) {
        if (ch == ',' || ch == '}') {
            bp = index - 1;
        }
    }

    @Override
    public long scanFieldSymbol(char[] fieldName) {
        matchStat = UNKNOWN;
        extr7(text, bp, fieldName);
        int index = bp + fieldName.length;
        char ch = charAt(index++);

        if (ch != '"') {
            while (isWhitespace(ch)) {
                ch = charAt(index++);
            }

            if (ch != '"') {
                matchStat = NOT_MATCH;
                return 0;
            }
        }
        long hash = FNV1A_64_MAGIC_HASHCODE;

        for (;;) {

            if (ch == ',') {
                this.ch = charAt(++bp);
                matchStat = VALUE;
                return hash;
            } else if (ch == '}') {
                next();
                skipWhitespace();
                ch = getCurrent();
                switch (ch) {
                    case ',':
                        token = JSONToken.COMMA;
                        this.ch = charAt(++bp);
                        break;
                    case ']':
                        token = JSONToken.RBRACKET;
                        this.ch = charAt(++bp);
                        break;
                    case '}':
                        token = JSONToken.RBRACE;
                        this.ch = charAt(++bp);
                        break;
                    case EOI:
                        token = JSONToken.EOF;
                        break;

                    default:
                        matchStat = NOT_MATCH;
                        return 0;
                }
                matchStat = END;
                break;
            } else if (isWhitespace(ch)) {
                ch = charAt(++bp);
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
        }
        return hash;
    }

    private void extr7(String text,int bp ,char[] fieldName){
        for (;;) {
            if (!charArrayCompare(text, bp, fieldName) && isWhitespace(ch)) {

                next();
                extracted30();
            } else {
                matchStat = NOT_MATCH_NAME;
                break;
            }

        }
    }

    private void extracted30() {
        while (isWhitespace(ch)) {
            next();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> scanFieldStringArray(char[] fieldName, Class<?> type) {
        matchStat = UNKNOWN;

        Collection<String> list = newCollectionByType(type);
        int startPos = this.bp;
        char startChar = this.ch;
        int index = bp + fieldName.length;
        char ch = charAt(index++);

        if (ch == '[') {
            ch = charAt(index++);
            for (;;) {

                if (ch == '"') {
                    int startIndex = index;
                    int endIndex = indexOf('"', startIndex);
                    extracted24(endIndex);
                    String stringVal = subString(startIndex, endIndex - startIndex);

                    if (stringVal.indexOf('\\') != -1) {
                        endIndex = extracted26(endIndex);
                        int charsLenVariabile = endIndex - startIndex;
                        char[] chars = subCharsMetodo(startIndex, charsLenVariabile);
                        stringVal = readString(chars, charsLenVariabile);
                    }
                    index = endIndex + 1;
                    charAt(index);
                    list.add(stringVal);
                } else if (extracted27(index, ch)) {
                    list.add(null);
                }
                matchStat = NOT_MATCH;
                return newCollectionByType(type);
            }
        } else if (text.startsWith("ull", index)) {
            index += 3;
            ch = charAt(index++);
            list = null;
        } else {
            matchStat = NOT_MATCH;
            return list;
        }

        bp = index;

        switch (ch) {
            case ',':
                this.ch = charAt(bp);
                matchStat = VALUE;
                return list;
            case '}':
                ch = charAt(bp);
                extracted29(index, ch);
                matchStat = END;
                break;
            default:
                this.ch = startChar;
                bp = startPos;
                matchStat = NOT_MATCH;
                return list;
        }

        return newCollectionByType(type);
    }
    private void extracted29(int index, char ch) {

        for (;;) {
            switch (ch) {
                case ',':
                    token = JSONToken.COMMA;
                    this.ch = charAt(++bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    this.ch = charAt(++bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    this.ch = charAt(++bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    this.ch = ch;
                    break;

                default:
                    while (isWhitespace(ch)) {
                        ch = charAt(index++);
                        bp = index;
                    }

                    matchStat = NOT_MATCH;
                    break;
            }
            break;
        }
    }
    private boolean extracted27(int index, char ch) {

        return ch == 'n' && text.startsWith("ull", index);

    }
    private int extracted26(int endIndex) {

        for (;;) {
            int slashCount = 0;
            slashCount = extracted25(endIndex, slashCount);
            if (slashCount % 2 == 0) {
                break;
            }
            endIndex = indexOf('"', endIndex + 1);
        }
        return endIndex;
    }
    private int extracted25(int endIndex, int slashCount) {

        for (int i = endIndex - 1; i >= 0; --i) {
            if (charAt(i) == '\\') {
                slashCount++;
            } else {
                break;
            }
        }
        return slashCount;
    }
    private void extracted24(int endIndex){

        if (endIndex == -1) {
            throw new JSONException(UNCLOSED);
        }
    }
    @Override
    public long scanFieldLong(char[] fieldName) {
        matchStat = UNKNOWN;
        int startPos = this.bp;
        char startChar = this.ch;

        if (!charArrayCompare(text, bp, fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int index = bp + fieldName.length;
        char ch = charAt(index++);
        final boolean quote = ch == '"';

        if (quote) {
            ch = charAt(index++);
        }
        boolean negative = false;

        if (ch == '-') {
            ch = charAt(index++);
            negative = true;
        }

        long value;

        if (extracted17(ch)) {
            value = (long) ch - '0';
            extr5(index, ch, quote);
            boolean valid = extracted14(negative, value);
            extr6(valid, startPos, startChar);
        } else {
            this.bp = startPos;
            this.ch = startChar;
            matchStat = NOT_MATCH;
            return 0;
        }

        for (;;) {
            switch (ch) {
                case ',':
                    this.ch = charAt(++bp);
                    matchStat = VALUE;
                    token = JSONToken.COMMA;
                    return extracted16(negative, value);
                case '}':
                    ch = charAt(++bp);
                    for (;;) {
                        switch (ch) {
                            case ',':
                                token = JSONToken.COMMA;
                                this.ch = charAt(++bp);
                                break;
                            case ']':
                                token = JSONToken.RBRACKET;
                                this.ch = charAt(++bp);
                                break;
                            case '}':
                                token = JSONToken.RBRACE;
                                this.ch = charAt(++bp);
                                break;
                            case EOI:
                                token = JSONToken.EOF;
                                break;
                            case ' ':
                            case '\t':
                            case '\b':
                                ch = charAt(++bp);
                                continue;

                            default:
                                this.bp = startPos;
                                this.ch = startChar;
                                matchStat = NOT_MATCH;
                                return 0;
                        }
                    }
                case ' ':
                case '\t':
                case '\b':
                    bp = index;
                    ch = charAt(index++);
                    continue;
                default:
                    matchStat = NOT_MATCH;
            }
        }
    }

    private int extr6(boolean valid, int startPos, char startChar){

        if (!valid) {
            this.bp = startPos;
            this.ch = startChar;
            matchStat = NOT_MATCH;

        }
        return 0;

    }
    private int extr5( int index, int value, boolean quote){

        int ret = 0;
        char ch;

        for (;;) {
            ch = charAt(index++);
            if (extracted17(ch)) {
                ret = value * 10 + (ch - '0');
            } else if (extracted19(ch)) {
                matchStat = NOT_MATCH;
                ret = 0;
            } else {
                if (extracted18(ch, quote)) {
                    matchStat = NOT_MATCH;
                    ret = 0;
                } else {
                    ch = charAt(index++);
                }

                extracted13(index, ch);
                break;
            }
        }
        return ret;

    }
    private boolean extracted18(char ch, final boolean quote) {
        return quote && ch != '"';
    }

    private long extracted16(boolean negative, long value) {
        return negative ? -value : value;
    }

    private boolean extracted14(boolean negative, long value) {
        return value >= 0 || (value == -9223372036854775808L && negative);
    }

    private void extracted13(int index, char ch) {

        if (ch == ',' || ch == '}') {
            bp = index - 1;
        }

    }

    @Override
    public boolean scanFieldBoolean(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(text, bp, fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return false;
        }

        int startPos = bp;
        int index = bp + fieldName.length;
        char ch = charAt(index++);
        final boolean quote = ch == '"';

        if (quote) {
            ch = charAt(index++);
        }

        switch (ch) {
            case 't':
                extr3(index, 'r');

                extr3(index, 'u');

                extr3(index, 'e');

                extr4(quote, index);
                bp = index;
                ch = charAt(bp);
                break;
            case 'f':
                extr3(index, 'a');

                extr3(index, 'l');

                extr3(index, 's');

                extr3(index, 'e');

                extr4(quote, index);
                bp = index;
                ch = charAt(bp);
                break;
            case '1':
            case '0':
                extr4(quote, index);
                bp = index;
                ch = charAt(bp);
                break;

            default:
                matchStat = NOT_MATCH;
                return false;
        }

        for (;;) {
            switch (ch) {
                case ',':
                    this.ch = charAt(++bp);
                    matchStat = VALUE;
                    token = JSONToken.COMMA;
                    break;

                case '}':
                    ch = charAt(++bp);
                    for (;;) {
                        switch (ch) {
                            case ',':
                                token = JSONToken.COMMA;
                                this.ch = charAt(++bp);
                                break;
                            case ']':
                                token = JSONToken.RBRACKET;
                                this.ch = charAt(++bp);
                                break;
                            case '}':
                                token = JSONToken.RBRACE;
                                this.ch = charAt(++bp);
                                break;
                            case EOI:
                                token = JSONToken.EOF;
                                break;
                            case ' ':
                            case '\t':
                            case '\b':
                                ch = charAt(++bp);
                                continue;

                            default:
                                matchStat = NOT_MATCH;
                                return false;
                        }
                    }
                case ' ':
                case '\t':
                case '\b':
                    ch = charAt(bp);
                    break;
                default:
                    bp = startPos;
                    charAt(++bp);
                    matchStat = NOT_MATCH;
                    return false;
            }

        }
    }

    private boolean extr4(boolean quote, int index){

        if (quote && charAt(index) != '"') {
            matchStat = NOT_MATCH;
        }
        return false;

    }
    private boolean extr3(int index, char ch1){

        if (charAt(index) != ch1) {
            matchStat = NOT_MATCH;
        }
        return false;

    }
    @Override
    public final int scanInt(char expectNext) {
        matchStat = UNKNOWN;
        int offset = bp;
        char chLocal = charAt(offset++);

        while (isWhitespace(chLocal)) {
            chLocal = charAt(offset++);
        }
        final boolean quote = chLocal == '"';

        if (quote) {
            chLocal = charAt(offset++);
        }

        final boolean negative = chLocal == '-';

        if (negative) {
            chLocal = charAt(offset++);
        }

        int value;

        if (extracted17(chLocal)) {
            value = chLocal - '0';
            extr(value);
        } else if (chLocal == 'n' && charAt(offset++) == 'u' && charAt(offset++) == 'l') {
            matchStat = VALUE_NULL;
            value = 0;
            chLocal = charAt(offset++);
            if (extracted20(chLocal, quote)) {
                chLocal = charAt(offset++);
            }

            extr2(chLocal, offset, value);

            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        for (;;) {
            if (chLocal == expectNext) {
                extracted21(offset);
                return extracted15(negative, value);
            } else if (isWhitespace(chLocal)) {

                chLocal = charAt(offset++);
                continue;

            }
            matchStat = NOT_MATCH;
            return extracted15(negative, value);
        }

    }

    private int extr2(char chLocal, int offset, int value){

        int a = 0;

        for(;;){
            switch (chLocal) {
                case ',':
                    a = extracted22(offset, value);
                    break;
                case ']':
                    a = extracted23(offset, value);
                    break;
                case ' ':
                case '\t':
                case '\b':
                    chLocal = charAt(offset++);
                    continue;
                default:
                    return a;
            }
        }
    }

    private int extr(int value){

        if (value < 0) {
            matchStat = NOT_MATCH;

        }
        return 0;

    }

    private int extracted23(int offset, int value) {

        bp = offset;
        this.ch = charAt(bp);
        matchStat = VALUE_NULL;
        token = JSONToken.RBRACKET;
        return value;

    }

    private int extracted22(int offset, int value) {

        bp = offset;
        this.ch = charAt(bp);
        matchStat = VALUE_NULL;
        token = JSONToken.COMMA;
        return value;

    }

    private void extracted21(int offset) {

        bp = offset;
        this.ch = charAt(bp);
        matchStat = VALUE;
        token = JSONToken.COMMA;

    }

    private boolean extracted20(char chLocal, final boolean quote) {

        return quote && chLocal == '"';

    }

    private boolean extracted19(char chLocal) {

        return chLocal == '.';

    }

    private int extracted15(final boolean negative, int value) {

        return negative ? -value : value;

    }

    private boolean extracted17(char chLocal) {

        return chLocal >= '0' && chLocal <= '9';

    }

    @Override
    public  double scanDouble(char seperator) {

        matchStat = UNKNOWN;
        int offset = bp;
        char chLocal = charAt(offset++);
        final boolean quote = chLocal == '"';

        if (quote) {
            chLocal = charAt(offset++);
        }

        boolean negative = chLocal == '-';

        if (negative) {
            chLocal = charAt(offset++);
        }

        double value;

        if (extracted17(chLocal)) {
            long intVal = (long) chLocal - '0';
            long power = 1;
            boolean exp = chLocal == 'e' || chLocal == 'E';

            if (exp) {
                chLocal = charAt(offset++);

                if (chLocal == '+' || chLocal == '-') {
                    chLocal = charAt(offset++);
                }

            }

            int start;
            int count;
            start = bp;
            count = offset - start - 1;
            value = extracted59(negative, intVal, power, exp, start, count);

        } else if (chLocal == 'n') {

            matchStat = VALUE_NULL;
            matchStat = NOT_MATCH;
            return 0;

        } else {

            matchStat = NOT_MATCH;
            return 0;

        }
        return extracted60(seperator, offset, chLocal, value);
    }

    private double extracted60(char seperator, int offset, char chLocal, double value) {

        if (chLocal == seperator) {

            bp = offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return 0;

        } else {

            matchStat = NOT_MATCH;
            return value;

        }
    }

    private double extracted59(boolean negative, long intVal, long power, boolean exp, int start, int count) {

        double value;

        if (!exp && count < 18) {
            value = ((double) intVal) / power;
            value = extracted58(negative, value);
        } else {
            String text2 = this.subString(start, count);
            value = Double.parseDouble(text2);
        }
        return value;
    }

    private double extracted58(boolean negative, double value) {

        if (negative) {
            value = -value;
        }
        return value;

    }

    @Override
    public long scanLong(char seperator) {

        matchStat = UNKNOWN;
        int offset = bp;
        char chLocal = charAt(offset++);
        final boolean negative = chLocal == '-';
        long value;

        if (extracted17(chLocal)) {
            value = (long) chLocal - '0';
            for (;;) {
                chLocal = charAt(offset++);
                value = extracted47(chLocal, value);

                if (!extracted19(chLocal)) {
                    break;
                }
            }
        } else if (chLocal == 'n') {
            matchStat = VALUE_NULL;
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }


        for (;;) {
            if (chLocal == seperator) {
                bp = offset;
                this.ch = charAt(bp);
                matchStat = VALUE;
                token = JSONToken.COMMA;
                return extracted16(negative, value);
            } else if (isWhitespace(chLocal)) {
                chLocal = charAt(offset++);
                continue;
            }
            matchStat = NOT_MATCH;
            return value;
        }
    }
    private long extracted47(char chLocal, long value) {

        if (extracted17(chLocal)) {
            value = extracted32(chLocal, value);
        }
        return value;
    }
    @Override
    public java.util.Date scanDate() {

        matchStat = UNKNOWN;
        int startPos = this.bp;
        char startChar = this.ch;
        int index = bp;
        char ch = charAt(index++);
        final java.util.Date dateVal;


        if (ch == '"') {
            int startIndex = index;
            int endIndex = indexOf('"', startIndex);
            extracted24(endIndex);
            bp = index;
            dateVal = calendar.getTime();
            ch = charAt(endIndex + 1);
            bp = startPos;
        } else if (extracted49(ch)) {
            long millis = 0;
            boolean negative = false;


            if (ch == '-') {
                negative = true;
            }


            millis = extracted53(ch, millis);
            if (millis < 0) {
                this.bp = startPos;
                this.ch = startChar;
                matchStat = NOT_MATCH;
                return null;
            }


            millis = extracted54(millis, negative);
            dateVal = new java.util.Date(millis);
        } else if (ch == 'n') {
            dateVal = null;
            ch = charAt(index);
            bp = index;
        } else {
            this.bp = startPos;
            this.ch = startChar;
            matchStat = NOT_MATCH;
            return null;
        }


        if (ch == ',') {
            this.ch = charAt(++bp);
            matchStat = VALUE;
            return dateVal;
        } else {
            //condition ch == '}' is always 'true'
            ch = charAt(++bp);
            extracted57(ch);
            extracted56(ch);
            extracted55(ch);
            matchStat = END;
        }
        return dateVal;
    }

    private void extracted57(char ch) {
        if (ch == ',') {
            token = JSONToken.COMMA;
            this.ch = charAt(++bp);
        }
    }

    private void extracted56(char ch) {
        if (ch == ']') {
            token = JSONToken.RBRACKET;
            this.ch = charAt(++bp);
        }
    }

    private void extracted55(char ch) {
        if (ch == '}') {
            token = JSONToken.RBRACE;
            this.ch = charAt(++bp);
        }
    }

    private long extracted54(long millis, boolean negative) {
        if (negative) {
            millis = -millis;
        }
        return millis;
    }

    private long extracted53(char ch, long millis) {
        if (extracted17(ch)) {
            millis = (long) ch - '0';
        }
        return millis;
    }

    private boolean extracted49(char ch) {
        return ch == '-' || extracted17(ch);
    }

    @Override
    protected final void arrayCopy(int srcPos, char[] dest, int destPos, int length) {
        text.getChars(srcPos, srcPos + length, dest, destPos);
    }

    @Override
    public String info() {
        StringBuilder buf = new StringBuilder();
        int line = 1;
        int column = 1;


        for (int i = 0; i < bp; ++i, column++) {
            char ch = text.charAt(i);

            if (ch == '\n') {
                column = 1;
                line++;
            }
        }

        buf.append("pos ").append(bp)
                .append(", line ").append(line)
                .append(", column ").append(column);


        if (text.length() < 65535) {
            buf.append(text);
        } else {
            buf.append(text.substring(0, 65535));
        }
        return buf.toString();
    }

    @Override
    public String[] scanFieldStringArray(char[] fieldName, int argTypesCount, SymbolTable typeSymbolTable) {
        int startPos = bp;
        char starChar = ch;
        extracted8();
        int offset;
        char ch;
        matchStat = UNKNOWN;


        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return new String[0];
        }

        offset = bp + fieldName.length;
        ch = text.charAt(offset++);

        while (isWhitespace(ch)) {
            ch = text.charAt(offset++);
        }

        if (ch != ':') {
            matchStat = NOT_MATCH;
            return new String[0];
        }

        ch = text.charAt(offset++);

        while (isWhitespace(ch)) {
            ch = text.charAt(offset++);
        }


        if (ch == '[') {
            bp = offset;
            this.ch = text.charAt(bp);
        } else if (extracted12(ch)) {
            bp += 4;
            this.ch = text.charAt(bp);
            return new String[0];
        } else {
            matchStat = NOT_MATCH;
            return new String[0];
        }

        String[] types = argTypesCount >= 0 ? new String[argTypesCount] : new String[4];
        int typeIndex = 0;

        for (;;) {
            extractedv9();

            if (this.ch != '\"') {
                this.bp = startPos;
                this.ch = starChar;
                matchStat = NOT_MATCH;
                return new String[0];
            }

            String type = scanSymbol(typeSymbolTable, '"');
            types = extracted10(types, typeIndex);
            types[typeIndex++] = type;
            extractedv9();

            if (this.ch == ',') {
                next();
            }

            break;
        }

        types = extracted11(types, typeIndex);
        extractedv9();


        if (this.ch != ']') {
            next();
        } else {
            this.bp = startPos;
            this.ch = starChar;
            matchStat = NOT_MATCH;
            return new String[0];
        }
        return types;
    }

    private boolean extracted12(char ch) {
        return ch == 'n' && text.startsWith("ull", bp + 1);
    }

    private String[] extracted11(String[] types, int typeIndex) {
        if (types.length != typeIndex) {
            String[] array = new String[typeIndex];
            System.arraycopy(types, 0, array, 0, typeIndex);
            types = array;
        }
        return types;
    }

    private String[] extracted10(String[] types, int typeIndex) {
        if (typeIndex == types.length) {
            int newCapacity = types.length + (types.length >> 1) + 1;
            String[] array = new String[newCapacity];
            System.arraycopy(types, 0, array, 0, types.length);
            types = array;
        }
        return types;
    }

    private void extractedv9() {
        while (isWhitespace(this.ch)) {
            next();
        }
    }

    private void extracted8() {
        while (isWhitespace(ch)) {
            next();
        }
    }

    @Override
    public boolean matchField2(char[] fieldName) {

        while (isWhitespace(ch)) {
            next();
        }

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return false;
        }

        int offset = bp + fieldName.length;
        char ch = text.charAt(offset++);

        while (isWhitespace(ch)) {
            ch = text.charAt(offset++);
        }


        if (ch == ':') {
            this.bp = offset;
            this.ch = charAt(bp);
            return true;
        } else {
            matchStat = NOT_MATCH_NAME;
            return false;
        }
    }

    @Override
    public final void skipObject() {
        skipObject(false);
    }

    @Override
    public final void skipObject(boolean valid) {
        boolean quote = false;
        int braceCnt = 0;
        int i = bp;


        for (; i < text.length(); ++i) {
            final char ch = text.charAt(i);

            if (ch == '\\') {
                extracted4(i, ch);
                ++i;
            } else if (ch == '"') {
                quote = !quote;
            } else if (ch == '{') {
                braceCnt++;
            } else if (ch == '}') {
                if (!quote) {
                    continue;
                } else {
                    braceCnt--;
                }
                extracted7(braceCnt, i);

            }
        }
        i = extracted3(i);
        extracted(i);
    }

    private void extracted7(int braceCnt, int i) {
        if (braceCnt == -1) {
            extracted6(i);
        }
    }

    private void extracted6(int i) {
        this.bp = i + 1;

        if (this.bp == text.length()) {
            this.ch = EOI;
            this.token = JSONToken.EOF;
            return;
        }

        this.ch = text.charAt(this.bp);

        if (this.ch == ',') {
            token = JSONToken.COMMA;
            int index = ++bp;
            this.ch = extracted5(index);
        } else if (this.ch == '}') {
            token = JSONToken.RBRACE;
            next();
        } else if (this.ch == ']') {
            token = JSONToken.RBRACKET;
            next();
        } else {
            nextToken(JSONToken.COMMA);
        }
    }

    private char extracted5(int index) {
        return index >= text.length() //
                ? EOI //
                : text.charAt(index);
    }

    private void extracted4(int i, final char ch) {
        if (i >= len - 1) {
            this.ch = ch;
            this.bp = i;
            throw new JSONException(ILLEGAL_STR + info());
        }
    }

    private int extracted3(int i) {
        for (int j = 0; j < bp; j++) {
            i = extracted2(i, j);
        }
        return i;
    }

    private int extracted2(int i, int j) {
        if (j < text.length() && text.charAt(j) == ' ') {
            i++;
        }
        return i;
    }

    private void extracted(int i) {
        if (i == text.length()) {
            throw new JSONException(ILLEGAL_STR + info());
        }
    }

    @Override
    public final void skipArray() {
        skipArray(false);
    }

    public final void skipArray(boolean valid) {
        boolean quote = false;
        int bracketCnt = 0;
        int i = bp;

        for (; i < text.length(); ++i) {
            char ch = text.charAt(i);
            i = extracted38(i, ch);
            quote = extracted39(quote, ch);
            bracketCnt = extracted40(bracketCnt, ch);
            extracted41(valid, ch);
            if (ch == ']') {

                if (quote) {
                    continue;
                } else {
                    bracketCnt--;
                }

                if (bracketCnt == -1) {
                    this.bp = i + 1;

                    if (this.bp == text.length()) {
                        this.ch = EOI;
                        token = JSONToken.EOF;
                        return;
                    }
                    this.ch = text.charAt(this.bp);
                    nextToken(JSONToken.COMMA);
                    return;
                }
            }
        }
        extracted(i);
    }

    private void extracted41(boolean valid, char ch) {
        if (ch == '{' && valid) {
            int index = ++bp;
            this.ch = extracted5(index);
            skipObject(valid);
        }
    }

    private int extracted40(int bracketCnt, char ch) {
        if (ch == '[') {
            bracketCnt++;
        }
        return bracketCnt;
    }

    private boolean extracted39(boolean quote, char ch) {
        if (ch == '"') {
            quote = !quote;
        }
        return quote;
    }

    private int extracted38(int i, char ch) {
        if (ch == '\\') {
            i = extracted28(i, ch);
        }
        return i;
    }

    private int extracted28(int i, char ch) {

        if (i < len - 1) {
            ++i;
        } else {
            this.ch = ch;
            this.bp = i;
            throw new JSONException(ILLEGAL_STR + info());
        }
        return i;

    }

    public final void skipString() {

        if (ch == '"') {
            int i = bp + 1;

            while (i < text.length()) {
                char c = text.charAt(i);

                if (c == '\\') {

                    if (i < len - 1) {
                        ++i;
                        ++i;
                        continue;
                    }

                } else if (c == '"') {
                    bp = i + 1;
                    this.ch = text.charAt(bp);
                    return;
                }
                ++i;
            }

            throw new JSONException(UNCLOSED);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean seekArrayToItem(int index) {
        extracted9(index);
        extracted46();

        for (int i = 0; i < index; ++i) {
            skipWhitespace();
            extracted48();

            if (ch == '[') {
                next();
                token = JSONToken.LBRACKET;
                skipArray(false);
            } else {
                boolean match = false;


                for (int j = bp + 1; j < text.length(); ++j) {
                    char c = text.charAt(j);
                    match = extracted51(match, j, c);

                    if (c == ']') {
                        bp = j + 1;
                        ch = charAt(bp);
                        nextToken();
                        return false;
                    }
                }
                extracted50(match);
            }
            if (token == JSONToken.COMMA) {
                continue;
            }
            return extracted52();
        }
        nextToken();
        return true;
    }

    private boolean extracted52() {
        if (token == JSONToken.RBRACKET) {
            return false;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private boolean extracted51(boolean match, int j, char c) {
        if (c == ',') {
            match = true;
            bp = j + 1;
            ch = charAt(bp);
        }
        return match;
    }

    private void extracted50(boolean match) {
        if (!match) {
            throw new JSONException("illegal json.");
        }
    }

    private void extracted48() {
        if (ch == '{') {
            next();
            token = JSONToken.LBRACE;
            skipObject(false);
        }
    }

    private void extracted46() {
        if (token != JSONToken.LBRACKET) {
            throw new UnsupportedOperationException();
        }
    }

    private void extracted9(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must > 0, but " + index);
        }
    }

    @Override
    public int seekObjectToField(long fieldNameHash, boolean deepScan) {

        if (token == JSONToken.EOF) {
            return JSONLexer.NOT_MATCH;
        }

        if (extracted88()) {
            nextToken();
            return JSONLexer.NOT_MATCH;
        }

        extracted89();


        for (;;) {

            if (ch == '}') {
                next();
                nextToken();
                return JSONLexer.NOT_MATCH;
            }

            if (ch == EOI) {
                return JSONLexer.NOT_MATCH;
            }

            extracted62();
            long hash = extracted108();


            if (hash == fieldNameHash) {
                extracted72();
                extracted93();
                return VALUE;
            }

            extracted72();
            extracted73();
            extracted76();
            // skip fieldValues
            extracted107();
            extracted106();
            extracted105();
            extracted104();
            extracted103();
            extracted109();
        }
    }

    private void extracted109() {
        if (ch == '{') {
            skipObject(false);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private long extracted108() {
        long hash;
        if (ch == '"') {
            hash = FNV1A_64_MAGIC_HASHCODE;
            hash = extracted64(hash);
        } else {
            throw new UnsupportedOperationException();
        }
        return hash;
    }

    private void extracted107() {
        if (extracted77()) {
            next();
            extracted78();
            extracted79();
            extracted80();
            extracted81();
            extracted82();
        }
    }

    private void extracted106() {
        if (ch == '"') {
            skipString();
            extracted83();
            extracted82();
        }
    }

    private void extracted105() {
        if (ch == 't') {
            next();
            extracted96();
            extracted83();
            extracted82();
        }
    }

    private void extracted104() {
        if (ch == 'n') {
            next();
            extracted99();
            extracted83();
            extracted82();
        }
    }

    private void extracted103() {
        if (ch == 'f') {
            next();
            extracted102();
            extracted83();
            extracted82();
        }
    }
    private void extracted102() {
        if (ch == 'a') {
            next();
            extracted101();
        }
    }
    private void extracted101() {
        if (ch == 'l') {
            next();
            extracted100();
        }
    }
    private void extracted100() {
        if (ch == 's') {
            next();
            extracted94();
        }
    }
    private void extracted99() {
        if (ch == 'u') {
            next();
            extracted98();
        }
    }
    private void extracted98() {
        if (ch == 'l') {
            next();
            extracted97();
        }
    }
    private void extracted97() {
        if (ch == 'l') {
            next();
        }
    }
    private void extracted96() {
        if (ch == 'r') {
            next();
            extracted95();
        }
    }
    private void extracted95() {
        if (ch == 'u') {
            next();
            extracted94();
        }
    }
    private void extracted94() {
        if (ch == 'e') {
            next();
        }
    }
    private void extracted93() {
        if (ch == ':') {
            int index = ++bp;
            ch = extracted5(index);
            extracted92();
            extracted91();
            extracted90();
        }
    }
    private void extracted92() {
        if (ch == ',') {
            int index = ++bp;
            ch = extracted5(index);
            token = JSONToken.COMMA;
        }
    }
    private void extracted91() {
        if (ch == ']') {
            int index = ++bp;
            ch = extracted5(index);
            token = JSONToken.RBRACKET;
        }
    }
    private void extracted90() {
        if (ch == '}') {
            int index = ++bp;
            ch = extracted5(index);
            token = JSONToken.RBRACE;
        } else
            extracted70();
    }
    private void extracted89() {
        if (token != JSONToken.LBRACE && token != JSONToken.COMMA) {
            throw new UnsupportedOperationException(JSONToken.name(token));
        }
    }
    private boolean extracted88() {
        return token == JSONToken.RBRACE || token == JSONToken.RBRACKET;
    }
    @Override
    public int seekObjectToField(long[] fieldNameHash) {
        extracted61();
        for (;;) {
            if (ch == '}') {
                next();
                nextToken();
                this.matchStat = JSONLexer.NOT_MATCH;
                return -1;
            }
            if (ch == EOI) {
                this.matchStat = JSONLexer.NOT_MATCH;
                return -1;
            }
            extracted62();
            long hash = extracted108();
            int matchIndex = extracted66(fieldNameHash, hash);
            if (matchIndex != -1) {
                extracted72();
                extracted71();
                matchStat = VALUE;
                return matchIndex;
            }
            extracted72();
            extracted73();
            extracted76();
            // skip fieldValues
            extracted84();
            extracted85();
            extracted86();
            extracted87();
        }
    }
    private void extracted87() {
        if (ch == '[') {
            next();
            skipArray(false);
        } else {
            throw new UnsupportedOperationException();
        }
    }
    private void extracted86() {
        if (ch == '{') {
            int index = ++bp;
            ch = extracted5(index);

            skipObject(false);
        }
    }
    private void extracted85() {
        extracted106();
    }
    private void extracted84() {
        extracted107();
    }
    private void extracted83() {
        if (ch != ',' && ch != '}') {
            skipWhitespace();
        }
    }
    private void extracted82() {
        if (ch == ',') {
            next();
        }
    }
    private void extracted81() {
        if (ch != ',') {
            skipWhitespace();
        }
    }
    private void extracted80() {
        if (ch == 'E' || ch == 'e') {
            next();
            if (ch == '-' || ch == '+') {
                next();
            }
            extracted78();
        }
    }
    private void extracted79() {
        if (ch == '.') {
            next();
            extracted78();
        }
    }
    private void extracted78() {
        while (ch >= '0' && ch <= '9') {
            next();
        }
    }
    private boolean extracted77() {
        return ch == '-' || ch == '+' || (ch >= '0' && ch <= '9');
    }
    private void extracted76() {
        if (extracted75()) {
            skipWhitespace();
        }
    }
    private boolean extracted75() {
        return extracted74()
                && ch != '6'
                && ch != '7'
                && ch != '8'
                && ch != '9'
                && ch != '+'
                && ch != '-';
    }
    private boolean extracted74() {
        return ch != '"'
                && ch != '\''
                && ch != '{'
                && ch != '['
                && ch != '0'
                && ch != '1'
                && ch != '2'
                && ch != '3'
                && ch != '4'
                && ch != '5';
    }
    private void extracted73() {
        if (ch == ':') {
            int index = ++bp;
            ch = extracted5(index);
        } else {
            throw new JSONException("illegal json, " + info());
        }
    }
    private void extracted72() {
        if (ch != ':') {
            skipWhitespace();
        }
    }
    private void extracted71() {
        if (ch == ':') {
            int index = ++bp;
            ch = extracted5(index);
            extracted67();
            extracted68();
            extracted69();
            extracted70();
        }
    }
    private void extracted70() {
        if (ch >= '0' && ch <= '9') {
            sp = 0;
            pos = bp;
            scanNumber();
        } else {
            nextToken(JSONToken.LITERAL_INT);
        }
    }
    private void extracted69() {
        if (ch == '}') {
            int index = ++bp;
            ch = extracted5(index);
            token = JSONToken.RBRACE;
        }
    }
    private void extracted68() {
        extracted91();
    }
    private void extracted67() {
        extracted92();
    }
    private int extracted66(long[] fieldNameHash, long hash) {
        int matchIndex = -1;
        for (int i = 0; i < fieldNameHash.length; i++) {
            if (hash == fieldNameHash[i]) {
                matchIndex = i;
                break;
            }
        }
        return matchIndex;
    }
    private long extracted64(long hash) {
        int i = bp + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                ++i;
                extracted63(i);
                c = text.charAt(i);
            }
            if (c == '"') {
                bp = i + 1;
                ch = (bp >= text.length() //
                        ? EOI //
                        : text.charAt(bp));
                break;
            }
            hash ^= c;
            hash *= FNV1A_64_MAGIC_PRIME;
            ++i;
        }
        return hash;
    }
    private void extracted63(int i) {
        if (i == text.length()) {
            throw new JSONException("unclosed str, " + info());
        }
    }
    private void extracted62() {
        if (ch != '"') {
            skipWhitespace();
        }
    }
    private void extracted61() {
        if (token != JSONToken.LBRACE && token != JSONToken.COMMA) {
            throw new UnsupportedOperationException();
        }
    }
    @Override
    public String scanTypeName(SymbolTable symbolTable) {
        if (text.startsWith("\"@type\":\"", bp)) {
            int p = text.indexOf('"', bp + 9);
            if (p != -1) {
                bp += 9;
                int h = 0;
                for (int i = bp; i < p; i++) {
                    h = 31 * h + text.charAt(i);
                }
                String typeName = addSymbol(bp, p - bp, h, symbolTable);
                char separator = text.charAt(p + 1);
                if (separator != ',' && separator != ']') {
                    return null;
                }
                bp = p + 2;
                ch = text.charAt(bp);
                return typeName;
            }
        }
        return null;
    }
}