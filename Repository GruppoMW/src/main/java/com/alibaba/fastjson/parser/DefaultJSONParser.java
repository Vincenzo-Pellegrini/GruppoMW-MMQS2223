/*
 * Copyright 1999-2019 Alibaba Group.
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

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.parser.deserializer.*;
import com.alibaba.fastjson.serializer.*;
import com.alibaba.fastjson.util.TypeUtils;

import java.io.Closeable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.alibaba.fastjson.parser.JSONLexer.EOI;
import static com.alibaba.fastjson.parser.JSONToken.*;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class DefaultJSONParser implements Closeable {

    public final Object                input;
    public final SymbolTable           symbolTable;
    protected ParserConfig             config;

    private static final Set<Class<?>> primitiveClasses   = new HashSet<>();

    private String                     dateFormatPattern  = JSON.DEFAULT_DATE_FORMAT;
    private DateFormat                 dateFormat;

    public final JSONLexer             lexer;

    protected ParseContext             context;

    private ParseContext[]             contextArray;
    private int                        contextArrayIndex  = 0;

    private List<ResolveTask>          resolveTaskList;

    public static final int            NONE               = 0;
    public static final int            NEED_TO_RESOLVE      = 1;
    public static final int            TYPE_NAME_REDIRECT   = 2;
    public static final String         SYNTAX_ERROR       = "syntax error, ";
    public static final String         ACTUAL       = ", actual ";
    public static final String         SYNTAX_ERROR_NOT_COMMA       = "syntax error";
    public static final String         NAME       = ", name ";
    public static final String         EXPECT_AT       = "expect ':' at ";
    public static final String         SYNTAX_ERROR_EXPECT       = "syntax error, expect {, actual ";

    public int                         resolveStatus      = NONE;

    private List<ExtraTypeProvider>    extraTypeProviders = null;
    private List<ExtraProcessor>       extraProcessors    = null;
    protected FieldTypeResolver        fieldTypeResolver  = null;

    private int                        objectKeyLevel     = 0;

    boolean                    autoTypeEnable;
    String[]                   autoTypeAccept     = null;

    protected BeanContext    lastBeanContext;

    static {
        Class<?>[] classes = new Class[] {
                boolean.class,
                byte.class,
                short.class,
                int.class,
                long.class,
                float.class,
                double.class,

                Boolean.class,
                Byte.class,
                Short.class,
                Integer.class,
                Long.class,
                Float.class,
                Double.class,

                BigInteger.class,
                BigDecimal.class,
                String.class
        };

        primitiveClasses.addAll(Arrays.asList(classes));
    }

    public String getDateFomartPattern() {
        return dateFormatPattern;
    }

    public DateFormat getDateFormat() {
        if (dateFormat == null) {
            dateFormat = new SimpleDateFormat(dateFormatPattern, lexer.getLocale());
            dateFormat.setTimeZone(lexer.getTimeZone());
        }
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormatPattern = dateFormat;
        this.dateFormat = null;
    }

    public void setDateFomrat(DateFormat dateFormat) {
        this.setDateFormat(dateFormat);
    }

    public void setDateFormat(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
    }

    public DefaultJSONParser(String input){
        this(input, ParserConfig.getGlobalInstance(), JSON.DEFAULT_PARSER_FEATURE);
    }

    public DefaultJSONParser(final String input, final ParserConfig config){
        this(input, new JSONScanner(input, JSON.DEFAULT_PARSER_FEATURE), config);
    }

    public DefaultJSONParser(final String input, final ParserConfig config, int features){
        this(input, new JSONScanner(input, features), config);
    }

    public DefaultJSONParser(final char[] input, int length, final ParserConfig config, int features){
        this(input, new JSONScanner(input, length, features), config);
    }

    public DefaultJSONParser(final JSONLexer lexer){
        this(lexer, ParserConfig.getGlobalInstance());
    }

    public DefaultJSONParser(final JSONLexer lexer, final ParserConfig config){
        this(null, lexer, config);
    }

    public DefaultJSONParser(final Object input, final JSONLexer lexer, final ParserConfig config){
        this.lexer = lexer;
        this.input = input;
        this.config = config;
        this.symbolTable = config.symbolTable;

        int ch = lexer.getCurrent();
        if (ch == '{') {
            lexer.next();
            ((JSONLexerBase) lexer).token = JSONToken.LBRACE;
        } else if (ch == '[') {
            lexer.next();
            ((JSONLexerBase) lexer).token = JSONToken.LBRACKET;
        } else {
            lexer.nextToken(); // prime the pump
        }
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public String getInput() {
        if (input instanceof char[]) {
            return new String((char[]) input);
        }
        return input.toString();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public final Object parseObject(final Map object, Object fieldName) {
        final JSONLexer lexer2 = this.lexer;

        if (lexer2.token() == JSONToken.NULL) {
            lexer2.nextToken();
            return null;
        }

        if (lexer2.token() == JSONToken.RBRACE) {
            lexer2.nextToken();
            return object;
        }

        if (lexer2.token() == JSONToken.LITERAL_STRING && lexer2.stringVal().length() == 0) {
            lexer2.nextToken();
            return object;
        }

        if (lexer2.token() != JSONToken.LBRACE && lexer2.token() != JSONToken.COMMA) {
            throw new JSONException(SYNTAX_ERROR_EXPECT + lexer2.tokenName() + ", " + lexer2.info());
        }

        ParseContext context2 = this.context;
        String strValue = lexer2.stringVal();
        try(JSONScanner iso8601Lexer = new JSONScanner(strValue)) {
            boolean isJsonObjectMap = object instanceof JSONObject;
            Map map = isJsonObjectMap ? ((JSONObject) object).getInnerMap() : object;

            boolean setContextFlag = false;
            for (;;) {
                lexer2.skipWhitespace();
                char ch = lexer2.getCurrent();
                if (lexer2.isEnabled(Feature.ALLOW_ARBITRARY_COMMAS)) {
                    while (ch == ',') {
                        lexer2.next();
                        lexer2.skipWhitespace();
                        ch = lexer2.getCurrent();
                    }
                }

                boolean isObjectKey = false;
                Object key;
                if (ch == '"') {
                    key = lexer2.scanSymbol(symbolTable, '"');
                    lexer2.skipWhitespace();
                    ch = lexer2.getCurrent();
                    if (ch != ':') {
                        throw new JSONException(EXPECT_AT + lexer2.pos() + NAME + key);
                    }
                } else if (ch == '}') {
                    lexer2.next();
                    lexer2.resetStringPosition();
                    lexer2.nextToken();

                    if (!setContextFlag) {
                        if (this.context != null && fieldName == this.context.fieldName && object == this.context.object) {
                            context2 = this.context;
                        } else {
                            ParseContext contextR = setContext(object, fieldName);
                            if (context2 == null) {
                                context2 = contextR;
                            }
                            setContextFlag = true;
                        }
                    }

                    return object;
                } else if (ch == '\'') {
                    if (!lexer2.isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
                        throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
                    }

                    key = lexer2.scanSymbol(symbolTable, '\'');
                    lexer2.skipWhitespace();
                    ch = lexer2.getCurrent();
                    if (ch != ':') {
                        throw new JSONException(EXPECT_AT + lexer2.pos());
                    }
                } else if (ch == EOI) {
                    throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
                } else if (ch == ',') {
                    throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
                } else if ((ch >= '0' && ch <= '9') || ch == '-') {
                    lexer2.resetStringPosition();
                    lexer2.scanNumber();
                    try {
                        if (lexer2.token() == JSONToken.LITERAL_INT) {
                            key = lexer2.integerValue();
                        } else {
                            key = lexer2.decimalValue(true);
                        }
                        if (lexer2.isEnabled(Feature.NON_STRING_KEY_AS_STRING) || isJsonObjectMap) {
                            key = key.toString();
                        }
                    } catch (NumberFormatException e) {
                        throw new JSONException("parse number key error" + lexer2.info());
                    }
                    ch = lexer2.getCurrent();
                    if (ch != ':') {
                        throw new JSONException("parse number key error" + lexer2.info());
                    }
                } else if (ch == '{' || ch == '[') {
                    if (objectKeyLevel++ > 512) {
                        throw new JSONException("object key level > 512");
                    }
                    lexer2.nextToken();
                    key = parse();
                    isObjectKey = true;
                } else {
                    if (!lexer2.isEnabled(Feature.ALLOW_UN_QUOTED_FIELD_NAMES)) {
                        throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
                    }

                    key = lexer2.scanSymbolUnQuoted(symbolTable);
                    lexer2.skipWhitespace();
                    ch = lexer2.getCurrent();
                    if (ch != ':') {
                        throw new JSONException(EXPECT_AT + lexer2.pos() + ACTUAL + ch);
                    }
                }

                if (!isObjectKey) {
                    lexer2.next();
                    lexer2.skipWhitespace();
                }

                ch = lexer2.getCurrent();

                lexer2.resetStringPosition();

                if (key == JSON.DEFAULT_TYPE_KEY
                        && !lexer2.isEnabled(Feature.DISABLE_SPECIAL_KEY_DETECT)) {
                    String typeName = lexer2.scanSymbol(symbolTable, '"');

                    if (lexer2.isEnabled(Feature.IGNORE_AUTO_TYPE)) {
                        continue;
                    }

                    Class<?> clazz = null;
                    if (object != null
                            && object.getClass().getName().equals(typeName)) {
                        clazz = object.getClass();
                    } else if ("java.util.HashMap".equals(typeName)) {
                        clazz = java.util.HashMap.class;
                    } else if ("java.util.LinkedHashMap".equals(typeName)) {
                        clazz = java.util.LinkedHashMap.class;
                    } else {

                        boolean allDigits = true;
                        for (int i = 0; i < typeName.length(); ++i) {
                            char c = typeName.charAt(i);
                            if (c < '0' || c > '9') {
                                allDigits = false;
                                break;
                            }
                        }

                        if (!allDigits) {
                            clazz = config.checkAutoType(typeName, null, lexer2.getFeatures());
                        }
                    }

                    if (clazz == null) {
                        map.put(JSON.DEFAULT_TYPE_KEY, typeName);
                        continue;
                    }

                    lexer2.nextToken(JSONToken.COMMA);
                    if (lexer2.token() == JSONToken.RBRACE) {
                        lexer2.nextToken(JSONToken.COMMA);
                        try {
                            Object instance = null;
                            ObjectDeserializer deserializer = this.config.getDeserializer(clazz);
                            if (deserializer instanceof JavaBeanDeserializer) {
                                instance = TypeUtils.cast(object, clazz, this.config);
                            }

                            if (instance == null) {
                                if (clazz == Cloneable.class) {
                                    instance = new HashMap();
                                } else if ("java.util.Collections$EmptyMap".equals(typeName)) {
                                    instance = Collections.emptyMap();
                                } else if ("java.util.Collections$UnmodifiableMap".equals(typeName)) {
                                    instance = Collections.unmodifiableMap(new HashMap());
                                }
                            }

                            return instance;
                        } catch (Exception e) {
                            throw new JSONException("create instance error", e);
                        }
                    }

                    this.setResolveStatus(TYPE_NAME_REDIRECT);

                    if (this.context != null
                            && fieldName != null
                            && !(fieldName instanceof Integer)
                            && !(this.context.fieldName instanceof Integer)) {
                        this.popContext();
                    }

                    if (object.size() > 0) {
                        Object newObj = TypeUtils.cast(object, clazz, this.config);
                        this.setResolveStatus(NONE);
                        this.parseObject(newObj);
                        return newObj;
                    }

                    ObjectDeserializer deserializer = config.getDeserializer(clazz);
                    Class deserClass = deserializer.getClass();
                    if (JavaBeanDeserializer.class.isAssignableFrom(deserClass)
                            && deserClass != JavaBeanDeserializer.class
                            && deserClass != ThrowableDeserializer.class) {
                        this.setResolveStatus(NONE);
                    }
                    return deserializer.deserialze(this, clazz, fieldName);
                }

                if (key == "$ref"
                        && context2 != null
                        && (object == null || object.size() == 0)
                        && !lexer2.isEnabled(Feature.DISABLE_SPECIAL_KEY_DETECT)) {
                    lexer2.nextToken(JSONToken.LITERAL_STRING);
                    if (lexer2.token() == JSONToken.LITERAL_STRING) {
                        String ref = lexer2.stringVal();
                        lexer2.nextToken(JSONToken.RBRACE);

                        if (lexer2.token() == JSONToken.COMMA) {
                            map.put(key, ref);
                            continue;
                        }

                        Object refValue = null;
                        if ("@".equals(ref)) {
                            if (this.context != null) {
                                ParseContext thisContext = this.context;
                                Object thisObj = thisContext.object;
                                if (thisObj instanceof Object[] || thisObj instanceof Collection<?>) {
                                    refValue = thisObj;
                                } else if (thisContext.parent != null) {
                                    refValue = thisContext.parent.object;
                                }
                            }
                        } else if ("..".equals(ref)) {
                            if (context2.object != null) {
                                refValue = context2.object;
                            } else {
                                addResolveTask(new ResolveTask(context2, ref));
                                setResolveStatus(DefaultJSONParser.NEED_TO_RESOLVE);
                            }
                        } else if ("$".equals(ref)) {
                            ParseContext rootContext = context2;
                            while (rootContext.parent != null) {
                                rootContext = rootContext.parent;
                            }

                            if (rootContext.object != null) {
                                refValue = rootContext.object;
                            } else {
                                addResolveTask(new ResolveTask(rootContext, ref));
                                setResolveStatus(DefaultJSONParser.NEED_TO_RESOLVE);
                            }
                        } else {
                            JSONPath jsonpath = JSONPath.compile(ref);
                            if (jsonpath.isRef()) {
                                addResolveTask(new ResolveTask(context2, ref));
                                setResolveStatus(DefaultJSONParser.NEED_TO_RESOLVE);
                            } else {
                                refValue = new JSONObject()
                                        .fluentPut("$ref", ref);
                            }
                        }

                        if (lexer2.token() != JSONToken.RBRACE) {
                            throw new JSONException(SYNTAX_ERROR + lexer2.info());
                        }
                        lexer2.nextToken(JSONToken.COMMA);

                        return refValue;
                    } else {
                        throw new JSONException("illegal ref, " + JSONToken.name(lexer2.token()));
                    }
                }

                if (!setContextFlag) {
                    if (this.context != null && fieldName == this.context.fieldName && object == this.context.object) {
                        context2 = this.context;
                    } else {
                        ParseContext contextR = setContext(object, fieldName);
                        if (context2 == null) {
                            context2 = contextR;
                        }
                        setContextFlag = true;
                    }
                }

                if (object.getClass() == JSONObject.class && key == null) {
                    key = "null";
                }

                Object value;
                if (ch == '"') {
                    lexer2.scanString();
                    value = strValue;

                    if (lexer2.isEnabled(Feature.ALLOW_ISO8601_DATE_FORMAT) && iso8601Lexer.scanISO8601DateIfMatch()) {

                        value = iso8601Lexer.getCalendar().getTime();

                        iso8601Lexer.close();
                    }

                    map.put(key, value);
                } else if (ch >= '0' && ch <= '9' || ch == '-') {
                    lexer2.scanNumber();
                    if (lexer2.token() == JSONToken.LITERAL_INT) {
                        value = lexer2.integerValue();
                    } else {
                        value = lexer2.decimalValue(lexer2.isEnabled(Feature.USE_BIG_DECIMAL));
                    }

                    map.put(key, value);
                } else if (ch == '[') { // 减少嵌套，兼容android
                    lexer2.nextToken();

                    JSONArray list = new JSONArray();

                    if (fieldName == null) {
                        this.setContext(context2);
                    }

                    this.parseArray(list, key);

                    if (lexer2.isEnabled(Feature.USE_OBJECT_ARRAY)) {
                        value = list.toArray();
                    } else {
                        value = list;
                    }
                    map.put(key, value);

                    if (lexer2.token() == JSONToken.RBRACE) {
                        lexer2.nextToken();
                        return object;
                    } else if (lexer2.token() == JSONToken.COMMA) {
                        continue;
                    } else {
                        throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
                    }
                } else if (ch == '{') { // 减少嵌套，兼容 Android
                    lexer2.nextToken();

                    final boolean parentIsArray = fieldName != null && fieldName.getClass() == Integer.class;

                    Map mapInput;
                    if (lexer2.isEnabled(Feature.CUSTOM_MAP_DESERIALIZER)) {
                        MapDeserializer mapDeserializer = (MapDeserializer) config.getDeserializer(Map.class);


                        mapInput = (lexer2.getFeatures() & Feature.ORDERED_FIELD.mask) != 0
                                ? mapDeserializer.createMap(Map.class, lexer2.getFeatures())
                                : mapDeserializer.createMap(Map.class);
                    } else {
                        mapInput = new JSONObject(lexer2.isEnabled(Feature.ORDERED_FIELD));
                    }
                    ParseContext ctxLocal = null;

                    if (!parentIsArray) {
                        ctxLocal = setContext(this.context, mapInput, key);
                    }

                    Object obj = null;
                    boolean objParsed = false;
                    if (fieldTypeResolver != null) {
                        String resolveFieldName = key != null ? key.toString() : null;
                        Type fieldType = fieldTypeResolver.resolve(object, resolveFieldName);
                        if (fieldType != null) {
                            ObjectDeserializer fieldDeser = config.getDeserializer(fieldType);
                            obj = fieldDeser.deserialze(this, fieldType, key);
                            objParsed = true;
                        }
                    }
                    if (!objParsed) {
                        obj = this.parseObject(mapInput, key);
                    }

                    if (ctxLocal != null && mapInput != obj) {
                        ctxLocal.object = object;
                    }

                    if (key != null) {
                        checkMapResolve(object, key.toString());
                    }

                    map.put(key, obj);

                    if (parentIsArray) {
                        setContext(obj, key);
                    }

                    if (lexer2.token() == JSONToken.RBRACE) {
                        lexer2.nextToken();

                        setContext(context2);
                        return object;
                    } else if (lexer2.token() == JSONToken.COMMA) {
                        if (parentIsArray) {
                            this.popContext();
                        } else {
                            this.setContext(context2);
                        }
                        continue;
                    } else {
                        throw new JSONException(SYNTAX_ERROR + lexer2.tokenName());
                    }
                } else {
                    lexer2.nextToken();
                    value = parse();

                    map.put(key, value);

                    if (lexer2.token() == JSONToken.RBRACE) {
                        lexer2.nextToken();
                        return object;
                    } else if (lexer2.token() == JSONToken.COMMA) {
                        continue;
                    } else {
                        throw new JSONException("syntax error, position at " + lexer2.pos() + NAME + key);
                    }
                }

                lexer2.skipWhitespace();
                ch = lexer2.getCurrent();
                if (ch == ',') {
                    lexer2.next();

                } else if (ch == '}') {
                    lexer2.next();
                    lexer2.resetStringPosition();
                    lexer2.nextToken();

                    this.setContext(value, key);

                    return object;
                } else {
                    throw new JSONException("syntax error, position at " + lexer2.pos() + NAME + key);
                }

            }
        } finally {
            this.setContext(context2);
        }

    }

    public ParserConfig getConfig() {
        return config;
    }

    public void setConfig(ParserConfig config) {
        this.config = config;
    }

    // compatible
    @SuppressWarnings("unchecked")
    public <T> T parseObject(Class<T> clazz) {
        return (T) parseObject(clazz, null);
    }

    public <T> T parseObject(Type type) {
        return parseObject(type, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T parseObject(Type type, Object fieldName) {
        int token = lexer.token();
        if (token == JSONToken.NULL) {
            lexer.nextToken();

            return (T) TypeUtils.optionalEmpty(type);
        }

        if (token == JSONToken.LITERAL_STRING) {
            if (type == byte[].class) {
                byte[] bytes = lexer.bytesValue();
                lexer.nextToken();
                return (T) bytes;
            }

            if (type == char[].class) {
                String strVal = lexer.stringVal();
                lexer.nextToken();
                return (T) strVal.toCharArray();
            }
        }

        ObjectDeserializer deserializer = config.getDeserializer(type);

        try {
            if (deserializer.getClass() == JavaBeanDeserializer.class) {
                if (lexer.token()!= JSONToken.LBRACE && lexer.token()!=JSONToken.LBRACKET) {
                    throw new JSONException("syntax error,expect start with { or [,but actually start with "+ lexer.tokenName());
                }
                return ((JavaBeanDeserializer) deserializer).deserialze(this, type, fieldName, 0);
            } else {
                return deserializer.deserialze(this, type, fieldName);
            }
        } catch (JSONException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }

    public <T> List<T> parseArray(Class<T> clazz) {
        List<T> array = new ArrayList<>();
        parseArray(clazz, array);
        return array;
    }

    public void parseArray(Class<?> clazz, @SuppressWarnings("rawtypes") Collection array) {
        parseArray((Type) clazz, array);
    }

    @SuppressWarnings("rawtypes")
    public void parseArray(Type type, Collection array) {
        parseArray(type, array, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void parseArray(Type type, Collection array, Object fieldName) {
        int token = lexer.token();
        if (token == JSONToken.SET || token == JSONToken.TREE_SET) {
            lexer.nextToken();
            token = lexer.token();
        }

        if (token != JSONToken.LBRACKET) {
            throw new JSONException("field " + fieldName + " expect '[', but " + JSONToken.name(token) + ", " + lexer.info());
        }

        ObjectDeserializer deserializer = null;
        if (int.class == type) {
            deserializer = IntegerCodec.instance;
            lexer.nextToken(JSONToken.LITERAL_INT);
        } else if (String.class == type) {
            deserializer = StringCodec.instance;
            lexer.nextToken(JSONToken.LITERAL_STRING);
        } else {
            deserializer = config.getDeserializer(type);
            lexer.nextToken(deserializer.getFastMatchToken());
        }

        ParseContext context2 = this.context;
        this.setContext(array, fieldName);
        try {
            for (int i = 0;; ++i) {
                extracted43();

                if (lexer.token() == JSONToken.RBRACKET) {
                    break;
                }

                extracted47(type, array, deserializer, i);

                if (lexer.token() == JSONToken.COMMA) {
                    lexer.nextToken(deserializer.getFastMatchToken());
                }
            }
        } finally {
            this.setContext(context2);
        }

        lexer.nextToken(JSONToken.COMMA);
    }

    private void extracted47(Type type, Collection<Object> array, ObjectDeserializer deserializer, int i) {
        if (int.class == type) {
            Object val = IntegerCodec.instance.deserialze(this, null, null);
            array.add(val);
        } else if (String.class == type) {
            String value;
            value = extracted45();

            array.add(value);
        } else {
            Object val;
            val = extracted46(type, deserializer, i);
            array.add(val);
            checkListResolve(array);
        }
    }

    private Object extracted46(Type type, ObjectDeserializer deserializer, int i) {
        Object val;
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken();
            val = null;
        } else {
            val = deserializer.deserialze(this, type, i);
        }
        return val;
    }

    private String extracted45() {
        String value;
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            value = lexer.stringVal();
            lexer.nextToken(JSONToken.COMMA);
        } else {
            Object obj = this.parse();
            value = extracted44(obj);
        }
        return value;
    }

    private String extracted44(Object obj) {
        String value;
        if (obj == null) {
            value = null;
        } else {
            value = obj.toString();
        }
        return value;
    }

    private void extracted43() {
        if (lexer.isEnabled(Feature.ALLOW_ARBITRARY_COMMAS)) {
            extracted42();
        }
    }

    private void extracted42() {
        while (lexer.token() == JSONToken.COMMA) {
            lexer.nextToken();
        }
    }

    public Object[] parseArray(Type[] types) {
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
            return new Object[0];
        }


        if (lexer.token() != JSONToken.LBRACKET) {
            throw new JSONException("syntax error : " + lexer.tokenName());
        }

        Object[] list = new Object[types.length];
        if (types.length == 0) {
            lexer.nextToken(JSONToken.RBRACKET);

            if (lexer.token() != JSONToken.RBRACKET) {
                throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
            }

            lexer.nextToken(JSONToken.COMMA);
            return new Object[0];
        }

        lexer.nextToken(JSONToken.LITERAL_INT);

        for (int i = 0; i < types.length; ++i) {
            Object value;

            if (lexer.token() == JSONToken.NULL) {
                value = null;
                lexer.nextToken(JSONToken.COMMA);
            } else {
                Type type = types[i];
                value = extracted41(types, i, type);
            }
            list[i] = value;

            if (lexer.token() == JSONToken.RBRACKET) {
                break;
            }

            extracted36();

            extracted35(types, i);
        }

        if (lexer.token() != JSONToken.RBRACKET) {
            throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
        }

        lexer.nextToken(JSONToken.COMMA);

        return list;
    }

    private Object extracted41(Type[] types, int i, Type type) {
        Object value;
        if (type == int.class || type == Integer.class) {
            value = extracted40(type);
        } else if (type == String.class) {
            value = extracted39(type);
        } else {
            boolean isArray = false;
            Class<?> componentType = null;
            if (i == types.length - 1 && type instanceof Class) {
                Class<?> clazz = (Class<?>) type;
                //如果最后一个type是字节数组，且当前token为字符串类型，不应该当作可变长参数进行处理
                //而是作为一个整体的Base64字符串进行反序列化
                if (!((clazz == byte[].class || clazz == char[].class) && lexer.token() == LITERAL_STRING)) {
                    isArray = clazz.isArray();
                    componentType = clazz.getComponentType();
                }
            }

            // support varArgs
            value = extracted38(i, type, isArray, componentType);
        }
        return value;
    }

    private Object extracted40(Type type) {
        Object value;
        if (lexer.token() == JSONToken.LITERAL_INT) {
            value = Integer.valueOf(lexer.intValue());
            lexer.nextToken(JSONToken.COMMA);
        } else {
            value = this.parse();
            value = TypeUtils.cast(value, type, config);
        }
        return value;
    }

    private Object extracted39(Type type) {
        Object value;
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            value = lexer.stringVal();
            lexer.nextToken(JSONToken.COMMA);
        } else {
            value = this.parse();
            value = TypeUtils.cast(value, type, config);
        }
        return value;
    }

    private Object extracted38(int i, Type type, boolean isArray, Class<?> componentType) {
        Object value;
        if (isArray && lexer.token() != JSONToken.LBRACKET) {
            List<Object> varList = new ArrayList<>();

            ObjectDeserializer deserializer = config.getDeserializer(componentType);
            int fastMatch = deserializer.getFastMatchToken();

            extracted37(type, varList, deserializer, fastMatch);

            value = TypeUtils.cast(varList, type, config);
        } else {
            ObjectDeserializer deserializer = config.getDeserializer(type);
            value = deserializer.deserialze(this, type, i);
        }
        return value;
    }

    private void extracted37(Type type, List<Object> varList, ObjectDeserializer deserializer, int fastMatch) {
        if (lexer.token() != JSONToken.RBRACKET) {
            extracted34(type, varList, deserializer, fastMatch);
        }
    }

    private void extracted36(){
        if (lexer.token() != JSONToken.COMMA) {
            throw new JSONException("syntax error :" + JSONToken.name(lexer.token()));
        }
    }

    private void extracted35(Type[] types, int i) {
        if (i == types.length - 1) {
            lexer.nextToken(JSONToken.RBRACKET);
        } else {
            lexer.nextToken(JSONToken.LITERAL_INT);
        }
    }

    private void extracted34(Type type, List<Object> varList, ObjectDeserializer deserializer, int fastMatch)
    {
        for (;;) {
            Object item = deserializer.deserialze(this, type, null);
            varList.add(item);

            if (lexer.token() == JSONToken.COMMA) {
                lexer.nextToken(fastMatch);
            } else if (lexer.token() == JSONToken.RBRACKET) {
                break;
            } else {
                throw new JSONException("syntax error :" + JSONToken.name(lexer.token()));
            }
        }
    }

    public void parseObject(Object object) {
        Class<?> clazz = object.getClass();
        JavaBeanDeserializer beanDeser = null;
        ObjectDeserializer deserializer = config.getDeserializer(clazz);
        if (deserializer instanceof JavaBeanDeserializer) {
            beanDeser = (JavaBeanDeserializer) deserializer;
        }

        if (lexer.token() != JSONToken.LBRACE && lexer.token() != JSONToken.COMMA) {
            throw new JSONException(SYNTAX_ERROR_EXPECT + lexer.tokenName());
        }

        for (;;) {
            // lexer.scanSymbol
            String key = lexer.scanSymbol(symbolTable);





            FieldDeserializer fieldDeser = null;
            fieldDeser = extracted33(beanDeser, key, fieldDeser);

            if (fieldDeser == null) {
                extracted32(clazz, key);

                lexer.nextTokenWithColon();
                parse(); // skip

                if (lexer.token() == JSONToken.RBRACE) {
                    lexer.nextToken();
                    return;
                }

            } else {
                Class<?> fieldClass = fieldDeser.fieldInfo.fieldClass;
                Type fieldType = fieldDeser.fieldInfo.fieldType;
                Object fieldValue;
                fieldValue = extracted31(fieldClass, fieldType);

                fieldDeser.setValue(object, fieldValue);
            }

            if (lexer.token() == JSONToken.RBRACE) {
                lexer.nextToken(JSONToken.COMMA);
                return;
            }
        }
    }

    private FieldDeserializer extracted33(JavaBeanDeserializer beanDeser, String key, FieldDeserializer fieldDeser) {
        if (beanDeser != null) {
            fieldDeser = beanDeser.getFieldDeserializer(key);
        }
        return fieldDeser;
    }

    private void extracted32(Class<?> clazz, String key){
        if (!lexer.isEnabled(Feature.IGNORE_NOT_MATCH)) {
            throw new JSONException("setter not found, class " + clazz.getName() + ", property " + key);
        }
    }

    private Object extracted31(Class<?> fieldClass, Type fieldType) {
        Object fieldValue;
        if (fieldClass == int.class) {
            lexer.nextTokenWithColon(JSONToken.LITERAL_INT);
            fieldValue = IntegerCodec.instance.deserialze(this, fieldType, null);
        } else if (fieldClass == String.class) {
            lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);
            fieldValue = StringCodec.deserialze(this);
        } else if (fieldClass == long.class) {
            lexer.nextTokenWithColon(JSONToken.LITERAL_INT);
            fieldValue = LongCodec.instance.deserialze(this, fieldType, null);
        } else {
            ObjectDeserializer fieldValueDeserializer = config.getDeserializer(fieldClass, fieldType);

            lexer.nextTokenWithColon(fieldValueDeserializer.getFastMatchToken());
            fieldValue = fieldValueDeserializer.deserialze(this, fieldType, null);
        }
        return fieldValue;
    }

    public Object parseArrayWithType(Type collectionType) {
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken();
            return null;
        }

        Type[] actualTypes = ((ParameterizedType) collectionType).getActualTypeArguments();

        if (actualTypes.length != 1) {
            throw new JSONException("not support type " + collectionType);
        }

        Type actualTypeArgument = actualTypes[0];

        if (actualTypeArgument instanceof Class) {
            List<Object> array = new ArrayList<>();
            this.parseArray((Class<?>) actualTypeArgument, array);
            return array;
        }

        if (actualTypeArgument instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) actualTypeArgument;

            Type upperBoundType = wildcardType.getUpperBounds()[0];

            if (Object.class.equals(upperBoundType)) {
                return extracted(collectionType, wildcardType);
            }

            List<Object> array = new ArrayList<>();
            this.parseArray((Class<?>) upperBoundType, array);
            return array;

        }

        if (actualTypeArgument instanceof TypeVariable) {
            TypeVariable<?> typeVariable = (TypeVariable<?>) actualTypeArgument;
            Type[] bounds = typeVariable.getBounds();

            if (bounds.length != 1) {
                throw new JSONException("not support : " + typeVariable);
            }

            Type boundType = bounds[0];
            if (boundType instanceof Class) {
                List<Object> array = new ArrayList<>();
                this.parseArray((Class<?>) boundType, array);
                return array;
            }
        }

        if (actualTypeArgument instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) actualTypeArgument;

            List<Object> array = new ArrayList<>();
            this.parseArray(parameterizedType, array);
            return array;
        }

        throw new JSONException("TODO : " + collectionType);
    }

    private Object extracted(Type collectionType, WildcardType wildcardType) throws JSONException {
        if (wildcardType.getLowerBounds().length == 0) {
            // Collection<?>
            return parse();
        } else {
            throw new JSONException("not support type : " + collectionType);
        }
    }

    public void acceptType(String typeName) {
        JSONLexer lexer10 = this.lexer;

        lexer10.nextTokenWithColon();

        if (lexer10.token() != JSONToken.LITERAL_STRING) {
            throw new JSONException("type not match error");
        }

        if (typeName.equals(lexer10.stringVal())) {
            lexer10.nextToken();
            if (lexer10.token() == JSONToken.COMMA) {
                lexer10.nextToken();
            }
        } else {
            throw new JSONException("type not match error");
        }
    }

    public int getResolveStatus() {
        return resolveStatus;
    }

    public void setResolveStatus(int resolveStatus) {
        this.resolveStatus = resolveStatus;
    }

    public Object getObject(String path) {
        for (int i = 0; i < contextArrayIndex; ++i) {
            if (path.equals(contextArray[i].toString())) {
                return contextArray[i].object;
            }
        }

        return null;
    }

    @SuppressWarnings("rawtypes")
    public void checkListResolve(Collection array) {
        if (resolveStatus == NEED_TO_RESOLVE) {
            if (array instanceof List) {
                final int index = array.size() - 1;
                final List list = (List) array;
                ResolveTask task = getLastResolveTask();
                task.fieldDeserializer = new ResolveFieldDeserializer(this, list, index);
                task.ownerContext = context;
                setResolveStatus(DefaultJSONParser.NONE);
            } else {
                ResolveTask task = getLastResolveTask();
                task.fieldDeserializer  = new ResolveFieldDeserializer(array);
                task.ownerContext = context;
                setResolveStatus(DefaultJSONParser.NONE);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public void checkMapResolve(Map object, Object fieldName) {
        if (resolveStatus == NEED_TO_RESOLVE) {
            ResolveFieldDeserializer fieldResolver = new ResolveFieldDeserializer(object, fieldName);
            ResolveTask task = getLastResolveTask();
            task.fieldDeserializer = fieldResolver;
            task.ownerContext = context;
            setResolveStatus(DefaultJSONParser.NONE);
        }
    }

    @SuppressWarnings("rawtypes")
    public Object parseObject(final Map object) {
        return parseObject(object, null);
    }

    public JSONObject parseObject() {
        JSONObject object = new JSONObject(lexer.isEnabled(Feature.ORDERED_FIELD));
        Object parsedObject = parseObject(object);

        if (parsedObject instanceof JSONObject) {
            return (JSONObject) parsedObject;
        }

        if (parsedObject == null) {
            return new JSONObject();
        }

        return new JSONObject((Map) parsedObject);
    }

    @SuppressWarnings("rawtypes")
    public final void parseArray(final Collection array) {
        parseArray(array, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public final void parseArray(final Collection array, Object fieldName) {
        final JSONLexer lexer3 = this.lexer;

        if (lexer3.token() == JSONToken.SET || lexer3.token() == JSONToken.TREE_SET) {
            lexer3.nextToken();
        }

        if (lexer3.token() != JSONToken.LBRACKET) {
            throw new JSONException("syntax error, expect [, actual " + JSONToken.name(lexer3.token()) + ", pos "
                    + lexer3.pos() + ", fieldName " + fieldName);
        }

        lexer3.nextToken(JSONToken.LITERAL_STRING);

        if (this.context != null && this.context.level > 512) {
            throw new JSONException("array level > 512");
        }

        ParseContext context4 = this.context;
        this.setContext(array, fieldName);
        String stringLiteral = lexer3.stringVal();
        try(JSONScanner iso8601Lexer = new JSONScanner(stringLiteral)) {
            for (int i = 0; ; ++i) {
                extracted25(lexer3);

                Object value;
                switch (lexer3.token()) {
                    case LITERAL_INT:
                        value = lexer3.integerValue();
                        lexer3.nextToken(JSONToken.COMMA);
                        break;
                    case LITERAL_FLOAT:
                        value = extracted29(lexer3);
                        lexer3.nextToken(JSONToken.COMMA);
                        break;
                    case LITERAL_STRING:
                        lexer3.nextToken(JSONToken.COMMA);

                        value = extracted28(lexer3, stringLiteral, iso8601Lexer);

                        break;
                    case TRUE:
                        value = Boolean.TRUE;
                        lexer3.nextToken(JSONToken.COMMA);
                        break;
                    case FALSE:
                        value = Boolean.FALSE;
                        lexer3.nextToken(JSONToken.COMMA);
                        break;
                    case LBRACE:
                        JSONObject object = new JSONObject(lexer3.isEnabled(Feature.ORDERED_FIELD));
                        value = parseObject(object, i);
                        break;
                    case LBRACKET:
                        Collection items = new JSONArray();
                        parseArray(items, i);
                        value = extracted26(lexer3, items);
                        break;
                    case NULL:
                    case UNDEFINED:
                        value = null;
                        lexer3.nextToken(JSONToken.LITERAL_STRING);
                        break;
                    case RBRACKET:
                        lexer3.nextToken(JSONToken.COMMA);
                        return;
                    case EOF:
                        throw new JSONException("unclosed jsonArray");
                    default:
                        value = parse();
                        break;
                }

                array.add(value);
                checkListResolve(array);

                extracted30(lexer3);
            }
        } catch (ClassCastException e) {
            throw new JSONException("unkown error", e);
        } finally {
            this.setContext(context4);
        }
    }

    private void extracted30(final JSONLexer lexer) {
        if (lexer.token() == JSONToken.COMMA) {
            lexer.nextToken(JSONToken.LITERAL_STRING);
            //ciao a tutti
        }
    }

    private Object extracted29(final JSONLexer lexer) {
        Object value;
        if (lexer.isEnabled(Feature.USE_BIG_DECIMAL)) {
            value = lexer.decimalValue(true);
        } else {
            value = lexer.decimalValue(false);
        }
        return value;
    }

    private Object extracted28(final JSONLexer lexer, String stringLiteral, JSONScanner iso8601Lexer) {
        Object value;
        if (lexer.isEnabled(Feature.ALLOW_ISO8601_DATE_FORMAT)) {
            value = extracted27(stringLiteral, iso8601Lexer);
        } else {
            value = stringLiteral;
        }
        return value;
    }

    private Object extracted27(String stringLiteral, JSONScanner iso8601Lexer) {
        Object value;
        if (iso8601Lexer.scanISO8601DateIfMatch()) {
            value = iso8601Lexer.getCalendar().getTime();
        } else {
            value = stringLiteral;
        }
        return value;
    }

    private Object extracted26(final JSONLexer lexer, Collection<Object> items) {
        Object value;
        if (lexer.isEnabled(Feature.USE_OBJECT_ARRAY)) {
            value = items.toArray();
        } else {
            value = items;
        }
        return value;
    }

    private void extracted25(final JSONLexer lexer) {
        if (lexer.isEnabled(Feature.ALLOW_ARBITRARY_COMMAS)) {
            extracted24(lexer);
        }
    }

    private void extracted24(final JSONLexer lexer) {
        while (lexer.token() == JSONToken.COMMA) {
            lexer.nextToken();
        }
    }

    public ParseContext getContext() {
        return context;
    }

    public ParseContext getOwnerContext() {
        return context.parent;
    }

    public List<ResolveTask> getResolveTaskList() {
        if (resolveTaskList == null) {
            resolveTaskList = new ArrayList<>(2);
        }
        return resolveTaskList;
    }

    public void addResolveTask(ResolveTask task) {
        if (resolveTaskList == null) {
            resolveTaskList = new ArrayList<>(2);
        }
        resolveTaskList.add(task);
    }

    public ResolveTask getLastResolveTask() {
        return resolveTaskList.get(resolveTaskList.size() - 1);
    }

    public List<ExtraProcessor> getExtraProcessors() {
        if (extraProcessors == null) {
            extraProcessors = new ArrayList<>(2);
        }
        return extraProcessors;
    }

    public List<ExtraTypeProvider> getExtraTypeProviders() {
        if (extraTypeProviders == null) {
            extraTypeProviders = new ArrayList<>(2);
        }
        return extraTypeProviders;
    }

    public FieldTypeResolver getFieldTypeResolver() {
        return fieldTypeResolver;
    }

    public void setFieldTypeResolver(FieldTypeResolver fieldTypeResolver) {
        this.fieldTypeResolver = fieldTypeResolver;
    }

    public void setContext(ParseContext context) {
        if (lexer.isEnabled(Feature.DISABLE_CIRCULAR_REFERENCE_DETECT)) {
            return;
        }
        this.context = context;
    }

    public void popContext() {
        if (lexer.isEnabled(Feature.DISABLE_CIRCULAR_REFERENCE_DETECT)) {
            return;
        }

        this.context = this.context.parent;

        if (contextArrayIndex <= 0) {
            return;
        }

        contextArrayIndex--;
        contextArray[contextArrayIndex] = null;
    }

    public ParseContext setContext(Object object, Object fieldName) {
        if (lexer.isEnabled(Feature.DISABLE_CIRCULAR_REFERENCE_DETECT)) {
            return null;
        }

        return setContext(this.context, object, fieldName);
    }

    public ParseContext setContext(ParseContext parent, Object object, Object fieldName) {
        if (lexer.isEnabled(Feature.DISABLE_CIRCULAR_REFERENCE_DETECT)) {
            return null;
        }

        this.context = new ParseContext(parent, object, fieldName);
        addContext(this.context);

        return this.context;
    }

    private void addContext(ParseContext context) {
        int i = contextArrayIndex++;
        if (contextArray == null) {
            contextArray = new ParseContext[8];
        } else if (i >= contextArray.length) {
            int newLen = (contextArray.length * 3) / 2;
            ParseContext[] newArray = new ParseContext[newLen];
            System.arraycopy(contextArray, 0, newArray, 0, contextArray.length);
            contextArray = newArray;
        }
        contextArray[i] = context;
    }

    public Object parse() {
        return parse(null);
    }

    public Object parseKey() {
        if (lexer.token() == JSONToken.IDENTIFIER) {
            String value = lexer.stringVal();
            lexer.nextToken(JSONToken.COMMA);
            return value;
        }
        return parse(null);
    }

    public Object parse(Object fieldName) {
        final JSONLexer lexer4 = this.lexer;
        switch (lexer4.token()) {
            case SET:
                lexer4.nextToken();
                HashSet<Object> set = new HashSet<>();
                parseArray(set, fieldName);
                return set;
            case TREE_SET:
                lexer4.nextToken();
                TreeSet<Object> treeSet = new TreeSet<>();
                parseArray(treeSet, fieldName);
                return treeSet;
            case LBRACKET:
                Collection<Object> array = extracted2();
                parseArray(array, fieldName);
                if (lexer4.isEnabled(Feature.USE_OBJECT_ARRAY)) {
                    return array.toArray();
                }
                return array;
            case LBRACE:
                Map<Object,Object> object = extracted3();
                return parseObject(object, fieldName);
            case LITERAL_INT:
                Number intValue = lexer4.integerValue();
                lexer4.nextToken();
                return intValue;
            case LITERAL_FLOAT:
                Object value = lexer4.decimalValue(lexer4.isEnabled(Feature.USE_BIG_DECIMAL));
                lexer4.nextToken();
                return value;
            case LITERAL_STRING:
                String stringLiteral = lexer4.stringVal();
                lexer4.nextToken(JSONToken.COMMA);

                if (lexer4.isEnabled(Feature.ALLOW_ISO8601_DATE_FORMAT)) {
                    try(JSONScanner iso8601Lexer = new JSONScanner(stringLiteral)) {
                        if (iso8601Lexer.scanISO8601DateIfMatch()) {
                            return iso8601Lexer.getCalendar().getTime();
                        }
                    }
                }

                return stringLiteral;
            case NULL:
            case UNDEFINED:
                lexer4.nextToken();
                return null;
            case TRUE:
                lexer4.nextToken();
                return Boolean.TRUE;
            case FALSE:
                lexer4.nextToken();
                return Boolean.FALSE;
            case NEW:
                lexer4.nextToken(JSONToken.IDENTIFIER);

                if (lexer4.token() != JSONToken.IDENTIFIER) {
                    throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
                }
                lexer4.nextToken(JSONToken.LPAREN);

                accept(JSONToken.LPAREN);
                long time = lexer4.integerValue().longValue();
                accept(JSONToken.LITERAL_INT);

                accept(JSONToken.RPAREN);

                return new Date(time);
            case EOF:
                if (lexer4.isBlankInput()) {
                    return null;
                }
                throw new JSONException("unterminated json string, " + lexer4.info());
            case HEX:
                byte[] bytes = lexer4.bytesValue();
                lexer4.nextToken();
                return bytes;
            case IDENTIFIER:
                String identifier = lexer4.stringVal();
                if ("NaN".equals(identifier)) {
                    lexer4.nextToken();
                    return null;
                }
                throw new JSONException(SYNTAX_ERROR + lexer4.info());
            case ERROR:
            default:
                throw new JSONException(SYNTAX_ERROR + lexer4.info());
        }
    }

    private Map<Object,Object> extracted3() {
        if (isEnabled(Feature.USE_NATIVE_JAVA_OBJECT)){
            return new HashMap<>();
        }
        else{
            return  new LinkedHashMap<>();
        }
    }

    private Collection<Object> extracted2() {
        if (isEnabled(Feature.USE_NATIVE_JAVA_OBJECT)) {
            return new ArrayList<>();
        } else {
            return new JSONArray();
        }
    }


    public void config(Feature feature, boolean state) {
        this.lexer.config(feature, state);
    }

    public boolean isEnabled(Feature feature) {
        return lexer.isEnabled(feature);
    }

    public JSONLexer getLexer() {
        return lexer;
    }

    public final void accept(final int token) {
        final JSONLexer lexer5 = this.lexer;
        if (lexer5.token() == token) {
            lexer5.nextToken();
        } else {
            throw new JSONException("syntax error, expect " + JSONToken.name(token) + ACTUAL
                    + JSONToken.name(lexer5.token()));
        }
    }

    public final void accept(final int token, int nextExpectToken) {
        final JSONLexer lexer6 = this.lexer;
        if (lexer6.token() == token) {
            lexer6.nextToken(nextExpectToken);
        } else {
            throwException(token);
        }
    }

    public void throwException(int token) {
        throw new JSONException("syntax error, expect " + JSONToken.name(token) + ACTUAL
                + JSONToken.name(lexer.token()));
    }

    public void close() {
        final JSONLexer lexer7 = this.lexer;

        try {
            if (lexer7.isEnabled(Feature.AUTO_CLOSE_SOURCE) && lexer7.token() != JSONToken.EOF) {
                throw new JSONException("not close json text, token : " + JSONToken.name(lexer7.token()));
            }
        } finally {
            lexer7.close();
        }
    }

    public Object resolveReference(String ref) {
        if(contextArray == null) {
            return null;
        }
        for (int i = 0; i < contextArray.length && i < contextArrayIndex; i++) {
            ParseContext context5 = contextArray[i];
            if (context5.toString().equals(ref)) {
                return context5.object;
            }
        }
        return null;
    }

    public void handleResovleTask(Object value) {
        if (resolveTaskList == null) {
            return;
        }

        for (int i = 0, size = resolveTaskList.size(); i < size; ++i) {
            ResolveTask task = resolveTaskList.get(i);
            String ref = task.referenceValue;

            Object object = null;
            object = extracted15(task, object);

            Object refValue;

            refValue = extracted19(value, task, ref);

            FieldDeserializer fieldDeser = task.fieldDeserializer;

            if (fieldDeser != null) {
                refValue = extracted21(ref, refValue, fieldDeser);

                // workaround for bug
                object = extracted23(task, object, fieldDeser);

                fieldDeser.setValue(object, refValue);
            }
        }
    }

    private Object extracted23(ResolveTask task, Object object, FieldDeserializer fieldDeser) {
        if (fieldDeser.getOwnerClass() != null
                && (!fieldDeser.getOwnerClass().isInstance(object))
                && task.ownerContext.parent != null
        ) {
            object = extracted22(task, object, fieldDeser);
        }
        return object;
    }

    private Object extracted22(ResolveTask task, Object object, FieldDeserializer fieldDeser) {
        for (ParseContext ctx = task.ownerContext.parent;ctx != null;ctx = ctx.parent) {
            if (fieldDeser.getOwnerClass().isInstance(ctx.object)) {
                object = ctx.object;
                break;
            }
        }
        return object;
    }

    private Object extracted21(String ref, Object refValue, FieldDeserializer fieldDeser) {
        if (refValue != null
                && refValue.getClass() == JSONObject.class
                && fieldDeser.fieldInfo != null
                && !Map.class.isAssignableFrom(fieldDeser.fieldInfo.fieldClass)) {
            Object root = this.contextArray[0].object;
            JSONPath jsonpath = JSONPath.compile(ref);
            refValue = extracted20(refValue, root, jsonpath);
        }
        return refValue;
    }

    private Object extracted20(Object refValue, Object root, JSONPath jsonpath) {
        if (jsonpath.isRef()) {
            refValue = jsonpath.eval(root);
        }
        return refValue;
    }

    private Object extracted19(Object value, ResolveTask task, String ref) {
        Object refValue;
        if (ref.startsWith("$")) {
            refValue = getObject(ref);
            refValue = extracted18(value, ref, refValue);
        } else {
            refValue = task.context6.object;
        }
        return refValue;
    }

    private Object extracted18(Object value, String ref, Object refValue) {
        if (refValue == null) {
            refValue = extracted17(value, ref, refValue);
        }
        return refValue;
    }

    private Object extracted17(Object value, String ref, Object refValue) {
        try {
            JSONPath jsonpath = new JSONPath(ref, SerializeConfig.getGlobalInstance(), config, true);
            refValue = extracted16(value, refValue, jsonpath);
        } catch (JSONPathException ex) {
            // skip
        }
        return refValue;
    }

    private Object extracted16(Object value, Object refValue, JSONPath jsonpath) {
        if (jsonpath.isRef()) {
            refValue = jsonpath.eval(value);
        }
        return refValue;
    }

    private Object extracted15(ResolveTask task, Object object) {
        if (task.ownerContext != null) {
            object = task.ownerContext.object;
        }
        return object;
    }

    public static class ResolveTask {

        public final ParseContext context6;
        public final String       referenceValue;
        FieldDeserializer  fieldDeserializer;
        ParseContext       ownerContext;

        public ResolveTask(ParseContext context, String referenceValue){
            this.context6 = context;
            this.referenceValue = referenceValue;
        }
    }

    public void parseExtra(Object object, String key) {
        final JSONLexer lexer8 = this.lexer; // xxx
        lexer8.nextTokenWithColon();
        Type type = null;

        if (extraTypeProviders != null) {
            for (ExtraTypeProvider extraProvider : extraTypeProviders) {
                type = extraProvider.getExtraType(object, key);
            }
        }
        Object value = type == null //
                ? parse() // skip
                : parseObject(type);

        if (object instanceof ExtraProcessable) {
            ExtraProcessable extraProcessable = ((ExtraProcessable) object);
            extraProcessable.processExtra(key, value);
            return;
        }

        if (extraProcessors != null) {
            for (ExtraProcessor process : extraProcessors) {
                process.processExtra(object, key, value);
            }
        }

        if (resolveStatus == NEED_TO_RESOLVE) {
            resolveStatus = NONE;
        }
    }

    public Object parse(PropertyProcessable object, Object fieldName) {
        if (lexer.token() != JSONToken.LBRACE) {
            String msg = SYNTAX_ERROR_EXPECT + lexer.tokenName();
            msg = extracted4(fieldName, msg);
            msg += ", ";
            msg += lexer.info();

            JSONArray array = new JSONArray();
            parseArray(array, fieldName);

            if (array.size() == 1) {
                Object first = array.get(0);
                if (first instanceof JSONObject) {
                    return first;
                }
            }

            throw new JSONException(msg);
        }

        ParseContext context7 = this.context;
        try {
            for (int i = 0;;++i) {
                lexer.skipWhitespace();
                char ch = lexer.getCurrent();
                ch = extracted6(ch);

                String key;
                switch (ch) {
                    case '"':
                        key = lexer.scanSymbol(symbolTable, '"');
                        lexer.skipWhitespace();
                        ch = lexer.getCurrent();
                        extracted7(ch);
                        break;
                    case '}':
                        lexer.next();
                        lexer.resetStringPosition();
                        lexer.nextToken(JSONToken.COMMA);
                        return object;
                    case '\'':
                        extracted8();
                        key = lexer.scanSymbol(symbolTable, '\'');
                        lexer.skipWhitespace();
                        ch = lexer.getCurrent();
                        extracted9(ch);
                        break;

                    default:
                        extracted10();

                        key = lexer.scanSymbolUnQuoted(symbolTable);
                        lexer.skipWhitespace();
                        ch = lexer.getCurrent();
                        extracted11(ch);
                        break;
                }

                lexer.next();
                lexer.skipWhitespace();
                ch = lexer.getCurrent();

                lexer.resetStringPosition();

                if (key.equals(JSON.DEFAULT_TYPE_KEY) && !lexer.isEnabled(Feature.DISABLE_SPECIAL_KEY_DETECT)) {
                    String typeName = lexer.scanSymbol(symbolTable, '"');

                    Class<?> clazz = config.checkAutoType(typeName, null, lexer.getFeatures());





                    ObjectDeserializer deserializer = config.getDeserializer(clazz);

                    lexer.nextToken(JSONToken.COMMA);

                    setResolveStatus(DefaultJSONParser.TYPE_NAME_REDIRECT);

                    extracted12(fieldName, context7);

                    return deserializer.deserialze(this, clazz, fieldName);
                }

                Object value;
                lexer.nextToken();

                extracted14(context7, i);

                Type valueType = object.getType(key);

                value = extracted13(key, valueType);

                object.apply(key, value);

                setContext(context7, value, key);
                setContext(context7);

                final int tok = lexer.token();
                if (tok == JSONToken.EOF || tok == JSONToken.RBRACKET) {
                    return object;
                }




            }
        } finally {
            setContext(context7);
        }
    }

    private void extracted14(ParseContext context, int i) {
        if (i != 0) {
            setContext(context);
        }
    }

    private Object extracted13(String key, Type valueType) {
        Object value;
        if (lexer.token() == JSONToken.NULL) {
            value = null;
            lexer.nextToken();
        } else {
            value = parseObject(valueType, key);
        }
        return value;
    }

    private void extracted12(Object fieldName, ParseContext context) {
        if (context != null && !(fieldName instanceof Integer)) {
            popContext();
        }
    }

    private void extracted11(char ch) throws JSONException {
        if (ch != ':') {
            throw new JSONException(EXPECT_AT + lexer.pos() + ACTUAL + ch);
        }
    }

    private void extracted10() throws JSONException {
        if (!lexer.isEnabled(Feature.ALLOW_UN_QUOTED_FIELD_NAMES)) {
            throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
        }
    }

    private void extracted9(char ch) throws JSONException {
        if (ch != ':') {
            throw new JSONException(EXPECT_AT + lexer.pos());
        }
    }

    private void extracted8() throws JSONException {
        if (!lexer.isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
            throw new JSONException(SYNTAX_ERROR_NOT_COMMA);
        }
    }

    private void extracted7(char ch){
        if (ch != ':') {
            throw new JSONException(EXPECT_AT + lexer.pos());
        }
    }

    private char extracted6(char ch) {
        if (lexer.isEnabled(Feature.ALLOW_ARBITRARY_COMMAS)) {
            ch = extracted5(ch);
        }
        return ch;
    }

    private char extracted5(char ch) {
        while (ch == ',') {
            lexer.next();
            lexer.skipWhitespace();
            ch = lexer.getCurrent();
        }
        return ch;
    }

    private String extracted4(Object fieldName, String msg) {
        if (fieldName instanceof String) {
            msg += ", fieldName ";
            msg += fieldName;
        }
        return msg;
    }

}
