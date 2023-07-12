package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.*;
import com.alibaba.fastjson.parser.DefaultJSONParser.ResolveTask;

public class MapDeserializer extends ContextObjectDeserializer implements ObjectDeserializer {
    
    static final String             EXPECT_DOTS_AT           = "expect ':' at ";

    public static final MapDeserializer instance = new MapDeserializer(); 

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName, String format, int features)
    {
        if (type == JSONObject.class && parser.getFieldTypeResolver() == null) {
            return (T) parser.parseObject();
        }
        
        final JSONLexer lexer = parser.lexer;
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
            return null;
        }

        boolean unmodifiableMap = type instanceof Class
                && type.getClass().isInstance("java.util.Collections$UnmodifiableMap");

        Map<Object, Object> map = (lexer.getFeatures() & Feature.ORDERED_FIELD.mask) != 0
                ? createMap(type, lexer.getFeatures())
                : createMap(type);

        ParseContext context = parser.getContext();

        try {
            parser.setContext(context, map, fieldName);
            T t = (T) deserialze(parser, type, fieldName, map, features);
            if (unmodifiableMap) {
                t = (T) Collections.unmodifiableMap((Map) t);
            }
            return t;
        } finally {
            parser.setContext(context);
        }
    }

    protected Object deserialze(DefaultJSONParser parser, Type type, Object fieldName, Map<?,?> map) {
        return deserialze(parser, type, fieldName, map, 0);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object deserialze(DefaultJSONParser parser, Type type, Object fieldName, Map map, int features) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type keyType = parameterizedType.getActualTypeArguments()[0];
            Type valueType = null;
            if(map.getClass().isInstance("org.springframework.util.LinkedMultiValueMap")){
                valueType = List.class;
            }else{
                valueType = parameterizedType.getActualTypeArguments()[1];
            }
            if (String.class == keyType) {
                return parseMap(parser, map, valueType, fieldName);
            } else {
                return null;
            }
        } else {
            return parser.parseObject(map, fieldName);
        }
    }
    
    @SuppressWarnings("rawtypes")
    public static Map parseMap(DefaultJSONParser parser, Map<String, Object> map, Type valueType, Object fieldName) {
        JSONLexer lexer = parser.lexer;

        int token = lexer.token();
        extracted16(fieldName, lexer, token);

        ParseContext context = parser.getContext();
        try {
            for (int i = 0;;++i) {
                lexer.skipWhitespace();
                char ch = lexer.getCurrent();
                ch = extracted18(lexer, ch);

                String key;
                ch = extracted24(lexer, ch);
                if (ch == '}') {
                    lexer.next();
                    lexer.resetStringPosition();
                    lexer.nextToken(JSONToken.COMMA);
                    return map;
                }
                key = extracted23(parser, lexer, ch);

                lexer.next();
                lexer.skipWhitespace();
                ch = lexer.getCurrent();

                lexer.resetStringPosition();

                Object value;
                lexer.nextToken();

                extracted29(parser, context, i);
                
                value = extracted28(parser, valueType, lexer, key);

                map.put(key, value);
                parser.checkMapResolve(map, key);

                parser.setContext(context, value, key);
                parser.setContext(context);

                final int tok = lexer.token();
                if (extracted30(tok)) {
                    return map;
                }

                if (tok == JSONToken.RBRACE) {
                    lexer.nextToken();
                    return map;
                }
            }
        } finally {
            parser.setContext(context);
        }

    }

    private static boolean extracted30(final int tok) {
        return tok == JSONToken.EOF || tok == JSONToken.RBRACKET;
    }

    private static void extracted29(DefaultJSONParser parser, ParseContext context, int i) {
        if (i != 0) {
            parser.setContext(context);
        }
    }

    private static Object extracted28(DefaultJSONParser parser, Type valueType, JSONLexer lexer, String key) {
        Object value;
        if (lexer.token() == JSONToken.NULL) {
            value = null;
            lexer.nextToken();
        } else {
            value = parser.parseObject(valueType, key);
        }
        return value;
    }
    private static char extracted24(JSONLexer lexer, char ch) {
        if (ch == '"') {
            lexer.skipWhitespace();
            ch = lexer.getCurrent();
            extracted19(lexer, ch);
        }
        return ch;
    }

    private static String extracted23(DefaultJSONParser parser, JSONLexer lexer, char ch) {
        String key;
        if (ch == '\'') {
            extracted22(lexer);

            key = lexer.scanSymbol(parser.getSymbolTable(), '\'');
            lexer.skipWhitespace();
            ch = lexer.getCurrent();
            extracted19(lexer, ch);
        } else {
            extracted21(lexer);

            key = lexer.scanSymbolUnQuoted(parser.getSymbolTable());
            lexer.skipWhitespace();
            ch = lexer.getCurrent();
            extracted20(lexer, ch);
        }
        return key;
    }

    private static void extracted22(JSONLexer lexer) {
        if (!lexer.isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
            throw new JSONException("syntax error");
        }
    }

    private static void extracted21(JSONLexer lexer) {
        if (!lexer.isEnabled(Feature.ALLOW_UN_QUOTED_FIELD_NAMES)) {
            throw new JSONException("syntax error");
        }
    }

    private static void extracted20(JSONLexer lexer, char ch) {
        if (ch != ':') {
            throw new JSONException(EXPECT_DOTS_AT + lexer.pos() + ", actual " + ch);
        }
    }

    private static void extracted19(JSONLexer lexer, char ch) {
        if (ch != ':') {
            throw new JSONException(EXPECT_DOTS_AT + lexer.pos());
        }
    }

    private static char extracted18(JSONLexer lexer, char ch) {
        if (lexer.isEnabled(Feature.ALLOW_ARBITRARY_COMMAS)) {
            ch = extracted17(lexer, ch);
        }
        return ch;
    }

    private static char extracted17(JSONLexer lexer, char ch) {
        while (ch == ',') {
            lexer.next();
            lexer.skipWhitespace();
            ch = lexer.getCurrent();
        }
        return ch;
    }

    private static void extracted16(Object fieldName, JSONLexer lexer, int token) {
        if (token != JSONToken.LBRACE) {

            String msg = "syntax error, expect {, actual " + lexer.tokenName();
            msg = extracted15(fieldName, msg);
            msg += ", ";
            msg += lexer.info();

            throw new JSONException(msg);
        }
    }

    private static String extracted15(Object fieldName, String msg) {
        if (fieldName instanceof String) {
            msg += ", fieldName ";
            msg += fieldName;
        }
        return msg;
    }
    
    public static Object parseMap(DefaultJSONParser parser, Map<Object, Object> map, Type keyType, Type valueType) {
        JSONLexer lexer = parser.lexer;

        extracted(lexer);

        ObjectDeserializer keyDeserializer = parser.getConfig().getDeserializer(keyType);
        ObjectDeserializer valueDeserializer = parser.getConfig().getDeserializer(valueType);
        lexer.nextToken(keyDeserializer.getFastMatchToken());

        ParseContext context = parser.getContext();
        
        String keyStrValue = lexer.stringVal();

        try (DefaultJSONParser keyParser = new DefaultJSONParser(keyStrValue, parser.getConfig(), parser.getLexer().getFeatures())) {
            for (;;) {
                if (lexer.token() == JSONToken.RBRACE) {
                    lexer.nextToken(JSONToken.COMMA);
                    break;
                }

                if (extracted2(lexer)) {
                    Object object = null;

                    lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);
                    object = extracted5(parser, lexer, context, object);

                    lexer.nextToken(JSONToken.RBRACE);
                    if (lexer.token() != JSONToken.RBRACE) {
                        throw new JSONException("illegal ref");
                    }
                    lexer.nextToken(JSONToken.COMMA);

                    

                    return object;
                }

                if (extracted6(map, lexer)) {
                    lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);
                    lexer.nextToken(JSONToken.COMMA);
                    if (lexer.token() == JSONToken.RBRACE) {
                        lexer.nextToken();
                        return map;
                    }
                    lexer.nextToken(keyDeserializer.getFastMatchToken());
                }

                Object key;
                key = extracted8(parser, keyType, lexer, keyDeserializer, keyParser);

                extracted9(lexer);

                lexer.nextToken(valueDeserializer.getFastMatchToken());

                Object value = valueDeserializer.deserialze(parser, valueType, key);
                parser.checkMapResolve(map, key);

                map.put(key, value);

                extracted10(lexer, keyDeserializer);
            }
        } finally {
            parser.setContext(context);
        }

        return map;
    }

    private static void extracted10(JSONLexer lexer, ObjectDeserializer keyDeserializer) {
        if (lexer.token() == JSONToken.COMMA) {
            lexer.nextToken(keyDeserializer.getFastMatchToken());
        }
    }

    private static void extracted9(JSONLexer lexer) {
        if (lexer.token() != JSONToken.COLON) {
            throw new JSONException("syntax error, expect :, actual " + lexer.token());
        }
    }

    private static Object extracted8(DefaultJSONParser parser, Type keyType, JSONLexer lexer,
            ObjectDeserializer keyDeserializer, DefaultJSONParser keyParser) {
        Object key;
        if (extracted7(lexer, keyDeserializer)) {
            lexer.nextToken();
            keyParser.setDateFormat(parser.getDateFomartPattern());
            key = keyDeserializer.deserialze(keyParser, keyType, null);
        } else {
            key = keyDeserializer.deserialze(parser, keyType, null);
        }
        return key;
    }

    private static boolean extracted7(JSONLexer lexer, ObjectDeserializer keyDeserializer) {
        return lexer.token() == JSONToken.LITERAL_STRING
                && keyDeserializer instanceof JavaBeanDeserializer;
    }

    private static boolean extracted6(Map<Object, Object> map, JSONLexer lexer) {
        return map.size() == 0 //
            && lexer.token() == JSONToken.LITERAL_STRING //
            && JSON.DEFAULT_TYPE_KEY.equals(lexer.stringVal()) //
            && !lexer.isEnabled(Feature.DISABLE_SPECIAL_KEY_DETECT);
    }

    private static Object extracted5(DefaultJSONParser parser, JSONLexer lexer, ParseContext context, Object object) {
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            String ref = lexer.stringVal();
            object = extracted4(parser, context, object, ref);
        } else {
            throw new JSONException("illegal ref, " + JSONToken.name(lexer.token()));
        }
        return object;
    }

    private static Object extracted4(DefaultJSONParser parser, ParseContext context, Object object, String ref) {
        if ("..".equals(ref)) {
            ParseContext parentContext = context.parent;
            object = parentContext.object;
        } else if ("$".equals(ref)) {
            ParseContext rootContext = context;
            rootContext = extracted3(rootContext);

            object = rootContext.object;
        } else {
            parser.addResolveTask(new ResolveTask(context, ref));
            parser.setResolveStatus(DefaultJSONParser.NEED_TO_RESOLVE);
        }
        return object;
    }

    private static ParseContext extracted3(ParseContext rootContext) {
        while (rootContext.parent != null) {
            rootContext = rootContext.parent;
        }
        return rootContext;
    }

    private static boolean extracted2(JSONLexer lexer) {
        return lexer.token() == JSONToken.LITERAL_STRING //
            && lexer.isRef() //
            && !lexer.isEnabled(Feature.DISABLE_SPECIAL_KEY_DETECT) //
;
    }

    private static void extracted(JSONLexer lexer) {
        if (lexer.token() != JSONToken.LBRACE && lexer.token() != JSONToken.COMMA) {
            throw new JSONException("syntax error, expect {, actual " + lexer.tokenName());
        }
    }

    public Map<Object, Object> createMap(Type type) {
        return createMap(type, JSON.DEFAULT_GENERATE_FEATURE);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<Object, Object> createMap(Type type, int featrues) {
        if (type == Properties.class) {
            return new Properties();
        }

        if (type == Hashtable.class) {
            return new Hashtable();
        }

        if (type == IdentityHashMap.class) {
            return new IdentityHashMap();
        }

        if (extracted12(type)) {
            return new TreeMap();
        }

        if (extracted11(type)) {
            return new ConcurrentHashMap();
        }
        
        if (type == Map.class) {
            return (featrues & Feature.ORDERED_FIELD.mask) != 0
                    ? new LinkedHashMap()
                    : new HashMap();
        }

        if (type == HashMap.class) {
            return new HashMap();
        }

        if (type == LinkedHashMap.class) {
            return new LinkedHashMap();
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;

            Type rawType = parameterizedType.getRawType();
            if (EnumMap.class.equals(rawType)) {
                Type[] actualArgs = parameterizedType.getActualTypeArguments();
                return new EnumMap((Class) actualArgs[0]);
            }

            return createMap(rawType, featrues);
        }

        Class<?> clazz = (Class<?>) type;
        if (clazz.isInterface()) {
            throw new JSONException("unsupport type " + type);
        }

        if (clazz.isInstance("java.util.Collections$UnmodifiableMap")) {
            return new HashMap();
        }
        
        return extracted13(type, clazz);
    }

    private Map<Object, Object> extracted13(Type type, Class<?> clazz) {
        try {
            return (Map<Object, Object>) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new JSONException("unsupport type " + type, e);
        }
    }

    private boolean extracted12(Type type) {
        return type == SortedMap.class || type == TreeMap.class;
    }

    private boolean extracted11(Type type) {
        return type == ConcurrentMap.class || type == ConcurrentHashMap.class;
    }
    

    public int getFastMatchToken() {
        return JSONToken.LBRACE;
    }
}
