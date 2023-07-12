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
package com.alibaba.fastjson.util;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.PropertyNamingStrategy;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.JSONScanner;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.EnumDeserializer;
import com.alibaba.fastjson.parser.deserializer.JavaBeanDeserializer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.CalendarCodec;
import com.alibaba.fastjson.serializer.SerializeBeanInfo;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.AccessControlException;
import java.sql.Clob;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class TypeUtils {
    private TypeUtils() {
        throw new IllegalStateException("Utility class");
    }

    static final  String AND_DECREMENT_STRING = "andDecrement";
    static final  String KOTLIN_COLLECTION = "kotlin.collections.CollectionsKt";
    private static final Pattern NUMBER_WITH_TRAILING_ZEROS_PATTERN = Pattern.compile("\\.0*$");
    static boolean compatibleWithJavaBean = false;
    /**
     * 根据field name的大小写输出输入数据
     */
    static boolean compatibleWithFieldName = false;
    private static boolean setAccessibleEnable = true;

    private static boolean optionalClassInited = false;
    private static Class<?> optionalClass;
    private static boolean transientClassInited = false;
    private static Class<? extends Annotation> transientClass;
    private static Class<? extends Annotation> classOneToMany = null;
    private static boolean classOneToManyError = false;
    private static Class<? extends Annotation> classManyToMany = null;
    private static boolean classManyToManyError = false;
    private static Method methodHibernateIsInitialized = null;
    private static boolean methodHibernateIsInitializedError = false;
    private static Class<?> kotlinMetadataClasse;
    private static volatile boolean kotlinMetadataError;

    private static ConcurrentMap<String, Class<?>> mappings = new ConcurrentHashMap<>(256, 0.75f, 1);
    private static Class<?> pathClass;
    private static boolean pathClassError = false;
    private static Class<? extends Annotation> classJacksonCreator = null;
    private static boolean classJacksonCreatorError = false;
    private static  Class classXmlAccessorType = null;
    private static volatile boolean classXmlAccessorTypeError = false;
    private static Class<?> classDequeClasse = null;
    private static final String NOT_CAST = "can not cast to : ";
    private static final String CAN_NOT_TIMESTAMP = "can not cast to Timestamp, value : ";
    private static final String CAN_NOT_CAST_TO_DATE = "can not cast to Date, value : ";
    static {
        try {
            TypeUtils.compatibleWithJavaBean = "true".equals(IOUtils.getStringProperty(IOUtils.FASTJSON_COMPATIBLEWITHJAVABEAN));
            TypeUtils.compatibleWithFieldName = "true".equals(IOUtils.getStringProperty(IOUtils.FASTJSON_COMPATIBLEWITHFIELDNAME));
        } catch (RuntimeException e) {
            // skip
        }
        try {
            classDequeClasse = Class.forName("java.util.Deque");
        } catch (Throwable e) {
            // skip
        }
    }
    public static boolean isXmlField(Class<?> clazz) {
        try {
            Class<? extends Annotation> xmlAccessorType = Class.forName("javax.xml.bind.annotation.XmlAccessorType").asSubclass(Annotation.class);
            if (!clazz.isAnnotationPresent(xmlAccessorType)) {
                return false;
            }
            Annotation annotation = clazz.getAnnotation(xmlAccessorType);
            Class<?> xmlAccessType = Class.forName("javax.xml.bind.annotation.XmlAccessType");
            Field field = xmlAccessType.getField("FIELD");
            Object fieldValue = field.get(null);
            return fieldValue.equals(getAnnotationValue(annotation, "value"));
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            // Gestione dell'eccezione
        }
        return false;
    }

    private static Object getAnnotationValue(Annotation annotation, String attributeName) {
        try {
            Method method = annotation.annotationType().getDeclaredMethod(attributeName);
            return method.invoke(annotation);
        } catch (Exception e) {
            // Gestione dell'eccezione
        }
        return null;
    }

    public static Annotation getXmlAccessorType(Class<?> clazz) {
        if (classXmlAccessorType == null && !classXmlAccessorTypeError) {
            try {
                classXmlAccessorType = Class.forName("javax.xml.bind.annotation.XmlAccessorType");
            } catch (Throwable ex) {
                classXmlAccessorTypeError = true;
            }
        }
        if (classXmlAccessorType == null) {
            return null;
        }
        return TypeUtils.getAnnotation(clazz, classXmlAccessorType);
    }

    private static Function<Class<?>, Boolean> isClobFunction = new Function<Class<?>, Boolean>() {
        public Boolean apply(Class clazz) {
            return Clob.class.isAssignableFrom(clazz);
        }
    };
    public static boolean isClob(final Class<?> clazz) {
        Boolean isClob = ModuleUtil.callWhenHasJavaSql(isClobFunction, clazz);
        return isClob != null && isClob;
    }
    public static String castToString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
    public static Byte castToByte(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return byteValue((BigDecimal) value);
        }
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }
        if (value instanceof String) {
            String strVal = (String) value;
            if (strVal.length() == 0 //
                    || "null".equals(strVal) //
                    || "NULL".equals(strVal)) {
                return null;
            }
            return Byte.parseByte(strVal);
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? (byte) 1 : (byte) 0;
        }
        throw new JSONException("can not cast to byte, value : " + value);
    }
    public static Character castToChar(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Character) {
            return (Character) value;
        }
        if (value instanceof String) {
            String strVal = (String) value;
            if (strVal.length() == 0) {
                return null;
            }
            if (strVal.length() != 1) {
                throw new JSONException("can not cast to char, value : " + value);
            }
            return strVal.charAt(0);
        }
        throw new JSONException("can not cast to char, value : " + value);
    }
    public static Short castToShort(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return shortValue((BigDecimal) value);
        }
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        if (value instanceof String) {
            String strVal = (String) value;
            if (strVal.length() == 0 //
                    || "null".equals(strVal) //
                    || "NULL".equals(strVal)) {
                return null;
            }
            return Short.parseShort(strVal);
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? (short) 1 : (short) 0;
        }
        throw new JSONException("can not cast to short, value : " + value);
    }
    public static BigDecimal castToBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Float) {
            if (Float.isNaN((Float) value) || Float.isInfinite((Float) value)) {
                return null;
            }
        } else if (value instanceof Double) {
            if (Double.isNaN((Double) value) || Double.isInfinite((Double) value)) {
                return null;
            }
        } else if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        } else if (value instanceof Map && ((Map) value).size() == 0) {
            return null;
        }
        String strVal = value.toString();
        strVal = strValue(strVal);
        return new BigDecimal(strVal);
    }
    protected static String strValue(String strVal){
        if (strVal.length() == 0
                || strVal.equalsIgnoreCase("null")) {
            return null;
        }
        if (strVal.length() > 65535) {
            throw new JSONException("decimal overflow");
        }
        return strVal;
    }
    public static BigInteger castToBigInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Float) {
            Float floatValue = (Float) value;
            if (Float.isNaN(floatValue) || Float.isInfinite(floatValue)) {
                return null;
            }
            return BigInteger.valueOf(floatValue.longValue());
        } else if (value instanceof Double) {
            Double doubleValue = (Double) value;
            if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
                return null;
            }
            return BigInteger.valueOf(doubleValue.longValue());
        } else if (value instanceof BigInteger) {
            return (BigInteger) value;
        } else if (value instanceof BigDecimal) {
            BigDecimal decimal = (BigDecimal) value;
            int scale = decimal.scale();
            if (scale > -1000 && scale < 1000) {
                return ((BigDecimal) value).toBigInteger();
            }
        }
        String strVal = value.toString();
        strVal = strValueDecimal(strVal);
        return new BigInteger(strVal);
    }
    protected static String strValueDecimal(String strVal){
        String strValp = null;
        if (strVal.length() == 0
                || strVal.equalsIgnoreCase("null")) {
            return null;
        }
        if (strVal.length() > 65535) {
            throw new JSONException("decimal overflow");
        }
        strValp = strVal;
        return strValp;
    }
    public static Float castToFloat(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            String strVal = value.toString();
            if (strVal.length() == 0 //
                    || "null".equals(strVal) //
                    || "NULL".equals(strVal)) {
                return null;
            }
            if (strVal.indexOf(',') != -1) {
                strVal = strVal.replace(",", "");
            }
            return Float.parseFloat(strVal);
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? 1F : 0F;
        }
        throw new JSONException("can not cast to float, value : " + value);
    }
    public static Double castToDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            String strVal = value.toString();
            if (strVal.length() == 0 //
                    || "null".equals(strVal) //
                    || "NULL".equals(strVal)) {
                return null;
            }
            if (strVal.indexOf(',') != -1) {
                strVal = strVal.replace(",", "");
            }
            return Double.parseDouble(strVal);
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? 1D : 0D;
        }
        throw new JSONException("can not cast to double, value : " + value);
    }
    public static Date castToDate(Object value) {
        return castToDate(value, null);
    }
    public static Date castToDate(Object value, String format) {
        if (value == null) {
            return null;
        }

        if (value instanceof Date) {
            return (Date) value;
        }

        if (value instanceof Calendar) {
            return ((Calendar) value).getTime();
        }

        long longValue = -1;

        if (value instanceof BigDecimal) {
            longValue = longValue((BigDecimal) value);
        } else if (value instanceof Number) {
            longValue = ((Number) value).longValue();
            if ("unixtime".equals(format)) {
                longValue *= 1000;
            }
        } else if (value instanceof String) {
            String strValue = (String) value;
            longValue = parseLongValue(strValue);
            if (longValue == -1) {
                longValue = parseDateValue(strValue, format);
            }
        } else {
            throw new JSONException(CAN_NOT_CAST_TO_DATE + value);
        }

        if (longValue == -1) {
            throw new JSONException(CAN_NOT_CAST_TO_DATE + value);
        }

        return new Date(longValue);
    }

    private static long parseLongValue(String strValue) {
        if (strValue == null || strValue.isEmpty()) {
            return -1;
        }

        if (strValue.startsWith("/Date(") && strValue.endsWith(")/")) {
            strValue = strValue.substring(6, strValue.length() - 2);
        }

        try {
            return Long.parseLong(strValue);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long parseDateValue(String strValue, String format) {
        if (strValue == null || strValue.isEmpty()) {
            return -1;
        }

        try (JSONScanner dateLexer = new JSONScanner(strValue)) {
            if (dateLexer.scanISO8601DateIfMatch(false)) {
                Calendar calendar = dateLexer.getCalendar();
                return calendar.getTimeInMillis();
            }
        }

        if (format == null) {
            format = guessDateFormat(strValue);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(format, JSON.defaultLocale);
        dateFormat.setTimeZone(JSON.defaultTimeZone);

        try {
            return dateFormat.parse(strValue).getTime();
        } catch (ParseException e) {
            throw new JSONException(CAN_NOT_CAST_TO_DATE + strValue);
        }
    }

    private static String guessDateFormat(String strValue) {
        int length = strValue.length();
        if (length == JSON.DEFAULT_DATE_FORMAT.length()
                || (length == 22 && JSON.DEFAULT_DATE_FORMAT.equals("yyyyMMddHHmmssSSSZ"))) {
            return JSON.DEFAULT_DATE_FORMAT;
        } else if (length == 10) {
            return "yyyy-MM-dd";
        } else if (length == "yyyy-MM-dd HH:mm:ss".length()) {
            return "yyyy-MM-dd HH:mm:ss";
        } else if (length == 29
                && strValue.charAt(26) == ':'
                && strValue.charAt(28) == '0') {
            return "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
        } else if (length == 23 && strValue.charAt(19) == ',') {
            return "yyyy-MM-dd HH:mm:ss,SSS";
        } else {
            return "yyyy-MM-dd HH:mm:ss.SSS";
        }
    }

    private static Function<Object, Object> castToSqlDateFunction = new Function<Object, Object>() {
        public Object apply(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof java.sql.Date) {
                return value;
            }
            if (value instanceof Date) {
                return new java.sql.Date(((Date) value).getTime());
            }
            if (value instanceof Calendar) {
                return new java.sql.Date(((Calendar) value).getTimeInMillis());
            }
            long longValue = 0;
            if (value instanceof BigDecimal) {
                longValue = longValue((BigDecimal) value);
            } else if (value instanceof Number) {
                longValue = ((Number) value).longValue();
            }
            if (value instanceof String) {
                String strVal = (String) value;
                if (strVal.length() == 0 //
                        || "null".equals(strVal) //
                        || "NULL".equals(strVal)) {
                    return null;
                }
                if (isNumber(strVal)) {
                    longValue = Long.parseLong(strVal);
                } else {
                    JSONScanner scanner = new JSONScanner(strVal);
                    if (scanner.scanISO8601DateIfMatch(false)) {
                        longValue = scanner.getCalendar().getTime().getTime();
                    } else {
                        throw new JSONException(CAN_NOT_TIMESTAMP + strVal);
                    }
                    scanner.close();
                }
            }
            if (longValue <= 0) {
                throw new JSONException(CAN_NOT_CAST_TO_DATE + value);
            }
            return new java.sql.Date(longValue);
        }
    };
    public static Object castToSqlDate(final Object value) {
        return ModuleUtil.callWhenHasJavaSql(castToSqlDateFunction, value);
    }
    public static long longExtractValue(Number number) {
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).longValueExact();
        }
        return number.longValue();
    }
    private static Function<Object, Object> castToSqlTimeFunction = new Function<Object, Object>() {
        public Object apply(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof java.sql.Time) {
                return value;
            }
            if (value instanceof java.util.Date) {
                return new java.sql.Time(((java.util.Date) value).getTime());
            }
            if (value instanceof Calendar) {
                return new java.sql.Time(((Calendar) value).getTimeInMillis());
            }
            long longValue = 0;
            if (value instanceof BigDecimal) {
                longValue = longValue((BigDecimal) value);
            } else if (value instanceof Number) {
                longValue = ((Number) value).longValue();
            }
            if (value instanceof String) {
                String strVal = (String) value;
                if (strVal.length() == 0 //
                        || "null".equalsIgnoreCase(strVal)) {
                    return null;
                }
                if (isNumber(strVal)) {
                    longValue = Long.parseLong(strVal);
                } else {
                    if (strVal.length() == 8 && strVal.charAt(2) == ':' && strVal.charAt(5) == ':') {
                        return java.sql.Time.valueOf(strVal);
                    }
                    JSONScanner scanner = new JSONScanner(strVal);
                    if (scanner.scanISO8601DateIfMatch(false)) {
                        longValue = scanner.getCalendar().getTime().getTime();
                    } else {
                        throw new JSONException(CAN_NOT_TIMESTAMP + strVal);
                    }
                    scanner.close();
                }
            }
            if (longValue <= 0) {
                throw new JSONException(CAN_NOT_CAST_TO_DATE + value);
            }
            return new java.sql.Time(longValue);
        }
    };
    public static Object castToSqlTime(final Object value) {
        return ModuleUtil.callWhenHasJavaSql(castToSqlTimeFunction, value);
    }
    public static final Function<Object, Object> castToTimestampFunction = new Function<Object, Object>() {
        public Object apply(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Calendar) {
                return new java.sql.Timestamp(((Calendar) value).getTimeInMillis());
            }
            if (value instanceof java.sql.Timestamp) {
                return value;
            }
            if (value instanceof java.util.Date) {
                return new java.sql.Timestamp(((java.util.Date) value).getTime());
            }
            long longValue = 0;
            if (value instanceof BigDecimal) {
                longValue = longValue((BigDecimal) value);
            } else if (value instanceof Number) {
                longValue = ((Number) value).longValue();
            }
            if (value instanceof String) {
                String strVal = (String) value;
                if (strVal.length() == 0 //
                        || "null".equals(strVal) //
                        || "NULL".equals(strVal)) {
                    return null;
                }
                if (strVal.endsWith(".000000000")) {
                    strVal = strVal.substring(0, strVal.length() - 10);
                } else if (strVal.endsWith(".000000")) {
                    strVal = strVal.substring(0, strVal.length() - 7);
                }
                if (strVal.length() == 29
                        && strVal.charAt(4) == '-'
                        && strVal.charAt(7) == '-'
                        && strVal.charAt(10) == ' '
                        && strVal.charAt(13) == ':'
                        && strVal.charAt(16) == ':'
                        && strVal.charAt(19) == '.') {
                    int year = num(
                            strVal.charAt(0),
                            strVal.charAt(1),
                            strVal.charAt(2),
                            strVal.charAt(3));
                    int month = num(
                            strVal.charAt(5),
                            strVal.charAt(6));
                    int day = num(
                            strVal.charAt(8),
                            strVal.charAt(9));
                    int hour = num(
                            strVal.charAt(11),
                            strVal.charAt(12));
                    int minute = num(
                            strVal.charAt(14),
                            strVal.charAt(15));
                    int second = num(
                            strVal.charAt(17),
                            strVal.charAt(18));
                    int nanos = num(
                            strVal.charAt(20),
                            strVal.charAt(21),
                            strVal.charAt(22),
                            strVal.charAt(23),
                            strVal.charAt(24),
                            strVal.charAt(25),
                            strVal.charAt(26),
                            strVal.charAt(27),
                            strVal.charAt(28));
                    return new java.sql.Timestamp(year - 1900, month - 1, day, hour, minute, second, nanos);
                }
                if (isNumber(strVal)) {
                    longValue = Long.parseLong(strVal);
                } else {
                    JSONScanner scanner = new JSONScanner(strVal);
                    if (scanner.scanISO8601DateIfMatch(false)) {
                        longValue = scanner.getCalendar().getTime().getTime();
                    } else {
                        throw new JSONException(CAN_NOT_TIMESTAMP + strVal);
                    }
                    scanner.close();
                }
            }
            return new java.sql.Timestamp(longValue);
        }
    };
    public static Object castToTimestamp(final Object value) {
        return ModuleUtil.callWhenHasJavaSql(castToTimestampFunction, value);
    }
    static int num(char c0, char c1) {
        if (c0 >= '0'
                && c0 <= '9'
                && c1 >= '0'
                && c1 <= '9'
        ) {
            return (c0 - '0') * 10
                    + (c1 - '0');
        }
        return -1;
    }
    static int num(char c0, char c1, char c2, char c3) {
        if (c0 >= '0'
                && c0 <= '9'
                && c1 >= '0'
                && c1 <= '9'
                && c2 >= '0'
                && c2 <= '9'
                && c3 >= '0'
                && c3 <= '9'
        ) {
            return (c0 - '0') * 1000
                    + (c1 - '0') * 100
                    + (c2 - '0') * 10
                    + (c3 - '0');
        }
        return -1;
    }
    static int num(char c0, char c1, char c2, char c3, char c4, char c5, char c6, char c7, char c8) {
        if (c0 >= '0'
                && c0 <= '9'
                && c1 >= '0'
                && c1 <= '9'
                && c2 >= '0'
                && c2 <= '9'
                && c3 >= '0'
                && c3 <= '9'
                && c4 >= '0'
                && c4 <= '9'
                && c5 >= '0'
                && c5 <= '9'
                && c6 >= '0'
                && c6 <= '9'
                && c7 >= '0'
                && c7 <= '9'
                && c8 >= '0'
                && c8 <= '9'
        ) {
            return (c0 - '0') * 100000000
                    + (c1 - '0') * 10000000
                    + (c2 - '0') * 1000000
                    + (c3 - '0') * 100000
                    + (c4 - '0') * 10000
                    + (c5 - '0') * 1000
                    + (c6 - '0') * 100
                    + (c7 - '0') * 10
                    + (c8 - '0');
        }
        return -1;
    }
    public static boolean isNumber(String str) {
        for (int i = 0; i < str.length(); ++i) {
            char ch = str.charAt(i);
            if (ch == '+' || ch == '-') {
                if (i != 0) {
                    return false;
                }
            } else if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }
    public static Long castToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String strVal = trimToNull((String) value);
            if (strVal == null || strVal.equalsIgnoreCase("null")) {
                return null;
            }
            strVal = strVal.replace(",", "");
            try {
                return Long.parseLong(strVal);
            } catch (NumberFormatException ex) {
                // ignore exception
            }
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? 1L : 0L;
        }
        throw new JSONException("Can't cast to long, value: " + value);
    }
    private static String trimToNull(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        } else {
            return str.trim();
        }
    }


    public static byte byteValue(BigDecimal decimal) {
        if (decimal == null) {
            return 0;
        }
        int scale = decimal.scale();
        if (scale >= -100 && scale <= 100) {
            return decimal.byteValue();
        }
        return decimal.byteValueExact();
    }
    public static short shortValue(BigDecimal decimal) {
        if (decimal == null) {
            return 0;
        }
        int scale = decimal.scale();
        if (scale >= -100 && scale <= 100) {
            return decimal.shortValue();
        }
        return decimal.shortValueExact();
    }
    public static int intValue(BigDecimal decimal) {
        if (decimal == null) {
            return 0;
        }
        int scale = decimal.scale();
        if (scale >= -100 && scale <= 100) {
            return decimal.intValue();
        }
        return decimal.intValueExact();
    }
    public static long longValue(BigDecimal decimal) {
        if (decimal == null) {
            return 0;
        }
        int scale = decimal.scale();
        if (scale >= -100 && scale <= 100) {
            return decimal.longValue();
        }
        return decimal.longValueExact();
    }
    public static Integer castToInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }

        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).intValue();
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value instanceof String) {
            String strVal = ((String) value).trim().replace(",", "");
            if (strVal.length() == 0 || strVal.equalsIgnoreCase("null")) {
                return null;
            }
            Matcher matcher = NUMBER_WITH_TRAILING_ZEROS_PATTERN.matcher(strVal);
            if (matcher.find()) {
                strVal = matcher.replaceAll("");
            }
            return Integer.parseInt(strVal);
        }

        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.size() == 2 && map.containsKey("andIncrement") && map.containsKey(AND_DECREMENT_STRING)) {
                return castToInt(map.get(AND_DECREMENT_STRING));
            }
        }

        throw new JSONException("Cannot cast value to Integer: " + value);
    }
    public static byte[] castToBytes(Object value) {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof String) {
            return IOUtils.decodeBase64((String) value);
        }
        throw new JSONException("can not cast to byte[], value : " + value);
    }
    public static Boolean castToBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof BigDecimal || value instanceof Number) {
            return intValue(value) == 1;
        }
        if (value instanceof String) {
            String strVal = ((String) value).toLowerCase();
            if (strVal.isEmpty() || strVal.equals("null")) {
                return false;
            }
            if (strVal.equals("true") || strVal.equals("1") || strVal.equals("y") || strVal.equals("t")) {
                return true;
            }
            if (strVal.equals("false") || strVal.equals("0") || strVal.equals("n") || strVal.equals("f")) {
                return false;
            }
        }
        throw new JSONException("Cannot cast to boolean, value: " + value);
    }

    private static int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        throw new RuntimeException("Cannot convert value to int: " + value);
    }

    public static <T> T castToJavaBean(Object obj, Class<T> clazz) {
        return cast(obj, clazz, ParserConfig.getGlobalInstance());
    }
    private static BiFunction<Object, Class <?>, Object> castFunction = new BiFunction<Object, Class <?>, Object>() {
        public Object apply(Object obj, Class clazz) {
            if (clazz == java.sql.Date.class) {
                return castToSqlDate(obj);
            }
            if (clazz == java.sql.Time.class) {
                return castToSqlTime(obj);
            }
            if (clazz == java.sql.Timestamp.class) {
                return castToTimestamp(obj);
            }
            return null;
        }
    };
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T cast(final Object obj, final Class<T> clazz, ParserConfig config) {
        if (obj == null) {
            if (clazz == int.class) {
                return (T) Integer.valueOf(0);
            } else if (clazz == long.class) {
                return (T) Long.valueOf(0);
            } else if (clazz == short.class) {
                return (T) Short.valueOf((short) 0);
            } else if (clazz == byte.class) {
                return (T) Byte.valueOf((byte) 0);
            } else if (clazz == float.class) {
                return (T) Float.valueOf(0);
            } else if (clazz == double.class) {
                return (T) Double.valueOf(0);
            } else if (clazz == boolean.class) {
                return (T) Boolean.FALSE;
            }
            return null;
        }
        if (clazz == null) {
            throw new IllegalArgumentException("clazz is null");
        }
        if (clazz == obj.getClass()) {
            return (T) obj;
        }
        if (obj instanceof Map) {
            if (clazz == Map.class) {
                return (T) obj;
            }
            Map map = (Map) obj;
            if (clazz == Object.class && !map.containsKey(JSON.DEFAULT_TYPE_KEY)) {
                return (T) obj;
            }
            return castToJavaBean((Map<String, Object>) obj, clazz, config);
        }
        if (clazz.isArray()) {
            if (obj instanceof Collection) {
                Collection collection = (Collection) obj;
                int index = 0;
                Object array = Array.newInstance(clazz.getComponentType(), collection.size());
                for (Object item : collection) {
                    Object value = cast(item, clazz.getComponentType(), config);
                    Array.set(array, index, value);
                    index++;
                }
                return (T) array;
            }
            if (clazz == byte[].class) {
                return (T) castToBytes(obj);
            }
        }
        if (clazz.isAssignableFrom(obj.getClass())) {
            return (T) obj;
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return (T) castToBoolean(obj);
        }
        if (clazz == byte.class || clazz == Byte.class) {
            return (T) castToByte(obj);
        }
        if (clazz == char.class || clazz == Character.class) {
            return (T) castToChar(obj);
        }
        if (clazz == short.class || clazz == Short.class) {
            return (T) castToShort(obj);
        }
        if (clazz == int.class || clazz == Integer.class) {
            return (T) castToInt(obj);
        }
        if (clazz == long.class || clazz == Long.class) {
            return (T) castToLong(obj);
        }
        if (clazz == float.class || clazz == Float.class) {
            return (T) castToFloat(obj);
        }
        if (clazz == double.class || clazz == Double.class) {
            return (T) castToDouble(obj);
        }
        if (clazz == String.class) {
            return (T) castToString(obj);
        }
        if (clazz == BigDecimal.class) {
            return (T) castToBigDecimal(obj);
        }
        if (clazz == BigInteger.class) {
            return (T) castToBigInteger(obj);
        }
        if (clazz == Date.class) {
            return (T) castToDate(obj);
        }
        T retObj = (T) ModuleUtil.callWhenHasJavaSql(castFunction, obj, clazz);
        if (retObj != null) {
            return retObj;
        }
        if (clazz.isEnum()) {
            return castToEnum(obj, clazz, config);
        }
        if (Calendar.class.isAssignableFrom(clazz)) {
            Date date = castToDate(obj);
            Calendar calendar;
            if (clazz == Calendar.class) {
                calendar = Calendar.getInstance(JSON.defaultTimeZone, JSON.defaultLocale);
            } else {
                try {
                    calendar = (Calendar) clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new JSONException(NOT_CAST + clazz.getName(), e);
                }
            }
            calendar.setTime(date);
            return (T) calendar;
        }
        String className = clazz.getName();
        if (className.equals("javax.xml.datatype.XMLGregorianCalendar")) {
            Date date = castToDate(obj);
            Calendar calendar = Calendar.getInstance(JSON.defaultTimeZone, JSON.defaultLocale);
            calendar.setTime(date);
            return (T) CalendarCodec.instance.createXMLGregorianCalendar(calendar);
        }
        if (obj instanceof String) {
            String strVal = (String) obj;
            if (strVal.length() == 0 //
                    || "null".equals(strVal) //
                    || "NULL".equals(strVal)) {
                return null;
            }
            if (clazz == java.util.Currency.class) {
                return (T) java.util.Currency.getInstance(strVal);
            }
            if (clazz == java.util.Locale.class) {
                return (T) toLocale(strVal);
            }
            if (className.startsWith("java.time.")) {
                String json = JSON.toJSONString(strVal);
                return JSON.parseObject(json, clazz);
            }
        }
        final ObjectDeserializer objectDeserializer = config.get(clazz);
        if (objectDeserializer != null) {
            String str = JSON.toJSONString(obj);
            return JSON.parseObject(str, clazz);
        }
        throw new JSONException(NOT_CAST + clazz.getName());
    }
    public static Locale toLocale(String strVal) {
        String[] items = strVal.split("_");
        if (items.length == 1) {
            return new Locale(items[0]);
        }
        if (items.length == 2) {
            return new Locale(items[0], items[1]);
        }
        return new Locale(items[0], items[1], items[2]);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T castToEnum(Object obj, Class<T> clazz, ParserConfig mapping) {
        try {
            if (obj instanceof String) {
                String name = (String) obj;
                if (name.length() == 0) {
                    return null;
                }
                if (mapping == null) {
                    mapping = ParserConfig.getGlobalInstance();
                }
                ObjectDeserializer deserializer = mapping.getDeserializer(clazz);
                if (deserializer instanceof EnumDeserializer) {
                    EnumDeserializer enumDeserializer = (EnumDeserializer) deserializer;
                    return (T) enumDeserializer.getEnumByHashCode(TypeUtils.fnv1a64(name));
                }
                return (T) Enum.valueOf((Class<? extends Enum>) clazz, name);
            }
            if (obj instanceof BigDecimal) {
                int ordinal = intValue((BigDecimal) obj);
                Object[] values = clazz.getEnumConstants();
                if (ordinal < values.length) {
                    return (T) values[ordinal];
                }
            }
            if (obj instanceof Number) {
                int ordinal = ((Number) obj).intValue();
                Object[] values = clazz.getEnumConstants();
                if (ordinal < values.length) {
                    return (T) values[ordinal];
                }
            }
        } catch (Exception ex) {
            throw new JSONException(NOT_CAST + clazz.getName(), ex);
        }
        throw new JSONException(NOT_CAST + clazz.getName());
    }
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object obj, Type type, ParserConfig mapping) {
        if (obj == null) {
            return null;
        }
        if (type instanceof Class) {
            return cast(obj, (Class<T>) type, mapping);
        }
        if (type instanceof ParameterizedType) {
            return (T) cast(obj, (ParameterizedType) type, mapping);
        }
        if (obj instanceof String) {
            String strVal = (String) obj;
            if (strVal.length() == 0 //
                    || "null".equals(strVal) //
                    || "NULL".equals(strVal)) {
                return null;
            }
        }
        if (type instanceof TypeVariable) {
            return (T) obj;
        }
        throw new JSONException(NOT_CAST + type);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> T cast(Object obj, ParameterizedType type, ParserConfig mapping) throws NullPointerException{
        Type rawTye = type.getRawType();
        if (rawTye == List.class || rawTye == ArrayList.class) {
            Type itemType = type.getActualTypeArguments()[0];
            if (obj instanceof List) {
                List listObj = (List) obj;
                List arrayList = new ArrayList(listObj.size());
                for (Object item : listObj) {
                    Object itemValue;
                    if (itemType instanceof Class) {
                        if (item != null && item.getClass() == JSONObject.class) {
                            itemValue = item;
                        } else {
                            itemValue = cast(item, (Class<T>) itemType, mapping);
                        }
                    } else {
                        itemValue = cast(item, itemType, mapping);
                    }
                    arrayList.add(itemValue);
                }
                return (T) arrayList;
            }
        }
        if (rawTye == Set.class || rawTye == HashSet.class //
                || rawTye == TreeSet.class //
                || rawTye == Collection.class //
                || rawTye == List.class //
                || rawTye == ArrayList.class) {
            Type itemType = type.getActualTypeArguments()[0];
            if (obj instanceof Iterable) {
                Collection collection;
                if (rawTye == Set.class || rawTye == HashSet.class) {
                    collection = new HashSet();
                } else if (rawTye == TreeSet.class) {
                    collection = new TreeSet();
                } else {
                    collection = new ArrayList();
                }
                for (Object item : (Iterable) obj) {
                    Object itemValue = null;
                    if (itemType instanceof Class) {
                        if (item.getClass() != JSONObject.class) {
                            itemValue = cast(item, (Class<T>) itemType, mapping);
                        }
                    } else {
                        itemValue = cast(item, itemType, mapping);
                    }
                    collection.add(itemValue);
                }
                return (T) collection;
            }
        }
        if (rawTye == Map.class || rawTye == HashMap.class) {
            Type keyType = type.getActualTypeArguments()[0];
            Type valueType = type.getActualTypeArguments()[1];
            if (obj instanceof Map) {
                Map map = new HashMap();
                for (Map.Entry entry : ((Map<?, ?>) obj).entrySet()) {
                    Object key = cast(entry.getKey(), keyType, mapping);
                    Object value = cast(entry.getValue(), valueType, mapping);
                    map.put(key, value);
                }
                return (T) map;
            }
        }
        if (obj instanceof String) {
            String strVal = (String) obj;
            if (strVal.length() == 0) {
                return null;
            }
        }
        Type[] actualTypeArguments = type.getActualTypeArguments();
        if (actualTypeArguments.length == 1) {
            Type argType = type.getActualTypeArguments()[0];
            if (argType instanceof WildcardType) {
                return (T) cast(obj, rawTye, mapping);
            }
        }
        if (rawTye == Map.Entry.class && obj instanceof Map && ((Map) obj).size() == 1) {
            Map.Entry entry = (Map.Entry) ((Map) obj).entrySet().iterator().next();
            Object entryValue = entry.getValue();
            if (actualTypeArguments.length == 2 && entryValue instanceof Map) {
                Type valueType = actualTypeArguments[1];
                entry.setValue(
                        cast(entryValue, valueType, mapping)
                );
            }
            return (T) entry;
        }
        if (rawTye instanceof Class) {
            if (mapping == null) {
                mapping = ParserConfig.global;
            }
            ObjectDeserializer deserializer = mapping.getDeserializer(rawTye);
            if (deserializer != null) {
                String str = JSON.toJSONString(obj);
                DefaultJSONParser parser = new DefaultJSONParser(str, mapping);
                return (T) deserializer.deserialze(parser, type, null);
            }
        }
        throw new JSONException(NOT_CAST + type);
    }
    @SuppressWarnings({"unchecked"})
    public static <T> T castToJavaBean(Map<String, Object> map, Class<?> clazz, ParserConfig config) {
        try {
            Object iClassObject = map.get(JSON.DEFAULT_TYPE_KEY);
            if (iClassObject instanceof String) {
                String className = (String) iClassObject;
                Class<?> loadClazz;
                if (config == null) {
                    config = ParserConfig.global;
                }
                loadClazz = config.checkAutoType(className, null);
                if (loadClazz == null) {
                    throw new ClassNotFoundException(className + " not found");
                }
                if (!loadClazz.equals(clazz)) {
                    return (T) castToJavaBean(map, loadClazz, config);
                }
            }
            if (clazz == LinkedHashMap.class && map instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) map;
                Map<String, Object> innerMap = jsonObject.getInnerMap();
                if (innerMap instanceof LinkedHashMap) {
                    return (T) innerMap;
                }
            }
            JavaBeanDeserializer javaBeanDeser = null;
            ObjectDeserializer deserializer = config.getDeserializer(clazz);
            if (deserializer instanceof JavaBeanDeserializer) {
                javaBeanDeser = (JavaBeanDeserializer) deserializer;
            }
            if (javaBeanDeser == null) {
                throw new JSONException("can not get javaBeanDeserializer. " + clazz.getName());
            }
            return (T) javaBeanDeser.createInstance(map, config);
        } catch (Exception e) {
            throw new JSONException(e.getMessage(), e);
        }
    }
    public static StackTraceElement returnStackTraceElement(Map<String, Object> map, Class<?> clazz){
        if (clazz == StackTraceElement.class) {
            String declaringClass = (String) map.get("className");
            String methodName = (String) map.get("methodName");
            String fileName = (String) map.get("fileName");
            int lineNumber;

            lineNumber = extracted2(map);

            return  new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
        }
        return null;
    }

    private static int extracted2(Map<String, Object> map) {
        int lineNumber;
        Number value = (Number) map.get("lineNumber");
        if (value == null) {
            lineNumber = 0;
        } else if (value instanceof BigDecimal) {
            lineNumber = ((BigDecimal) value).intValueExact();
        } else {
            lineNumber = value.intValue();
        }
        return lineNumber;
    }
    public static Locale returnLocale(Map<String, Object> map, Class<?> clazz){
        if (clazz == Locale.class) {
            Object arg0 = map.get("language");
            Object arg1 = map.get("country");
            if (arg0 instanceof String) {
                String language = (String) arg0;
                if (arg1 instanceof String) {
                    String country = (String) arg1;
                    return new Locale(language, country);
                } else if (arg1 == null) {
                    return new Locale(language);
                }
            }
        }
        return null;
    }
    private static Function<Map<String, Class<?>>, Void> addBaseClassMappingsFunction =
            new Function<Map<String, Class<?>>, Void>() {
                public Void apply(Map<String, Class<?>> mappings) {
                    Class<?>[] classes = new Class[]{
                            java.sql.Time.class,
                            java.sql.Date.class,
                            java.sql.Timestamp.class
                    };
                    for (Class <?> clazz : classes) {
                        if (clazz == null) {
                            continue;
                        }
                        mappings.put(clazz.getName(), clazz);
                    }
                    return null;
                }
            };
    static {
        addBaseClassMappings();
    }
    private static void addBaseClassMappings() {
        mappings.put("byte", byte.class);
        mappings.put("short", short.class);
        mappings.put("int", int.class);
        mappings.put("long", long.class);
        mappings.put("float", float.class);
        mappings.put("double", double.class);
        mappings.put("boolean", boolean.class);
        mappings.put("char", char.class);
        mappings.put("[byte", byte[].class);
        mappings.put("[short", short[].class);
        mappings.put("[int", int[].class);
        mappings.put("[long", long[].class);
        mappings.put("[float", float[].class);
        mappings.put("[double", double[].class);
        mappings.put("[boolean", boolean[].class);
        mappings.put("[char", char[].class);
        mappings.put("[B", byte[].class);
        mappings.put("[S", short[].class);
        mappings.put("[I", int[].class);
        mappings.put("[J", long[].class);
        mappings.put("[F", float[].class);
        mappings.put("[D", double[].class);
        mappings.put("[C", char[].class);
        mappings.put("[Z", boolean[].class);
        Class<?>[] classes = new Class[]{
                Object.class,
                java.lang.Cloneable.class,
                loadClass("java.lang.AutoCloseable"),
                java.lang.Exception.class,
                java.lang.RuntimeException.class,
                java.lang.IllegalAccessError.class,
                java.lang.IllegalAccessException.class,
                java.lang.IllegalArgumentException.class,
                java.lang.IllegalMonitorStateException.class,
                java.lang.IllegalStateException.class,
                java.lang.IllegalThreadStateException.class,
                java.lang.IndexOutOfBoundsException.class,
                java.lang.InstantiationError.class,
                java.lang.InstantiationException.class,
                java.lang.InternalError.class,
                java.lang.InterruptedException.class,
                java.lang.LinkageError.class,
                java.lang.NegativeArraySizeException.class,
                java.lang.NoClassDefFoundError.class,
                java.lang.NoSuchFieldError.class,
                java.lang.NoSuchFieldException.class,
                java.lang.NoSuchMethodError.class,
                java.lang.NoSuchMethodException.class,
                java.lang.NullPointerException.class,
                java.lang.NumberFormatException.class,
                java.lang.OutOfMemoryError.class,
                java.lang.SecurityException.class,
                java.lang.StackOverflowError.class,
                java.lang.StringIndexOutOfBoundsException.class,
                java.lang.TypeNotPresentException.class,
                java.lang.VerifyError.class,
                java.lang.StackTraceElement.class,
                java.util.HashMap.class,
                java.util.LinkedHashMap.class,
                java.util.Hashtable.class,
                java.util.TreeMap.class,
                java.util.IdentityHashMap.class,
                java.util.WeakHashMap.class,
                java.util.LinkedHashMap.class,
                java.util.HashSet.class,
                java.util.LinkedHashSet.class,
                java.util.TreeSet.class,
                java.util.ArrayList.class,
                java.util.concurrent.TimeUnit.class,
                java.util.concurrent.ConcurrentHashMap.class,
                java.util.concurrent.atomic.AtomicInteger.class,
                java.util.concurrent.atomic.AtomicLong.class,
                java.util.Collections.EMPTY_MAP.getClass(),
                java.lang.Boolean.class,
                java.lang.Character.class,
                java.lang.Byte.class,
                java.lang.Short.class,
                java.lang.Integer.class,
                java.lang.Long.class,
                java.lang.Float.class,
                java.lang.Double.class,
                java.lang.Number.class,
                java.lang.String.class,
                java.math.BigDecimal.class,
                java.math.BigInteger.class,
                java.util.BitSet.class,
                java.util.Calendar.class,
                java.util.Date.class,
                java.util.Locale.class,
                java.util.UUID.class,
                java.text.SimpleDateFormat.class,
                com.alibaba.fastjson.JSONObject.class,
                com.alibaba.fastjson.JSONPObject.class,
                com.alibaba.fastjson.JSONArray.class,
        };
        for (Class <?> clazz : classes) {
            if (clazz == null) {
                continue;
            }
            mappings.put(clazz.getName(), clazz);
        }
        ModuleUtil.callWhenHasJavaSql(addBaseClassMappingsFunction, mappings);
    }
    public static void clearClassMapping() {
        mappings.clear();
        addBaseClassMappings();
    }
    public static void addMapping(String className, Class<?> clazz) {
        mappings.put(className, clazz);
    }
    public static Class<?> loadClass(String className) {
        return loadClass(className, null);
    }
    public static boolean isPath(Class<?> clazz) {
        if (pathClass == null && !pathClassError) {
            try {
                pathClass = Class.forName("java.nio.file.Path");
            } catch (Throwable ex) {
                pathClassError = true;
            }
        }
        if (pathClass != null) {
            return pathClass.isAssignableFrom(clazz);
        }
        return false;
    }
    public static Class<?> getClassFromMapping(String className) {
        return mappings.get(className);
    }
    public static Class<?> loadClass(String className, ClassLoader classLoader) {
        return loadClass(className, classLoader, false);
    }
    public static Class<?> loadClass(String className, ClassLoader classLoader, boolean cache) {
        if (classNameBool(className)) {
            return null;
        }
        if (className.length() > 198) {
            throw new JSONException("illegal className : " + className);
        }
        Class<?> clazz = mappings.get(className);
        if (clazz != null) {
            return clazz;
        }
        if (className.charAt(0) == '[') {
            Class<?> componentType = loadClass(className.substring(1), classLoader);
            return Array.newInstance(componentType, 0).getClass();
        }
        if (classNameBool2(className)) {
            String newClassName = className.substring(1, className.length() - 1);
            return loadClass(newClassName, classLoader);
        }
        try {
            if (classLoader != null) {
                clazz = classLoader.loadClass(className);
                if (cache) {
                    mappings.put(className, clazz);
                }
                return clazz;
            }
        } catch (Throwable e) {
            // skip
        }
        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null && contextClassLoader != classLoader) {
                clazz = contextClassLoader.loadClass(className);
                if (cache) {
                    mappings.put(className, clazz);
                }
                return clazz;
            }
        } catch (Throwable e) {
            // skip
        }
        clazz = loadClassTryBlock(clazz , className , cache);
        return clazz;
    }
    protected static boolean classNameBool(String className){
        return className == null || className.length() == 0;
    }
    protected static boolean classNameBool2(String className){
        return className.startsWith("L") && className.endsWith(";");
    }
    protected static Class<?> loadClassTryBlock(Class<?> clazz , String className , boolean cache){
        try {
            clazz = Class.forName(className);
            if (cache) {
                mappings.put(className, clazz);
            }
            return clazz;
        } catch (Throwable e) {
            // skip
        }
        return clazz;
    }
    public static SerializeBeanInfo buildBeanInfo(Class<?> beanType //
            , Map<String, String> aliasMap //
            , PropertyNamingStrategy propertyNamingStrategy) {
        return buildBeanInfo(beanType, aliasMap, propertyNamingStrategy, false);
    }
    public static SerializeBeanInfo buildBeanInfo(Class<?> beanType,
                                                  Map<String, String> aliasMap,
                                                  PropertyNamingStrategy propertyNamingStrategy,
                                                  boolean fieldBased) {
        JSONType jsonType = beanType.getAnnotation(JSONType.class);
        String[] orders = jsonType != null ? jsonType.orders() : null;
        int features = jsonType != null ? SerializerFeature.of(jsonType.serialzeFeatures()) : 0;
        String typeName = jsonType != null && jsonType.typeName().length() != 0 ? jsonType.typeName() : null;
        String typeKey = getTypeKeyFromSuperclasses(beanType);
        Map<String, Field> fieldCacheMap = new HashMap<>();
        ParserConfig.parserAllFieldToCache(beanType, fieldCacheMap);
        List<FieldInfo> fieldInfoList = fieldBased ?
                computeGettersWithFieldBase(beanType, aliasMap, false, propertyNamingStrategy) :
                computeGetters(beanType, jsonType, aliasMap, fieldCacheMap, false, propertyNamingStrategy);
        FieldInfo[] fields = fieldInfoList.toArray(new FieldInfo[0]);
        List<FieldInfo> sortedFieldList = getSortedFieldList(orders, fieldBased, beanType, aliasMap, propertyNamingStrategy, fieldCacheMap, jsonType);
        FieldInfo[] sortedFields = sortedFieldList.toArray(new FieldInfo[0]);
        if (Arrays.equals(sortedFields, fields)) {
            sortedFields = fields;
        }
        return new SerializeBeanInfo(beanType, jsonType, typeName, typeKey, features, fields, sortedFields);
    }
    private static String getTypeKeyFromSuperclasses(Class<?> beanType) {
        String typeKey = null;
        for (Class<?> superClass = beanType.getSuperclass(); superClass != null && superClass != Object.class; superClass = superClass.getSuperclass()) {
            JSONType superJsonType = superClass.getAnnotation(JSONType.class);
            if (superJsonType != null && superJsonType.typeKey().length() != 0) {
                typeKey = superJsonType.typeKey();
                break;
            }
        }
        if (typeKey != null && typeKey.length() == 0) {
            typeKey = null;
        }
        for (Class<?> interfaceClass : beanType.getInterfaces()) {
            JSONType superJsonType = interfaceClass.getAnnotation(JSONType.class);
            if (superJsonType != null && superJsonType.typeKey().length() != 0) {
                typeKey = superJsonType.typeKey();
                break;
            }
        }
        return typeKey;
    }
    private static List<FieldInfo> getSortedFieldList(String[] orders, boolean fieldBased, Class<?> beanType,
                                                      Map<String, String> aliasMap, PropertyNamingStrategy propertyNamingStrategy,
                                                      Map<String, Field> fieldCacheMap, JSONType jsonType) {
        List<FieldInfo> sortedFieldList;
        if (orders != null && orders.length != 0) {
            sortedFieldList = fieldBased ?
                    computeGettersWithFieldBase(beanType, aliasMap, true, propertyNamingStrategy) :
                    computeGetters(beanType, jsonType, aliasMap, fieldCacheMap, true, propertyNamingStrategy);
        } else {
            sortedFieldList = new ArrayList<>(computeGetters(beanType, jsonType, aliasMap, fieldCacheMap, false, propertyNamingStrategy));
            Collections.sort(sortedFieldList);
        }
        return sortedFieldList;
    }
    public static List<FieldInfo> computeGettersWithFieldBase(
            Class<?> clazz, //
            Map<String, String> aliasMap, //
            boolean sorted, //
            PropertyNamingStrategy propertyNamingStrategy) {
        Map<String, FieldInfo> fieldInfoMap = new LinkedHashMap<>();
        for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
            Field[] fields = currentClass.getDeclaredFields();
            computeFields(currentClass, aliasMap, propertyNamingStrategy, fieldInfoMap, fields);
        }
        return getFieldInfos(clazz, sorted, fieldInfoMap);
    }
    public static List<FieldInfo> computeGetters(Class<?> clazz, Map<String, String> aliasMap) {
        return computeGetters(clazz, aliasMap, true);
    }
    public static List<FieldInfo> computeGetters(Class<?> clazz, Map<String, String> aliasMap, boolean sorted) {
        JSONType jsonType = TypeUtils.getAnnotation(clazz, JSONType.class);
        Map<String, Field> fieldCacheMap = new HashMap<>();
        ParserConfig.parserAllFieldToCache(clazz, fieldCacheMap);
        return computeGetters(clazz, jsonType, aliasMap, fieldCacheMap, sorted, PropertyNamingStrategy.CAMEL_CASE);
    }
    public static List<FieldInfo> computeGetters(Class<?> clazz, //
                                                 JSONType jsonType, //
                                                 Map<String, String> aliasMap, //
                                                 Map<String, Field> fieldCacheMap, //
                                                 boolean sorted, //
                                                 PropertyNamingStrategy propertyNamingStrategy //
    ) {
        Map<String, FieldInfo> fieldInfoMap = new LinkedHashMap<>();
        boolean kotlin = TypeUtils.isKotlin();
        // for kotlin
        Constructor[] constructors = null;
        Annotation[][] paramAnnotationArrays = null;
        String[] paramNames = null;
        short[] paramNameMapping = null;
        Method[] methods = clazz.getMethods();
        try {
            Arrays.sort(methods, new MethodInheritanceComparator());
        } catch (Throwable ignored) {
            //skip
        }
        for (Method method : methods) {
            String methodName = method.getName();
            int ordinal = 0;
            int serialzeFeatures = 0;
            int parserFeatures = 0;


            /**
             *  如果在属性或者方法上存在JSONField注解，并且定制了name属性，不以类上的propertyNamingStrategy设置为准，以此字段的JSONField的name定制为准。
             */
            Boolean fieldAnnotationAndNameExists = false;
            JSONField annotation = TypeUtils.getAnnotation(method, JSONField.class);
            if (annotation == null) {
                annotation = getSuperMethodAnnotation(clazz, method);
            }
            if (annotation == null && kotlin) {
                if (constructors == null) {
                    constructors = clazz.getDeclaredConstructors();
                    Constructor <?> creatorConstructor = TypeUtils.getKotlinConstructor(constructors);
                    if (creatorConstructor != null) {
                        paramAnnotationArrays = TypeUtils.getParameterAnnotations(creatorConstructor);
                        paramNames = TypeUtils.getKoltinConstructorParameters(clazz);
                        if (paramNames != null) {
                            String[] paramNamesSorted = new String[paramNames.length];
                            System.arraycopy(paramNames, 0, paramNamesSorted, 0, paramNames.length);
                            Arrays.sort(paramNamesSorted);
                            paramNameMapping = new short[paramNames.length];
                            for (short p = 0; p < paramNames.length; p++) {
                                int index = Arrays.binarySearch(paramNamesSorted, paramNames[p]);
                                paramNameMapping[index] = p;
                            }
                            paramNames = paramNamesSorted;
                        }
                    }
                }
                if (paramNames != null && paramNameMapping != null && methodName.startsWith("get")) {
                    String propertyName = decapitalize(methodName.substring(3));
                    int p = Arrays.binarySearch(paramNames, propertyName);
                    if (p < 0) {
                        for (int i = 0; i < paramNames.length; i++) {
                            if (propertyName.equalsIgnoreCase(paramNames[i])) {
                                p = i;
                                break;
                            }
                        }
                    }
                    if (p >= 0) {
                        short index = paramNameMapping[p];
                        Annotation[] paramAnnotations = paramAnnotationArrays[index];
                        if (paramAnnotations != null) {
                            for (Annotation paramAnnotation : paramAnnotations) {
                                if (paramAnnotation instanceof JSONField) {
                                    annotation = (JSONField) paramAnnotation;
                                    break;
                                }
                            }
                        }
                        if (annotation == null) {
                            Field field = ParserConfig.getFieldFromCache(propertyName, fieldCacheMap);
                            if (field != null) {
                                annotation = TypeUtils.getAnnotation(field, JSONField.class);
                            }
                        }
                    }
                }
            }
            if (annotation != null) {

                ordinal = annotation.ordinal();
                serialzeFeatures = SerializerFeature.of(annotation.serialzeFeatures());
                parserFeatures = Feature.of(annotation.parseFeatures());
                if (annotation.name().length() != 0) {
                    String propertyName = annotation.name();
                    if (aliasMap != null) {
                        propertyName = aliasMap.get(propertyName);

                    }
                    FieldInfo fieldInfo = new FieldInfo(propertyName, method, null, clazz, null, ordinal,
                            serialzeFeatures, parserFeatures, annotation, null);
                    fieldInfoMap.put(propertyName, fieldInfo);
                }
            }
            if (methodName.startsWith("get")) {


                char c3 = methodName.charAt(3);
                String propertyName;
                Field field = null;
                if (Character.isUpperCase(c3) //
                        || c3 > 512 // for unicode method name
                ) {
                    if (compatibleWithJavaBean) {
                        propertyName = decapitalize(methodName.substring(3));
                    } else {
                        propertyName = TypeUtils.getPropertyNameByMethodName(methodName);
                    }
                    propertyName = getPropertyNameByCompatibleFieldName(fieldCacheMap, methodName, propertyName, 3);
                } else if (c3 == '_') {
                    propertyName = methodName.substring(3);
                    field = fieldCacheMap.get(propertyName);
                    if (field == null) {
                        String temp = propertyName;
                        propertyName = methodName.substring(4);
                        field = ParserConfig.getFieldFromCache(propertyName, fieldCacheMap);
                        if (field == null) {
                            propertyName = temp; //减少修改代码带来的影响
                        }
                    }
                } else if (c3 == 'f') {
                    propertyName = methodName.substring(3);
                } else if (methodName.length() >= 5 && Character.isUpperCase(methodName.charAt(4))) {
                    propertyName = decapitalize(methodName.substring(3));
                } else {
                    propertyName = methodName.substring(3);
                    field = ParserConfig.getFieldFromCache(propertyName, fieldCacheMap);

                }


                if (field == null) {
                    // 假如bean的field很多的情况一下，轮询时将大大降低效率
                    field = ParserConfig.getFieldFromCache(propertyName, fieldCacheMap);
                }
                if (field == null && propertyName.length() > 1) {
                    char ch = propertyName.charAt(1);
                    if (ch >= 'A' && ch <= 'Z') {
                        String javaBeanCompatiblePropertyName = decapitalize(methodName.substring(3));
                        field = ParserConfig.getFieldFromCache(javaBeanCompatiblePropertyName, fieldCacheMap);
                    }
                }
                JSONField fieldAnnotation = null;
                if (field != null) {
                    fieldAnnotation = TypeUtils.getAnnotation(field, JSONField.class);
                    if (fieldAnnotation != null) {

                        ordinal = fieldAnnotation.ordinal();
                        serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
                        parserFeatures = Feature.of(fieldAnnotation.parseFeatures());
                        if (fieldAnnotation.name().length() != 0) {
                            fieldAnnotationAndNameExists = true;
                            propertyName = fieldAnnotation.name();
                            if (aliasMap != null) {
                                propertyName = aliasMap.get(propertyName);
                            }
                        }
                    }
                }
                if (aliasMap != null) {
                    propertyName = aliasMap.get(propertyName);
                }
                if (propertyNamingStrategy != null && !fieldAnnotationAndNameExists) {
                    propertyName = propertyNamingStrategy.translate(propertyName);
                }
                FieldInfo fieldInfo = new FieldInfo(propertyName, method, field, clazz, null, ordinal, serialzeFeatures, parserFeatures,
                        annotation, fieldAnnotation);
                fieldInfoMap.put(propertyName, fieldInfo);
            }
            if (methodName.startsWith("is")) {

                char c2 = methodName.charAt(2);
                String propertyName;
                Field field = null;
                if (Character.isUpperCase(c2)) {
                    if (compatibleWithJavaBean) {
                        propertyName = decapitalize(methodName.substring(2));
                    } else {
                        propertyName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
                    }
                    propertyName = getPropertyNameByCompatibleFieldName(fieldCacheMap, methodName, propertyName, 2);
                } else if (c2 == '_') {
                    propertyName = methodName.substring(3);
                    field = fieldCacheMap.get(propertyName);
                    if (field == null) {
                        String temp = propertyName;
                        propertyName = methodName.substring(2);
                        field = ParserConfig.getFieldFromCache(propertyName, fieldCacheMap);
                        if (field == null) {
                            propertyName = temp;
                        }
                    }
                } else if (c2 == 'f') {
                    propertyName = methodName.substring(2);
                } else {
                    propertyName = methodName.substring(2);
                    field = ParserConfig.getFieldFromCache(propertyName, fieldCacheMap);
                }

                if (field == null) {
                    field = ParserConfig.getFieldFromCache(propertyName, fieldCacheMap);
                }
                if (field == null) {
                    field = ParserConfig.getFieldFromCache(methodName, fieldCacheMap);
                }
                JSONField fieldAnnotation = null;
                if (field != null) {
                    fieldAnnotation = TypeUtils.getAnnotation(field, JSONField.class);
                    if (fieldAnnotation != null) {
                        ordinal = fieldAnnotation.ordinal();
                        serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
                        parserFeatures = Feature.of(fieldAnnotation.parseFeatures());
                        if (fieldAnnotation.name().length() != 0) {
                            propertyName = fieldAnnotation.name();
                            if (aliasMap != null) {
                                propertyName = aliasMap.get(propertyName);
                            }
                        }
                    }
                }
                if (aliasMap != null) {
                    propertyName = aliasMap.get(propertyName);

                }
                if (propertyNamingStrategy != null) {
                    propertyName = propertyNamingStrategy.translate(propertyName);
                }
                FieldInfo fieldInfo = new FieldInfo(propertyName, method, field, clazz, null, ordinal, serialzeFeatures, parserFeatures,
                        annotation, fieldAnnotation);
                fieldInfoMap.put(propertyName, fieldInfo);
            }
        }
        Field[] fields = clazz.getFields();
        computeFields(clazz, aliasMap, propertyNamingStrategy, fieldInfoMap, fields);
        return getFieldInfos(clazz, sorted, fieldInfoMap);
    }
    private static List<FieldInfo> getFieldInfos(Class<?> clazz, boolean sorted, Map<String, FieldInfo> fieldInfoMap) {
        List<FieldInfo> fieldInfoList = new ArrayList<>();
        String[] orders = null;
        JSONType annotation = TypeUtils.getAnnotation(clazz, JSONType.class);
        if (annotation != null) {
            orders = annotation.orders();
        }
        if (orders != null && orders.length > 0) {
            LinkedHashMap<String, FieldInfo> map = new LinkedHashMap<>(fieldInfoMap.size());
            for (FieldInfo field : fieldInfoMap.values()) {
                map.put(field.name, field);
            }
            for (String item : orders) {
                FieldInfo field = map.get(item);
                if (field != null) {
                    fieldInfoList.add(field);
                    map.remove(item);
                }
            }
            fieldInfoList.addAll(map.values());
        } else {
            fieldInfoList.addAll(fieldInfoMap.values());
            if (sorted) {
                Collections.sort(fieldInfoList);
            }
        }
        return fieldInfoList;
    }
    private static void computeFields(Class<?> clazz, Map<String, String> aliasMap, PropertyNamingStrategy propertyNamingStrategy,
                                      Map<String, FieldInfo> fieldInfoMap, Field[] fields) {
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                JSONField fieldAnnotation = field.getAnnotation(JSONField.class);
                if (fieldAnnotation == null || fieldAnnotation.serialize()) {
                    putFieldInfo(fieldAnnotation,fieldInfoMap,clazz,field,aliasMap,propertyNamingStrategy);
                }
            }
        }
    }
    public static void putFieldInfo(JSONField fieldAnnotation,Map<String, FieldInfo> fieldInfoMap,Class<?> clazz,Field field,Map<String, String> aliasMap,PropertyNamingStrategy propertyNamingStrategy){
        int ordinal = 0;
        int serialzeFeatures = 0;
        int parserFeatures = 0;
        String propertyName = field.getName();

        if (fieldAnnotation != null) {
            ordinal = fieldAnnotation.ordinal();
            serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
            parserFeatures = Feature.of(fieldAnnotation.parseFeatures());
            if (fieldAnnotation.name().length() != 0) {
                propertyName = fieldAnnotation.name();
            }
        }
        if (aliasMap != null) {
            propertyName = aliasMap.get(propertyName);
        }
        if (propertyNamingStrategy != null) {
            propertyName = propertyNamingStrategy.translate(propertyName);
        }
        if (!fieldInfoMap.containsKey(propertyName)) {
            FieldInfo fieldInfo = new FieldInfo(propertyName, null, field, clazz, null, ordinal, serialzeFeatures, parserFeatures,
                    null, fieldAnnotation);
            fieldInfoMap.put(propertyName, fieldInfo);
        }
    }
    private static String getPropertyNameByCompatibleFieldName(Map<String, Field> fieldCacheMap, String methodName,
                                                               String propertyName, int fromIdx) {
        if (compatibleWithFieldName &&  (!fieldCacheMap.containsKey(propertyName))) {
            String tempPropertyName = methodName.substring(fromIdx);
            return fieldCacheMap.containsKey(tempPropertyName) ? tempPropertyName : propertyName;

        }
        return propertyName;
    }
    public static JSONField getSuperMethodAnnotation(final Class<?> clazz, final Method method) {
        JSONField annotation = null;

        // Check superclass methods
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && !Modifier.isAbstract(superClass.getModifiers())) {
            Method superMethod = getMethod(superClass, method.getName(), method.getParameterTypes());
            annotation = getAnnotation(superMethod);
        }

        // Check interface methods
        for (Class<?> interfaceClass : clazz.getInterfaces()) {
            Method interfaceMethod = getMethod(interfaceClass, method.getName(), method.getParameterTypes());
            annotation = getAnnotation(interfaceMethod);
            if (annotation != null) {
                break;
            }
        }

        return annotation;
    }

    private static Method getMethod(Class<?> clazz, String name, Class<?>[] parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static JSONField getAnnotation(Method method) {
        return method != null ? method.getAnnotation(JSONField.class) : null;
    }


    private static boolean isJSONTypeIgnore(Class<?> clazz, String propertyName) {
        JSONType jsonType = TypeUtils.getAnnotation(clazz, JSONType.class);
        if (jsonType != null) {
            String[] fields = jsonType.includes().length > 0 ? jsonType.includes() : jsonType.ignores();
            for (String field : fields) {
                if ((propertyName == null) || propertyName.equals(field)) {
                    return jsonType.includes().length == 0;
                }
            }
        }
        return (clazz.getSuperclass() != Object.class && clazz.getSuperclass() != null) && isJSONTypeIgnore(clazz.getSuperclass(), propertyName);
    }
    public static boolean isGenericParamType(Type type) {
        if (type instanceof ParameterizedType) {
            return true;
        }
        if (type instanceof Class) {
            Type superType = ((Class<?>) type).getGenericSuperclass();
            return superType != Object.class && isGenericParamType(superType);
        }
        return false;
    }
    public static Type getGenericParamType(Type type) {
        if (type instanceof ParameterizedType) {
            return type;
        }
        if (type instanceof Class) {
            return getGenericParamType(((Class<?>) type).getGenericSuperclass());
        }
        return type;
    }
    public static Type unwrapOptional(Type type) {
        if (!optionalClassInited) {
            try {
                optionalClass = Class.forName("java.util.Optional");
            } catch (Exception e) {
                // skip
            } finally {
                optionalClassInited = true;
            }
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType() == optionalClass) {
                return parameterizedType.getActualTypeArguments()[0];
            }
        }
        return type;
    }
    public static Class<?> getClass(Type type) {
        if (type.getClass() == Class.class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return getClass(((ParameterizedType) type).getRawType());
        }
        if (type instanceof TypeVariable) {
            Type boundType = ((TypeVariable<?>) type).getBounds()[0];
            if (boundType instanceof Class) {
                return (Class) boundType;
            }
            return getClass(boundType);
        }
        if (type instanceof WildcardType) {
            Type[] upperBounds = ((WildcardType) type).getUpperBounds();
            if (upperBounds.length == 1) {
                return getClass(upperBounds[0]);
            }
        }
        return Object.class;
    }
    public static Field getField(Class<?> clazz, String fieldName, Field[] declaredFields) {
        for (Field field : declaredFields) {
            String itemName = field.getName();
            if (fieldName.equals(itemName)) {
                return field;
            }
            char c0;
            char c1;
            if (fieldName.length() > 2
                    && (c0 = fieldName.charAt(0)) >= 'a' && c0 <= 'z'
                    && (c1 = fieldName.charAt(1)) >= 'A' && c1 <= 'Z'
                    && fieldName.equalsIgnoreCase(itemName)) {
                return field;
            }
        }
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return getField(superClass, fieldName, superClass.getDeclaredFields());
        }
        return null;
    }

    public static int getSerializeFeatures(Class<?> clazz) {
        JSONType annotation = TypeUtils.getAnnotation(clazz, JSONType.class);
        if (annotation == null) {
            return 0;
        }
        return SerializerFeature.of(annotation.serialzeFeatures());
    }
    public static int getParserFeatures(Class<?> clazz) {
        JSONType annotation = TypeUtils.getAnnotation(clazz, JSONType.class);
        if (annotation == null) {
            return 0;
        }
        return Feature.of(annotation.parseFeatures());
    }
    public static String decapitalize(String name) {
        if (name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
    /**
     * resolve property name from get/set method name
     *
     * @param methodName get/set method name
     * @return property name
     */
    public static String getPropertyNameByMethodName(String methodName) {
        return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
    }
    static void setAccessible(AccessibleObject obj) {
        if (!setAccessibleEnable) {
            return;
        }
        if (obj.trySetAccessible()) {
            return;
        }
        try {
            //skip
        } catch (AccessControlException error) {
            setAccessibleEnable = false;
        }
    }
    public static Type getCollectionItemType(Type fieldType) {
        if (fieldType instanceof ParameterizedType) {
            return getCollectionItemType((ParameterizedType) fieldType);
        }
        if (fieldType instanceof Class<?>) {
            return getCollectionItemType((Class<?>) fieldType);
        }
        return Object.class;
    }
    private static Type getCollectionItemType(Class<?> clazz) {
        return clazz.getName().startsWith("java.")
                ? Object.class
                : getCollectionItemType(getCollectionSuperType(clazz));
    }
    private static Type getCollectionItemType(ParameterizedType parameterizedType) {
        Type rawType = parameterizedType.getRawType();
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (rawType == Collection.class) {
            return getWildcardTypeUpperBounds(actualTypeArguments[0]);
        }
        Class<?> rawClass = (Class<?>) rawType;
        Map<TypeVariable, Type> actualTypeMap = createActualTypeMap(rawClass.getTypeParameters(), actualTypeArguments);
        Type superType = getCollectionSuperType(rawClass);
        if (superType instanceof ParameterizedType) {
            Class<?> superClass = getRawClass(superType);
            Type[] superClassTypeParameters = ((ParameterizedType) superType).getActualTypeArguments();
            return superClassTypeParameters.length > 0
                    ? getCollectionItemType(makeParameterizedType(superClass, superClassTypeParameters, actualTypeMap))
                    : getCollectionItemType(superClass);
        }
        return getCollectionItemType((Class<?>) superType);
    }
    private static Type getCollectionSuperType(Class<?> clazz) {
        Type assignable = null;
        for (Type type : clazz.getGenericInterfaces()) {
            Class<?> rawClass = getRawClass(type);
            if (rawClass == Collection.class) {
                return type;
            }
            if (Collection.class.isAssignableFrom(rawClass)) {
                assignable = type;
            }
        }
        return assignable == null ? clazz.getGenericSuperclass() : assignable;
    }
    private static Map<TypeVariable, Type> createActualTypeMap(TypeVariable[] typeParameters, Type[] actualTypeArguments) {
        int length = typeParameters.length;
        Map<TypeVariable, Type> actualTypeMap = new HashMap<>(length);
        for (int i = 0; i < length; i++) {
            actualTypeMap.put(typeParameters[i], actualTypeArguments[i]);
        }
        return actualTypeMap;
    }
    private static ParameterizedType makeParameterizedType(Class<?> rawClass, Type[] typeParameters, Map<?, Type> actualTypeMap) {
        int length = typeParameters.length;
        Type[] actualTypeArguments = new Type[length];
        for (int i = 0; i < length; i++) {
            actualTypeArguments[i] = getActualType(typeParameters[i], actualTypeMap);
        }
        return new ParameterizedTypeImpl(actualTypeArguments, null, rawClass);
    }
    private static Type getActualType(Type typeParameter, Map<?, Type> actualTypeMap) {
        if (typeParameter instanceof TypeVariable) {
            return actualTypeMap.get(typeParameter);
        } else if (typeParameter instanceof ParameterizedType) {
            return makeParameterizedType(getRawClass(typeParameter), ((ParameterizedType) typeParameter).getActualTypeArguments(), actualTypeMap);
        } else if (typeParameter instanceof GenericArrayType) {
            return new GenericArrayTypeImpl(getActualType(((GenericArrayType) typeParameter).getGenericComponentType(), actualTypeMap));
        }
        return typeParameter;
    }
    private static Type getWildcardTypeUpperBounds(Type type) {
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            return upperBounds.length > 0 ? upperBounds[0] : Object.class;
        }
        return type;
    }
    public static Class<?> getCollectionItemClass(Type fieldType) {
        if (fieldType instanceof ParameterizedType) {
            Class<?> itemClass;
            Type actualTypeArgument = ((ParameterizedType) fieldType).getActualTypeArguments()[0];
            if (actualTypeArgument instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) actualTypeArgument;
                Type[] upperBounds = wildcardType.getUpperBounds();
                if (upperBounds.length == 1) {
                    actualTypeArgument = upperBounds[0];
                }
            }
            if (actualTypeArgument instanceof Class) {
                itemClass = (Class<?>) actualTypeArgument;
                if (!Modifier.isPublic(itemClass.getModifiers())) {
                    throw new JSONException("can not create ASMParser");
                }
            } else {
                throw new JSONException("can not create ASMParser");
            }
            return itemClass;
        }
        return Object.class;
    }
    private static final HashMap<Class<?>, String> primitiveTypeMap = new HashMap<>(8);

    static{
        primitiveTypeMap.put(boolean.class, "Z");
        primitiveTypeMap.put(char.class, "C");
        primitiveTypeMap.put(byte.class, "B");
        primitiveTypeMap.put(short.class, "S");
        primitiveTypeMap.put(int.class, "I");
        primitiveTypeMap.put(long.class, "J");
        primitiveTypeMap.put(float.class, "F");
        primitiveTypeMap.put(double.class, "D");

    }
    public static Type checkPrimitiveArray(GenericArrayType genericArrayType) {
        Type clz = genericArrayType;
        Type genericComponentType = genericArrayType.getGenericComponentType();
        String prefix = "[";
        while (genericComponentType instanceof GenericArrayType) {
            genericComponentType = ((GenericArrayType) genericComponentType)
                    .getGenericComponentType();
        }
        if (genericComponentType instanceof Class<?>) {
            Class<?> ck = (Class<?>) genericComponentType;
            if (ck.isPrimitive()) {
                try {
                    String postfix = primitiveTypeMap.get(ck);
                    if (postfix != null) {
                        clz = Class.forName(prefix + postfix);
                    }
                } catch (ClassNotFoundException ignored) {
                    //skip
                }
            }
        }
        return clz;
    }
    public static Set<Object> createSet(Type type) {
        Class<?> rawClass = getRawClass(type);
        Set<Object> set;
        if (rawClass == AbstractCollection.class //
                || rawClass == Collection.class) {
            set = new HashSet<>();
        } else if (rawClass.isAssignableFrom(HashSet.class)) {
            set = new HashSet<>();
        } else if (rawClass.isAssignableFrom(LinkedHashSet.class)) {
            set = new LinkedHashSet<>();
        } else if (rawClass.isAssignableFrom(TreeSet.class)) {
            set = new TreeSet<>();
        } else if (rawClass.isAssignableFrom(EnumSet.class)) {
            Type itemType;
            if (type instanceof ParameterizedType) {
                itemType = ((ParameterizedType) type).getActualTypeArguments()[0];
            } else {
                itemType = Object.class;
            }
            set = EnumSet.noneOf((Class<Enum>) itemType);
        } else {
            try {
                set = (Set) rawClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new JSONException("create instance error, class " + rawClass.getName());
            }
        }
        return set;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Collection createCollection(Type type) {
        Class<?> rawClass = getRawClass(type);
        Collection list =  null;
        if (rawClass == AbstractCollection.class //
                || rawClass == Collection.class) {
            list = new ArrayList();
        } else if (rawClass.isAssignableFrom(HashSet.class)) {
            list = new HashSet();
        } else if (rawClass.isAssignableFrom(LinkedHashSet.class)) {
            list = new LinkedHashSet();
        } else if (rawClass.isAssignableFrom(TreeSet.class)) {
            list = new TreeSet();
        } else if (rawClass.isAssignableFrom(ArrayList.class)) {
            list = new ArrayList();
        } else if (rawClass.isAssignableFrom(EnumSet.class)) {
            Type itemType;
            if (type instanceof ParameterizedType) {
                itemType = ((ParameterizedType) type).getActualTypeArguments()[0];
            } else {
                itemType = Object.class;
            }
            list = EnumSet.noneOf((Class<Enum>) itemType);
        } else if (rawClass.isAssignableFrom(Queue.class)
                || (classDequeClasse != null && rawClass.isAssignableFrom(classDequeClasse))) {
            list = new LinkedList();
        } else {
            list = tryBlock(type);
        }
        return list;
    }
    protected static Collection <String> tryBlock(Type type){
        Class<?> rawClass = getRawClass(type);
        Collection<String> pList = null;
        try {
            //Skip
        } catch (Exception e) {
            throw new JSONException("create instance error, class " + rawClass.getName());
        }
        return pList;
    }
    public static Class<?> getRawClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return getRawClass(((ParameterizedType) type).getRawType());
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length == 1) {
                return getRawClass(upperBounds[0]);
            } else {
                throw new JSONException("TODO");
            }
        } else {
            throw new JSONException("TODO");
        }
    }
    private static final Set<String> isProxyClassNames = new HashSet<>(6);
    static{
        isProxyClassNames.add("net.sf.cglib.proxy.Factory");
        isProxyClassNames.add("org.springframework.cglib.proxy.Factory");
        isProxyClassNames.add("javassist.util.proxy.ProxyObject");
        isProxyClassNames.add("org.apache.ibatis.javassist.util.proxy.ProxyObject");
        isProxyClassNames.add("org.hibernate.proxy.HibernateProxy");
        isProxyClassNames.add("org.springframework.context.annotation.ConfigurationClassEnhancer$EnhancedConfiguration");

    }
    public static boolean isProxy(Class<?> clazz) {
        for (Class<?> item : clazz.getInterfaces()) {
            String interfaceName = item.getName();
            if (isProxyClassNames.contains(interfaceName)) {
                return true;
            }
        }
        return false;
    }
    public static boolean isTransient(Method method) {
        if (method == null) {
            return false;
        }
        if (!transientClassInited) {
            try {
                transientClass = (Class<? extends Annotation>) Class.forName("java.beans.Transient");
            } catch (Exception e) {
                // skip
            } finally {
                transientClassInited = true;
            }
        }
        if (transientClass != null) {
            Annotation annotation = TypeUtils.getAnnotation(method, transientClass);
            return annotation != null;
        }
        return false;
    }
    public static boolean isAnnotationPresentOneToMany(Method method) {
        if (method == null) {
            return false;
        }
        if (classOneToMany == null && !classOneToManyError) {
            try {
                classOneToMany = (Class<? extends Annotation>) Class.forName("javax.persistence.OneToMany");
            } catch (Throwable e) {
                // skip
                classOneToManyError = true;
            }
        }
        return classOneToMany != null && method.isAnnotationPresent(classOneToMany);
    }
    public static boolean isAnnotationPresentManyToMany(Method method) {
        if (method == null) {
            return false;
        }
        if (classManyToMany == null && !classManyToManyError) {
            try {
                classManyToMany = (Class<? extends Annotation>) Class.forName("javax.persistence.ManyToMany");
            } catch (Throwable e) {
                // skip
                classManyToManyError = true;
            }
        }
        return classManyToMany != null && (method.isAnnotationPresent(classOneToMany) || method.isAnnotationPresent(classManyToMany));
    }
    public static boolean isHibernateInitialized(Object object) {
        if (object == null) {
            return false;
        }
        if (methodHibernateIsInitialized == null && !methodHibernateIsInitializedError) {
            try {
                Class<?> classHibernate = Class.forName("org.hibernate.Hibernate");
                methodHibernateIsInitialized = classHibernate.getMethod("isInitialized", Object.class);
            } catch (Throwable e) {
                // skip
                methodHibernateIsInitializedError = true;
            }
        }
        if (methodHibernateIsInitialized != null) {
            try {
                return (Boolean) methodHibernateIsInitialized.invoke(null, object);
            } catch (Throwable e) {
                // skip
            }
        }
        return true;
    }
    public static double parseDouble(String str) {
        final int len = str.length();
        if (len > 10) {
            return Double.parseDouble(str);
        }
        boolean negative = false;
        long longValue = 0;
        int scale = 0;
        for (int i = 0; i < len; ++i) {
            char ch = str.charAt(i);
            if (ch == '-' && i == 0) {
                negative = true;
            }
            if (ch == '.') {
                if (scale != 0) {
                    return Double.parseDouble(str);
                }
                scale = len - i - 1;
                continue;
            }
            if (chValue(ch)) {
                int digit = ch - '0';
                longValue = longValue * 10 + digit;
            } else {
                return Double.parseDouble(str);
            }
        }
        if (negative) {
            longValue = -longValue;
        }
        switch (scale) {
            case 0:
                return longValue;
            case 1:
                return ((double) longValue) / 10;
            case 2:
                return ((double) longValue) / 100;
            case 3:
                return ((double) longValue) / 1000;
            case 4:
                return ((double) longValue) / 10000;
            case 5:
                return ((double) longValue) / 100000;
            case 6:
                return ((double) longValue) / 1000000;
            case 7:
                return ((double) longValue) / 10000000;
            case 8:
                return ((double) longValue) / 100000000;
            case 9:
                return ((double) longValue) / 1000000000;
            default:
                break;
        }
        return Double.parseDouble(str);
    }
    protected static boolean chValue(char ch){
        return ch >= '0' && ch <= '9';
    }
    public static float parseFloat(String str) {
        final int len = str.length();
        if (len >= 10) {
            return Float.parseFloat(str);
        }
        boolean negative = false;
        long longValue = 0;
        int scale = 0;
        for (int i = 0; i < len; ++i) {
            char ch = str.charAt(i);
            if (ch == '-' && i == 0) {
                negative = true;
            }
            if (ch == '.') {
                if (scale != 0) {
                    return Float.parseFloat(str);
                }
                scale = len - i - 1;
                continue;
            }
            if (chValue(ch)) {
                int digit = ch - '0';
                longValue = longValue * 10 + digit;
            } else {
                return Float.parseFloat(str);
            }
        }
        if (negative) {
            longValue = -longValue;
        }
        switch (scale) {
            case 0:
                return longValue;
            case 1:
                return ((float) longValue) / 10;
            case 2:
                return ((float) longValue) / 100;
            case 3:
                return ((float) longValue) / 1000;
            case 4:
                return ((float) longValue) / 10000;
            case 5:
                return ((float) longValue) / 100000;
            case 6:
                return ((float) longValue) / 1000000;
            case 7:
                return ((float) longValue) / 10000000;
            case 8:
                return ((float) longValue) / 100000000;
            case 9:
                return ((float) longValue) / 1000000000;
            default:
                break;
        }
        return Float.parseFloat(str);
    }
    public static final long FNV1A_64_MAGIC_HASHCODE= 0xcbf29ce484222325L;
    public static final long FNV1A_64_MAGIC_PRIME = 0x100000001b3L;
    public static long fnv1a64Extract(String key) {
        long hashCode = FNV1A_64_MAGIC_HASHCODE;
        for (int i = 0; i < key.length(); ++i) {
            char ch = key.charAt(i);
            if (ch == '_' || ch == '-') {
                continue;
            }
            if (ch >= 'A' && ch <= 'Z') {
                ch = (char) (ch + 32);
            }
            hashCode ^= ch;
            hashCode *= FNV1A_64_MAGIC_PRIME;
        }
        return hashCode;
    }
    public static long fnv1a64Lower(String key) {
        long hashCode = FNV1A_64_MAGIC_HASHCODE;
        for (int i = 0; i < key.length(); ++i) {
            char ch = key.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                ch = (char) (ch + 32);
            }
            hashCode ^= ch;
            hashCode *= FNV1A_64_MAGIC_PRIME;
        }
        return hashCode;
    }
    public static long fnv1a64(String key) {
        long hashCode = FNV1A_64_MAGIC_HASHCODE;
        for (int i = 0; i < key.length(); ++i) {
            char ch = key.charAt(i);
            hashCode ^= ch;
            hashCode *= FNV1A_64_MAGIC_PRIME;
        }
        return hashCode;
    }
    public static boolean isKotlin() {
        if (kotlinMetadataClasse == null && !kotlinMetadataError) {
            try {
                kotlinMetadataClasse = Class.forName("kotlin.Metadata");
            } catch (Throwable e) {
                kotlinMetadataError = true;
            }
        }
        return true;
    }
    public static Constructor<Object> getKotlinConstructor(Constructor[] constructors) {
        return getKotlinConstructor(constructors, null);
    }
    public static Constructor<Object> getKotlinConstructor(Constructor[] constructors, String[] paramNames) {
        Constructor<?> creatorConstructor = null;
        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (paramNames != null && parameterTypes.length != paramNames.length) {
                //skip
            }
            if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1].isInstance("kotlin.jvm.internal.DefaultConstructorMarker")) {
                //skip
            }
            if (creatorConstructor != null && creatorConstructor.getParameterTypes().length >= parameterTypes.length) {
                continue;
            }
            creatorConstructor = constructor;
        }
        return null;
    }
    public static String[] getKoltinConstructorParameters(Class <?> clazz) {
        try {
            Class <?> classKotlinKclass = Class.forName("kotlin.reflect.jvm.internal.KClassImpl");
            Constructor<?> kotlinKclassConstructor = classKotlinKclass.getConstructor(Class.class);
            Object kclassImpl = kotlinKclassConstructor.newInstance(clazz);
            Method kotlinKfunctionGetParameters = Class.forName("kotlin.reflect.KFunction").getMethod("getParameters");
            Method kotlinKparameterGetName = Class.forName("kotlin.reflect.KParameter").getMethod("getName");
            Object constructor = null;
            Object[] constructors = (Object[]) Class.forName(KOTLIN_COLLECTION).getMethod("toArray").invoke(null, Class.forName(KOTLIN_COLLECTION).getMethod("filterNotNull").invoke(null, Class.forName(KOTLIN_COLLECTION).getMethod("mapNotNull").invoke(null, Class.forName(KOTLIN_COLLECTION).getMethod("toList").invoke(null, Class.forName("kotlin.reflect.jvm.internal.KClassImpl").getMethod("getConstructors").invoke(kclassImpl)), new Object() {
                public final Object invoke(Object it) {
                    return it;
                }
            }), new Object() {
                public final boolean invoke(Object it) {
                    return it != null;
                }
            }));
            for (Object item : constructors) {
                List <?> parameters = (List) kotlinKfunctionGetParameters.invoke(item);
                if (constructor != null && parameters.isEmpty()) {
                    continue;
                }
                constructor = item;
            }
            if (constructor == null) {
                return null;
            }
            List <?> parameters = (List) kotlinKfunctionGetParameters.invoke(constructor);
            String[] names = new String[parameters.size()];
            for (int i = 0; i < parameters.size(); i++) {
                Object param = parameters.get(i);
                String name;
                name = extracted(kotlinKparameterGetName, param);
                names[i] = name;
            }
            return names;
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private static String extracted(Method kotlinKparameterGetName, Object param)
            throws IllegalAccessException, InvocationTargetException {
        String name;
        try {
            name = (String) kotlinKparameterGetName.invoke(param);
        } catch (RuntimeException  ignored) {
            name = null;
        }
        return name;
    }


    public static <A extends Annotation> A getAnnotation(Class<?> targetClass, Class<A> annotationClass) {
        A targetAnnotation = targetClass.getAnnotation(annotationClass);

        if (targetAnnotation != null) {
            return targetAnnotation;
        }

        Type type = JSON.getMixInAnnotations(targetClass);
        if (type instanceof Class<?>) {
            Class<?> mixInClass = (Class<?>) type;
            A mixInAnnotation = mixInClass.getAnnotation(annotationClass);
            if (mixInAnnotation != null) {
                return mixInAnnotation;
            }
            Annotation[] annotations = mixInClass.getAnnotations();
            for (Annotation annotation : annotations) {
                mixInAnnotation = annotation.annotationType().getAnnotation(annotationClass);
                if (mixInAnnotation != null) {
                    return mixInAnnotation;
                }
            }
        }

        Annotation[] targetClassAnnotations = targetClass.getAnnotations();
        for (Annotation annotation : targetClassAnnotations) {
            targetAnnotation = annotation.annotationType().getAnnotation(annotationClass);
            if (targetAnnotation != null) {
                return targetAnnotation;
            }
        }

        return null;
    }

    public static <A extends Annotation> A getAnnotation(Field field, Class<A> annotationClass) {
        A targetAnnotation = field.getAnnotation(annotationClass);
        Class<?> clazz = field.getDeclaringClass();
        A mixInAnnotation;
        Class<?> mixInClass = null;
        Type type = JSON.getMixInAnnotations(clazz);
        if (type instanceof Class<?>) {
            mixInClass = (Class<?>) type;
        }
        if (mixInClass != null) {
            Field mixInField = null;
            String fieldName = field.getName();
            // 递归从MixIn类的父类中查找注解（如果有父类的话）
            for (Class<?> currClass = mixInClass; currClass != null && currClass != Object.class;
                 currClass = currClass.getSuperclass()) {
                try {
                    mixInField = currClass.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    // skip
                }
            }
            if (mixInField == null) {
                return targetAnnotation;
            }
            mixInAnnotation = mixInField.getAnnotation(annotationClass);
            if (mixInAnnotation != null) {
                return mixInAnnotation;
            }
        }
        return targetAnnotation;
    }
    public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationClass) {
        A targetAnnotation = method.getAnnotation(annotationClass);
        Class<?> clazz = method.getDeclaringClass();
        A mixInAnnotation;
        Class<?> mixInClass = null;
        Type type = JSON.getMixInAnnotations(clazz);
        if (type instanceof Class<?>) {
            mixInClass = (Class<?>) type;
        }
        if (mixInClass != null) {
            Method mixInMethod = null;
            String methodName = method.getName();
            Class<?>[] parameterTypes = method.getParameterTypes();
            // 递归从MixIn类的父类中查找注解（如果有父类的话）
            for (Class<?> currClass = mixInClass; currClass != null && currClass != Object.class;
                 currClass = currClass.getSuperclass()) {
                try {
                    mixInMethod = currClass.getDeclaredMethod(methodName, parameterTypes);
                    break;
                } catch (NoSuchMethodException e) {
                    // skip
                }
            }
            if (mixInMethod == null) {
                return targetAnnotation;
            }
            mixInAnnotation = mixInMethod.getAnnotation(annotationClass);
            if (mixInAnnotation != null) {
                return mixInAnnotation;
            }
        }
        return targetAnnotation;
    }
    public static Annotation[][] getParameterAnnotations(Method method) {
        Annotation[][] targetAnnotations = method.getParameterAnnotations();
        Class<?> clazz = method.getDeclaringClass();
        Annotation[][] mixInAnnotations;
        Class<?> mixInClass = null;
        Type type = JSON.getMixInAnnotations(clazz);
        if (type instanceof Class<?>) {
            mixInClass = (Class<?>) type;
        }
        if (mixInClass != null) {
            Method mixInMethod = null;
            String methodName = method.getName();
            Class<?>[] parameterTypes = method.getParameterTypes();
            // 递归从MixIn类的父类中查找注解（如果有父类的话）
            for (Class<?> currClass = mixInClass; currClass != null && currClass != Object.class;
                 currClass = currClass.getSuperclass()) {
                try {
                    mixInMethod = currClass.getDeclaredMethod(methodName, parameterTypes);
                    break;
                } catch (NoSuchMethodException e) {
                    //skip
                }
            }
            if (mixInMethod == null) {
                return targetAnnotations;
            }
            mixInAnnotations = mixInMethod.getParameterAnnotations();
            if (mixInAnnotations != null) {
                return mixInAnnotations;
            }
        }
        return targetAnnotations;
    }
    public static Annotation[][] getParameterAnnotations(Constructor<?> constructor) {
        Annotation[][] targetAnnotations = constructor.getParameterAnnotations();
        Class<?> clazz = constructor.getDeclaringClass();
        Annotation[][] mixInAnnotations = null;
        Type type = JSON.getMixInAnnotations(clazz);
        if (type instanceof Class<?>) {
            Class<? extends Annotation> mixInClass = (Class<? extends Annotation>) type;
            Constructor<?> mixInConstructor = getMixInConstructor(constructor.getParameterTypes(), mixInClass);
            if (mixInConstructor != null) {
                mixInAnnotations = mixInConstructor.getParameterAnnotations();
            }
        }
        return mixInAnnotations != null ? mixInAnnotations : targetAnnotations;
    }

    private static Constructor<?> getMixInConstructor(Class<?>[] parameterTypes, Class<? extends Annotation> mixInClass) {
        Constructor<?> mixInConstructor = null;
        int level = 0;
        List<Class<?>> enclosingClasses = new ArrayList<>(2);
        for (Class<?> enclosingClass = mixInClass.getEnclosingClass(); enclosingClass != null; enclosingClass = enclosingClass.getEnclosingClass()) {
            enclosingClasses.add(enclosingClass);
            level++;
        }
        for (Class<?> currClass = mixInClass; currClass != null && currClass != Object.class; currClass = currClass.getSuperclass()) {
            try {
                Class<?>[] constructorParamTypes = level != 0 ? Arrays.copyOf(enclosingClasses.toArray(new Class[0]), level + parameterTypes.length) : parameterTypes;
                if (level != 0) {
                    System.arraycopy(parameterTypes, 0, constructorParamTypes, level, parameterTypes.length);
                }
                mixInConstructor = mixInClass.getDeclaredConstructor(constructorParamTypes);
                break;
            } catch (NoSuchMethodException e) {
                level--;
            }
        }
        return mixInConstructor;
    }


    public static boolean isJacksonCreator(Method method) {
        if (method == null) {
            return false;
        }
        if (classJacksonCreator == null && !classJacksonCreatorError) {
            try {
                classJacksonCreator = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonCreator");
            } catch (Throwable e) {
                // skip
                classJacksonCreatorError = true;
            }
        }
        return classJacksonCreator != null && method.isAnnotationPresent(classJacksonCreator);
    }
    private static Object optionalEmpty;
    private static boolean optionalError = false;
    public static Object optionalEmpty(Type type) {
        if (optionalError) {
            return null;
        }
        Class<?> clazz = getClass(type);
        if (clazz == null) {
            return null;
        }
        String className = clazz.getName();
        if ("java.util.Optional".equals(className)) {
            if (optionalEmpty == null) {
                try {
                    Method empty = Class.forName(className).getMethod("empty");
                    optionalEmpty = empty.invoke(null);
                } catch (Throwable e) {
                    optionalError = true;
                }
            }
            return optionalEmpty;
        }
        return null;
    }
    public static class MethodInheritanceComparator implements Comparator<Method> {
        public int compare(Method m1, Method m2) {
            int cmp = m1.getName().compareTo(m2.getName());
            if (cmp != 0) {
                return cmp;
            }
            Class<?> class1 = m1.getReturnType();
            Class<?> class2 = m2.getReturnType();
            if (class1.equals(class2)) {
                return 0;
            }
            if (class1.isAssignableFrom(class2)) {
                return -1;
            }

            if (class2.isAssignableFrom(class1)) {
                return 1;
            }
            return 0;
        }
    }
}