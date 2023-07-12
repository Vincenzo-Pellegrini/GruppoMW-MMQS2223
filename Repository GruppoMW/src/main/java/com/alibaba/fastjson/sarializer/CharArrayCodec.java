package com.alibaba.fastjson.serializer;

import java.lang.reflect.Type;
import java.util.Collection;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import java.util.function.Predicate;


public class CharArrayCodec implements ObjectDeserializer {

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        return (T) deserialze(parser);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T deserialze(DefaultJSONParser parser) {
        final JSONLexer lexer = parser.lexer;
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            String val = lexer.stringVal();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val.toCharArray();
        }
        if (lexer.token() == JSONToken.LITERAL_INT) {
            Number val = lexer.integerValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val.toString().toCharArray();
        }
        
        Object value = parser.parse();
        
        if (value instanceof String) {
            return (T) ((String) value).toCharArray();
        }
        
        if (value instanceof Collection) {
            Collection<?> collection = (Collection) value;
        
        boolean accept = collection.stream().allMatch(new Predicate<Object>() {
            @Override
            public boolean test(Object item) {
                if (item instanceof String) {
                    return ((String) item).length() == 1;
                }
                return false;
            }
        });
    
        if (!accept) {
            throw new JSONException("can not cast to char[]");
        }
    
        char[] chars = new char[collection.size()];
        int pos = 0;
        for (Object item : collection) {
            chars[pos++] = ((String) item).charAt(0);
        }
            return (T) chars;
        }
        
        return value == null
                ? null
                : (T) JSON.toJSONString(value).toCharArray();
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_STRING;
    }
}
