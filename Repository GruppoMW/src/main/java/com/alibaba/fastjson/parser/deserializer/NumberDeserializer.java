package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.Type;
import java.math.BigDecimal;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.util.TypeUtils;

public class NumberDeserializer implements ObjectDeserializer {

    public static final NumberDeserializer instance = new NumberDeserializer();

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        final JSONLexer lexer = parser.lexer;
        

        if (lexer.token() == JSONToken.LITERAL_FLOAT) {
            if (extracted6(clazz)) {
                String val = lexer.numberString();
                lexer.nextToken(JSONToken.COMMA);
                return (T) Double.valueOf(Double.parseDouble(val));
            }

            if (extracted5(clazz)) {
                BigDecimal val = lexer.decimalValue();
                lexer.nextToken(JSONToken.COMMA);
                short shortValue = TypeUtils.shortValue(val);
                return (T) Short.valueOf(shortValue);
            }

            if (extracted4(clazz)) {
                BigDecimal val = lexer.decimalValue();
                lexer.nextToken(JSONToken.COMMA);
                byte byteValue = TypeUtils.byteValue(val);
                return (T) Byte.valueOf(byteValue);
            }
            lexer.nextToken(JSONToken.COMMA);
        }

        Object value = parser.parse();

        if (extracted6(clazz)) {
            try {
                return (T) TypeUtils.castToDouble(value);
            } catch (Exception ex) {
                throw new JSONException("parseDouble error, field : " + fieldName, ex);
            }
        }

        if (extracted5(clazz)) {
            try {
                return (T) TypeUtils.castToShort(value);
            } catch (Exception ex) {
                throw new JSONException("parseShort error, field : " + fieldName, ex);
            }
        }

        return (T) TypeUtils.castToBigDecimal(value);
    }

    private boolean extracted6(Type clazz) {
        return clazz == double.class || clazz == Double.class;
    }

    private boolean extracted5(Type clazz) {
        return clazz == short.class || clazz == Short.class;
    }

    private boolean extracted4(Type clazz) {
        return clazz == byte.class || clazz == Byte.class;
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_INT;
    }
}
