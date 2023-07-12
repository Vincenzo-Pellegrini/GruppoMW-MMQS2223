package com.alibaba.fastjson.parser.deserializer;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.serializer.BeanContext;
import com.alibaba.fastjson.util.FieldInfo;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class FieldDeserializer {

    static final String         JAVA_UTIL_COLLECTIONS_UNMODIFIABLE  = "java.util.Collections$Unmodifiable";

    public final FieldInfo fieldInfo;

    protected final Class<?> clazz;

    protected BeanContext beanContext;

    protected FieldDeserializer(Class<?> clazz, FieldInfo fieldInfo) {
        this.clazz = clazz;
        this.fieldInfo = fieldInfo;
    }

    public Class<?> getOwnerClass() {
        return clazz;
    }

    public abstract void parseField(DefaultJSONParser parser, Object object, Type objectType,
                                    Map<String, Object> fieldValues);

    public int getFastMatchToken() {
        return 0;
    }

    public void setValue(Object object, boolean value) {
        setValue(object, Boolean.valueOf(value));
    }

    public void setValue(Object object, int value) {
        setValue(object, Integer.valueOf(value));
    }

    public void setValue(Object object, long value) {
        setValue(object, Long.valueOf(value));
    }

    public void setValue(Object object, String value) {
        setValue(object, (Object) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void setValue(Object object, Object value) {
        if (value == null //
                && fieldInfo.fieldClass.isPrimitive()) {
            return;
        } else if (value == null) {
            return;
        } else if (fieldInfo.fieldClass == String.class
                && fieldInfo.format != null
                && fieldInfo.format.equals("trim")) {
            value = ((String) value).trim();
        }

        try {
            Method method = fieldInfo.method;
            if (method != null) {
                if (fieldInfo.getOnly) {
                    if (fieldInfo.fieldClass == AtomicInteger.class) {
                        AtomicInteger atomic = (AtomicInteger) method.invoke(object);
                        if (atomic != null) {
                            atomic.set(((AtomicInteger) value).get());
                        } else {
                            degradeValueAssignment(method, object, value);
                        }
                    } else if (fieldInfo.fieldClass == AtomicLong.class) {
                        AtomicLong atomic = (AtomicLong) method.invoke(object);
                        if (atomic != null) {
                            atomic.set(((AtomicLong) value).get());
                        } else {
                            degradeValueAssignment(method, object, value);
                        }
                    } else if (fieldInfo.fieldClass == AtomicBoolean.class) {
                        AtomicBoolean atomic = (AtomicBoolean) method.invoke(object);
                        if (atomic != null) {
                            atomic.set(((AtomicBoolean) value).get());
                        } else {
                            degradeValueAssignment(method, object, value);
                        }
                    } else if (Map.class.isAssignableFrom(method.getReturnType())) {
                        Map map = null;
                        
                        map = (Map) method.invoke(object);
                        
                        if (map != null) {
                            if (map == Collections.emptyMap()) {
                                return;
                            }

                            if (map.isEmpty() && ((Map) value).isEmpty()) {
                                return;
                            }

                            String mapClassName = map.getClass().getName();
                            if (mapClassName.equals("java.util.ImmutableCollections$Map1")
                                    || mapClassName.equals("java.util.ImmutableCollections$MapN")
                                    || mapClassName.startsWith(JAVA_UTIL_COLLECTIONS_UNMODIFIABLE)) {
                                // skip

                                return;
                            }

                            if (map.getClass().isInstance("kotlin.collections.EmptyMap")) {
                                degradeValueAssignment(method, object, value);
                                return;
                            }

                            map.putAll((Map) value);
                        } else if (value != null) {
                            degradeValueAssignment(method, object, value);
                        }
                    } else {
                        Collection collection = null;
                        
                        collection = (Collection) method.invoke(object);
                        
                        if (collection != null && value != null) {
                            String collectionClassName = collection.getClass().getName();

                            if (collection == Collections.emptySet()
                                    || collection == Collections.emptyList()
                                    || collectionClassName.equals("java.util.ImmutableCollections$ListN")
                                    || collectionClassName.equals("java.util.ImmutableCollections$List12")
                                    || collectionClassName.startsWith(JAVA_UTIL_COLLECTIONS_UNMODIFIABLE)) {
                                // skip
                                return;
                            }

                            if (!collection.isEmpty()) {
                                collection.clear();
                            } else if (((Collection) value).isEmpty()) {
                                return; //skip
                            }


                            if (collectionClassName.equals("kotlin.collections.EmptyList")
                                    || collectionClassName.equals("kotlin.collections.EmptySet")) {
                                degradeValueAssignment(method, object, value);
                                return;
                            }
                            collection.addAll((Collection) value);
                        } else if (collection == null && value != null) {
                            degradeValueAssignment(method, object, value);
                        }
                    }
                } else {
                    method.invoke(object, value);
                }
            } else {
                final Field field = fieldInfo.field;
                
                if (fieldInfo.getOnly) {
                    if (fieldInfo.fieldClass == AtomicInteger.class) {
                        AtomicInteger atomic = (AtomicInteger) field.get(object);
                        if (atomic != null) {
                            atomic.set(((AtomicInteger) value).get());
                        }
                    } else if (fieldInfo.fieldClass == AtomicLong.class) {
                        AtomicLong atomic = (AtomicLong) field.get(object);
                        if (atomic != null) {
                            atomic.set(((AtomicLong) value).get());
                        }
                    } else if (fieldInfo.fieldClass == AtomicBoolean.class) {
                        AtomicBoolean atomic = (AtomicBoolean) field.get(object);
                        if (atomic != null) {
                            atomic.set(((AtomicBoolean) value).get());
                        }
                    } else if (Map.class.isAssignableFrom(fieldInfo.fieldClass)) {
                        Map map = (Map) field.get(object);
                        if (map != null) {
                            if (map == Collections.emptyMap()
                                    || map.getClass().getName().startsWith(JAVA_UTIL_COLLECTIONS_UNMODIFIABLE)) {
                                // skip
                                return;
                            }
                            map.putAll((Map) value);
                        }
                    } else {
                        Collection collection = (Collection) field.get(object);
                        if (collection != null && value != null) {
                            if (collection == Collections.emptySet()
                                    || collection == Collections.emptyList()
                                    || collection.getClass().getName().startsWith(JAVA_UTIL_COLLECTIONS_UNMODIFIABLE)) {
                                // skip
                                return;
                            }

                            collection.clear();
                            collection.addAll((Collection) value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new JSONException("set property error, " + clazz.getName() + "#" + fieldInfo.name, e);
        }
    }

    /**
     * kotlin代理类property的get方法会抛未初始化异常，用set方法直接赋值
     */
    private static boolean degradeValueAssignment(
            Method getMethod,
            Object object,
            Object value
    ) throws InvocationTargetException, IllegalAccessException {
        

        try {
            Method setMethod = object
                    .getClass()
                    .getDeclaredMethod("set" + getMethod.getName().substring(3), getMethod.getReturnType());
            setMethod.invoke(object, value);
            return true;
        } catch (InvocationTargetException | NoSuchMethodException |IllegalAccessException ignore) { //1
        }
        return false;
    }

    

    public void setWrappedValue(String key, Object value) {
        throw new JSONException("TODO");
    }
}
