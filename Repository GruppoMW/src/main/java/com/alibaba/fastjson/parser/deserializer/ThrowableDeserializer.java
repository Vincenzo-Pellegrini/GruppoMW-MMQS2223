package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.TypeUtils;

public class ThrowableDeserializer extends JavaBeanDeserializer {

    static final String             SYNTAX_ERROR           = "syntax error";

    public ThrowableDeserializer(ParserConfig mapping, Class<?> clazz){
        super(mapping, clazz, clazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        JSONLexer lexer = parser.lexer;
        
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken();
            return null;
        }

        extracted2(parser, lexer);

        Throwable cause = null;
        Class<?> exClass = null;
        
        exClass = extracted4(type, exClass);
        
        String message = null;
        StackTraceElement[] stackTrace = null;
        Map<String, Object> otherValues = null;


        for (;;) {
            // lexer.scanSymbol
            String key = lexer.scanSymbol(parser.getSymbolTable());

            if (key == null) {
                if (lexer.token() == JSONToken.RBRACE) {
                    lexer.nextToken(JSONToken.COMMA);
                }
                if (extracted22(lexer)) {
                        //skip
                }
            }

            lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);

            exClass = extracted19(lexer, exClass, key);
            message = extracted20(lexer, message, key);
            cause = extracted21(parser, cause, key);
            if ("stackTrace".equals(key)) {
                stackTrace = parser.parseObject(StackTraceElement[].class);
            } else {
                otherValues = extracted7(otherValues);
                otherValues.put(key, parser.parse());
            }

            if (lexer.token() == JSONToken.RBRACE) {
                lexer.nextToken(JSONToken.COMMA);
                break;
            }
        }

        Throwable ex = extracted18(cause, exClass, message, stackTrace);

        extracted17(parser, exClass, otherValues, ex);

        return (T) ex;
    }

    private boolean extracted22(JSONLexer lexer) {
        return lexer.token() == JSONToken.COMMA && lexer.isEnabled(Feature.ALLOW_ARBITRARY_COMMAS);
    }

    private Throwable extracted21(DefaultJSONParser parser, Throwable cause, String key) {
        if ("cause".equals(key)) {
            cause = deserialze(parser, null, "cause");
        }
        return cause;
    }

    private String extracted20(JSONLexer lexer, String message, String key) {
        if ("message".equals(key)) {
            lexer.nextToken();
        }
        return message;
    }

    private Class<?> extracted19(JSONLexer lexer, Class<?> exClass, String key) {
        if (JSON.DEFAULT_TYPE_KEY.equals(key)) {
            lexer.nextToken(JSONToken.COMMA);
        }
        return exClass;
    }

    private Throwable extracted18(Throwable cause, Class<?> exClass, String message, StackTraceElement[] stackTrace) {
        Throwable ex = extracted9(cause, exClass, message);
        if (ex == null){
            throw new NullPointerException("ex è nullo");
        } else {
            if (stackTrace != null) {
                ex.setStackTrace(stackTrace);
            }
            return ex;
        }
    }

    private void extracted17(DefaultJSONParser parser, Class<?> exClass, Map<String, Object> otherValues,
            Throwable ex) {
        if (otherValues != null) {
            JavaBeanDeserializer exBeanDeser = extracted12(parser, exClass);

            extracted16(parser, otherValues, ex, exBeanDeser);
        }
    }

    private void extracted16(DefaultJSONParser parser, Map<String, Object> otherValues, Throwable ex,
            JavaBeanDeserializer exBeanDeser) {
        if (exBeanDeser != null) {
            extracted15(parser, otherValues, ex, exBeanDeser);
        }
    }

    private void extracted15(DefaultJSONParser parser, Map<String, Object> otherValues, Throwable ex,
            JavaBeanDeserializer exBeanDeser) {
        for (Map.Entry<String, Object> entry : otherValues.entrySet()) {
            extracted14(parser, ex, exBeanDeser, entry);
        }
    }

    private void extracted14(DefaultJSONParser parser, Throwable ex, JavaBeanDeserializer exBeanDeser,
            Map.Entry<String, Object> entry) {
        String key = entry.getKey();
        Object value = entry.getValue();

        FieldDeserializer fieldDeserializer = exBeanDeser.getFieldDeserializer(key);
        if (fieldDeserializer != null) {
            FieldInfo fieldInfo = fieldDeserializer.fieldInfo;
            value = extracted13(parser, value, fieldInfo);
            fieldDeserializer.setValue(ex, value);
        }
    }

    private Object extracted13(DefaultJSONParser parser, Object value, FieldInfo fieldInfo) {
        if (!fieldInfo.fieldClass.isInstance(value)) {
            value = TypeUtils.cast(value, fieldInfo.fieldType, parser.getConfig());
        }
        return value;
    }

    private JavaBeanDeserializer extracted12(DefaultJSONParser parser, Class<?> exClass) {
        JavaBeanDeserializer exBeanDeser = null;

        if (exClass != null) {
            exBeanDeser = extracted11(parser, exClass, exBeanDeser);
        }
        return exBeanDeser;
    }

    private JavaBeanDeserializer extracted11(DefaultJSONParser parser, Class<?> exClass,
            JavaBeanDeserializer exBeanDeser) {
        if (exClass == clazz) {
            exBeanDeser = this;
        } else {
            exBeanDeser = extracted10(parser, exClass, exBeanDeser);
        }
        return exBeanDeser;
    }

    private JavaBeanDeserializer extracted10(DefaultJSONParser parser, Class<?> exClass,
            JavaBeanDeserializer exBeanDeser) {
        ObjectDeserializer exDeser = parser.getConfig().getDeserializer(exClass);
        if (exDeser instanceof JavaBeanDeserializer) {
            exBeanDeser = (JavaBeanDeserializer) exDeser;
        }
        return exBeanDeser;
    }

    private Throwable extracted9(Throwable cause, Class<?> exClass, String message) {
        Throwable ex = null;
        if (exClass == null) {
            ex = new Exception(message, cause);
        } else {
            if (!Throwable.class.isAssignableFrom(exClass)) {
                throw new JSONException("type not match, not Throwable. " + exClass.getName());
            }
        }
        return ex;
    }

    private Map<String, Object> extracted7(Map<String, Object> otherValues) {
        if (otherValues == null) {
            otherValues = new HashMap<>();
        }
        return otherValues;
    }

    private Class<?> extracted4(Type type, Class<?> exClass) {
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            exClass = extracted3(exClass, clazz);
        }
        return exClass;
    }

    private Class<?> extracted3(Class<?> exClass, Class<?> clazz) {
        if (Throwable.class.isAssignableFrom(clazz)) {
            exClass = clazz;
        }
        return exClass;
    }

    private void extracted2(DefaultJSONParser parser, JSONLexer lexer) {
        if (parser.getResolveStatus() == DefaultJSONParser.TYPE_NAME_REDIRECT) {
            parser.setResolveStatus(DefaultJSONParser.NONE);
        } else {
            extracted(lexer);
        }
    }

    private void extracted(JSONLexer lexer) {
        if (lexer.token() != JSONToken.LBRACE) {
            throw new JSONException(SYNTAX_ERROR);
        }
    }
    @Override
    public int getFastMatchToken() {
        return JSONToken.LBRACE;
    }
}
