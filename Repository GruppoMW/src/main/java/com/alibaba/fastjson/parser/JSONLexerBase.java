package com.alibaba.fastjson.parser;

import java.io.Closeable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.util.IOUtils;

import static com.alibaba.fastjson.parser.JSONToken.*;
import static com.alibaba.fastjson.util.TypeUtils.FNV1A_64_MAGIC_HASHCODE;
import static com.alibaba.fastjson.util.TypeUtils.FNV1A_64_MAGIC_PRIME;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public abstract class JSONLexerBase implements JSONLexer, Closeable {

    private static final String ERROR_PARSE_TRUE = "error parse true";
    private static final String ERROR_PARSE_FALSE = "error parse false";
    private static final String ILLEGAL_STATE = "illegal state. ";
    private static final String UNCLOSED_STR = "unclosed str";

    protected void lexError() {
        token = ERROR;
    }

    protected int                            token;
    protected int                            pos;
    protected int                            features;

    protected char                           ch;
    protected int                            bp;

    protected int                            eofPos;

    /**
     * A character buffer for literals.
     */
    protected char[]                         sbuf;
    protected int                            sp;

    /**
     * number start position
     */
    protected int                            np;

    protected boolean                        hasSpecial;

    protected Calendar                       calendar           = null;
    protected TimeZone                       timeZone           = JSON.defaultTimeZone;
    protected Locale                         locale             = JSON.defaultLocale;

    public int                               matchStat          = UNKNOWN;

    private static final ThreadLocal<char[]> SBUF_LOCAL         = new ThreadLocal<>();

    protected String                         stringDefaultValue = null;
    protected int                            nanos              = 0;

    protected JSONLexerBase(int features){
        this.features = features;

        if ((features & Feature.INIT_STRING_FIELD_AS_EMPTY.mask) != 0) {
            stringDefaultValue = "";
        }

        sbuf = SBUF_LOCAL.get();

        if (sbuf == null) {
            sbuf = new char[512];
        }
        SBUF_LOCAL.remove();
    }

    public final int matchStat() {
        return matchStat;
    }

    /**
     * internal method, don't invoke
     * @param token
     */
    public void setToken(int token) {
        this.token = token;
    }

    public final void nextToken() {
        sp = 0;

        for (;;) {
            pos = bp;

            if (ch == '/') {
                skipComment();
                continue;
            }

            if (ch == '"') {
                scanString();
                return;
            }

            if (ch == ',') {
                next();
                token = COMMA;
                return;
            }

            if (extractedv52()) {
                scanNumber();
                return;
            }

            if (ch == '-') {
                scanNumber();
                return;
            }

            switch (ch) {
                case '\'':
                    extracted53();
                    scanStringSingleQuote();
                    return;
                case ' ':
                case '\t':
                case '\b':
                case '\f':
                case '\n':
                case '\r':
                    next();
                    break;
                case 't': // true
                    scanTrue();
                    return;
                case 'f': // false
                    scanFalse();
                    return;
                case 'n': // new,null
                    scanNullOrNew();
                    return;
                case 'T':
                case 'N': // NULL
                case 'S':
                case 'u': // undefined
                    scanIdent();
                    return;
                case '(':
                    next();
                    token = LPAREN;
                    return;
                case ')':
                    next();
                    token = RPAREN;
                    return;
                case '[':
                    next();
                    token = LBRACKET;
                    return;
                case ']':
                    next();
                    token = RBRACKET;
                    return;
                case '{':
                    next();
                    token = LBRACE;
                    return;
                case '}':
                    next();
                    token = RBRACE;
                    return;
                case ':':
                    next();
                    token = COLON;
                    return;
                case ';':
                    next();
                    token = SEMI;
                    return;
                case '.':
                    next();
                    token = DOT;
                    return;
                case '+':
                    next();
                    scanNumber();
                    return;
                case 'x':
                    scanHex();
                    return;
                default:
                    extracted56();

                    return;
            }
        }

    }

    private void extracted56() {
        if (isEOF()) { // JLS
            extracted54();

            token = EOF;
            eofPos = pos = bp;
        } else {
            extracted55();

            lexError();
            next();
        }
    }

    private void extracted55() {
        if (ch <= 31 || ch == 127) {
            next();
        }
    }

    private void extracted54(){
        if (token == EOF) {
            throw new JSONException("EOF error");
        }
    }

    private void extracted53(){
        if (!isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
            throw new JSONException("Feature.ALLOW_SINGLE_QUOTES is false");
        }
    }

    public final void nextToken(int expect) {
        sp = 0;


        switch (expect) {
            case JSONToken.LBRACE:
                if (ch == '{') {
                    token = JSONToken.LBRACE;
                    next();
                    return;
                }

                break;
            case JSONToken.COMMA:
                if (ch == ',') {
                    token = JSONToken.COMMA;
                    next();
                    return;
                }
                break;
            case JSONToken.LITERAL_INT:
                if (extractedv52()) {
                    pos = bp;
                    scanNumber();
                    return;
                }

                break;
            case JSONToken.LITERAL_STRING:
                if (ch == '"') {
                    pos = bp;
                    scanString();
                    return;
                }
                break;
            case JSONToken.LBRACKET:
                if (ch == '[') {
                    token = JSONToken.LBRACKET;
                    next();
                    return;
                }

                break;
            case JSONToken.RBRACKET:
                if (ch == ']') {
                    token = JSONToken.RBRACKET;
                    next();
                    return;
                }
                break;
            case JSONToken.EOF:
                if (ch == EOI) {
                    token = JSONToken.EOF;
                    return;
                }
                break;
            case JSONToken.IDENTIFIER:
                nextIdent();
                return;
            default:
                break;
        }



        nextToken();
    }


    private boolean extractedv52() {
        return ch >= '0' && ch <= '9';
    }

    private boolean extracted51() {
        return ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f' || ch == '\b';
    }

    public final void nextIdent() {
        while (isWhitespace(ch)) {
            next();
        }
        if (ch == '_' || ch == '$' || Character.isLetter(ch)) {
            scanIdent();
        } else {
            nextToken();
        }
    }

    public final void nextTokenWithColon() {
        nextTokenWithChar(':');
    }

    public final void nextTokenWithChar(char expect) {
        sp = 0;

        for (;;) {
            if (ch == expect) {
                next();
                nextToken();
                return;
            }

            if (extracted51()) {
                next();
                continue;
            }

            throw new JSONException("not match " + expect + " - " + ch + ", info : " + this.info());
        }
    }

    public final int token() {
        return token;
    }

    public final String tokenName() {
        return JSONToken.name(token);
    }

    public final int pos() {
        return pos;
    }

    public final String stringDefaultValue() {
        return stringDefaultValue;
    }

    public final Number integerValue() throws NumberFormatException {
        long result = 0;
        boolean negative = false;
        if (np == -1) {
            np = 0;
        }
        int i = np;
        int max = np + sp;
        long limit;
        long multmin;
        int digit;

        char type = ' ';

        switch (charAt(max - 1)) {
            case 'L':
                max--;
                type = 'L';
                break;
            case 'S':
                max--;
                type = 'S';
                break;
            case 'B':
                max--;
                type = 'B';
                break;
            default:
                break;
        }

        if (charAt(np) == '-') {
            negative = true;
            limit = Long.MIN_VALUE;
            i++;
        } else {
            limit = -Long.MAX_VALUE;
        }
        multmin = MULTMIN_RADIX_TEN;
        if (i < max) {
            digit = charAt(i++) - '0';
            result = -digit;
        }
        while (i < max) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = charAt(i++) - '0';
            if (result < multmin) {
                return new BigInteger(numberString(), 10);
            }
            result *= 10;
            if (result < limit + digit) {
                return new BigInteger(numberString(), 10);
            }
            result -= digit;
        }

        return extracted50(result, negative, i, type);
    }

    private Number extracted50(long result, boolean negative, int i, char type){
        if (negative) {
            return extracted47(result, i, type);
        } else {
            result = -result;
            if (extracted48(result, type)) {
                if (extractedv49(type) ) {
                    return (short) result;
                }

                return (int) result;
            }
            return result;
        }
    }

    private boolean extractedv49(char type) {
        return type == 'S' ||type == 'B';
    }

    private boolean extracted48(long result, char type) {
        return result <= Integer.MAX_VALUE && type != 'L';
    }

    private Number extracted47(long result, int i, char type){
        if (i > np + 1) {
            if (result >= Integer.MIN_VALUE && type != 'L') {
                if (type == 'S') {
                    return (short) result;
                }

                if (type == 'B') {
                    return (byte) result;
                }

                return (int) result;
            }
            return result;
        } else { /* Only got "-" */
            throw new JSONException("illegal number format : " + numberString());
        }
    }

    public final void nextTokenWithColon(int expect) {
        nextTokenWithChar(':');
    }

    public float floatValue() {
        String strVal = numberString();
        float floatValue = Float.parseFloat(strVal);
        if (floatValue == 0 || floatValue == Float.POSITIVE_INFINITY) {
            char c0 = strVal.charAt(0);
            if (c0 > '0' && c0 <= '9') {
                throw new JSONException("float overflow : " + strVal);
            }
        }
        return floatValue;
    }

    public double doubleValue() {
        return Double.parseDouble(numberString());
    }

    public void config(Feature feature, boolean state) {
        features = Feature.config(features, feature, state);

        if ((features & Feature.INIT_STRING_FIELD_AS_EMPTY.mask) != 0) {
            stringDefaultValue = "";
        }
    }

    public final boolean isEnabled(Feature feature) {
        return isEnabled(feature.mask);
    }

    public final boolean isEnabled(int feature) {
        return (this.features & feature) != 0;
    }

    public final boolean isEnabled(int features, int feature) {
        return (this.features & feature) != 0 || (features & feature) != 0;
    }

    public abstract String numberString();

    public abstract boolean isEOF();

    public final char getCurrent() {
        return ch;
    }

    public abstract char charAt(int index);









    public abstract char next();

    protected void skipComment() {
        next();
        if (ch == '/') {
            for (;;) {
                next();
                extr(ch,'*','\0');
                if (ch == EOI) {
                    return;
                }
            }
        } else if (ch == '*') {
            next();

            while (ch != EOI) {
                extr(ch,'*','/');
                if (ch != '/') {
                    continue;
                }
                next();
            }
        } else {
            throw new JSONException("invalid comment");
        }
    }

    public void extr(char ch1, char ch2, char ch3 ){
        if (ch1 == ch2) {
            next();
            if (ch1 == ch3 && ch3 != '\0') {
                next();
            }
        }
    }

    public final String scanSymbol(final SymbolTable symbolTable) {
        skipWhitespace();

        if (ch == '"') {
            return scanSymbol(symbolTable, '"');
        }

        if (ch == '\'') {
            if (!isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
                throw new JSONException("syntax error");
            }

            return scanSymbol(symbolTable, '\'');
        }

        if (ch == '}') {
            next();
            token = JSONToken.RBRACE;
            return null;
        }

        if (ch == ',') {
            next();
            token = JSONToken.COMMA;
            return null;
        }

        if (ch == EOI) {
            token = JSONToken.EOF;
            return null;
        }

        if (!isEnabled(Feature.ALLOW_UN_QUOTED_FIELD_NAMES)) {
            throw new JSONException("syntax error");
        }

        return scanSymbolUnQuoted(symbolTable);
    }



    protected abstract void arrayCopy(int srcPos, char[] dest, int destPos, int length);

    public final String scanSymbol(final SymbolTable symbolTable, final char quote) {
        int hash = 0;

        np = bp;
        sp = 0;
        boolean hasSpecial1 = false;
        char chLocal;
        for (;;) {
            chLocal = next();

            if (chLocal == quote) {
                break;
            }

            extracted46(chLocal);

            if (chLocal == '\\') {
                hasSpecial1 = extracted45(hasSpecial1);

                chLocal = next();

                switch (chLocal) {
                    case '0':
                        hash = 31 * hash + chLocal;
                        putChar('\0');
                        break;
                    case '1':
                        hash = 31 * hash + chLocal;
                        putChar('\1');
                        break;
                    case '2':
                        hash = 31 * hash + chLocal;
                        putChar('\2');
                        break;
                    case '3':
                        hash = 31 * hash + chLocal;
                        putChar('\3');
                        break;
                    case '4':
                        hash = 31 * hash + chLocal;
                        putChar('\4');
                        break;
                    case '5':
                        hash = 31 * hash + chLocal;
                        putChar('\5');
                        break;
                    case '6':
                        hash = 31 * hash + chLocal;
                        putChar('\6');
                        break;
                    case '7':
                        hash = 31 * hash + chLocal;
                        putChar('\7');
                        break;
                    case 'b': // 8
                        hash = 31 * hash + '\b';
                        putChar('\b');
                        break;
                    case 't': // 9
                        hash = 31 * hash + '\t';
                        putChar('\t');
                        break;
                    case 'n': // 10
                        hash = 31 * hash + '\n';
                        putChar('\n');
                        break;
                    case 'v': // 11
                        hash = 31 * hash + '\u000B';
                        putChar('\u000B');
                        break;
                    case 'f': // 12
                    case 'F':
                        hash = 31 * hash + '\f';
                        putChar('\f');
                        break;
                    case 'r': // 13
                        hash = 31 * hash + '\r';
                        putChar('\r');
                        break;
                    case '"': // 34
                        hash = 31 * hash + '"';
                        putChar('"');
                        break;
                    case '\'': // 39
                        hash = 31 * hash + '\'';
                        putChar('\'');
                        break;
                    case '/': // 47
                        hash = 31 * hash + '/';
                        putChar('/');
                        break;
                    case '\\': // 92
                        hash = 31 * hash + '\\';
                        putChar('\\');
                        break;
                    case 'x':
                        char x1 = ch = next();
                        char x2 = ch = next();

                        int xVal = digits[x1] * 16 + digits[x2];
                        char xChar = (char) xVal;
                        hash = 31 * hash + xChar;
                        putChar(xChar);
                        break;
                    case 'u':
                        char c1 = chLocal;
                        char c2 = chLocal;
                        char c3 = chLocal;
                        char c4 = chLocal;
                        int val = Integer.parseInt(new String(new char[] { c1, c2, c3, c4 }), 16);
                        hash = 31 * hash + val;
                        putChar((char) val);
                        break;
                    default:
                        this.ch = chLocal;
                        throw new JSONException("unclosed.str.lit");
                }
            }

            hash = 31 * hash + chLocal;

            if (!hasSpecial) {
                sp++;
            }

            extracted8(chLocal);
        }

        token = LITERAL_STRING;

        String value;
        if (!hasSpecial) {

            int offset;
            if (np == -1) {
                offset = 0;
            } else {
                offset = np + 1;
            }
            value = addSymbol(offset, sp, hash, symbolTable);
        } else {
            value = symbolTable.addSymbol(sbuf, 0, sp, hash);
        }

        sp = 0;
        this.next();

        return value;
    }

    private void extracted46(char chLocal){
        if (chLocal == EOI) {
            throw new JSONException("unclosed.str");
        }
    }

    private boolean extracted45(boolean hasSpecial) {
        if (!hasSpecial) {
            hasSpecial = true;

            extracted43();



            arrayCopy(np + 1, sbuf, 0, sp);
        }
        return hasSpecial;
    }

    public final void resetStringPosition() {
        this.sp = 0;
    }

    public String info() {
        return "";
    }

    public final String scanSymbolUnQuoted(final SymbolTable symbolTable) {
        if (token == JSONToken.ERROR && pos == 0 && bp == 1) {
            bp = 0; // adjust
        }
        final boolean[] firstIdentifierFlags = IOUtils.firstIdentifierFlags;
        final char first = ch;

        final boolean firstFlag = ch >= firstIdentifierFlags.length || firstIdentifierFlags[first];
        if (!firstFlag) {
            throw new JSONException("illegal identifier : " + ch //
                    + info());
        }

        final boolean[] identifierFlags = IOUtils.identifierFlags;

        int hash = first;

        np = bp;
        sp = 1;
        char chLocal;
        for (;;) {
            chLocal = next();

            if (chLocal < identifierFlags.length && !identifierFlags[chLocal]) {

                break;

            }

            hash = 31 * hash + chLocal;

            sp++;
        }

        this.ch = charAt(bp);
        token = JSONToken.IDENTIFIER;

        final int NULL_HASH = 3392903;
        if (sp == 4 && hash == NULL_HASH && charAt(np) == 'n' && charAt(np + 1) == 'u' && charAt(np + 2) == 'l'
                && charAt(np + 3) == 'l') {
            return null;
        }



        if (symbolTable == null) {
            return subString(np, sp);
        }

        return this.addSymbol(np, sp, hash, symbolTable);

    }

    protected abstract void copyTo(int offset, int count, char[] dest);

    public final void scanString() {
        np = bp;
        hasSpecial = false;
        char chscanString;
        for (;;) {
            chscanString = next();

            if (chscanString == '\"') {
                break;
            }

            if (chscanString == EOI) {
                if (!isEOF()) {
                    putChar(EOI);
                }
                throw new JSONException("unclosed string : " + chscanString);
            }

            if (chscanString == '\\') {
                extracted44();

                chscanString = next();

                switch (chscanString) {
                    case '0':
                        putChar('\0');
                        break;
                    case '1':
                        putChar('\1');
                        break;
                    case '2':
                        putChar('\2');
                        break;
                    case '3':
                        putChar('\3');
                        break;
                    case '4':
                        putChar('\4');
                        break;
                    case '5':
                        putChar('\5');
                        break;
                    case '6':
                        putChar('\6');
                        break;
                    case '7':
                        putChar('\7');
                        break;
                    case 'b': // 8
                        putChar('\b');
                        break;
                    case 't': // 9
                        putChar('\t');
                        break;
                    case 'n': // 10
                        putChar('\n');
                        break;
                    case 'v': // 11
                        putChar('\u000B');
                        break;
                    case 'f': // 12
                    case 'F':
                        putChar('\f');
                        break;
                    case 'r': // 13
                        putChar('\r');
                        break;
                    case '"': // 34
                        putChar('"');
                        break;
                    case '\'': // 39
                        putChar('\'');
                        break;
                    case '/': // 47
                        putChar('/');
                        break;
                    case '\\': // 92
                        putChar('\\');
                        break;
                    case 'x':
                        char x1 = next();
                        char x2 = next();

                        boolean hex1 = extracted7(x1);
                        boolean hex2 = extracted7(x2);
                        extracted11(x1, x2, hex1, hex2);

                        char xChar = (char) (digits[x1] * 16 + digits[x2]);
                        putChar(xChar);
                        break;
                    case 'u':
                        char u1 = next();
                        char u2 = next();
                        char u3 = next();
                        char u4 = next();
                        int val = Integer.parseInt(new String(new char[] { u1, u2, u3, u4 }), 16);
                        putChar((char) val);
                        break;
                    default:
                        throw new JSONException("unclosed string : " + ch);
                }
            }

            if (!hasSpecial) {
                sp++;
            }

            extracted8(ch);
        }

        token = JSONToken.LITERAL_STRING;
        this.ch = next();
    }

    private void extracted44() {
        if (!hasSpecial) {
            hasSpecial = true;

            extracted43();

            copyTo(np + 1, sp, sbuf);


        }
    }

    private void extracted43() {
        if (sp >= sbuf.length) {
            int newCapcity = sbuf.length * 2;
            newCapcity = extracted42(newCapcity);
            char[] newsbuf = new char[newCapcity];
            System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
            sbuf = newsbuf;
        }
    }

    private int extracted42(int newCapcity) {
        if (sp > newCapcity) {
            newCapcity = sp;
        }
        return newCapcity;
    }

    public Calendar getCalendar() {
        return this.calendar;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public final int intValue() {
        if (np == -1) {
            np = 0;
        }

        int result = 0;
        boolean negative = false;
        int i = np;
        int max = np + sp;
        int limit;
        int digit;

        if (charAt(np) == '-') {
            negative = true;
            limit = Integer.MIN_VALUE;
            i++;
        } else {
            limit = -Integer.MAX_VALUE;
        }
        long multmin = INT_MULTMIN_RADIX_TEN;
        if (i < max) {
            digit = charAt(i++) - '0';
            result = -digit;
        }
        while (i < max) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            char chLocal = charAt(i++);

            if (chLocal == 'L' || chLocal == 'S' || chLocal == 'B') {
                break;
            }

            digit = chLocal - '0';

            if (result < multmin) {
                throw new NumberFormatException(numberString());
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException(numberString());
            }
            result -= digit;
        }

        if (!negative) {
            return -result;
        }

        if (i > np + 1) {
            return result;
        } else { /* Only got "-" */
            throw new NumberFormatException(numberString());
        }

    }

    public abstract byte[] bytesValue();

    public void close() {
        if (sbuf.length <= 1024 * 8) {
            SBUF_LOCAL.set(sbuf);
        }
        this.sbuf = null;
    }

    public final boolean isRef() {
        if (sp != 4) {
            return false;
        }

        return charAt(np + 1) == '$' //
                && charAt(np + 2) == 'r' //
                && charAt(np + 3) == 'e' //
                && charAt(np + 4) == 'f';
    }

    public String scanTypeName(SymbolTable symbolTable) {
        return null;
    }

    protected static final char[] typeFieldName = ("\"" + JSON.DEFAULT_TYPE_KEY + "\":\"").toCharArray();

    public final int scanType(String type) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(typeFieldName)) {
            return NOT_MATCH_NAME;
        }

        int bpLocal = this.bp + typeFieldName.length;

        final int typeLength = type.length();
        for (int i = 0; i < typeLength; ++i) {
            if (type.charAt(i) != charAt(bpLocal + i)) {
                return NOT_MATCH;
            }
        }
        bpLocal += typeLength;
        if (charAt(bpLocal) != '"') {
            return NOT_MATCH;
        }

        this.ch = charAt(++bpLocal);

        if (ch == ',') {
            this.ch = charAt(++bpLocal);
            this.bp = bpLocal;
            token = JSONToken.COMMA;
            return VALUE;
        } else if (ch == '}') {
            ch = charAt(++bpLocal);
            if (ch == ',') {
                token = JSONToken.COMMA;
                this.ch = charAt(++bpLocal);
            } else if (ch == ']') {
                token = JSONToken.RBRACKET;
                this.ch = charAt(++bpLocal);
            } else if (ch == '}') {
                token = JSONToken.RBRACE;
                this.ch = charAt(++bpLocal);
            } else if (ch == EOI) {
                token = JSONToken.EOF;
            } else {
                return NOT_MATCH;
            }
            matchStat = END;
        }

        this.bp = bpLocal;
        return matchStat;
    }

    public final boolean matchField(char[] fieldName) {
        for (;;) {
            if (!charArrayCompare(fieldName)) {
                if (isWhitespace(ch)) {
                    next();
                }
                return false;
            } else {
                break;
            }
        }

        bp = bp + fieldName.length;
        ch = charAt(bp);

        if (ch == '{') {
            next();
            token = JSONToken.LBRACE;
        } else if (ch == '[') {
            next();
            token = JSONToken.LBRACKET;
        } else if (ch == 'S' && charAt(bp + 1) == 'e' && charAt(bp + 2) == 't' && charAt(bp + 3) == '[') {
            bp += 3;
            ch = charAt(bp);
            token = JSONToken.SET;
        } else {
            nextToken();
        }

        return true;
    }

    public int matchField(long fieldNameHash) {
        throw new UnsupportedOperationException();
    }

    public boolean seekArrayToItem(int index) {
        throw new UnsupportedOperationException();
    }

    public int seekObjectToField(long fieldNameHash, boolean deepScan) {
        throw new UnsupportedOperationException();
    }

    public int seekObjectToField(long[] fieldNameHash) {
        throw new UnsupportedOperationException();
    }

    public int seekObjectToFieldDeepScan(long fieldNameHash) {
        throw new UnsupportedOperationException();
    }

    public void skipObject() {
        throw new UnsupportedOperationException();
    }

    public void skipObject(boolean valid) {
        throw new UnsupportedOperationException();
    }

    public void skipArray() {
        throw new UnsupportedOperationException();
    }

    public abstract int indexOf(char ch, int startIndex);

    public abstract String addSymbol(int offset, int len, int hash, final SymbolTable symbolTable);

    public String scanFieldString(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return stringDefaultValue();
        }



        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '"') {
            matchStat = NOT_MATCH;

            return stringDefaultValue();
        }

        final String strVal;
        int startIndex = bp + fieldName.length + 1;
        int endIndex = indexOf('"', startIndex);
        extracted13(endIndex);

        int startIndex2 = bp + fieldName.length + 1; // must re compute
        String stringVal = subString(startIndex2, endIndex - startIndex2);
        if (stringVal.indexOf('\\') != -1) {
            endIndex = extracted15(endIndex);

            int charsLen = endIndex - (bp + fieldName.length + 1);
            char[] chars = subChars( bp + fieldName.length + 1, charsLen);

            stringVal = readString(chars, charsLen);
        }

        offset += (endIndex - (bp + fieldName.length + 1) + 1);
        chLocal = charAt(bp + (offset++));
        strVal = stringVal;

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return strVal;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                ch = EOI;
            } else {
                matchStat = NOT_MATCH;
                return stringDefaultValue();
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return stringDefaultValue();
        }

        return strVal;
    }

    public String scanString(char expectNextChar) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        if (chLocal == 'n') {
            return extracted27(expectNextChar, offset, chLocal);
        }

        final String strVal;
        for (;;) {
            if (chLocal == '"') {
                int startIndex = bp + offset;
                int endIndex = indexOf('"', startIndex);
                extracted13(endIndex);

                String stringVal = subString(bp + offset, endIndex - startIndex);
                if (stringVal.indexOf('\\') != -1) {
                    endIndex = extracted15(endIndex);

                    int charsLen = endIndex - startIndex;
                    char[] chars = subChars(bp + 1, charsLen);

                    stringVal = readString(chars, charsLen);
                }

                offset += (endIndex - startIndex + 1);
                chLocal = charAt(bp + (offset++));
                strVal = stringVal;
                break;
            } else if (isWhitespace(chLocal)) {
                chLocal = charAt(bp + (offset++));

            } else {
                matchStat = NOT_MATCH;

                return stringDefaultValue();
            }
        }

        for (;;) {
            if (chLocal == expectNextChar) {
                bp += offset;
                this.ch = charAt(bp);
                matchStat = VALUE;
                token = JSONToken.COMMA;
                return strVal;
            } else if (isWhitespace(chLocal)) {
                chLocal = charAt(bp + (offset++));

            } else {
                extracted26(offset, chLocal);
                return strVal;
            }
        }
    }

    private String extracted27(char expectNextChar, int offset, char chLocal) {
        if (chLocal == expectNextChar) {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return null;
        } else {
            matchStat = NOT_MATCH;
            return "null";
        }
    }

    private void extracted26(int offset, char chLocal) {
        if (chLocal == ']') {
            bp += offset;
            this.ch = charAt(bp);
            matchStat = NOT_MATCH;
        }
    }

    public long scanFieldSymbol(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '"') {
            matchStat = NOT_MATCH;
            return 0;
        }

        long hash = FNV1A_64_MAGIC_HASHCODE;
        for (;;) {
            chLocal = charAt(bp + (offset++));
            if (chLocal == '\"') {
                chLocal = charAt(bp + (offset++));
                break;
            }

            hash ^= chLocal;
            hash *= FNV1A_64_MAGIC_PRIME;

            if (chLocal == '\\') {
                matchStat = NOT_MATCH;
                return 0;
            }
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return hash;
        }


        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));

            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;

                default:
                    break;
            }

            if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                ch = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return hash;
    }

    public long scanEnumSymbol(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '"') {
            matchStat = NOT_MATCH;
            return 0;
        }

        long hash = FNV1A_64_MAGIC_HASHCODE;
        for (;;) {
            chLocal = charAt(bp + (offset++));
            if (chLocal == '\"') {
                chLocal = charAt(bp + (offset++));
                break;
            }

            hash = extracted(chLocal, hash);
            hash *= FNV1A_64_MAGIC_PRIME;

            if (chLocal == '\\') {
                matchStat = NOT_MATCH;
                return 0;
            }
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return hash;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                default:
                    break;
            }
            if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                ch = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return hash;
    }

    private long extracted(char chLocal, long hash) {
        hash ^= ((chLocal >= 'A' && chLocal <= 'Z') ? (chLocal + 32) : chLocal);
        return hash;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Enum<?> scanEnum(Class<?> enumClass, final SymbolTable symbolTable, char serperator) {
        String name = scanSymbolWithSeperator(symbolTable, serperator);
        if (name == null) {
            return null;
        }
        return Enum.valueOf((Class<? extends Enum>) enumClass, name);
    }

    public String scanSymbolWithSeperator(final SymbolTable symbolTable, char serperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        if (chLocal == 'n') {
            if (!extracted2(offset)) {
                matchStat = NOT_MATCH;
                return null;
            }

            offset += 3;
            chLocal = charAt(bp + (offset++));




            if (chLocal != serperator) {
                matchStat = NOT_MATCH;
                return null;
            }

            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return null;
        }

        if (chLocal != '"') {
            matchStat = NOT_MATCH;
            return null;
        }

        String strVal;

        int hash = 0;
        for (;;) {
            chLocal = charAt(bp + (offset++));
            if (chLocal == '\"') {


                int start = bp + 0 + 1;
                int len = bp + offset - start - 1;
                strVal = addSymbol(start, len, hash, symbolTable);
                chLocal = charAt(bp + (offset++));
                break;
            }

            hash = 31 * hash + chLocal;
            extr(chLocal, '\\');

        }

        for (;;) {
            if (chLocal == serperator) {
                bp += offset;
                this.ch = this.charAt(bp);
                matchStat = VALUE;
                return strVal;
            } else  if (isWhitespace(chLocal)){

                chLocal = charAt(bp + (offset++));

            }else{
                matchStat = NOT_MATCH;
                return strVal;
            }
        }
    }

    private String extr(char ch1,char ch2){
        if (ch1 == ch2) {
            matchStat = NOT_MATCH;

        }
        return null;
    }
    private boolean extracted2(int offset) {
        return charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l';
    }

    public Collection<String> newCollectionByType(Class<?> type){
        if (type.isAssignableFrom(HashSet.class)) {
            return new HashSet<>();
        } else if (type.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<>();
        } else if (type.isAssignableFrom(LinkedList.class)) {
            return new LinkedList<>();
        } else {
            try {
                //Skip
            } catch (Exception e) {
                throw new JSONException(e.getMessage(), e);
            }
        }
        return new LinkedList<>();
    }

    @SuppressWarnings("unchecked")
    public Collection<String> scanFieldStringArray(char[] fieldName, Class<?> type) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return Collections.emptyList();
        }

        Collection<String> list = newCollectionByType(type);















        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));



        for (;;) {

            if (chLocal == '"') {
                int startIndex = bp + offset;
                int endIndex = indexOf('"', startIndex);
                extracted13(endIndex);

                int startIndex2 = bp + offset; // must re compute
                String stringVal = subString(startIndex2, endIndex - startIndex2);
                if (stringVal.indexOf('\\') != -1) {
                    endIndex = extracted15(endIndex);

                    int charsLen = endIndex - (bp + offset);
                    char[] chars = subChars(bp + offset, charsLen);

                    stringVal = readString(chars, charsLen);
                }

                list.add(stringVal);
            } else if (extracted18(offset, chLocal)) {
                list.add(null);
            } else if (extracted41(list, chLocal)) {
                break;
            } else {
                throw new JSONException("illega str");
            }


            matchStat = NOT_MATCH;
            return Collections.emptyList();
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return list;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    break;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return Collections.emptyList();
        }

        return list;
    }

    private boolean extracted41(Collection<String> list, char chLocal) {
        return chLocal == ']' && list.isEmpty();
    }

    public void scanStringArray(Collection<String> list, char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        if (extracted18(offset, chLocal)
                && charAt(bp + offset + 3) == seperator
        ) {
            bp += 5;
            ch = charAt(bp);
            matchStat = VALUE_NULL;
            return;
        }

        if (chLocal != '[') {
            matchStat = NOT_MATCH;
            return;
        }

        chLocal = charAt(bp + (offset++));

        for (;;) {
            if (extracted18(offset, chLocal)) {
                list.add(null);
            } else if (extracted41(list, chLocal)) {
                chLocal = charAt(bp + (offset++));
                break;
            } else if (chLocal != '"') {
                matchStat = NOT_MATCH;
                return;
            } else {
                int startIndex = bp + offset;
                int endIndex = indexOf('"', startIndex);
                extracted13(endIndex);

                String stringVal = subString(bp + offset, endIndex - startIndex);
                if (stringVal.indexOf('\\') != -1) {
                    endIndex = extracted15(endIndex);

                    int charsLen = endIndex - startIndex;
                    char[] chars = subChars(bp + offset, charsLen);

                    stringVal = readString(chars, charsLen);
                }
                list.add(stringVal);
            }



            matchStat = NOT_MATCH;
            return;
        }

        if (chLocal == seperator) {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;

        } else {
            matchStat = NOT_MATCH;

        }
    }

    public int scanFieldInt(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        int value;
        if (extractedv17(chLocal)) {
            value = chLocal - '0';

            if (extracted40(fieldName, offset, negative, value)) {

                matchStat = NOT_MATCH;
                return 0;

            }
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return extracted33(negative, value);
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    break;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return extracted33(negative, value);
    }

    private boolean extracted40(char[] fieldName, int offset, final boolean negative, int value) {
        return (extracted38(fieldName, offset, value)) && (extracted39(offset, negative, value));
    }

    private boolean extracted39(int offset, final boolean negative, int value) {
        return value != Integer.MIN_VALUE //
                || offset != 17 //
                || !negative;
    }

    private boolean extracted38(char[] fieldName, int offset, int value) {
        return value < 0 //
                || offset > 11 + 3 + fieldName.length;
    }

    public final int[] scanFieldIntArray(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return new int[0];
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '[') {
            matchStat = NOT_MATCH_NAME;
            return new int[0];
        }
        chLocal = charAt(bp + (offset++));

        int[] array = new int[16];
        int arrayIndex = 0;

        if (chLocal == ']') {
            chLocal = charAt(bp + (offset++));
        } else {
            for (;;) {
                boolean nagative = false;

                int value = chLocal - '0';
                if (extractedv17(chLocal)) {

                    array = extracted35(array, arrayIndex);
                    arrayIndex = extracted37(array, arrayIndex, nagative, value);


                } else {
                    matchStat = NOT_MATCH;
                    return new int[0];
                }
            }
        }


        array = extracted36(array, arrayIndex);

        if (chLocal == ',') {
            bp += (offset - 1);
            this.next();
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return array;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    break;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return new int[0];
        }

        return array;
    }

    private int extracted37(int[] array, int arrayIndex, boolean nagative, int value) {
        array[arrayIndex++] = nagative ? -value : value;
        return arrayIndex;
    }

    private int[] extracted36(int[] array, int arrayIndex) {
        if (arrayIndex != array.length) {
            int[] tmp = new int[arrayIndex];
            System.arraycopy(array, 0, tmp, 0, arrayIndex);
            array = tmp;
        }
        return array;
    }

    private int[] extracted35(int[] array, int arrayIndex) {
        if (arrayIndex >= array.length) {
            int[] tmp = new int[array.length * 3 / 2];
            System.arraycopy(array, 0, tmp, 0, arrayIndex);
            array = tmp;
        }
        return array;
    }

    public boolean scanBoolean(char expectNext) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        boolean value = false;

        switch (chLocal) {
            case 't':
                if (charAt(bp + offset) == 'r' //
                        && charAt(bp + offset + 1) == 'u' //
                        && charAt(bp + offset + 2) == 'e') {
                    offset += 3;
                    chLocal = charAt(bp + (offset++));
                    value = true;
                } else {
                    matchStat = NOT_MATCH;
                    return false;
                }
                break;

            case 'f':
                if (charAt(bp + offset) == 'a' //
                        && charAt(bp + offset + 1) == 'l' //
                        && charAt(bp + offset + 2) == 's' //
                        && charAt(bp + offset + 3) == 'e') {
                    offset += 4;
                    chLocal = charAt(bp + (offset++));
                    value = false;
                } else {
                    matchStat = NOT_MATCH;
                    return false;
                }
                break;

            case '1':
                chLocal = charAt(bp + (offset++));
                value = true;
                break;

            case '0':
                chLocal = charAt(bp + (offset++));
                break;

            default:
                break;
        }


        for (;;) {
            if (chLocal == expectNext) {
                bp += offset;
                this.ch = this.charAt(bp);
                matchStat = VALUE;
                return value;
            } else if (isWhitespace(chLocal)){
                chLocal = charAt(bp + (offset++));
            }else{
                matchStat = NOT_MATCH;
                return value;
            }
        }
    }


    public int scanInt(char expectNext) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        final boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        int value;
        if (extractedv17(chLocal)) {
            value = chLocal - '0';

        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (extracted19(chLocal, quote)) {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.ch = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == ']') {
                    bp += offset;
                    this.ch = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACKET;
                    return value;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        for (;;) {
            return extracted34(expectNext, offset, chLocal, negative, value);
        }
    }

    private int extracted34(char expectNext, int offset, char chLocal, final boolean negative, int value) {
        if (chLocal == expectNext) {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return extracted33(negative, value);
        } else {

            matchStat = NOT_MATCH;
            return extracted33(negative, value);
        }
    }

    private int extracted33(final boolean negative, int value) {
        return negative ? -value : value;
    }

    public boolean scanFieldBoolean(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return false;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        boolean value;
        if (chLocal == 't') {
            if ((charAt(bp + (offset++)) != 'r') || (charAt(bp + (offset++)) != 'u') || (charAt(bp + (offset++)) != 'e')) {
                matchStat = NOT_MATCH;
                return false;
            }


            value = true;
        } else if (chLocal == 'f') {
            if ((charAt(bp + (offset++)) != 'a')|| (charAt(bp + (offset++)) != 'l')|| (charAt(bp + (offset++)) != 's') || (charAt(bp + (offset++)) != 'e')) {
                matchStat = NOT_MATCH;
                return false;
            }


            value = false;
        } else {
            matchStat = NOT_MATCH;
            return false;
        }

        chLocal = charAt(bp + offset++);
        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;

            return value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    break;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return false;
        }

        return value;
    }

    public long scanFieldLong(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        boolean negative = false;
        if (chLocal == '-') {
            chLocal = charAt(bp + (offset++));
            negative = true;
        }

        long value;
        if (extractedv17(chLocal)) {
            value = (long) chLocal - '0';

            boolean valid = extracted32(fieldName, offset, negative, value);
            if (!valid) {
                matchStat = NOT_MATCH;
                return 0;
            }
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return extracted31(negative, value);
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                ch = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return extracted31(negative, value);
    }

    private boolean extracted32(char[] fieldName, int offset, boolean negative, long value) {
        return offset - fieldName.length < 21
                && (value >= 0 || (value == -9223372036854775808L && negative));
    }

    public long scanLong(char expectNextChar) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        final boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        long value = chLocal;
        if (extractedv17(chLocal)) {
            value = (long) chLocal - '0';

            boolean valid = value >= 0 || (value == -9223372036854775808L && negative);
            if (!valid) {
                String val = subString(bp, offset - 1);
                throw new NumberFormatException(val);
            }
        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (extracted19(chLocal, quote)) {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                switch (chLocal) {
                    case ',':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.COMMA;
                        return value;
                    case '}':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.RBRACE;
                        return value;
                    case ' ':
                    case '\b':
                    case '\t':
                        chLocal = charAt(bp + offset++);
                        continue;
                    default:
                        break;
                }
            }
        }



        return value;
    }

    private long extracted31(final boolean negative, long value) {
        return negative ? -value : value;
    }

    public final float scanFieldFloat(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        float value;
        if (extractedv17(chLocal)) {
            long intVal = (long) chLocal - '0';
            long power = 1;
            boolean exp = chLocal == 'e' || chLocal == 'E';

            int start;
            int count;
            start = bp + fieldName.length + 1;
            count = bp + offset - start - 2;


            value = extracted30(negative, intVal, power, start, count, exp);
        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            for (;;) {
                switch (chLocal) {
                    case ',':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.COMMA;
                        return value;
                    case '}':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.RBRACE;
                        return value;
                    case ' ':
                    case '\b':
                    case '\t':

                    default:
                        break;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == '}') {
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return value;
    }

    public final float scanFloat(char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        float value;
        if (extractedv17(chLocal)) {
            long intVal = (long) chLocal - '0';

            long power = 1;
            int start;
            int count;
            start = bp + 1;
            count = bp + offset - start - 2;

            boolean exp = chLocal == 'e' || chLocal == 'E';
            value = extracted30(negative, intVal, power, start, count, exp);
        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (extracted19(chLocal, quote)) {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                switch (chLocal) {
                    case ',':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.COMMA;
                        return value;
                    case '}':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.RBRACE;
                        return value;
                    case ' ':
                    case '\b':
                    case '\t':
                        chLocal = charAt(bp + offset++);
                        break;
                    default:
                        break;
                }
            }
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == seperator) {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        } else {
            matchStat = NOT_MATCH;
            return value;
        }
    }

    private float extracted30(boolean negative, long intVal, long power, int start, int count, boolean exp) {
        float value;
        if ((!exp) && count < 17) {
            value = (float) (((double) intVal) / power);
            if (negative) {
                value = -value;
            }
        } else {
            String text = this.subString(start, count);
            value = Float.parseFloat(text);
        }
        return value;
    }

    public double scanDouble(char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        double value;
        if (extractedv17(chLocal)) {
            long intVal = (long) chLocal - '0';
            for (; ; ) {
                chLocal = charAt(bp + (offset++));
                if (extractedv17(chLocal)) {
                    intVal = intVal * 10 + (chLocal - '0');

                } else {
                    break;
                }
            }

            long power = 1;


            boolean exp = chLocal == 'e' || chLocal == 'E';


            int start;
            int count;
            start = bp + 1;
            count = bp + offset - start - 2;


            value = extracted29(negative, intVal, power, exp, start, count);
        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;


            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == seperator) {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        } else {
            matchStat = NOT_MATCH;
            return value;
        }
    }

    private double extracted29(boolean negative, long intVal, long power, boolean exp, int start, int count) {
        double value;
        if (!exp && count < 17) {
            value = ((double) intVal) / power;
            if (negative) {
                value = -value;
            }
        } else {
            String text = this.subString(start, count);
            value = Double.parseDouble(text);
        }
        return value;
    }

    public BigDecimal scanDecimal(char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        BigDecimal value;
        if (extractedv17(chLocal)) {



            int start;
            int count;
            start = bp + 1;
            count = bp + offset - start - 2;

            extracted28(count);
            char[] chars = this.subChars(start, count);
            value = new BigDecimal(chars, 0, chars.length, MathContext.UNLIMITED);
        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;
            value = null;
            offset += 3;
            chLocal = charAt(bp + offset++);



            for (;;) {
                switch (chLocal) {
                    case ',':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.COMMA;
                        return value;
                    case '}':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.RBRACE;
                        return value;
                    case ' ':
                    case '\b':
                    case '\t':
                        chLocal = charAt(bp + offset++);
                        continue;
                    default:
                        break;
                }
            }
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == ']') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return null;
            }

            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return value;
    }

    private void extracted28(int count) {
        if (count > 65535) {
            throw new JSONException("decimal overflow");
        }
    }

    public final float[] scanFieldFloatArray(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return new float[0];
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));
        if (chLocal != '[') {
            matchStat = NOT_MATCH_NAME;
            return new float[0];
        }
        chLocal = charAt(bp + (offset++));

        float[] array = new float[16];
        int arrayIndex = 0;

        for (;;) {
            int start = bp + offset - 1;

            boolean negative = chLocal == '-';
            if (negative) {
                chLocal = charAt(bp + (offset++));
            }

            if (extractedv17(chLocal)) {
                int intVal = chLocal - '0';


                int power = 1;

                boolean exp = chLocal == 'e' || chLocal == 'E';



                int count = bp + offset - start - 1;

                float value;
                value = extracted24(start, negative, intVal, power, exp, count);

                array = extracted23(array, arrayIndex);
                array[arrayIndex++] = value;


            } else {
                matchStat = NOT_MATCH;
                return new float[0];
            }
            break;
        }


        array = extracted25(array, arrayIndex);

        if (chLocal == ',') {
            bp += (offset - 1);
            this.next();
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return array;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return new float[0];
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return new float[0];
        }

        return array;
    }

    private float[] extracted25(float[] array, int arrayIndex) {
        if (arrayIndex != array.length) {
            float[] tmp = new float[arrayIndex];
            System.arraycopy(array, 0, tmp, 0, arrayIndex);
            array = tmp;
        }
        return array;
    }

    private float extracted24(int start, boolean negative, int intVal, int power, boolean exp, int count) {
        float value;
        if (!exp && count < 10) {
            value = ((float) intVal) / power;
            if (negative) {
                value = -value;
            }
        } else {
            String text = this.subString(start, count);
            value = Float.parseFloat(text);
        }
        return value;
    }

    private float[] extracted23(float[] array, int arrayIndex) {
        if (arrayIndex >= array.length) {
            float[] tmp = new float[array.length * 3 / 2];
            System.arraycopy(array, 0, tmp, 0, arrayIndex);
            array = tmp;
        }
        return array;
    }

    public final float[][] scanFieldFloatArray2(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return new float[0][0];
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '[') {
            matchStat = NOT_MATCH_NAME;
            return new float[0][0];
        }
        chLocal = charAt(bp + (offset++));

        float[][] arrayarray = new float[16][];
        int arrayarrayIndex = 0;

        for (;;) {
            if (chLocal == '[') {
                chLocal = charAt(bp + (offset++));

                float[] array = new float[16];
                int arrayIndex = 0;

                for (; ; ) {
                    int start = bp + offset - 1;
                    boolean negative = chLocal == '-';
                    if (negative) {
                        chLocal = charAt(bp + (offset++));
                    }

                    if (extractedv17(chLocal)) {
                        int intVal = chLocal - '0';
                        for (; ; ) {
                            chLocal = charAt(bp + (offset++));

                            if (extractedv17(chLocal)) {
                                intVal = intVal * 10 + (chLocal - '0');

                            } else {
                                break;
                            }
                        }

                        int power = 1;
                        if (chLocal == '.') {
                            chLocal = charAt(bp + (offset++));

                            if (extractedv17(chLocal)) {
                                intVal = intVal * 10 + (chLocal - '0');
                                power = 10;
                            } else {
                                matchStat = NOT_MATCH;
                                return new float[0][0];
                            }
                        }

                        boolean exp = chLocal == 'e' || chLocal == 'E';
                        if (exp) {
                            chLocal = charAt(bp + (offset++));
                            if (chLocal == '+' || chLocal == '-') {
                                chLocal = charAt(bp + (offset++));
                            }
                        }

                        int count = bp + offset - start - 1;
                        float value;
                        value = extracted24(start, negative, intVal, power, exp, count);

                        array = extracted23(array, arrayIndex);
                        array[arrayIndex++] = value;

                        if (chLocal == ',') {
                            chLocal = charAt(bp + (offset++));
                        } else if (chLocal == ']') {
                            chLocal = charAt(bp + (offset++));
                            break;
                        }
                    } else {
                        matchStat = NOT_MATCH;
                        return new float[0][0];
                    }
                }

                array = extracted25(array, arrayIndex);

                if (arrayarrayIndex >= arrayarray.length) {
                    float[][] tmp = new float[arrayarray.length * 3 / 2][];
                    System.arraycopy(array, 0, tmp, 0, arrayIndex);
                    arrayarray = tmp;
                }
                arrayarray[arrayarrayIndex++] = array;

                if (chLocal == ',' || chLocal == ']') {
                    chLocal = charAt(bp + (offset++));
                }
            } else {
                break;
            }
        }

        // compact
        if (arrayarrayIndex != arrayarray.length) {
            float[][] tmp = new float[arrayarrayIndex][];
            System.arraycopy(arrayarray, 0, tmp, 0, arrayarrayIndex);
            arrayarray = tmp;
        }

        if (chLocal == ',') {
            bp += (offset - 1);
            this.next();
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return arrayarray;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == EOI) {
                bp += (offset - 1);
                token = JSONToken.EOF;
                ch = EOI;
            } else {
                matchStat = NOT_MATCH;
                return new float[0][0];
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return new float[0][0];
        }

        return arrayarray;
    }

    public final double scanFieldDouble(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;


            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    break;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return 1;
    }

    public BigDecimal scanFieldDecimal(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        BigDecimal value;
        if (extractedv17(chLocal)) {

            int start;
            int count;
            start = bp + fieldName.length + 1;
            count = bp + offset - start - 2;


            extracted22(count);

            char[] chars = this.subChars(start, count);
            value = new BigDecimal(chars, 0, chars.length, MathContext.UNLIMITED);
        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;




            matchStat = NOT_MATCH;
            return null;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return value;
    }

    private void extracted22(int count){
        if (count > 65535) {
            throw new JSONException("scan decimal overflow");
        }
    }

    public BigInteger scanFieldBigInteger(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        boolean negative = chLocal == '-';


        BigInteger value;
        if (extractedv17(chLocal)) {
            long intVal = (long) chLocal - '0';
            boolean overflow = false;


            int start;
            int count;
            start = bp + fieldName.length + 1;
            count = bp + offset - start;

            value = extracted21(negative, intVal, overflow, start, count);
        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;
            value = null;
            offset += 3;
            chLocal = charAt(bp + offset++);



            for (;;) {
                switch (chLocal) {
                    case ',':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.COMMA;
                        return value;
                    case '}':
                        bp += offset;
                        this.ch = charAt(bp);
                        matchStat = VALUE_NULL;
                        token = JSONToken.RBRACE;
                        return value;
                    case ' ':
                    case '\b':
                    case '\t':
                        chLocal = charAt(bp + offset++);
                        break;
                    default:
                        break;
                }
            }
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == '}') {

            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return value;
    }


    private BigInteger extracted21(boolean negative, long intVal, boolean overflow, int start, int count) {
        BigInteger value;
        if (extracted20(negative, overflow, count)) {
            value = BigInteger.valueOf(negative ? -intVal : intVal);
        } else {



            extracted17(count);

            String strVal = this.subString(start, count);
            value = new BigInteger(strVal, 10);
        }
        return value;
    }

    private boolean extracted20(boolean negative, boolean overflow, int count) {
        return !overflow && (count < 20 || (negative && count < 21));
    }

    private boolean extracted19(char chLocal, final boolean quote) {
        return quote && chLocal == '"';
    }

    private void extracted17(int count){
        if (count > 65535) {
            throw new JSONException("scanInteger overflow");
        }
    }

    public java.util.Date scanFieldDate(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }



        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final java.util.Date dateVal;
        if (chLocal == '"'){
            int startIndex = bp + fieldName.length + 1;
            int endIndex = indexOf('"', startIndex);
            extracted13(endIndex);

            int startIndex2 = bp + fieldName.length + 1; // must re compute
            String stringVal = subString(startIndex2, endIndex - startIndex2);
            if (stringVal.indexOf('\\') != -1) {
                endIndex = extracted15(endIndex);

                int charsLen = endIndex - (bp + fieldName.length + 1);
                char[] chars = subChars( bp + fieldName.length + 1, charsLen);

                stringVal = readString(chars, charsLen);
            }

            offset += (endIndex - (bp + fieldName.length + 1) + 1);
            chLocal = charAt(bp + (offset++));

            JSONScanner dateLexer = new JSONScanner(stringVal);
            try {
                if (dateLexer.scanISO8601DateIfMatch(false)) {
                    Calendar calendar2 = dateLexer.getCalendar();
                    dateVal = calendar2.getTime();
                } else {
                    matchStat = NOT_MATCH;
                    return null;
                }
            } finally {
                dateLexer.close();
            }
        } else if (extracted16(chLocal)) {
            long millis = 0;



            dateVal = new java.util.Date(millis);
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return dateVal;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return dateVal;
    }

    public java.util.Date scanDate() {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        final java.util.Date dateVal;
        if (chLocal == '"'){
            int startIndex = bp + 1;
            int endIndex = indexOf('"', startIndex);
            extracted13(endIndex);

            int startIndex2 = bp + 1; // must re compute
            String stringVal = subString(startIndex2, endIndex - startIndex2);
            if (stringVal.indexOf('\\') != -1) {
                endIndex = extracted15(endIndex);

                int charsLen = endIndex - (bp + 1);
                char[] chars = subChars( bp + 1, charsLen);

                stringVal = readString(chars, charsLen);
            }

            offset += (endIndex - (bp + 1) + 1);
            chLocal = charAt(bp + (offset++));

            JSONScanner dateLexer = new JSONScanner(stringVal);
            try {
                if (dateLexer.scanISO8601DateIfMatch(false)) {
                    Calendar calendar1 = dateLexer.getCalendar();
                    dateVal = calendar1.getTime();
                } else {
                    matchStat = NOT_MATCH;
                    return null;
                }
            } finally {
                dateLexer.close();
            }
        } else if (extracted16(chLocal)) {
            long millis = 0;



            dateVal = new java.util.Date(millis);
        } else if (extracted18(offset, chLocal)) {
            matchStat = VALUE_NULL;
            dateVal = null;
            offset += 3;
            chLocal = charAt(bp + offset++);
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return dateVal;
        }

        if (chLocal == ']') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return dateVal;
    }

    private boolean extracted18(int offset, char chLocal) {
        return chLocal == 'n' &&
                charAt(bp + offset) == 'u' &&
                charAt(bp + offset + 1) == 'l' &&
                charAt(bp + offset + 2) == 'l';
    }

    private boolean extractedv17(char chLocal) {
        return chLocal >= '0' && chLocal <= '9';
    }

    private boolean extracted16(char chLocal) {
        return chLocal == '-' || extractedv17(chLocal);
    }

    private int extracted15(int endIndex) {
        for (;;) {
            int slashCount = 0;
            slashCount = extracted14(endIndex, slashCount);
            if (slashCount % 2 == 0) {
                break;
            }
            endIndex = indexOf('"', endIndex + 1);
        }
        return endIndex;
    }

    private int extracted14(int endIndex, int slashCount) {
        for (int i = endIndex - 1; i >= 0; --i) {
            if (charAt(i) == '\\') {
                slashCount++;
            } else {
                break;
            }
        }
        return slashCount;
    }

    private void extracted13(int endIndex){
        if (endIndex == -1) {
            throw new JSONException(UNCLOSED_STR);
        }
    }

    public java.util.UUID scanFieldUUID(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }



        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final java.util.UUID uuid;
        if (chLocal == '"') {
            int startIndex = bp + fieldName.length + 1;
            int endIndex = indexOf('"', startIndex);
            extracted13(endIndex);

            int startIndex2 = bp + fieldName.length + 1; // must re compute
            int len = endIndex - startIndex2;
            if (len == 36) {
                long mostSigBits = 0;
                long leastSigBits = 0;
                mostSigBits = extracted57(startIndex2, mostSigBits);
                uuid = new UUID(mostSigBits, leastSigBits);

                offset += (endIndex - (bp + fieldName.length + 1) + 1);
                chLocal = charAt(bp + (offset++));
            } else if (len == 32) {
                long mostSigBits = 0;
                long leastSigBits = 0;
                mostSigBits = extracted58(startIndex2, mostSigBits);

                uuid = new UUID(mostSigBits, leastSigBits);

                offset += (endIndex - (bp + fieldName.length + 1) + 1);
                chLocal = charAt(bp + (offset++));
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
        } else if (chLocal == 'n'
                && charAt(bp + (offset++)) == 'u'
                && charAt(bp + (offset++)) == 'l') {
            uuid = null;
            chLocal = charAt(bp + (offset++));
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return uuid;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return uuid;
    }

    public java.util.UUID scanUUID() {
        matchStat = UNKNOWN;



        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        final java.util.UUID uuid;
        if (chLocal == '"') {
            int startIndex = bp + 1;
            int endIndex = indexOf('"', startIndex);
            extracted13(endIndex);

            int startIndex2 = bp + 1; // must re compute
            int len = endIndex - startIndex2;
            if (len == 36) {
                long mostSigBits = 0;
                long leastSigBits = 0;
                mostSigBits = extracted57(startIndex2, mostSigBits);

                uuid = new UUID(mostSigBits, leastSigBits);

                offset += (endIndex - (bp + 1) + 1);
                chLocal = charAt(bp + (offset++));
            } else if (len == 32) {
                long mostSigBits = 0;
                long leastSigBits = 0;
                mostSigBits = extracted58(startIndex2, mostSigBits);


                uuid = new UUID(mostSigBits, leastSigBits);

                offset += (endIndex - (bp + 1) + 1);
                chLocal = charAt(bp + (offset++));
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
        } else if (chLocal == 'n'
                && charAt(bp + (offset++)) == 'u'
                && charAt(bp + (offset++)) == 'l') {
            uuid = null;
            chLocal = charAt(bp + (offset++));
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return uuid;
        }

        if (chLocal == ']') {
            chLocal = charAt(bp + (offset++));
            switch (chLocal) {
                case ',':
                    token = JSONToken.COMMA;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case ']':
                    token = JSONToken.RBRACKET;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case '}':
                    token = JSONToken.RBRACE;
                    bp += offset;
                    this.ch = this.charAt(bp);
                    break;
                case EOI:
                    token = JSONToken.EOF;
                    bp += (offset - 1);
                    ch = EOI;
                    break;

                default:
                    matchStat = NOT_MATCH;
                    return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return uuid;
    }

    private long extracted58(int startIndex2, long mostSigBits) {
        for (int i = 0; i < 32; ++i) {
            char chex58 = charAt(startIndex2 + i);
            int num = extr1(chex58);


            mostSigBits <<= 4;
            mostSigBits |= num;
        }
        return mostSigBits;
    }

    private long extracted57(int startIndex2, long mostSigBits) {
        for (int i = 0 ; i < 36; ++i) {
            if (extracted52(i) ) {
                continue;
            }
            char chex57 = charAt(startIndex2 + i);
            int num = extr1(chex57);

            mostSigBits <<= 4;
            mostSigBits |= num;
        }
        return mostSigBits;
    }

    private int extr1(char ch){
        int num = 0;
        if (extractedv17(ch)) {
            num = ch - '0';
        } else if (extracted3(ch)) {
            num = 10 + (ch - 'a');
        } else if (extractedvw49(ch)) {
            num = 10 + (ch - 'A');
        } else {
            matchStat = NOT_MATCH_NAME;

        }
        return num;
    }

    private boolean extracted52(int i) {
        return i == 8 || i == 13 || i == 18 || i == 23;
    }

    private boolean extractedvw49(char ch) {
        return ch >= 'A' && ch <= 'F';
    }

    private boolean extracted3(char ch) {
        return ch >= 'a' && ch <= 'f';
    }

    public final void scanTrue() {
        if (ch != 't') {
            throw new JSONException(ERROR_PARSE_TRUE);
        }
        next();

        if (ch != 'r') {
            throw new JSONException(ERROR_PARSE_TRUE);
        }
        next();

        if (ch != 'u') {
            throw new JSONException(ERROR_PARSE_TRUE);
        }
        next();

        if (ch != 'e') {
            throw new JSONException(ERROR_PARSE_TRUE);
        }
        next();

        if (ch == ' '  ||
                ch == ','  ||
                ch == '}'  ||
                ch == ']'  ||
                ch == '\n' ||
                ch == '\r' ||
                ch == '\t' ||
                ch == EOI  ||
                ch == '\f' ||
                ch == '\b' ||
                ch == ':'  ||
                ch == '/') {
            token = JSONToken.TRUE;
        } else {
            throw new JSONException("scan true error");
        }
    }

    public final void scanNullOrNew() {
        scanNullOrNew(true);
    }

    public final void scanNullOrNew(boolean acceptColon) {
        if (ch != 'n') {
            throw new JSONException("error parse null or new");
        }
        next();

        if (ch == 'u') {
            next();
            if (ch != 'l') {
                throw new JSONException("error parse null");
            }
            next();

            if (ch != 'l') {
                throw new JSONException("error parse null");
            }
            next();

            if (ch == ' '
                    || ch == ','
                    || ch == '}'
                    || ch == ']'
                    || ch == '\n'
                    || ch == '\r'
                    || ch == '\t'
                    || ch == EOI
                    || (ch == ':' && acceptColon)
                    || ch == '\f'
                    || ch == '\b') {

                token = JSONToken.NULL;
            } else {
                throw new JSONException("scan null error");
            }
            return;
        }

        if (ch != 'e') {
            throw new JSONException("error parse new");
        }
        next();

        if (ch != 'w') {
            throw new JSONException("error parse new");
        }
        next();

        switch (ch){
            case ' ':
            case ',':
            case '}':
            case ']':
            case '\n':
            case '\r':
            case '\t':
            case EOI:
            case '\f':
            case '\b':
                token = JSONToken.NEW;
                break;
            default:
                throw new JSONException("scan new error");
        }
    }

    public final void scanFalse() {
        if (ch != 'f') {
            throw new JSONException(ERROR_PARSE_FALSE);
        }
        next();

        if (ch != 'a') {
            throw new JSONException(ERROR_PARSE_FALSE);
        }
        next();

        if (ch != 'l') {
            throw new JSONException(ERROR_PARSE_FALSE);
        }
        next();

        if (ch != 's') {
            throw new JSONException(ERROR_PARSE_FALSE);
        }
        next();

        if (ch != 'e') {
            throw new JSONException(ERROR_PARSE_FALSE);
        }
        next();

        if (ch == ' '  ||
                ch == ','  ||
                ch == '}'  ||
                ch == ']'  ||
                ch == '\n' ||
                ch == '\r' ||
                ch == '\t' ||
                ch == EOI  ||
                ch == '\f' ||
                ch == '\b' ||
                ch == ':'  ||
                ch == '/') {
            token = JSONToken.FALSE;
        } else {
            throw new JSONException("scan false error");
        }
    }

    public final void scanIdent() {
        np = bp - 1;
        hasSpecial = false;

        for (;;) {
            sp++;

            next();
            if (Character.isLetterOrDigit(ch)) {
                continue;
            }

            String ident = stringVal();

            if ("null".equalsIgnoreCase(ident)) {
                token = JSONToken.NULL;
            } else if ("new".equals(ident)) {
                token = JSONToken.NEW;
            } else if ("true".equals(ident)) {
                token = JSONToken.TRUE;
            } else if ("false".equals(ident)) {
                token = JSONToken.FALSE;
            } else if ("undefined".equals(ident)) {
                token = JSONToken.UNDEFINED;
            } else if ("Set".equals(ident)) {
                token = JSONToken.SET;
            } else if ("TreeSet".equals(ident)) {
                token = JSONToken.TREE_SET;
            } else {
                token = JSONToken.IDENTIFIER;
            }
            return;
        }
    }

    public abstract String stringVal();

    public abstract String subString(int offset, int count);

    protected abstract char[] subChars(int offset, int count);

    public static String readString(char[] chars, int charsLen) {
        char[] sbuf = new char[charsLen];
        int len = 0;
        for (int i = 0; i < charsLen; i++) {
            char ch = chars[i];

            if (ch != '\\') {
                sbuf[len++] = ch;
                continue;
            }
            ch = chars[i + 1];

            switch (ch) {
                case '0':
                    sbuf[len++] = '\0';
                    break;
                case '1':
                    sbuf[len++] = '\1';
                    break;
                case '2':
                    sbuf[len++] = '\2';
                    break;
                case '3':
                    sbuf[len++] = '\3';
                    break;
                case '4':
                    sbuf[len++] = '\4';
                    break;
                case '5':
                    sbuf[len++] = '\5';
                    break;
                case '6':
                    sbuf[len++] = '\6';
                    break;
                case '7':
                    sbuf[len++] = '\7';
                    break;
                case 'b': // 8
                    sbuf[len++] = '\b';
                    break;
                case 't': // 9
                    sbuf[len++] = '\t';
                    break;
                case 'n': // 10
                    sbuf[len++] = '\n';
                    break;
                case 'v': // 11
                    sbuf[len++] = '\u000B';
                    break;
                case 'f': // 12
                case 'F':
                    sbuf[len++] = '\f';
                    break;
                case 'r': // 13
                    sbuf[len++] = '\r';
                    break;
                case '"': // 34
                    sbuf[len++] = '"';
                    break;
                case '\'': // 39
                    sbuf[len++] = '\'';
                    break;
                case '/': // 47
                    sbuf[len++] = '/';
                    break;
                case '\\': // 92
                    sbuf[len++] = '\\';
                    break;
                case 'x':
                    sbuf[len++] = (char) (digits[chars[i + 1]] * 16 + digits[chars[i + 1]]);
                    break;
                case 'u':
                    sbuf[len++] = (char) Integer.parseInt(new String(new char[] { chars[i + 1], //
                                    chars[i + 1], //
                                    chars[i + 1], //
                                    chars[i + 1] }),
                            16);
                    break;
                default:
                    throw new JSONException("unclosed.str.lit");
            }
        }
        return new String(sbuf, 0, len);
    }

    protected abstract boolean charArrayCompare(char[] chars);

    public boolean isBlankInput() {
        for (int i = 0;; ++i) {
            char chLocal = charAt(i);
            if (chLocal == EOI) {
                token = JSONToken.EOF;
                break;
            }

            if (!isWhitespace(chLocal)) {
                return false;
            }
        }

        return true;
    }

    public final void skipWhitespace() {
        for (;;) {
            if (ch <= '/') {
                if (ch == ' '  ||
                        ch == '\r' ||
                        ch == '\n' ||
                        ch == '\t' ||
                        ch == '\f' ||
                        ch == '\b') {
                    next();

                } else if (ch == '/') {
                    skipComment();

                }
            } else {
                break;
            }
        }
    }

    private void scanStringSingleQuote() {
        np = bp;
        hasSpecial = false;
        char chLocal;
        for (;;) {
            chLocal = next();



            if (chLocal == EOI) {
                if (!isEOF()) {
                    putChar(EOI);
                }
                throw new JSONException("unclosed single-quote string");
            }

            if (chLocal == '\\') {
                extracted10();

                chLocal = next();

                switch (chLocal) {
                    case '0':
                        putChar('\0');
                        break;
                    case '1':
                        putChar('\1');
                        break;
                    case '2':
                        putChar('\2');
                        break;
                    case '3':
                        putChar('\3');
                        break;
                    case '4':
                        putChar('\4');
                        break;
                    case '5':
                        putChar('\5');
                        break;
                    case '6':
                        putChar('\6');
                        break;
                    case '7':
                        putChar('\7');
                        break;
                    case 'b': // 8
                        putChar('\b');
                        break;
                    case 't': // 9
                        putChar('\t');
                        break;
                    case 'n': // 10
                        putChar('\n');
                        break;
                    case 'v': // 11
                        putChar('\u000B');
                        break;
                    case 'f': // 12
                    case 'F':
                        putChar('\f');
                        break;
                    case 'r': // 13
                        putChar('\r');
                        break;
                    case '"': // 34
                        putChar('"');
                        break;
                    case '\'': // 39
                        putChar('\'');
                        break;
                    case '/': // 47
                        putChar('/');
                        break;
                    case '\\': // 92
                        putChar('\\');
                        break;
                    case 'x':
                        extracted12();
                        break;
                    case 'u':
                        putChar((char) Integer.parseInt(new String(new char[] { next(), next(), next(), next() }), 16));
                        break;
                    default:
                        this.ch = chLocal;
                        throw new JSONException("unclosed single-quote string");
                }

            }

            if (!hasSpecial) {
                sp++;
                continue;
            }

            extracted8(chLocal);
        }
    }

    private void extracted12(){
        char x1 = next();
        char x2 = next();

        boolean hex1 = extracted7(x1);
        boolean hex2 = extracted7(x2);
        extracted11(x1, x2, hex1, hex2);

        putChar((char) (digits[x1] * 16 + digits[x2]));
    }

    private void extracted11(char x1, char x2, boolean hex1, boolean hex2){
        if (!hex1 || !hex2) {
            throw new JSONException("invalid escape character \\x" + x1 + x2);
        }
    }

    private void extracted10() {
        if (!hasSpecial) {
            hasSpecial = true;

            extracted9();


            this.copyTo(np + 1, sp, sbuf);

        }
    }

    private void extracted9() {
        if (sp > sbuf.length) {
            char[] newsbuf = new char[sp * 2];
            System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
            sbuf = newsbuf;
        }
    }

    private void extracted8(char chLocal) {
        if (sp == sbuf.length) {
            putChar(chLocal);
        } else {
            sbuf[sp++] = chLocal;
        }
    }

    private boolean extracted7(char x1) {
        return extractedv17(x1)
                || extracted3(x1)
                || extractedvw49(x1);
    }

    /**
     * Append a character to sbuf.
     */
    protected final void putChar(char ch) {
        if (sp >= sbuf.length) {
            int len = sbuf.length * 2;
            if (len < sp) {
                len = sp + 1;
            }
            char[] newsbuf = new char[len];
            System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
            sbuf = newsbuf;
        }
        sbuf[sp++] = ch;
    }

    public final void scanHex() {
        if (ch != 'x') {
            throw new JSONException(ILLEGAL_STATE + ch);
        }
        next();
        if (ch != '\'') {
            throw new JSONException(ILLEGAL_STATE + ch);
        }

        np = bp;
        next();

        if (ch == '\'') {
            next();
            token = JSONToken.HEX;
            return;
        }

        for (; ; ) {
            char chScanHex = next();
            if (extractedv17(chScanHex) || extractedvw49(chScanHex)) {
                sp++;

            } else if (chScanHex == '\'') {
                sp++;
                next();
                break;
            } else {
                throw new JSONException(ILLEGAL_STATE + chScanHex);
            }
        }
        token = JSONToken.HEX;
    }

    public final void scanNumber() {
        np = bp;

        if (ch == '-') {
            sp++;
            next();
        }

        extractedv3();

        boolean isDouble = false;

        isDouble = extracted5(isDouble);

        extracted4();
        switch (ch) {
            case 'L':
            case 'S':
            case 'B':
                sp++;
                next();
                break;
            case 'F':
            case 'D':
                sp++;
                next();
                isDouble = true;
                break;
            case 'e':
            case 'E':
                sp++;
                next();
                if (ch == '+' || ch == '-') {
                    sp++;
                    next();
                }

                extractedv3();

                if (ch == 'D' || ch == 'F') {
                    sp++;
                    next();
                }
                break;
            default:
                isDouble = true;
                break;
        }


        extracted6(isDouble);
    }

    private void extracted6(boolean isDouble) {
        if (isDouble) {
            token = JSONToken.LITERAL_FLOAT;
        } else {
            token = JSONToken.LITERAL_INT;
        }
    }

    private boolean extracted5(boolean isDouble) {
        if (ch == '.') {
            sp++;
            next();
            isDouble = true;

            extractedv3();
        }
        return isDouble;
    }

    private void extracted4(){
        if (sp > 65535) {
            throw new JSONException("scanNumber overflow");
        }
    }

    private void extractedv3() {
        for (;;) {
            if (extractedv52()) {
                sp++;
            } else {
                break;
            }
            next();
        }
    }

    public final long longValue() throws NumberFormatException {
        long result = 0;
        boolean negative = false;
        long limit;
        int digit;

        if (np == -1) {
            np = 0;
        }

        int i = np;
        int max = np + sp;

        if (charAt(np) == '-') {
            negative = true;
            limit = Long.MIN_VALUE;
            i++;
        } else {
            limit = -Long.MAX_VALUE;
        }
        long multmin = MULTMIN_RADIX_TEN;
        if (i < max) {
            digit = charAt(i++) - '0';
            result = -digit;
        }
        while (i < max) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            char chLocal = charAt(i++);

            switch (chLocal) {

                case 'L':
                case 'S':
                case 'B':
                    break;
                default:

                    continue;

            }

            digit = chLocal - '0';
            if (result < multmin) {
                throw new NumberFormatException(numberString());
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException(numberString());
            }
            result -= digit;


        }

        if (!negative) {
            return -result;
        }
        if (i > np + 1) {
            return result;
        } else { /* Only got "-" */
            throw new NumberFormatException(numberString());
        }
    }

    public final Number decimalValue(boolean decimal) {
        char chLocal = charAt(np + sp - 1);
        try {
            if (chLocal == 'F') {
                return Float.parseFloat(numberString());
            }

            if (chLocal == 'D') {
                return Double.parseDouble(numberString());
            }

            if (decimal) {
                return decimalValue();
            } else {
                return doubleValue();
            }
        } catch (NumberFormatException ex) {
            throw new JSONException(ex.getMessage() + ", " + info());
        }
    }

    public abstract BigDecimal decimalValue();

    public static boolean isWhitespace(char ch) {
        // 专门调整了判断顺序
        return ch <= ' '  &&
                (ch == ' '  ||
                        ch == '\n' ||
                        ch == '\r' ||
                        ch == '\t' ||
                        ch == '\f' ||
                        ch == '\b');
    }

    protected static final long  MULTMIN_RADIX_TEN     = Long.MIN_VALUE / 10;
    protected static final int   INT_MULTMIN_RADIX_TEN = Integer.MIN_VALUE / 10;

    protected static final int[] digits                = new int['f' + 1];

    static {
        for (int i = '0'; i <= '9'; ++i) {
            digits[i] = i - '0';
        }

        for (int i = 'a'; i <= 'f'; ++i) {
            digits[i] = (i - 'a') + 10;
        }
        for (int i = 'A'; i <= 'F'; ++i) {
            digits[i] = (i - 'A') + 10;
        }
    }

    /**
     * hsf support
     * @param fieldName
     * @param argTypesCount
     * @param typeSymbolTable
     * @return
     */
    public String[] scanFieldStringArray(char[] fieldName, int argTypesCount, SymbolTable typeSymbolTable) {
        throw new UnsupportedOperationException();
    }

    public boolean matchField2(char[] fieldName) {
        throw new UnsupportedOperationException();
    }

    public int getFeatures() {
        return this.features;
    }

    public void setFeatures(int features) {
        this.features = features;
    }

}