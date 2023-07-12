package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.*;
import com.alibaba.fastjson.util.TypeUtils;

public abstract class AbstractDateDeserializer extends ContextObjectDeserializer implements ObjectDeserializer {

    static final String             SYNTAX_ERROR           = "syntax error";
    @Override
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        return deserialze(parser, clazz, fieldName, null, 0);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName, String format, int features) {
        JSONLexer lexer = parser.lexer;

        Object val;
        extracted2(lexer);
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            String strVal = lexer.stringVal();
            
            if (format != null) {
                if (extracted9(clazz, format)) {
                    return (T) TypeUtils.castToTimestamp(strVal);
                }

                SimpleDateFormat simpleDateFormat = null;
                simpleDateFormat = extracted17(parser, format, simpleDateFormat);

                if (JSON.defaultTimeZone == null) {
                    throw new NullPointerException("ERROR");
                }

                val = extracted14(strVal, simpleDateFormat);

                val = extracted16(parser, format, val, strVal, simpleDateFormat);

                val = extracted12(format, val, strVal);
            } else {
                val = null;
            }
            
            extracted10(lexer, val, strVal);
        }
        extracted3(lexer);
        clazz = extracted7(parser, clazz, lexer);
        val = extracted8(parser, lexer);

        return (T) cast(parser, clazz, fieldName, val);
    }

    private SimpleDateFormat extracted17(DefaultJSONParser parser, String format, SimpleDateFormat simpleDateFormat) {
        try {
            simpleDateFormat = new SimpleDateFormat(format, parser.lexer.getLocale());
        } catch (IllegalArgumentException ex) {
            simpleDateFormat = extracted13(parser, format, simpleDateFormat, ex);
        }
        return simpleDateFormat;
    }

    private Object extracted16(DefaultJSONParser parser, String format, Object val, String strVal,
            SimpleDateFormat simpleDateFormat) {
        if (val == null && JSON.defaultLocale == Locale.CHINA) {
            simpleDateFormat = extracted15(parser, format, simpleDateFormat);
            simpleDateFormat.setTimeZone(parser.lexer.getTimeZone());

            val = extracted14(strVal, simpleDateFormat);
        }
        return val;
    }

    private SimpleDateFormat extracted15(DefaultJSONParser parser, String format, SimpleDateFormat simpleDateFormat) {
        try {
            simpleDateFormat = new SimpleDateFormat(format, Locale.US);
        } catch (IllegalArgumentException ex) {
            simpleDateFormat = extracted13(parser, format, simpleDateFormat, ex);
        }
        return simpleDateFormat;
    }

    private Object extracted14(String strVal, SimpleDateFormat simpleDateFormat) {
        Object val;
        if (simpleDateFormat == null){
            throw new NullPointerException("ERROR");
        }
        try {
            val = simpleDateFormat.parse(strVal);
        } catch (ParseException ex) {
            val = null;
            // skip
        }
        return val;
    }

    private SimpleDateFormat extracted13(DefaultJSONParser parser, String format, SimpleDateFormat simpleDateFormat,
            IllegalArgumentException ex) {
        if (format.contains("T")) {
            String fromat2 = format.replace("T", "'T'");
            try {
                simpleDateFormat = new SimpleDateFormat(fromat2, parser.lexer.getLocale());
            } catch (IllegalArgumentException e2) {
                throw ex;
            }
        }
        return simpleDateFormat;
    }

    private Object extracted12(String format, Object val, String strVal) {
        if (val == null) {
            if (format.equals("yyyy-MM-dd'T'HH:mm:ss.SSS") //
                    && strVal.length() == 19) {
                val = extracted11(strVal);
            } else {
                // skip
                val = null;
            }
        }
        return val;
    }

    private Object extracted11(String strVal) {
        Object val;
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", JSON.defaultLocale);
            df.setTimeZone(JSON.defaultTimeZone);
            val = df.parse(strVal);
        } catch (ParseException ex2) {
            // skip
            val = null;
        }
        return val;
    }

    private void extracted10(JSONLexer lexer, Object val, String strVal) {
        if (val == null) {
            lexer.nextToken(JSONToken.COMMA);
            
            if (lexer.isEnabled(Feature.ALLOW_ISO8601_DATE_FORMAT)) {
                JSONScanner iso8601Lexer = new JSONScanner(strVal);
                iso8601Lexer.close();
            }
        }
    }

    private boolean extracted9(Type clazz, String format) {
        return "yyyy-MM-dd HH:mm:ss.SSSSSSSSS".equals(format)
                && clazz instanceof Class
                && clazz.getClass().isInstance("java.sql.Timestamp");
    }

    private Object extracted8(DefaultJSONParser parser, JSONLexer lexer) {
        Object val;
        if (parser.getResolveStatus() == DefaultJSONParser.TYPE_NAME_REDIRECT) {
            parser.setResolveStatus(DefaultJSONParser.NONE);
            parser.accept(JSONToken.COMMA);

            extracted(lexer);

            parser.accept(JSONToken.COLON);

            val = parser.parse();

            parser.accept(JSONToken.RBRACE);
        } else {
            val = parser.parse();
        }
        return val;
    }

    private Type extracted7(DefaultJSONParser parser, Type clazz, JSONLexer lexer) {
        if (lexer.token() == JSONToken.LBRACE) {
            lexer.nextToken();
            clazz = extracted6(parser, clazz, lexer);
            if (lexer.token() == JSONToken.LITERAL_INT) {
                lexer.nextToken();
            } else {
                throw new JSONException("syntax error : " + lexer.tokenName());
            }
            
            parser.accept(JSONToken.RBRACE);
        }
        return clazz;
    }

    private Type extracted6(DefaultJSONParser parser, Type clazz, JSONLexer lexer) {
        String key;
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            key = lexer.stringVal();
            
            clazz = extracted5(parser, clazz, lexer, key);
            
            lexer.nextTokenWithColon(JSONToken.LITERAL_INT);
        } else {
            throw new JSONException(SYNTAX_ERROR);
        }
        return clazz;
    }

    private Type extracted5(DefaultJSONParser parser, Type clazz, JSONLexer lexer, String key) {
        if (JSON.DEFAULT_TYPE_KEY.equals(key)) {
            lexer.nextToken();
            parser.accept(JSONToken.COLON);
            
            String typeName = lexer.stringVal();
            Class<?> type = parser.getConfig().checkAutoType(typeName, null, lexer.getFeatures());
            clazz = extracted4(clazz, type);
            
            parser.accept(JSONToken.LITERAL_STRING);
            parser.accept(JSONToken.COMMA);
        }
        return clazz;
    }

    private Type extracted4(Type clazz, Class<?> type) {
        if (type != null) {
            clazz = type;
        }
        return clazz;
    }

    private void extracted3(JSONLexer lexer) {
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken();
        }
    }

    private void extracted2(JSONLexer lexer) {
        if (lexer.token() == JSONToken.LITERAL_INT) {
            lexer.nextToken(JSONToken.COMMA);
        }
    }

    private void extracted(JSONLexer lexer) {
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            if (!"val".equals(lexer.stringVal())) {
                throw new JSONException(SYNTAX_ERROR);
            }
            lexer.nextToken();
        } else {
            throw new JSONException(SYNTAX_ERROR);
        }
    }

    protected abstract <T> T cast(DefaultJSONParser parser, Type clazz, Object fieldName, Object value);
}
