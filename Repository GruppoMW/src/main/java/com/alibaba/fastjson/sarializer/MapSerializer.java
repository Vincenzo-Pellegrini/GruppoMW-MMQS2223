/*
 * Copyright 1999-2018 Alibaba Group.
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
package com.alibaba.fastjson.serializer;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class MapSerializer extends SerializeFilterable implements ObjectSerializer {

    public static final MapSerializer instance = new MapSerializer();

    private static final int NON_STRINGKEY_AS_STRING = SerializerFeature.of(
            new SerializerFeature[] {
                    SerializerFeature.BROWSER_COMPATIBLE,
                    SerializerFeature.WRITE_NON_STRING_KEY_AS_STRING,
                    SerializerFeature.BROWSER_SECURE});

    public void write(JSONSerializer serializer
            , Object object
            , Object fieldName
            , Type fieldType
            , int features) throws IOException {
        write(serializer, object, fieldName, fieldType, features, false);
    }

    @SuppressWarnings({ "rawtypes"})
    public void write(JSONSerializer serializer
            , Object object
            , Object fieldName
            , Type fieldType
            , int features //
            , boolean unwrapped) throws IOException {
        SerializeWriter out = serializer.out;

        if (objectIsNull(object, out)) return;

        Map<?, ?> map = getMap((Map<?, ?>) object, features, out);

        if (serializerContainsReference(serializer, object)) return;

        SerialContext parent = serializer.context;
        serializer.setContext(parent, object, fieldName, 0);
        try {
            if (!unwrapped) {
                out.write('{');
            }

            serializer.incrementIndent();

            Class<?> preClazz = null;
            ObjectSerializer preWriter = null;

            boolean first = true;

            first = isFirst(serializer, object, out, map, first);

            forFunction(serializer, object, fieldType, features, out, map, preClazz, preWriter, first);
        } finally {
            serializer.context = parent;
        }

        unrappedNot(serializer, unwrapped, out, map);
    }

    private void  forFunction(JSONSerializer serializer, Object object, Type fieldType, int features, SerializeWriter out, Map<?, ?> map, Class<?> preClazz, ObjectSerializer preWriter, boolean first) throws IOException {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();

            Object entryKey = entry.getKey();

            if (getPrefiltersAll(serializer, object, value, entryKey))
            {
                //non fare nulla
            }

            entryKey = extracted(serializer, object, value, entryKey);
            entryKey = extracted2(serializer, object, value, entryKey);

            value = extracted3(serializer, object, features, value, entryKey);

            if (valueIsNull(features, out, value))
            {
                //non fare nulla
            }

            entryKeyInstance(serializer, features, out, first, entryKey);

            first = false;

            if (valueIsNull(out, value))
            {
                continue;
            }
            Class<?> clazz = value.getClass();

            if (clazz != preClazz) {
                preClazz = clazz;
                preWriter = serializer.getObjectWriter(clazz);
            }

            serializerFeatureIsEnable(serializer, fieldType, features, preWriter, value, entryKey);
        }
    }

    private Object extracted3(JSONSerializer serializer, Object object, int features, Object value, Object entryKey) {
        value = getObject(serializer, object, features, value, entryKey);
        return value;
    }

    private Object extracted2(JSONSerializer serializer, Object object, Object value, Object entryKey) {
        entryKey = getEntryKey(serializer, object, value, entryKey);
        return entryKey;
    }

    private Object extracted(JSONSerializer serializer, Object object, Object value, Object entryKey) {
        entryKey = getObject(serializer, object, value, entryKey);
        return entryKey;
    }

    private boolean getPrefiltersAll(JSONSerializer serializer, Object object, Object value, Object entryKey) {
        if (getPrefilters(serializer, object, entryKey)) return true;
        if (getPrefilters2(serializer, object, entryKey)) return true;
        if (getPrefilter3(serializer, object, value, entryKey)) return true;
        if (propertyFiltersNotNull(serializer, object, value, entryKey)) return true;
        return false;
    }

    private boolean propertyFiltersNotNull(JSONSerializer serializer, Object object, Object value, Object entryKey) {
        List<PropertyFilter> propertyFilters = this.propertyFilters;
        if (propertyFilters != null && !propertyFilters.isEmpty()) {
            if (entryKey == null || entryKey instanceof String) {
                if (thisApply(serializer, object, value, (String) entryKey)) return true;
            } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                String strKey = JSON.toJSONString(entryKey);
                if (thisApply(serializer, object, value, strKey)) return true;
            }
        }
        return false;
    }

    private boolean thisApply(JSONSerializer serializer, Object object, Object value, String strKey) {
        return !this.apply(serializer, object, strKey, value);
    }

    private static void unrappedNot(JSONSerializer serializer, boolean unwrapped, SerializeWriter out, Map<?, ?> map) {
        serializer.decrementIdent();
        if (out.isEnabled(SerializerFeature.PRETTY_FORMAT) && map.size() > 0) {
            serializer.println();
        }

        if (!unwrapped) {
            out.write('}');
        }
    }

    private static void serializerFeatureIsEnable(JSONSerializer serializer, Type fieldType, int features, ObjectSerializer preWriter, Object value, Object entryKey) throws IOException {
        if (SerializerFeature.isEnabled(features, SerializerFeature.WRITE_CLASS_NAME)
                && preWriter instanceof JavaBeanSerializer) {
            Type valueType = null;
            if (fieldType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) fieldType;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length == 2) {
                    valueType = actualTypeArguments[1];
                }
            }

            JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) preWriter;
            javaBeanSerializer.writeNoneASM(serializer, value, entryKey, valueType, features);
        } else if(preWriter == null){
            throw new NullPointerException("Null Value");
        }
        else{
            preWriter.write(serializer, value, entryKey, null, features);
        }
    }

    private static boolean valueIsNull(SerializeWriter out, Object value) {
        if (value == null) {
            out.writeNull();
            return true;
        }
        return false;
    }

    private static void entryKeyInstance(JSONSerializer serializer, int features, SerializeWriter out, boolean first, Object entryKey) {
        if (entryKey instanceof String) {
            String key = (String) entryKey;

            if (!first) {
                out.write(',');
            }

            if (out.isEnabled(SerializerFeature.PRETTY_FORMAT)) {
                serializer.println();
            }
            out.writeFieldName2(key);
        } else {
            if (!first) {
                out.write(',');
            }

            if ((out.isEnabled(NON_STRINGKEY_AS_STRING) || SerializerFeature.isEnabled(features, SerializerFeature.WRITE_NON_STRING_KEY_AS_STRING))
                    && !(entryKey instanceof Enum)) {
                String strEntryKey = JSON.toJSONString(entryKey);
                serializer.write(strEntryKey);
            } else {
                serializer.write(entryKey);
            }

            out.write(':');
        }
    }

    private static boolean valueIsNull(int features, SerializeWriter out, Object value) {
        return value == null && (!SerializerFeature.isEnabled(out.features, features, SerializerFeature.WRITE_MAP_NULL_VALUE));
    }

    private Object getObject(JSONSerializer serializer, Object object, int features, Object value, Object entryKey) {
        if (entryKey == null || entryKey instanceof String) {
            value = this.processValue(serializer, null, object, (String) entryKey, value, features);
        } else {
            boolean objectOrArray = entryKey instanceof Map || entryKey instanceof Collection;
            if (!objectOrArray) {
                String strKey = JSON.toJSONString(entryKey);
                value = this.processValue(serializer, null, object, strKey, value, features);
            }
        }
        return value;
    }

    private Object getEntryKey(JSONSerializer serializer, Object object, Object value, Object entryKey) {
        List<NameFilter> nameFilters = this.nameFilters;
        if (nameFilters != null && !nameFilters.isEmpty()) {
            if (entryKey == null || entryKey instanceof String) {
                entryKey = this.processKey(serializer, object, (String) entryKey, value);
            } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                String strKey = JSON.toJSONString(entryKey);
                entryKey = this.processKey(serializer, object, strKey, value);
            }
        }
        return entryKey;
    }

    private Object getObject(JSONSerializer serializer, Object object, Object value, Object entryKey) {
        List<NameFilter> nameFilters = serializer.nameFilters;
        if (nameFilters != null && !nameFilters.isEmpty()) {
            if (entryKey == null || entryKey instanceof String) {
                entryKey = this.processKey(serializer, object, (String) entryKey, value);
            } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                String strKey = JSON.toJSONString(entryKey);
                entryKey = this.processKey(serializer, object, strKey, value);
            }
        }
        return entryKey;
    }

    private boolean getPrefilter3(JSONSerializer serializer, Object object, Object value, Object entryKey) {
        List<PropertyFilter> propertyFilters = serializer.propertyFilters;
        if (propertyFilters != null && !propertyFilters.isEmpty()) {
            if (entryKey == null || entryKey instanceof String) {
                if (!this.apply(serializer, object, (String) entryKey, value)) {
                    return true;
                }
            } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                String strKey = JSON.toJSONString(entryKey);
                if (!this.apply(serializer, object, strKey, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean getPrefilters2(JSONSerializer serializer, Object object, Object entryKey) {
        List<PropertyPreFilter> preFilters = this.propertyPreFilters;
        if (preFilters != null && !preFilters.isEmpty()) {
            if (entryKey == null || entryKey instanceof String) {
                if (!this.applyName(serializer, object, (String) entryKey)) {
                    return true;
                }
            } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                String strKey = JSON.toJSONString(entryKey);
                if (!this.applyName(serializer, object, strKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean getPrefilters(JSONSerializer serializer, Object object, Object entryKey) {
        List<PropertyPreFilter> preFilters = serializer.propertyPreFilters;
        if (preFilters != null && !preFilters.isEmpty()) {
            if (entryKey == null || entryKey instanceof String) {
                if (!this.applyName(serializer, object, (String) entryKey)) {
                    return true;
                }
            } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                String strKey = JSON.toJSONString(entryKey);
                if (!this.applyName(serializer, object, strKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFirst(JSONSerializer serializer, Object object, SerializeWriter out, Map<?, ?> map, boolean first) {
        if (out.isEnabled(SerializerFeature.WRITE_CLASS_NAME)) {
            String typeKey = serializer.config.typeKey;
            Class<?> mapClass = map.getClass();
            boolean containsKey = (mapClass == JSONObject.class || mapClass == HashMap.class || mapClass == LinkedHashMap.class)
                    && map.containsKey(typeKey);
            first = isFirst(object, out, first, typeKey, containsKey);
        }
        return first;
    }

    private static boolean isFirst(Object object, SerializeWriter out, boolean first, String typeKey, boolean containsKey) {
        if (!containsKey) {
            out.writeFieldName(typeKey);
            out.writeString(object.getClass().getName());
            first = false;
        }
        return first;
    }

    private static boolean serializerContainsReference(JSONSerializer serializer, Object object) {
        if (serializer.containsReference(object)) {
            serializer.writeReference(object);
            return true;
        }
        return false;
    }

    private static Map<?, ?> getMap(Map<?, ?> object, int features, SerializeWriter out) {
        Map<?, ?> map = object;
        final int mapSortFieldMask = SerializerFeature.MAP_SORT_FIELD.mask;
        if ((out.features & mapSortFieldMask) != 0 || (features & mapSortFieldMask) != 0) {
            if (map instanceof JSONObject) {
                map = ((JSONObject) map).getInnerMap();
            }

            if ((!(map instanceof SortedMap)) && !(map instanceof LinkedHashMap)) {
                try {
                    map = new TreeMap(map);
                } catch (Exception ex) {
                    // skip
                }
            }
        }
        return map;
    }

    private static boolean objectIsNull(Object object, SerializeWriter out) {
        if (object == null) {
            out.writeNull();
            return true;
        }
        return false;
    }
}