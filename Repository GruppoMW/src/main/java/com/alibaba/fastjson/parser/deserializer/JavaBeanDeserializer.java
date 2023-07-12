package com.alibaba.fastjson.parser.deserializer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONValidator;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.*;
import com.alibaba.fastjson.parser.DefaultJSONParser.ResolveTask;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.JavaBeanInfo;
import com.alibaba.fastjson.util.TypeUtils;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JavaBeanDeserializer implements ObjectDeserializer {

    static final String            PARSE_UNWRAPPED_FIELD_ERROR = "parse unwrapped field error.";
    static final String            CREATE_INSTANCE_ERROR       = "create instance error";
    static final String            CANT_CREATE_NON_STATIC_INNER_CLASS_INSTANCE       = "can't create non-static inner class instance.";
    static final String            CREATE_INSTANCE_ERROR_2 = "create instance error, ";

    private final FieldDeserializer[]   fieldDeserializers;
    protected final FieldDeserializer[] sortedFieldDeserializers;
    protected final Class<?>            clazz;
    public final JavaBeanInfo           beanInfo;
    private ConcurrentMap<String, Object> extraFieldDeserializers;

    private final Map<String, FieldDeserializer> alterNameFieldDeserializers;
    private Map<String, FieldDeserializer> fieldDeserializerMap;

    private  long[] smartMatchHashArray;
    private  short[] smartMatchHashArrayMapping;

    private  long[] hashArray;
    private  short[] hashArrayMapping;

    private final ParserConfig.AutoTypeCheckHandler autoTypeCheckHandler;

    public JavaBeanDeserializer(ParserConfig config, Class<?> clazz) {
        this(config, clazz, clazz);
    }

    public JavaBeanDeserializer(ParserConfig config, Class<?> clazz, Type type){
        this(config //
                , JavaBeanInfo.build(clazz, type, config.propertyNamingStrategy, config.fieldBased, config.isJacksonCompatible())
        );
    }

    public JavaBeanDeserializer(ParserConfig config, JavaBeanInfo beanInfo){
        this.clazz = beanInfo.clazz;
        this.beanInfo = beanInfo;

        ParserConfig.AutoTypeCheckHandler autoTypeCheckHandler1 = null;
        if (beanInfo.jsonType != null && beanInfo.jsonType.autoTypeCheckHandler() != ParserConfig.AutoTypeCheckHandler.class) {
            try {
                //Skip
            } catch (Exception e) {
                //
            }
        }
        this.autoTypeCheckHandler = autoTypeCheckHandler1;

        Map<String, FieldDeserializer> alterNameFieldDeserializers2 = null;
        sortedFieldDeserializers = new FieldDeserializer[beanInfo.sortedFields.length];
        for (int i = 0, size = beanInfo.sortedFields.length; i < size; ++i) {
            FieldInfo fieldInfo = beanInfo.sortedFields[i];
            FieldDeserializer fieldDeserializer = config.createFieldDeserializer(config, beanInfo, fieldInfo);

            sortedFieldDeserializers[i] = fieldDeserializer;

            if (size > 128 && fieldDeserializerMap == null) {

                fieldDeserializerMap = new HashMap<>();

                fieldDeserializerMap.put(fieldInfo.name, fieldDeserializer);
            }

            for (String name : fieldInfo.alternateNames) {
                if (alterNameFieldDeserializers2 == null) {
                    alterNameFieldDeserializers2 = new HashMap<>();
                }
                alterNameFieldDeserializers2.put(name, fieldDeserializer);
            }
        }
        this.alterNameFieldDeserializers = alterNameFieldDeserializers2;

        fieldDeserializers = new FieldDeserializer[beanInfo.fields.length];
        for (int i = 0, size = beanInfo.fields.length; i < size; ++i) {
            FieldInfo fieldInfo = beanInfo.fields[i];
            FieldDeserializer fieldDeserializer = getFieldDeserializer(fieldInfo.name);
            fieldDeserializers[i] = fieldDeserializer;
        }
    }

    public FieldDeserializer getFieldDeserializer(String key) {
        return getFieldDeserializer(key, null);
    }

    public FieldDeserializer getFieldDeserializer(String key, int[] setFlags) {
        if (key == null) {
            return null;
        }

        if (fieldDeserializerMap != null) {
            FieldDeserializer fieldDeserializer = fieldDeserializerMap.get(key);
            if (fieldDeserializer != null) {
                return fieldDeserializer;
            }
        }

        int low = 0;
        int high = sortedFieldDeserializers.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;

            String fieldName = sortedFieldDeserializers[mid].fieldInfo.name;

            int cmp = fieldName.compareTo(key);

            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                if (isSetFlag(mid, setFlags)) {
                    return null;
                }

                return sortedFieldDeserializers[mid]; // key found
            }
        }

        if(this.alterNameFieldDeserializers != null){
            return this.alterNameFieldDeserializers.get(key);
        }

        return null;  // key not found.
    }

    public FieldDeserializer getFieldDeserializer(long hash) {
        if (this.hashArray == null) {
            long[] hashArray5 = new long[sortedFieldDeserializers.length];
            for (int i = 0; i < sortedFieldDeserializers.length; i++) {
                hashArray5[i] = TypeUtils.fnv1a64(sortedFieldDeserializers[i].fieldInfo.name);
            }
            Arrays.sort(hashArray5);
            this.hashArray = hashArray5;
        }

        int pos = Arrays.binarySearch(hashArray, hash);
        if (pos < 0) {
            return null;
        }

        if (hashArrayMapping == null) {
            short[] mapping = new short[hashArray.length];
            Arrays.fill(mapping, (short) -1);
            for (int i = 0; i < sortedFieldDeserializers.length; i++) {
                int p = Arrays.binarySearch(hashArray
                        , TypeUtils.fnv1a64(sortedFieldDeserializers[i].fieldInfo.name));
                extracted13(mapping, i, p);
            }
            hashArrayMapping = mapping;
        }

        int setterIndex = hashArrayMapping[pos];
        if (setterIndex != -1) {
            return sortedFieldDeserializers[setterIndex];
        }

        return null; // key not found.
    }

    static boolean isSetFlag(int i, int[] setFlags) {
        if (setFlags == null) {
            return false;
        }

        int flagIndex = i / 32;
        return flagIndex < setFlags.length
                && (setFlags[flagIndex] & (1 << i % 32)) != 0;
    }

    public Object createInstance(DefaultJSONParser parser, Type type) {
        if (type instanceof Class && clazz.isInterface()) {

            Class<?> clazz4 = (Class<?>) type;
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final JSONObject obj = new JSONObject();
            return Proxy.newProxyInstance(loader, new Class<?>[] { clazz4 }, obj);

        }

        if (extracted20()) {
            return null;
        }

        if (extracted21()) {
            return null;
        }

        Object object = extracted35(parser, type);

        extracted34(parser, object);

        return object;
    }

    private Object extracted35(DefaultJSONParser parser, Type type) {
        Object object;
        try {
            object = extracted30(parser, type);
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            throw new JSONException("create instance error, class " + clazz.getName(), e);
        }
        return object;
    }

    private void extracted34(DefaultJSONParser parser, Object object) {
        if (parser != null //
                && parser.lexer.isEnabled(Feature.INIT_STRING_FIELD_AS_EMPTY)) {
            extracted33(object);
        }
    }

    private void extracted33(Object object) {
        for (FieldInfo fieldInfo : beanInfo.fields) {
            extracted32(object, fieldInfo);
        }
    }

    private void extracted32(Object object, FieldInfo fieldInfo) {
        if (fieldInfo.fieldClass == String.class) {
            extracted31(object, fieldInfo);
        }
    }

    private void extracted31(Object object, FieldInfo fieldInfo) {
        try {
            fieldInfo.set(object, "");
        } catch (Exception e) {
            throw new JSONException("create instance error, class " + clazz.getName(), e);
        }
    }

    private Object extracted30(DefaultJSONParser parser, Type type)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Object object;
        Constructor<?> constructor = beanInfo.defaultConstructor;
        if (beanInfo.defaultConstructorParameterSize == 0) {
            object = extracted22(constructor);
        } else {
            ParseContext context = parser.getContext();
            extracted23(context);

            final String typeName = extracted24(type);

            final int lastIndex = typeName.lastIndexOf('$');
            String parentClassName = typeName.substring(0, lastIndex);

            Object ctxObj = context.object;
            String parentName = ctxObj.getClass().getName();

            Object param = extracted28(context, parentClassName, ctxObj, parentName);

            extracted29(param);

            object = constructor.newInstance(param);
        }
        return object;
    }

    private void extracted29(Object param) {
        if (param == null || param instanceof Collection && ((Collection)param).isEmpty()) {
            throw new JSONException(CANT_CREATE_NON_STATIC_INNER_CLASS_INSTANCE);
        }
    }

    private Object extracted28(ParseContext context, String parentClassName, Object ctxObj, String parentName) {
        Object param = null;
        if (!parentName.equals(parentClassName)) {
            param = extracted27(context, parentClassName, ctxObj, parentName, param);
        } else {
            param = ctxObj;
        }
        return param;
    }

    private Object extracted27(ParseContext context, String parentClassName, Object ctxObj, String parentName,
                               Object param) {
        ParseContext parentContext = context.parent;
        if (extracted26(parentName, parentContext)) {
            parentName = parentContext.object.getClass().getName();
            param = extracted25(parentClassName, parentName, param, parentContext);
        } else {
            param = ctxObj;
        }
        return param;
    }

    private boolean extracted26(String parentName, ParseContext parentContext) {
        return parentContext != null
                && parentContext.object != null
                && ("java.util.ArrayList".equals(parentName)
                || "java.util.List".equals(parentName)
                || "java.util.Collection".equals(parentName)
                || "java.util.Map".equals(parentName)
                || "java.util.HashMap".equals(parentName));
    }

    private Object extracted25(String parentClassName, String parentName, Object param, ParseContext parentContext) {
        if (parentName.equals(parentClassName)) {
            param = parentContext.object;
        }
        return param;
    }

    private String extracted24(Type type) {
        final String typeName;
        if (type instanceof Class) {
            typeName = ((Class<?>) type).getName();
        } else {
            throw new JSONException(CANT_CREATE_NON_STATIC_INNER_CLASS_INSTANCE);
        }
        return typeName;
    }

    private void extracted23(ParseContext context) {
        if (context == null || context.object == null) {
            throw new JSONException(CANT_CREATE_NON_STATIC_INNER_CLASS_INSTANCE);
        }
    }

    private Object extracted22(Constructor<?> constructor)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Object object;
        if (constructor != null) {
            object = constructor.newInstance();
        } else {
            object = beanInfo.factoryMethod.invoke(null);
        }
        return object;
    }

    private boolean extracted21() {
        return beanInfo.factoryMethod != null && beanInfo.defaultConstructorParameterSize > 0;
    }

    private boolean extracted20() {
        return beanInfo.defaultConstructor == null && beanInfo.factoryMethod == null;
    }

    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        return deserialze(parser, type, fieldName, 0);
    }

    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName, int features) {
        return deserialze(parser, type, fieldName, null, features, null);
    }

    @SuppressWarnings({ "unchecked" })
    public <T> T deserialzeArrayMapping(DefaultJSONParser parser, Type type, Object fieldName, Object object) {
        final JSONLexer lexer = parser.lexer; // xxx
        extracted2(lexer);

        String typeName = null;
        if ((typeName = lexer.scanTypeName(parser.symbolTable)) != null) {
            ObjectDeserializer deserializer = getSeeAlso(parser.getConfig(), this.beanInfo, typeName);

            deserializer = extracted(parser, type, lexer, typeName, deserializer);

            if (deserializer instanceof JavaBeanDeserializer) {
                return ((JavaBeanDeserializer) deserializer).deserialzeArrayMapping(parser, type, fieldName, object);
            }
        }

        object = createInstance(parser, type);

        extracted6(parser, object, lexer);
        lexer.nextToken(JSONToken.COMMA);

        return (T) object;
    }

    private void extracted6(DefaultJSONParser parser, Object object, final JSONLexer lexer) {
        for (int i = 0, size = sortedFieldDeserializers.length; i < size; ++i) {
            final char seperator = extracted8(i, size);
            FieldDeserializer fieldDeser = sortedFieldDeserializers[i];
            Class<?> fieldClass = fieldDeser.fieldInfo.fieldClass;
            if (fieldClass == int.class) {
                int value = lexer.scanInt(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == String.class) {
                String value = lexer.scanString(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == long.class) {
                long value = lexer.scanLong(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass.isEnum()) {
                char ch = lexer.getCurrent();

                Object value;
                value = extracted5(parser, lexer, seperator, fieldDeser, fieldClass, ch);

                fieldDeser.setValue(object, value);
            } else if (fieldClass == boolean.class) {
                boolean value = lexer.scanBoolean(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == float.class) {
                float value = lexer.scanFloat(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == double.class) {
                double value = lexer.scanDouble(seperator);
                fieldDeser.setValue(object, value);
            } else if (extracted9(lexer, fieldClass)) {
                long longValue = lexer.scanLong(seperator);
                fieldDeser.setValue(object, new java.util.Date(longValue));
            } else if (fieldClass == BigDecimal.class) {
                BigDecimal value = lexer.scanDecimal(seperator);
                fieldDeser.setValue(object, value);
            } else {
                lexer.nextToken(JSONToken.LBRACKET);
                Object value = parser.parseObject(fieldDeser.fieldInfo.fieldType, fieldDeser.fieldInfo.name);
                fieldDeser.setValue(object, value);

                if (lexer.token() == JSONToken.RBRACKET) {
                    break;
                }

                extracted7(lexer, seperator);

            }
        }
    }

    private boolean extracted9(final JSONLexer lexer, Class<?> fieldClass) {
        return fieldClass == java.util.Date.class && lexer.getCurrent() == '1';
    }

    private char extracted8(int i, int size) {
        return (i == size - 1) ? ']' : ',';
    }

    private void extracted7(final JSONLexer lexer, final char seperator) {
        check(lexer, seperator == ']' ? JSONToken.RBRACKET : JSONToken.COMMA);
    }

    private Object extracted5(DefaultJSONParser parser, final JSONLexer lexer, final char seperator,
                              FieldDeserializer fieldDeser, Class<?> fieldClass, char ch) {
        Object value;
        if (extracted3(ch)) {
            value = lexer.scanEnum(fieldClass, parser.getSymbolTable(), seperator);
        } else if (extracted4(ch)) {
            int ordinal = lexer.scanInt(seperator);

            EnumDeserializer enumDeser = (EnumDeserializer) ((DefaultFieldDeserializer) fieldDeser).getFieldValueDeserilizer(parser.getConfig());
            value = enumDeser.valueOf(ordinal);
        } else {
            value = scanEnum(lexer, seperator);
        }
        return value;
    }

    private boolean extracted4(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private boolean extracted3(char ch) {
        return ch == '\"' || ch == 'n';
    }

    private void extracted2(final JSONLexer lexer) {
        if (lexer.token() != JSONToken.LBRACKET) {
            throw new JSONException("error");
        }
    }

    private ObjectDeserializer extracted(DefaultJSONParser parser, Type type, final JSONLexer lexer, String typeName,
                                         ObjectDeserializer deserializer) {
        Class<?> userType;
        if (deserializer == null) {
            Class<?> expectClass = TypeUtils.getClass(type);
            userType = parser.getConfig().checkAutoType(typeName, expectClass, lexer.getFeatures());
            deserializer = parser.getConfig().getDeserializer(userType);
        }
        return deserializer;
    }

    protected void check(final JSONLexer lexer, int token) {
        if (lexer.token() != token) {
            throw new JSONException("syntax error");
        }
    }

    protected <T extends Enum<T>> Enum<T> scanEnum(JSONLexer lexer, char seperator) {
        throw new JSONException("illegal enum. " + lexer.info());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected <T> T deserialze(DefaultJSONParser parser, //
                               Type type, //
                               Object fieldName, //
                               Object object, //
                               int features, //
                               int[] setFlags) {
        if (type == JSON.class || type == JSONObject.class) {
            return (T) parser.parse();
        }

        final JSONLexerBase lexer = (JSONLexerBase) parser.lexer; // xxx
        final ParserConfig config = parser.getConfig();

        int token = lexer.token();
        if (token == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
            return null;
        }

        ParseContext context = parser.getContext();
        if (object != null && context != null) {
            context = context.parent;
        }
        ParseContext childContext = null;

        try {
            Map<String, Object> fieldValues = null;

            if (token == JSONToken.RBRACE) {
                lexer.nextToken(JSONToken.COMMA);
                if (object == null) {
                    object = createInstance(parser, type);
                }
                return (T) object;
            }

            if (token == JSONToken.LBRACKET) {
                final int mask = Feature.SUPPORT_ARRAY_TO_BEAN.mask;
                boolean isSupportArrayToBean = (beanInfo.parserFeatures & mask) != 0 //
                        || lexer.isEnabled(Feature.SUPPORT_ARRAY_TO_BEAN) //
                        || (features & mask) != 0
                        ;
                if (isSupportArrayToBean) {
                    return deserialzeArrayMapping(parser, type, fieldName, object);
                }
            }

            if (token != JSONToken.LBRACE && token != JSONToken.COMMA) {
                if (lexer.isBlankInput()) {
                    return null;
                }

                if (token == JSONToken.LITERAL_STRING) {
                    String strVal = lexer.stringVal();
                    if (strVal.length() == 0) {
                        lexer.nextToken();
                        return null;
                    }

                    if (beanInfo.jsonType != null) {
                        for (Class<?> seeAlsoClass : beanInfo.jsonType.seeAlso()) {
                            if (Enum.class.isAssignableFrom(seeAlsoClass)) {
                                try {
                                    Enum<?> e = Enum.valueOf((Class<Enum>) seeAlsoClass, strVal);
                                    return (T) e;
                                } catch (IllegalArgumentException e) {
                                    // skip
                                }
                            }
                        }
                    }
                }

                if (token == JSONToken.LBRACKET && lexer.getCurrent() == ']') {
                    lexer.next();
                    lexer.nextToken();
                    return null;
                }

                if (beanInfo.factoryMethod != null && beanInfo.fields.length == 1) {
                    try {
                        FieldInfo field = beanInfo.fields[0];
                        if (field.fieldClass == Integer.class) {
                            if (token == JSONToken.LITERAL_INT) {
                                int intValue = lexer.intValue();
                                lexer.nextToken();
                                return (T) createFactoryInstance(intValue);
                            }
                        } else if (field.fieldClass == String.class && token == JSONToken.LITERAL_STRING) {

                            String stringVal = lexer.stringVal();
                            lexer.nextToken();
                            return (T) createFactoryInstance(stringVal);

                        }
                    } catch (Exception ex) {
                        throw new JSONException(ex.getMessage(), ex);
                    }
                }

                StringBuilder buf = (new StringBuilder()) //
                        .append("syntax error, expect {, actual ") //
                        .append(lexer.tokenName()) //
                        .append(", pos ") //
                        .append(lexer.pos());

                if (fieldName instanceof String) {
                    buf //
                            .append(", fieldName ") //
                            .append(fieldName);
                }

                buf.append(", fastjson-version ").append(JSON.VERSION);

                throw new JSONException(buf.toString());
            }

            if (parser.resolveStatus == DefaultJSONParser.TYPE_NAME_REDIRECT) {
                parser.resolveStatus = DefaultJSONParser.NONE;
            }

            String typeKey = beanInfo.typeKey;
            for (int fieldIndex = 0, notMatchCount = 0;; fieldIndex++) {
                String key = null;
                FieldDeserializer fieldDeserializer = null;
                FieldInfo fieldInfo = null;
                Class<?> fieldClass = null;
                JSONField fieldAnnotation = null;
                boolean customDeserializer = false;
                if (fieldIndex < sortedFieldDeserializers.length && notMatchCount < 16) {
                    fieldDeserializer = sortedFieldDeserializers[fieldIndex];
                    fieldInfo = fieldDeserializer.fieldInfo;
                    fieldClass = fieldInfo.fieldClass;
                    fieldAnnotation = fieldInfo.getAnnotation();
                    if (fieldAnnotation != null && fieldDeserializer instanceof DefaultFieldDeserializer) {
                        customDeserializer = ((DefaultFieldDeserializer) fieldDeserializer).customDeserilizer;
                    }
                }

                boolean matchField = false;
                boolean valueParsed = false;

                Object fieldValue = null;
                if (fieldDeserializer != null) {
                    char[] nameChars = fieldInfo.nameChars;
                    if (customDeserializer && lexer.matchField(nameChars)) {
                        matchField = true;
                    } else if (fieldClass == int.class || fieldClass == Integer.class) {
                        int intVal = lexer.scanFieldInt(nameChars);
                        if (intVal == 0 && lexer.matchStat == JSONLexer.VALUE_NULL) {
                            fieldValue = null;
                        } else {
                            fieldValue = intVal;
                        }

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == long.class || fieldClass == Long.class) {
                        long longVal = lexer.scanFieldLong(nameChars);
                        if (longVal == 0 && lexer.matchStat == JSONLexer.VALUE_NULL) {
                            fieldValue = null;
                        } else {
                            fieldValue = longVal;
                        }

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == String.class) {
                        fieldValue = lexer.scanFieldString(nameChars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == java.util.Date.class && fieldInfo.format == null) {
                        fieldValue = lexer.scanFieldDate(nameChars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == BigDecimal.class) {
                        fieldValue = lexer.scanFieldDecimal(nameChars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == BigInteger.class) {
                        fieldValue = lexer.scanFieldBigInteger(nameChars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == boolean.class || fieldClass == Boolean.class) {
                        boolean booleanVal = lexer.scanFieldBoolean(nameChars);

                        if (lexer.matchStat == JSONLexer.VALUE_NULL) {
                            fieldValue = null;
                        } else {
                            fieldValue = booleanVal;
                        }

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == float.class || fieldClass == Float.class) {
                        float floatVal = lexer.scanFieldFloat(nameChars);
                        if (floatVal == 0 && lexer.matchStat == JSONLexer.VALUE_NULL) {
                            fieldValue = null;
                        } else {
                            fieldValue = floatVal;
                        }

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == double.class || fieldClass == Double.class) {
                        double doubleVal = lexer.scanFieldDouble(nameChars);
                        if (doubleVal == 0 && lexer.matchStat == JSONLexer.VALUE_NULL) {
                            fieldValue = null;
                        } else {
                            fieldValue = doubleVal;
                        }

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass.isEnum() //
                            && parser.getConfig().getDeserializer(fieldClass) instanceof EnumDeserializer
                            && (fieldAnnotation == null || fieldAnnotation.deserializeUsing() == Void.class)
                    ) {
                        if (fieldDeserializer instanceof DefaultFieldDeserializer) {
                            ObjectDeserializer fieldValueDeserilizer = ((DefaultFieldDeserializer) fieldDeserializer).fieldValueDeserilizer;
                            fieldValue = this.scanEnum(lexer, nameChars, fieldValueDeserilizer);

                            if (lexer.matchStat > 0) {
                                matchField = true;
                                valueParsed = true;
                            } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                                //skip
                            }
                        }
                    } else if (fieldClass == int[].class) {
                        fieldValue = lexer.scanFieldIntArray(nameChars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == float[].class) {
                        fieldValue = lexer.scanFieldFloatArray(nameChars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (fieldClass == float[][].class) {
                        fieldValue = lexer.scanFieldFloatArray2(nameChars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            //skip
                        }
                    } else if (lexer.matchField(nameChars)) {
                        matchField = true;
                    } else {
                        //skip
                    }
                }

                if (!matchField) {
                    key = lexer.scanSymbol(parser.symbolTable);

                    if (key == null) {
                        token = lexer.token();
                        if (token == JSONToken.RBRACE) {
                            lexer.nextToken(JSONToken.COMMA);
                            //skip
                        }
                        if (token == JSONToken.COMMA && lexer.isEnabled(Feature.ALLOW_ARBITRARY_COMMAS)) {
                            //skip

                        }
                    }

                    if (key.equals("$ref") && context != null) {
                        lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);
                        token = lexer.token();
                        if (token == JSONToken.LITERAL_STRING) {
                            String ref = lexer.stringVal();
                            if ("@".equals(ref)) {
                                object = context.object;
                            } else if ("..".equals(ref)) {
                                ParseContext parentContext = context.parent;
                                if (parentContext.object != null) {
                                    object = parentContext.object;
                                } else {
                                    parser.addResolveTask(new ResolveTask(parentContext, ref));
                                    parser.resolveStatus = DefaultJSONParser.NEED_TO_RESOLVE;
                                }
                            } else if ("$".equals(ref)) {
                                ParseContext rootContext = context;
                                while (rootContext.parent != null) {
                                    rootContext = rootContext.parent;
                                }

                                if (rootContext.object != null) {
                                    object = rootContext.object;
                                } else {
                                    parser.addResolveTask(new ResolveTask(rootContext, ref));
                                    parser.resolveStatus = DefaultJSONParser.NEED_TO_RESOLVE;
                                }
                            } else {
                                if (ref.indexOf('\\') >= 0) {
                                    StringBuilder buf = new StringBuilder();
                                    for (int i = 0; i < ref.length(); i++) {
                                        char ch = ref.charAt(i);

                                        buf.append(ch);
                                    }
                                    ref = buf.toString();
                                }
                                Object refObj = parser.resolveReference(ref);
                                if (refObj != null) {
                                    object = refObj;
                                } else {
                                    parser.addResolveTask(new ResolveTask(context, ref));
                                    parser.resolveStatus = DefaultJSONParser.NEED_TO_RESOLVE;
                                }
                            }
                        } else {
                            throw new JSONException("illegal ref, " + JSONToken.name(token));
                        }

                        lexer.nextToken(JSONToken.RBRACE);
                        if (lexer.token() != JSONToken.RBRACE) {
                            throw new JSONException("illegal ref");
                        }
                        lexer.nextToken(JSONToken.COMMA);

                        parser.setContext(context, object, fieldName);

                        return (T) object;
                    }

                    if ((typeKey != null && typeKey.equals(key))
                            ||  key.equals(JSON.DEFAULT_TYPE_KEY)) {
                        lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);
                        if (lexer.token() == JSONToken.LITERAL_STRING) {
                            String typeName = lexer.stringVal();
                            lexer.nextToken(JSONToken.COMMA);

                            if (typeName.equals(beanInfo.typeName)|| parser.isEnabled(Feature.IGNORE_AUTO_TYPE) &&  (lexer.token() == JSONToken.RBRACE)) {
                                    lexer.nextToken();
                                //skip
                            }


                            ObjectDeserializer deserializer = getSeeAlso(config, this.beanInfo, typeName);
                            Class<?> userType = null;

                            if (deserializer == null) {
                                Class<?> expectClass = TypeUtils.getClass(type);

                                if (autoTypeCheckHandler != null) {
                                    userType = autoTypeCheckHandler.handler(typeName, expectClass, lexer.getFeatures());
                                }

                                if (userType == null && (typeName.equals("java.util.HashMap") || typeName.equals("java.util.LinkedHashMap")) &&  (lexer.token() == JSONToken.RBRACE)) {
                                        lexer.nextToken();                                    
                                    //skip
                                }

                                if (userType == null) {
                                    userType = config.checkAutoType(typeName, expectClass, lexer.getFeatures());
                                }
                                deserializer = parser.getConfig().getDeserializer(userType);
                            }

                            Object typedObject = deserializer.deserialze(parser, userType, fieldName);
                            if (deserializer instanceof JavaBeanDeserializer) {
                                JavaBeanDeserializer javaBeanDeserializer = (JavaBeanDeserializer) deserializer;
                                if (typeKey != null) {
                                    FieldDeserializer typeKeyFieldDeser = javaBeanDeserializer.getFieldDeserializer(typeKey);
                                    if (typeKeyFieldDeser != null) {
                                        typeKeyFieldDeser.setValue(typedObject, typeName);
                                    }
                                }
                            }
                            return (T) typedObject;
                        } else {
                            throw new JSONException("syntax error");
                        }
                    }
                }

                if (object == null && fieldValues == null) {
                    object = createInstance(parser, type);
                    if (object == null) {
                        fieldValues = new HashMap<>(this.fieldDeserializers.length);
                    }
                    childContext = parser.setContext(context, object, fieldName);
                    if (setFlags == null) {
                        setFlags = new int[(this.fieldDeserializers.length / 32) + 1];
                    }
                }

                if (matchField) {
                    if (!valueParsed) {
                        fieldDeserializer.parseField(parser, object, type, fieldValues);
                    } else {
                        if (object == null) {
                            fieldValues.put(fieldInfo.name, fieldValue);
                        } else if (fieldValue == null) {
                            if (fieldClass != int.class //
                                    && fieldClass != long.class //
                                    && fieldClass != float.class //
                                    && fieldClass != double.class //
                                    && fieldClass != boolean.class //
                            ) {
                                fieldDeserializer.setValue(object, fieldValue);
                            }
                        } else {
                            if (fieldClass == String.class
                                    && ((features & Feature.TRIM_STRING_FIELD_VALUE.mask) != 0
                                    || (beanInfo.parserFeatures & Feature.TRIM_STRING_FIELD_VALUE.mask) != 0
                                    || (fieldInfo.parserFeatures & Feature.TRIM_STRING_FIELD_VALUE.mask) != 0)) {
                                fieldValue = ((String) fieldValue).trim();
                            }

                            fieldDeserializer.setValue(object, fieldValue);
                        }

                        if (setFlags != null) {
                            int flagIndex = fieldIndex / 32;
                            int bitIndex = fieldIndex % 32;
                            setFlags[flagIndex] |= (1 << bitIndex);
                        }

                        if (lexer.matchStat == JSONLexer.END) {
                            //skip
                        }
                    }
                } else {
                    boolean match = parseField(parser, key, object, type,
                            fieldValues == null ? new HashMap<String, Object>(this.fieldDeserializers.length) : fieldValues, setFlags);

                    if (!match) {
                        if (lexer.token() == JSONToken.RBRACE) {
                            lexer.nextToken();
                            //skip
                        }

                        //skip
                    } else if (lexer.token() == JSONToken.COLON) {
                        throw new JSONException("syntax error, unexpect token ':'");
                    }
                }

                if (lexer.token() == JSONToken.COMMA) {
                    //skip
                }

                if (lexer.token() == JSONToken.RBRACE) {
                    lexer.nextToken(JSONToken.COMMA);
                    break;
                }

                if (lexer.token() == JSONToken.IDENTIFIER || lexer.token() == JSONToken.ERROR) {
                    throw new JSONException("syntax error, unexpect token " + JSONToken.name(lexer.token()));
                }
            }

            if (object == null) {
                if (fieldValues == null) {
                    object = createInstance(parser, type);
                    if (childContext == null) {
                        childContext = parser.setContext(context, object, fieldName);
                    }
                    return (T) object;
                }

                String[] paramNames = beanInfo.creatorConstructorParameters;
                final Object[] params;
                if (paramNames != null) {
                    params = new Object[paramNames.length];
                    for (int i = 0; i < paramNames.length; i++) {
                        String paramName = paramNames[i];

                        Object param = fieldValues.remove(paramName);
                        if (param == null) {
                            Type fieldType = beanInfo.creatorConstructorParameterTypes[i];
                            FieldInfo fieldInfo = beanInfo.fields[i];
                            if (fieldType == byte.class) {
                                param = (byte) 0;
                            } else if (fieldType == short.class) {
                                param = (short) 0;
                            } else if (fieldType == int.class) {
                                param = 0;
                            } else if (fieldType == long.class) {
                                param = 0L;
                            } else if (fieldType == float.class) {
                                param = 0F;
                            } else if (fieldType == double.class) {
                                param = 0D;
                            } else if (fieldType == boolean.class) {
                                param = Boolean.FALSE;
                            } else if (fieldType == String.class
                                    && (fieldInfo.parserFeatures & Feature.INIT_STRING_FIELD_AS_EMPTY.mask) != 0) {
                                param = "";
                            }
                        } else {
                            if (beanInfo.creatorConstructorParameterTypes != null && i < beanInfo.creatorConstructorParameterTypes.length) {
                                Type paramType = beanInfo.creatorConstructorParameterTypes[i];
                                if (paramType instanceof Class) {
                                    Class paramClass = (Class) paramType;
                                    if (!paramClass.isInstance(param) && param instanceof List) {

                                        List list = (List) param;
                                        if (list.size() == 1) {
                                            Object first = list.get(0);
                                            if (paramClass.isInstance(first)) {
                                                param = list.get(0);
                                            }
                                        }

                                    }
                                }
                            }
                        }
                        params[i] = param;
                    }
                } else {
                    FieldInfo[] fieldInfoList = beanInfo.fields;
                    int size = fieldInfoList.length;
                    params = new Object[size];
                    for (int i = 0; i < size; ++i) {
                        FieldInfo fieldInfo = fieldInfoList[i];
                        Object param = fieldValues.get(fieldInfo.name);
                        if (param == null) {
                            Type fieldType = fieldInfo.fieldType;
                            if (fieldType == byte.class) {
                                param = (byte) 0;
                            } else if (fieldType == short.class) {
                                param = (short) 0;
                            } else if (fieldType == int.class) {
                                param = 0;
                            } else if (fieldType == long.class) {
                                param = 0L;
                            } else if (fieldType == float.class) {
                                param = 0F;
                            } else if (fieldType == double.class) {
                                param = 0D;
                            } else if (fieldType == boolean.class) {
                                param = Boolean.FALSE;
                            } else if (fieldType == String.class
                                    && (fieldInfo.parserFeatures & Feature.INIT_STRING_FIELD_AS_EMPTY.mask) != 0) {
                                param = "";
                            }
                        }
                        params[i] = param;
                    }
                }

                if (beanInfo.creatorConstructor != null) {
                    boolean hasNull = false;
                    if (beanInfo.kotlin) {
                        for (int i = 0; i < params.length; i++) {
                            if (params[i] == null && beanInfo.fields != null && i < beanInfo.fields.length) {
                                FieldInfo fieldInfo = beanInfo.fields[i];
                                if (fieldInfo.fieldClass == String.class) {
                                    hasNull = true;
                                }
                                break;
                            }
                        }
                    }

                    try {
                        if (hasNull && beanInfo.kotlinDefaultConstructor != null) {
                            object = beanInfo.kotlinDefaultConstructor.newInstance();

                            for (int i = 0; i < params.length; i++) {
                                final Object param = params[i];
                                if (param != null && beanInfo.fields != null && i < beanInfo.fields.length) {
                                    FieldInfo fieldInfo = beanInfo.fields[i];
                                    fieldInfo.set(object, param);
                                }
                            }
                        } else {
                            object = beanInfo.creatorConstructor.newInstance(params);
                        }
                    } catch (Exception e) {
                        throw new JSONException(CREATE_INSTANCE_ERROR_2 + paramNames + ", "
                                + beanInfo.creatorConstructor.toGenericString(), e);
                    }

                    if (paramNames != null) {
                        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                            FieldDeserializer fieldDeserializer = getFieldDeserializer(entry.getKey());
                            if (fieldDeserializer != null) {
                                fieldDeserializer.setValue(object, entry.getValue());
                            }
                        }
                    }
                } else if (beanInfo.factoryMethod != null) {
                    try {
                        object = beanInfo.factoryMethod.invoke(null, params);
                    } catch (Exception e) {
                        throw new JSONException("create factory method error, " + beanInfo.factoryMethod.toString(), e);
                    }
                }

                if (childContext != null) {
                    childContext.object = object;
                }
            }

            Method buildMethod = beanInfo.buildMethod;
            if (buildMethod == null) {
                return (T) object;
            }


            Object builtObj;
            try {
                builtObj = buildMethod.invoke(object);
            } catch (Exception e) {
                throw new JSONException("build object error", e);
            }

            return (T) builtObj;
        } finally {
            if (childContext != null) {
                childContext.object = object;
            }
            parser.setContext(context);
        }
    }

    protected Enum<?> scanEnum(JSONLexerBase lexer, char[] nameChars, ObjectDeserializer fieldValueDeserilizer) {
        EnumDeserializer enumDeserializer = null;
        if (fieldValueDeserilizer instanceof EnumDeserializer) {
            enumDeserializer = (EnumDeserializer) fieldValueDeserilizer;
        }

        if (enumDeserializer == null) {
            lexer.matchStat = JSONLexer.NOT_MATCH;
            return null;
        }

        long enumNameHashCode = lexer.scanEnumSymbol(nameChars);
        if (lexer.matchStat > 0) {
            Enum<?> e = enumDeserializer.getEnumByHashCode(enumNameHashCode);
            if (e == null && lexer.isEnabled(Feature.ERROR_ON_ENUM_NOT_MATCH) &&  (lexer.isEnabled(Feature.ERROR_ON_ENUM_NOT_MATCH))) {
                throw new JSONException("not match enum value, " + enumDeserializer.enumClass);

            }

            return e;
        } else {
            return null;
        }
    }

    public boolean parseField(DefaultJSONParser parser, String key, Object object, Type objectType,
                              Map<String, Object> fieldValues) {
        return parseField(parser, key, object, objectType, fieldValues, null);
    }

    public boolean parseField(DefaultJSONParser parser, String key, Object object, Type objectType,
                              Map<String, Object> fieldValues, int[] setFlags) {
        JSONLexer lexer = parser.lexer; // xxx

        final int disableFieldSmartMatchMask = Feature.DISABLE_FIELD_SMART_MATCH.mask;
        final int initStringFieldAsEmpty = Feature.INIT_STRING_FIELD_AS_EMPTY.mask;
        FieldDeserializer fieldDeserializer;
        if (lexer.isEnabled(disableFieldSmartMatchMask) || (this.beanInfo.parserFeatures & disableFieldSmartMatchMask) != 0) {
            fieldDeserializer = getFieldDeserializer(key);
        } else if (lexer.isEnabled(initStringFieldAsEmpty) || (this.beanInfo.parserFeatures & initStringFieldAsEmpty) != 0) {
            fieldDeserializer = smartMatch(key);
        } else {
            fieldDeserializer = smartMatch(key, setFlags);
        }

        final int mask = Feature.SUPPORT_NON_PUBLIC_FIELD.mask;
        if (fieldDeserializer == null
                && (lexer.isEnabled(mask)
                || (this.beanInfo.parserFeatures & mask) != 0)) {
            if (this.extraFieldDeserializers == null) {
                extraFieldDeserializers = new ConcurrentHashMap<>(1, 0.75f, 1);
                for (Class<?> c = this.clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                    Field[] fields = c.getDeclaredFields();
                    for (Field field : fields) {
                        String fieldName = field.getName();
                        if (this.getFieldDeserializer(fieldName) != null) {
                            //skip
                        }
                        int fieldModifiers = field.getModifiers();
                        if ((fieldModifiers & Modifier.FINAL) != 0 || (fieldModifiers & Modifier.STATIC) != 0) {
                            continue;
                        }
                        JSONField jsonField = TypeUtils.getAnnotation(field, JSONField.class);
                        if (jsonField != null) {
                            String alteredFieldName = jsonField.name();
                            if (!"".equals(alteredFieldName)) {
                                fieldName = alteredFieldName;
                            }
                        }
                        extraFieldDeserializers.put(fieldName, field);
                    }
                }
            }

            Object deserOrField = extraFieldDeserializers.get(key);
            if (deserOrField != null) {
                if (deserOrField instanceof FieldDeserializer) {
                    fieldDeserializer = ((FieldDeserializer) deserOrField);
                } else {
                    Field field = (Field) deserOrField;
                    FieldInfo fieldInfo = new FieldInfo(key, field.getDeclaringClass(), field.getType(), field.getGenericType(), field, 0, 0, 0);
                    fieldDeserializer = new DefaultFieldDeserializer(clazz, fieldInfo);
                    extraFieldDeserializers.put(key, fieldDeserializer);
                }
            }
        }

        if (fieldDeserializer == null) {
            if (!lexer.isEnabled(Feature.IGNORE_NOT_MATCH)) {
                throw new JSONException("setter not found, class " + clazz.getName() + ", property " + key);
            }

            int fieldIndex = -1;
            for (int i = 0; i < this.sortedFieldDeserializers.length; i++) {
                FieldDeserializer fieldDeser = this.sortedFieldDeserializers[i];

                FieldInfo fieldInfo = fieldDeser.fieldInfo;
                if (fieldInfo.unwrapped //
                        && fieldDeser instanceof DefaultFieldDeserializer) {
                    if (fieldInfo.field != null) {
                        DefaultFieldDeserializer defaultFieldDeserializer = (DefaultFieldDeserializer) fieldDeser;
                        ObjectDeserializer fieldValueDeser = defaultFieldDeserializer.getFieldValueDeserilizer(parser.getConfig());
                        if (fieldValueDeser instanceof JavaBeanDeserializer) {
                            JavaBeanDeserializer javaBeanFieldValueDeserializer = (JavaBeanDeserializer) fieldValueDeser;
                            FieldDeserializer unwrappedFieldDeser = javaBeanFieldValueDeserializer.getFieldDeserializer(key);
                            if (unwrappedFieldDeser != null) {
                                Object fieldObject;
                                try {
                                    fieldObject = fieldInfo.field.get(object);
                                    if (fieldObject == null) {
                                        fieldObject = ((JavaBeanDeserializer) fieldValueDeser).createInstance(parser, fieldInfo.fieldType);
                                        fieldDeser.setValue(object, fieldObject);
                                    }
                                    lexer.nextTokenWithColon(defaultFieldDeserializer.getFastMatchToken());
                                    unwrappedFieldDeser.parseField(parser, fieldObject, objectType, fieldValues);
                                    fieldIndex = i;
                                } catch (Exception e) {
                                    throw new JSONException(PARSE_UNWRAPPED_FIELD_ERROR, e);
                                }
                            }
                        } else if (fieldValueDeser instanceof MapDeserializer) {
                            MapDeserializer javaBeanFieldValueDeserializer = (MapDeserializer) fieldValueDeser;

                            Map<Object,Object> fieldObject;
                            try {
                                fieldObject = (Map) fieldInfo.field.get(object);
                                if (fieldObject == null) {
                                    fieldObject = javaBeanFieldValueDeserializer.createMap(fieldInfo.fieldType);
                                    fieldDeser.setValue(object, fieldObject);
                                }

                                lexer.nextTokenWithColon();
                                Object fieldValue = parser.parse(key);
                                fieldObject.put(key, fieldValue);
                            } catch (Exception e) {
                                throw new JSONException(PARSE_UNWRAPPED_FIELD_ERROR, e);
                            }
                            fieldIndex = i;
                        }
                    } else if (fieldInfo.method.getParameterTypes().length == 2) {
                        lexer.nextTokenWithColon();
                        Object fieldValue = parser.parse(key);
                        try {
                            fieldInfo.method.invoke(object, key, fieldValue);
                        } catch (Exception e) {
                            throw new JSONException(PARSE_UNWRAPPED_FIELD_ERROR, e);
                        }
                        fieldIndex = i;
                    }
                }
            }

            if (fieldIndex != -1) {
                if (setFlags != null) {
                    int flagIndex = fieldIndex / 32;
                    int bitIndex = fieldIndex % 32;
                    setFlags[flagIndex] |= (1 << bitIndex);
                }
                return true;
            }

            parser.parseExtra(object, key);

            return false;
        }

        int fieldIndex = -1;
        for (int i = 0; i < sortedFieldDeserializers.length; ++i) {
            if (sortedFieldDeserializers[i] == fieldDeserializer) {
                fieldIndex = i;
                break;
            }
        }
        if (fieldIndex != -1 && setFlags != null && key.startsWith("_") && isSetFlag(fieldIndex, setFlags)) {

            parser.parseExtra(object, key);
            return false;

        }

        lexer.nextTokenWithColon(fieldDeserializer.getFastMatchToken());

        fieldDeserializer.parseField(parser, object, objectType, fieldValues);

        if (setFlags != null) {
            int flagIndex = fieldIndex / 32;
            int bitIndex = fieldIndex % 32;
            setFlags[flagIndex] |= (1 << bitIndex);
        }

        return true;
    }

    public FieldDeserializer smartMatch(String key) {
        return smartMatch(key, null);
    }

    public FieldDeserializer smartMatch(String key, int[] setFlags) {
        if (key == null) {
            return null;
        }

        FieldDeserializer fieldDeserializer = getFieldDeserializer(key, setFlags);

        if (fieldDeserializer == null) {
            extracted11();

            // smartMatchHashArrayMapping
            long smartKeyHash = TypeUtils.fnv1a64Lower(key);
            int pos = Arrays.binarySearch(smartMatchHashArray, smartKeyHash);
            pos = extracted12(key, pos);

            boolean is = false;
            if (pos < 0) {
                is = key.startsWith("is");
                smartKeyHash = TypeUtils.fnv1a64Extract(key.substring(2));
                pos = Arrays.binarySearch(smartMatchHashArray, smartKeyHash);
            }

            fieldDeserializer = extracted18(setFlags, fieldDeserializer, pos);

            if (fieldDeserializer != null) {
                FieldInfo fieldInfo = fieldDeserializer.fieldInfo;
                if ((fieldInfo.parserFeatures & Feature.DISABLE_FIELD_SMART_MATCH.mask) != 0) {
                    return null;
                }

                Class<?> fieldClass = fieldInfo.fieldClass;
                fieldDeserializer = extracted19(fieldDeserializer, is, fieldClass);
            }
        }


        return fieldDeserializer;
    }

    private FieldDeserializer extracted19(FieldDeserializer fieldDeserializer, boolean is, Class<?> fieldClass) {
        if (is && (fieldClass != boolean.class && fieldClass != Boolean.class)) {
            fieldDeserializer = null;
        }
        return fieldDeserializer;
    }

    private FieldDeserializer extracted18(int[] setFlags, FieldDeserializer fieldDeserializer, int pos) {
        if (pos >= 0) {
            extracted15();

            int deserIndex = smartMatchHashArrayMapping[pos];
            fieldDeserializer = extracted17(setFlags, fieldDeserializer, deserIndex);
        }
        return fieldDeserializer;
    }

    private FieldDeserializer extracted17(int[] setFlags, FieldDeserializer fieldDeserializer, int deserIndex) {
        if (deserIndex != -1) {
            fieldDeserializer = extracted16(setFlags, fieldDeserializer, deserIndex);
        }
        return fieldDeserializer;
    }

    private FieldDeserializer extracted16(int[] setFlags, FieldDeserializer fieldDeserializer, int deserIndex) {
        if (!isSetFlag(deserIndex, setFlags)) {
            fieldDeserializer = sortedFieldDeserializers[deserIndex];
        }
        return fieldDeserializer;
    }

    private void extracted15() {
        if (smartMatchHashArrayMapping == null) {
            short[] mapping = new short[smartMatchHashArray.length];
            Arrays.fill(mapping, (short) -1);
            extracted14(mapping);
            smartMatchHashArrayMapping = mapping;
        }
    }

    private void extracted14(short[] mapping) {
        for (int i = 0; i < sortedFieldDeserializers.length; i++) {
            int p = Arrays.binarySearch(smartMatchHashArray, sortedFieldDeserializers[i].fieldInfo.nameHashCode);
            extracted13(mapping, i, p);
        }
    }

    private void extracted13(short[] mapping, int i, int p) {
        if (p >= 0) {
            mapping[p] = (short) i;
        }
    }

    private int extracted12(String key, int pos) {
        if (pos < 0) {
            long smartKeyHash1 = TypeUtils.fnv1a64Extract(key);
            pos = Arrays.binarySearch(smartMatchHashArray, smartKeyHash1);
        }
        return pos;
    }

    private void extracted11() {
        if (this.smartMatchHashArray == null) {
            long[] hashArray3 = new long[sortedFieldDeserializers.length];
            extracted10(hashArray3);
            Arrays.sort(hashArray3);
            this.smartMatchHashArray = hashArray3;
        }
    }

    private void extracted10(long[] hashArray) {
        for (int i = 0; i < sortedFieldDeserializers.length; i++) {
            hashArray[i] = sortedFieldDeserializers[i].fieldInfo.nameHashCode;
        }
    }

    public int getFastMatchToken() {
        return JSONToken.LBRACE;
    }

    private Object createFactoryInstance(Object value) //
            throws IllegalArgumentException,
            IllegalAccessException,
            InvocationTargetException {
        return beanInfo.factoryMethod.invoke(null, value);
    }

    public Object createInstance(Map<String, Object> map, ParserConfig config) //
            throws IllegalArgumentException,
            IllegalAccessException,
            InvocationTargetException {
        Object object = null;

        if (beanInfo.creatorConstructor == null && beanInfo.factoryMethod == null) {
            object = createInstance(null, clazz);

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                FieldDeserializer fieldDeser = smartMatch(key);
                if (fieldDeser == null) {
                    continue;
                }

                final FieldInfo fieldInfo = fieldDeser.fieldInfo;
                Type paramType = fieldInfo.fieldType;

                Class<?> fieldClass = fieldInfo.fieldClass;
                JSONField fieldAnnation = fieldInfo.getAnnotation();

                if (fieldInfo.declaringClass != null
                        && ((!fieldClass.isInstance(value))
                        || (fieldAnnation != null && fieldAnnation.deserializeUsing() != Void.class))
                ) {
                    String input;
                    if (value instanceof String
                            && JSONValidator.from(((String) value))
                            .validate())
                    {
                        input = (String) value;
                    } else {
                        input = JSON.toJSONString(value);
                    }

                    DefaultJSONParser parser = new DefaultJSONParser(input);
                    fieldDeser.parseField(parser, object, paramType, null);
                }


                String format = fieldInfo.format;
                if (format != null && paramType == Date.class) {
                    value = TypeUtils.castToDate(value, format);
                } else if (format != null && (paramType instanceof Class) && (((Class) paramType).isInstance("java.time.LocalDateTime"))) {
                    value = Jdk8DateCodec.castToLocalDateTime(value, format);
                } else {
                    if (paramType instanceof ParameterizedType) {
                        value = TypeUtils.cast(value, (ParameterizedType) paramType, config);
                    } else {
                        value = TypeUtils.cast(value, paramType, config);
                    }
                }

                fieldDeser.setValue(object, value);
            }

            if (beanInfo.buildMethod != null) {
                Object builtObj;
                try {
                    builtObj = beanInfo.buildMethod.invoke(object);
                } catch (Exception e) {
                    throw new JSONException("build object error", e);
                }

                return builtObj;
            }

            return object;
        }


        FieldInfo[] fieldInfoList = beanInfo.fields;
        int size = fieldInfoList.length;
        Object[] params = new Object[size];
        Map<String, Integer> missFields = null;
        for (int i = 0; i < size; ++i) {
            FieldInfo fieldInfo = fieldInfoList[i];
            Object param = map.get(fieldInfo.name);

            if (param == null) {
                Class<?> fieldClass = fieldInfo.fieldClass;
                if (fieldClass == int.class) {
                    param = 0;
                } else if (fieldClass == long.class) {
                    param = 0L;
                } else if (fieldClass == short.class) {
                    param = Short.valueOf((short) 0);
                } else if (fieldClass == byte.class) {
                    param = Byte.valueOf((byte) 0);
                } else if (fieldClass == float.class) {
                    param = Float.valueOf(0);
                } else if (fieldClass == double.class) {
                    param = Double.valueOf(0);
                } else if (fieldClass == char.class) {
                    param = '0';
                } else if (fieldClass == boolean.class) {
                    param = false;
                }
                if (missFields == null) {
                    missFields = new HashMap<>();
                }
                missFields.put(fieldInfo.name, i);
            }
            params[i] = param;
        }

        if (missFields != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                FieldDeserializer fieldDeser = smartMatch(key);
                if (fieldDeser != null) {
                    Integer index = missFields.get(fieldDeser.fieldInfo.name);
                    if (index != null) {
                        params[index] = value;
                    }
                }
            }
        }

        if (beanInfo.creatorConstructor != null) {
            boolean hasNull = false;
            if (beanInfo.kotlin) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param == null) {
                        if (beanInfo.fields != null && i < beanInfo.fields.length) {
                            FieldInfo fieldInfo = beanInfo.fields[i];
                            if (fieldInfo.fieldClass == String.class) {
                                hasNull = true;
                            }
                        }
                    } else if (param.getClass() != beanInfo.fields[i].fieldClass){
                        params[i] = TypeUtils.cast(param, beanInfo.fields[i].fieldClass, config);
                    }
                }
            }

            if (hasNull && beanInfo.kotlinDefaultConstructor != null) {
                try {
                    object = beanInfo.kotlinDefaultConstructor.newInstance();

                    for (int i = 0; i < params.length; i++) {
                        final Object param = params[i];
                        if (param != null && beanInfo.fields != null && i < beanInfo.fields.length) {
                            FieldInfo fieldInfo = beanInfo.fields[i];
                            fieldInfo.set(object, param);
                        }
                    }
                } catch (Exception e) {
                    throw new JSONException(CREATE_INSTANCE_ERROR_2
                            + beanInfo.creatorConstructor.toGenericString(), e);
                }
            } else {
                try {
                    object = beanInfo.creatorConstructor.newInstance(params);
                } catch (Exception e) {
                    throw new JSONException(CREATE_INSTANCE_ERROR_2
                            + beanInfo.creatorConstructor.toGenericString(), e);
                }
            }
        } else if (beanInfo.factoryMethod != null) {
            try {
                object = beanInfo.factoryMethod.invoke(null, params);
            } catch (Exception e) {
                throw new JSONException("create factory method error, " + beanInfo.factoryMethod.toString(), e);
            }
        }

        return object;
    }

    public Type getFieldType(int ordinal) {
        return sortedFieldDeserializers[ordinal].fieldInfo.fieldType;
    }

    protected Object parseRest(DefaultJSONParser parser, Type type, Object fieldName, Object instance, int features) {
        return parseRest(parser, type, fieldName, instance, features, new int[0]);
    }

    protected Object parseRest(DefaultJSONParser parser
            , Type type
            , Object fieldName
            , Object instance
            , int features
            , int[] setFlags) {
        return deserialze(parser, type, fieldName, instance, features, setFlags);
    }

    protected static JavaBeanDeserializer getSeeAlso(ParserConfig config, JavaBeanInfo beanInfo, String typeName) {
        if (beanInfo.jsonType == null) {
            return null;
        }

        for (Class<?> seeAlsoClass : beanInfo.jsonType.seeAlso()) {
            ObjectDeserializer seeAlsoDeser = config.getDeserializer(seeAlsoClass);
            if (seeAlsoDeser instanceof JavaBeanDeserializer) {
                JavaBeanDeserializer seeAlsoJavaBeanDeser = (JavaBeanDeserializer) seeAlsoDeser;

                JavaBeanInfo subBeanInfo = seeAlsoJavaBeanDeser.beanInfo;
                if (subBeanInfo.typeName.equals(typeName)) {
                    return seeAlsoJavaBeanDeser;
                }

                JavaBeanDeserializer subSeeAlso = getSeeAlso(config, subBeanInfo, typeName);
                if (subSeeAlso != null) {
                    return subSeeAlso;
                }
            }
        }

        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected static void parseArray(Collection collection, //
                                     ObjectDeserializer deser, //
                                     DefaultJSONParser parser, //
                                     Type type) {

        final JSONLexerBase lexer = (JSONLexerBase) parser.lexer;
        int token = lexer.token();
        if (token == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
            return;
        }

        if (token != JSONToken.LBRACKET) {
            parser.throwException(token);
        }
        char ch = lexer.getCurrent();
        if (ch == '[') {
            lexer.next();
            lexer.setToken(JSONToken.LBRACKET);
        }

        lexer.nextToken(JSONToken.LBRACKET);

        if (lexer.token() == JSONToken.RBRACKET) {
            lexer.nextToken();
            return;
        }

        int index = 0;
        for (;;) {
            Object item = deser.deserialze(parser, type, index);
            collection.add(item);
            index++;
            if (lexer.token() == JSONToken.COMMA) {
                ch = lexer.getCurrent();
                if (ch == '[') {
                    lexer.next();
                    lexer.setToken(JSONToken.LBRACKET);
                } else {
                    lexer.nextToken(JSONToken.LBRACKET);
                }
            } else {
                break;
            }
        }

        token = lexer.token();
        if (token != JSONToken.RBRACKET) {
            parser.throwException(token);
        }

        ch = lexer.getCurrent();
        if (ch == ',') {
            lexer.next();
            lexer.setToken(JSONToken.COMMA);
        } else {
            lexer.nextToken(JSONToken.COMMA);
        }

    }

}
