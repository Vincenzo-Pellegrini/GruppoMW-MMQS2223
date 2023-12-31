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
package com.alibaba.fastjson;

import static com.alibaba.fastjson.util.TypeUtils.castToBigDecimal;
import static com.alibaba.fastjson.util.TypeUtils.castToBigInteger;
import static com.alibaba.fastjson.util.TypeUtils.castToBoolean;
import static com.alibaba.fastjson.util.TypeUtils.castToByte;
import static com.alibaba.fastjson.util.TypeUtils.castToBytes;
import static com.alibaba.fastjson.util.TypeUtils.castToDate;
import static com.alibaba.fastjson.util.TypeUtils.castToDouble;
import static com.alibaba.fastjson.util.TypeUtils.castToFloat;
import static com.alibaba.fastjson.util.TypeUtils.castToInt;
import static com.alibaba.fastjson.util.TypeUtils.castToLong;
import static com.alibaba.fastjson.util.TypeUtils.castToShort;
import static com.alibaba.fastjson.util.TypeUtils.castToSqlDate;
import static com.alibaba.fastjson.util.TypeUtils.castToTimestamp;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.TypeUtils;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class JSONObject extends JSON implements Map<String, Object>, Serializable, InvocationHandler {

    private static final long         serialVersionUID         = 1L;
    private static final int          DEFAULT_INITIAL_CAPACITY = 16;
    private static final String ILLEGAL_GETTER = "illegal getter";
    private static final String ILLEGAL_SETTER = "illegal setter";

    private final transient Map<String, Object> map;

    public JSONObject(){
        this(DEFAULT_INITIAL_CAPACITY, false);
    }

    public JSONObject(Map<String, Object> map){
        if (map == null) {
            throw new IllegalArgumentException("map is null.");
        }
        this.map = map;
    }

    public JSONObject(boolean ordered){
        this(DEFAULT_INITIAL_CAPACITY, ordered);
    }

    public JSONObject(int initialCapacity){
        this(initialCapacity, false);
    }

    public JSONObject(int initialCapacity, boolean ordered){
        if (ordered) {
            map = new LinkedHashMap<>(initialCapacity);
        } else {
            map = new HashMap<>(initialCapacity);
        }
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(Object key) {
        boolean result = map.containsKey(key);
        if (!result && (key instanceof Number || key instanceof Character || key instanceof Boolean || key instanceof UUID)) {

            result = map.containsKey(key.toString());

        }
        return result;
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public Object get(Object key) {
        Object val = map.get(key);

        if (val == null && (key instanceof Number || key instanceof Character || key instanceof Boolean || key instanceof UUID)) {

            val = map.get(key.toString());

        }

        return val;
    }

    public JSONObject getJSONObject(String key) {
        Object value = map.get(key);

        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }

        if (value instanceof Map) {
            return new JSONObject((Map) value);
        }

        if (value instanceof String) {
            return JSON.parseObject((String) value);
        }

        return (JSONObject) toJSON(value);
    }

    public JSONArray getJSONArray(String key) {
        Object value = map.get(key);

        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }

        if (value instanceof List) {
            return new JSONArray((List) value);
        }

        if (value instanceof String) {
            return (JSONArray) JSON.parse((String) value);
        }

        return (JSONArray) toJSON(value);
    }

    public <T> T getObject(String key, Class<T> clazz) {
        Object obj = map.get(key);
        return TypeUtils.castToJavaBean(obj, clazz);
    }

    public <T> T getObject(String key, Type type) {
        Object obj = map.get(key);
        return TypeUtils.cast(obj, type, ParserConfig.getGlobalInstance());
    }

    public <T> T getObject(String key, TypeReference<Object> typeReference) {
        Object obj = map.get(key);
        if (typeReference == null) {
            return (T) obj;
        }
        return TypeUtils.cast(obj, typeReference.getType(), ParserConfig.getGlobalInstance());
    }

    public Boolean getBoolean(String key) {
        Object value = get(key);

        if (value == null) {
            return false;
        }

        return castToBoolean(value);
    }

    public byte[] getBytes(String key) {
        Object value = get(key);

        if (value == null) {
            return new byte[0];
        }

        return castToBytes(value);
    }

    public boolean getBooleanValue(String key) {
        Object value = get(key);

        Boolean booleanVal = castToBoolean(value);
        if (booleanVal == null) {
            return false;
        }

        return booleanVal.booleanValue();
    }

    public Byte getByte(String key) {
        Object value = get(key);

        return castToByte(value);
    }

    public byte getByteValue(String key) {
        Object value = get(key);

        Byte byteVal = castToByte(value);
        if (byteVal == null) {
            return 0;
        }

        return byteVal.byteValue();
    }

    public Short getShort(String key) {
        Object value = get(key);

        return castToShort(value);
    }

    public short getShortValue(String key) {
        Object value = get(key);

        Short shortVal = castToShort(value);
        if (shortVal == null) {
            return 0;
        }

        return shortVal.shortValue();
    }

    public Integer getInteger(String key) {
        Object value = get(key);

        return castToInt(value);
    }

    public int getIntValue(String key) {
        Object value = get(key);

        Integer intVal = castToInt(value);
        if (intVal == null) {
            return 0;
        }

        return intVal.intValue();
    }

    public Long getLong(String key) {
        Object value = get(key);

        return castToLong(value);
    }

    public long getLongValue(String key) {
        Object value = get(key);

        Long longVal = castToLong(value);
        if (longVal == null) {
            return 0L;
        }

        return longVal.longValue();
    }

    public Float getFloat(String key) {
        Object value = get(key);

        return castToFloat(value);
    }

    public float getFloatValue(String key) {
        Object value = get(key);

        Float floatValue = castToFloat(value);
        if (floatValue == null) {
            return 0F;
        }

        return floatValue.floatValue();
    }

    public Double getDouble(String key) {
        Object value = get(key);

        return castToDouble(value);
    }

    public double getDoubleValue(String key) {
        Object value = get(key);

        Double doubleValue = castToDouble(value);
        if (doubleValue == null) {
            return 0D;
        }

        return doubleValue.doubleValue();
    }

    public BigDecimal getBigDecimal(String key) {
        Object value = get(key);

        return castToBigDecimal(value);
    }

    public BigInteger getBigInteger(String key) {
        Object value = get(key);

        return castToBigInteger(value);
    }

    public String getString(String key) {
        Object value = get(key);

        if (value == null) {
            return null;
        }

        return value.toString();
    }

    public Date getDate(String key) {
        Object value = get(key);

        return castToDate(value);
    }

    public Object getSqlDate(String key) {
        Object value = get(key);

        return castToSqlDate(value);
    }

    public Object getTimestamp(String key) {
        Object value = get(key);

        return castToTimestamp(value);
    }

    public Object put(String key, Object value) {
        return map.put(key, value);
    }

    public JSONObject fluentPut(String key, Object value) {
        map.put(key, value);
        return this;
    }

    public void putAll(Map<? extends String, ?> m) {
        map.putAll(m);
    }

    public JSONObject fluentPutAll(Map<String, ?> m) {
        map.putAll(m);
        return this;
    }

    public void clear() {
        map.clear();
    }

    public JSONObject fluentClear() {
        map.clear();
        return this;
    }

    public Object remove(Object key) {
        return map.remove(key);
    }

    public JSONObject fluentRemove(Object key) {
        map.remove(key);
        return this;
    }

    public Set<String> keySet() {
        return map.keySet();
    }

    public Collection<Object> values() {
        return map.values();
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        return map.entrySet();
    }



    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof JSONObject) {
            return this.map.equals(((JSONObject) obj).map);
        }

        return this.map.equals(obj);
    }

    @Override
    public int hashCode() {
        return this.map.hashCode();
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 1) {
            if (method.getName().equals("equals")) {
                return this.equals(args[0]);
            }

            Class<?> returnType = method.getReturnType();
            extracted(returnType);

            String name = null;
            JSONField annotation = TypeUtils.getAnnotation(method, JSONField.class);
            name = extracted2(name, annotation);

            name = extracted3(method, name);

            map.put(name, args[0]);
            return null;
        }

        if (parameterTypes.length == 0) {
            Class<?> returnType = method.getReturnType();
            extracted6(returnType);

            String name = null;
            JSONField annotation = TypeUtils.getAnnotation(method, JSONField.class);
            name = extracted2(name, annotation);

            if (name == null) {
                name = method.getName();
                if (name.startsWith("get")) {
                    name = name.substring(3);
                    extracted4(name);
                    name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                } else if (name.startsWith("is")) {
                    name = name.substring(2);
                    extracted4(name);
                    name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                } else if (name.startsWith("hashCode")) {
                    return this.hashCode();
                } else if (name.startsWith("toString")) {
                    return this.toString();
                } else {
                    throw new JSONException(ILLEGAL_GETTER);
                }
            }

            Object value = map.get(name);
            return TypeUtils.cast(value, method.getGenericReturnType(), ParserConfig.getGlobalInstance());
        }

        throw new UnsupportedOperationException(method.toGenericString());
    }

    private void extracted6(Class<?> returnType) {
        if (returnType == void.class) {
            throw new JSONException(ILLEGAL_GETTER);
        }
    }

    private void extracted4(String name){
        if (name.length() == 0) {
            throw new JSONException(ILLEGAL_GETTER);
        }
    }

    private String extracted3(Method method, String name){
        if (name == null) {
            name = method.getName();

            if (!name.startsWith("set")) {
                throw new JSONException(ILLEGAL_SETTER);
            }

            name = name.substring(3);
            if (name.length() == 0) {
                throw new JSONException(ILLEGAL_SETTER);
            }
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    private String extracted2(String name, JSONField annotation) {
        if (annotation != null && annotation.name().length() != 0) {

            name = annotation.name();

        }
        return name;
    }

    private void extracted(Class<?> returnType){
        if (returnType != void.class) {
            throw new JSONException(ILLEGAL_SETTER);
        }
    }

    public Map<String, Object> getInnerMap() {
        return this.map;
    }



    private void readObject(final java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        SecureObjectInputStream.ensureFields();
        if (SecureObjectInputStream.fields != null && !SecureObjectInputStream.fieldsError) {
            ObjectInputStream secIn = new SecureObjectInputStream(in);
            try {
                secIn.defaultReadObject();
                return;
            } catch (java.io.NotActiveException e) {
                // skip
            }
        }

        in.defaultReadObject();
        for (Entry<String, Object> entry : map.entrySet()) {
            final Object key = entry.getKey();
            if (key != null) {
                ParserConfig.global.checkAutoType(key.getClass());
            }

            final Object value = entry.getValue();
            if (value != null) {
                ParserConfig.global.checkAutoType(value.getClass());
            }
        }
    }

    static class SecureObjectInputStream extends ObjectInputStream {
        static Field[] fields;
        static volatile boolean fieldsError;

        static void ensureFields() {
            if (fields == null && !fieldsError) {

                final Field[] declaredFields = ObjectInputStream.class.getDeclaredFields();
                String[] fieldnames = new String[]{"bin", "passHandle", "handles", "curContext"};
                Field[] array = new Field[fieldnames.length];
                for (int i = 0; i < fieldnames.length; i++) {
                    Field field = TypeUtils
                            .getField(ObjectInputStream.class
                                    , fieldnames[i]
                                    , declaredFields
                            );
                    array[i] = field;
                }
                fields = array;

            }
        }

        public SecureObjectInputStream(ObjectInputStream in) throws IOException {
            super(in);
        }
        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc)
                throws IOException, ClassNotFoundException {
            String name = desc.getName();
            if (name.length() > 2) {
                int index = name.lastIndexOf('[');
                if (index != -1) {
                    name = name.substring(index + 1);
                }
                if (name.length() > 2 && name.charAt(0) == 'L' && name.charAt(name.length() - 1) == ';') {
                    name = name.substring(1, name.length() - 1);
                }

                if (TypeUtils.getClassFromMapping(name) == null) {
                    ParserConfig.global.checkAutoType(name, null, Feature.SUPPORT_AUTO_TYPE.mask);
                }
            }
            return super.resolveClass(desc);
        }
        @Override
        protected Class<?> resolveProxyClass(String[] interfaces)
                throws IOException, ClassNotFoundException {
            for (String interfacename : interfaces) {
                //检查是否处于黑名单
                if (TypeUtils.getClassFromMapping(interfacename) == null) {
                    ParserConfig.global.checkAutoType(interfacename, null);
                }
            }
            return super.resolveProxyClass(interfaces);
        }

        //Hack:默认构造方法会调用这个方法，重写此方法使用反射还原部分关键属性
        @Override
        protected void readStreamHeader() throws IOException{
            //Metodo inutilizzato 
        }
    }

    @Override
    public <T> T toJavaObject(Class<T> clazz) {
        if (clazz == Map.class || clazz == JSONObject.class || clazz == JSON.class) {
            return (T) this;
        }

        if (clazz == Object.class && !containsKey(JSON.DEFAULT_TYPE_KEY)) {
            return (T) this;
        }

        return TypeUtils.castToJavaBean(this, clazz, ParserConfig.getGlobalInstance());
    }


    public <T> T toJavaObject(Class<T> clazz, ParserConfig config) {
        if (clazz == Map.class) {
            return (T) this;
        }

        if (clazz == Object.class && !containsKey(JSON.DEFAULT_TYPE_KEY)) {
            return (T) this;
        }

        return TypeUtils.castToJavaBean(this, clazz, config);
    }
}
