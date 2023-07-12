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
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.PropertyNamingStrategy;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.TypeUtils;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class JavaBeanSerializer extends SerializeFilterable implements ObjectSerializer {
    // serializers
    protected final FieldSerializer[] getters;
    protected final FieldSerializer[] sortedGetters;

    protected final SerializeBeanInfo  beanInfo;

    private  AtomicLongArray hashArray;
    private  AtomicReferenceArray<Integer> hashArrayMapping;

    protected boolean hashNotMatch;

    static final String GET_FIELD_VALUE_ERROR = "getFieldValue error.";

    public JavaBeanSerializer(Class<?> beanType){
        this(beanType, (Map<String, String>) null);
    }

    public JavaBeanSerializer(Class<?> beanType, String... aliasList){
        this(beanType, createAliasMap(aliasList));
    }

    static Map<String, String> createAliasMap(String... aliasList) {
        Map<String, String> aliasMap = new HashMap<>();
        for (String alias : aliasList) {
            aliasMap.put(alias, alias);
        }

        return aliasMap;
    }

    public JSONType getJSONType() {
        return beanInfo.jsonType;
    }

    /**
     * @since 1.2.42
     */
    public Class<?> getType() {
        return beanInfo.beanType;
    }

    public JavaBeanSerializer(Class<?> beanType, Map<String, String> aliasMap){
        this(TypeUtils.buildBeanInfo(beanType, aliasMap, null));
    }

    public JavaBeanSerializer(SerializeBeanInfo beanInfo) {
        this.beanInfo = beanInfo;

        sortedGetters = new FieldSerializer[beanInfo.sortedFields.length];
        for (int i = 0; i < sortedGetters.length; ++i) {
            sortedGetters[i] = new FieldSerializer(beanInfo.beanType, beanInfo.sortedFields[i]);
        }

        if (beanInfo.fields == beanInfo.sortedFields) {
            getters = sortedGetters;
        } else {
            getters = new FieldSerializer[beanInfo.fields.length];
            hashNotMatch = false;
            for (int i = 0; i < getters.length; ++i) {
                FieldSerializer fieldSerializer = getFieldSerializer(beanInfo.fields[i].name);
                if (fieldSerializer == null) {
                    hashNotMatch = true;
                    break;
                }
                getters[i] = fieldSerializer;
            }
        }

        if (beanInfo.jsonType != null) {
            for (Class<? extends SerializeFilter> filterClass : beanInfo.jsonType.serialzeFilters()) {
                try {
                    SerializeFilter filter = filterClass.getConstructor().newInstance();
                    this.addFilter(filter);
                } catch (Exception e) {
                    // skip
                }
            }
        }
    }

    public void writeDirectNonContext(JSONSerializer serializer, //
                                      Object object, //
                                      Object fieldName, //
                                      Type fieldType, //
                                      int features) throws IOException {
        write(serializer, object, fieldName, fieldType, features);
    }

    public void writeAsArray(JSONSerializer serializer, //
                             Object object, //
                             Object fieldName, //
                             Type fieldType, //
                             int features) throws IOException {
        write(serializer, object, fieldName, fieldType, features);
    }

    public void writeAsArrayNonContext(JSONSerializer serializer, //
                                       Object object, //
                                       Object fieldName, //
                                       Type fieldType, //
                                       int features) throws IOException {
        write(serializer, object, fieldName, fieldType, features);
    }

    public void write(JSONSerializer serializer, //
                      Object object, //
                      Object fieldName, //
                      Type fieldType, //
                      int features) throws IOException {
        write(serializer, object, fieldName, fieldType, features, false);
    }

    public void writeNoneASM(JSONSerializer serializer, //
                             Object object, //
                             Object fieldName, //
                             Type fieldType, //
                             int features) {
        write(serializer, object, fieldName, fieldType, features, false);
    }

    protected Void write(JSONSerializer serializer, //
                         Object object, //
                         Object fieldName, //
                         Type fieldType, //
                         int features,
                         boolean unwrapped
    ) {
        return getaVoid(serializer, object, fieldName, fieldType, features, unwrapped);
    }

    private Void getaVoid(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features, boolean unwrapped) {
        SerializeWriter out = serializer.out;

        if (objectIsNull(object, out)) return null;

        if (writeReference(serializer, object, features)) {
            return null;
        }

        final FieldSerializer[] getters1;

        getters1 = getFieldSerializers(out);

        SerialContext parent = serializer.context;
        thisBeanInfo(serializer, object, fieldName, features, parent);

        final boolean writeAsArray = isWriteAsArray(serializer, features);

        FieldSerializer errorFieldSerializer = null;
        try {
            final char startSeperator = writeAsArray ? '[' : '{';
            final char endSeperator = writeAsArray ? ']' : '}';
            notUnwrapped(unwrapped, out, startSeperator);

            gettersLength1(serializer, out, getters1);

            boolean commaFlag = false;

            commaFlag = isCommaFlag(serializer, object, fieldType, features, commaFlag);

            char seperator = commaFlag ? ',' : '\0';

            char newSeperator = this.writeBefore(serializer, object, seperator);
            commaFlag = newSeperator == ',';


            for (int i = 0; i < getters1.length; ++i) {
                FieldSerializer fieldSerializer = getters1[i];

                FieldInfo fieldInfo = fieldSerializer.fieldInfo;
                String fieldInfoName = fieldInfo.name;
                Class<?> fieldClass = fieldInfo.fieldClass;

                boolean notApply = false;
                isNotApply(serializer, object, writeAsArray, fieldInfo, fieldInfoName, notApply);

                if (fieldInfoNameEquals(serializer, fieldType, fieldInfoName)) continue;

                Object propertyValue = null;



                propertyValue = getObject(fieldInfo, fieldClass, propertyValue);

                propertyValue = this.processValue(serializer, fieldSerializer.fieldContext, object, fieldInfoName,
                        propertyValue, features);

                boolean fieldUnwrappedNull = false;
                fieldUnwrappedNull = getaVoid(serializer, fieldInfo, propertyValue, fieldUnwrappedNull);

                commaFlag = isFieldUnwrappedNull(commaFlag, fieldUnwrappedNull);
            }

            this.writeAfter(serializer, object, commaFlag ? ',' : '\0');

            gettersLength(serializer, out, getters1);

            notUnwrapped(unwrapped, out, endSeperator);
        } catch (Exception e) {
            String errorMessage = "write javaBean error, fastjson version " + JSON.VERSION;
            errorMessage = getString(object, errorMessage);
            errorMessage = getString(fieldName, errorFieldSerializer, e, errorMessage);

            Throwable cause = null;
            cause = getThrowable(e, cause);

            throw new JSONException(errorMessage, cause);
        } finally {
            serializer.context = parent;
        }
        return null;
    }

    private static void gettersLength1(JSONSerializer serializer, SerializeWriter out, FieldSerializer[] getters) {
        if (getters.length > 0 && out.isEnabled(SerializerFeature.PRETTY_FORMAT)) {
            serializer.incrementIndent();
            serializer.println();
        }
    }

    private boolean isNotApply(JSONSerializer serializer, Object object, boolean writeAsArray, FieldInfo fieldInfo, String fieldInfoName, boolean notApply) {
        if ((!this.applyName(serializer, object, fieldInfoName)) //
                || !this.applyLabel(serializer, fieldInfo.label) &&  (writeAsArray)) {
            notApply = true;

        }
        return notApply;
    }

    private boolean fieldInfoNameEquals(JSONSerializer serializer, Type fieldType, String fieldInfoName) {
        if (fieldInfoName.equals(beanInfo.typeKey)
                && serializer.isWriteClassName(fieldType)) {
            //ciao a tutti
        }
        return false;
    }

    private static void gettersLength(JSONSerializer serializer, SerializeWriter out, FieldSerializer[] getters) {
        if (getters.length > 0 && out.isEnabled(SerializerFeature.PRETTY_FORMAT)) {
            serializer.decrementIdent();
            serializer.println();
        }
    }

    private static boolean getaVoid(JSONSerializer serializer, FieldInfo fieldInfo, Object propertyValue, boolean fieldUnwrappedNull) {
        if (fieldInfo.unwrapped
                && propertyValue instanceof Map) {
            Map map = ((Map) propertyValue);
            if (map.size() == 0) {
                fieldUnwrappedNull = true;
            } else if (!serializer.isEnabled(SerializerFeature.WRITE_MAP_NULL_VALUE)){
                boolean hasNotNull = false;
                for (Object value : map.values()) {
                    if (value != null) {
                        hasNotNull = true;
                        break;
                    }
                }
                fieldUnwrappedNull = isFieldUnwrappedNull(fieldUnwrappedNull, hasNotNull);
            }
        }
        return fieldUnwrappedNull;
    }

    private static boolean isFieldUnwrappedNull(boolean fieldUnwrappedNull, boolean hasNotNull) {
        if (!hasNotNull) {
            fieldUnwrappedNull = true;
        }
        return fieldUnwrappedNull;
    }

    private static Throwable getThrowable(Exception e, Throwable cause) {
        if (e instanceof InvocationTargetException) {
            cause = e.getCause();
        }
        if (cause == null) {
            cause = e;
        }
        return cause;
    }

    private static String getString(Object fieldName, FieldSerializer errorFieldSerializer, Exception e, String errorMessage) {
        if (fieldName != null) {
            errorMessage += ", fieldName : " + fieldName;
        } else if (errorFieldSerializer != null && errorFieldSerializer.fieldInfo != null) {
            FieldInfo fieldInfo = errorFieldSerializer.fieldInfo;
            if (fieldInfo.method != null) {
                errorMessage += ", method : " + fieldInfo.method.getName();
            } else {
                errorMessage += ", fieldName : " + errorFieldSerializer.fieldInfo.name;
            }
        }
        if (e.getMessage() != null) {
            errorMessage += (", " + e.getMessage());
        }
        return errorMessage;
    }

    private static String getString(Object object, String errorMessage) {
        if (object != null) {
            errorMessage += ", class " + object.getClass().getName();
        }
        return errorMessage;
    }

    private static void notUnwrapped(boolean unwrapped, SerializeWriter out, char startSeperator) {
        if (!unwrapped) {
            out.append(startSeperator);
        }
    }

    private static Object getObject(FieldInfo fieldInfo, Class<?> fieldClass, Object propertyValue) {
        if (fieldClass == String.class && "trim".equals(fieldInfo.format) && propertyValue != null) {
            propertyValue = ((String) propertyValue).trim();
        }
        return propertyValue;
    }

    private boolean isCommaFlag(JSONSerializer serializer, Object object, Type fieldType, int features, boolean commaFlag) {
        if ((this.beanInfo.features & SerializerFeature.WRITE_CLASS_NAME.mask) != 0
                ||(features & SerializerFeature.WRITE_CLASS_NAME.mask) != 0
                || serializer.isWriteClassName(fieldType)) {
            Class<?> objClass = object.getClass();

            final Type type;
            type = getType(fieldType, objClass);

            commaFlag = isCommaFlag(serializer, object, commaFlag, objClass, type);
        }
        return commaFlag;
    }

    private static Type getType(Type fieldType, Class<?> objClass) {
        final Type type;
        if (objClass != fieldType && fieldType instanceof WildcardType) {
            type = TypeUtils.getClass(fieldType);
        } else {
            type = fieldType;
        }
        return type;
    }

    private boolean isCommaFlag(JSONSerializer serializer, Object object, boolean commaFlag, Class<?> objClass, Type type) {
        if (objClass != type) {
            writeClassName(serializer, beanInfo.typeKey, object);
            commaFlag = true;
        }
        return commaFlag;
    }

    private void thisBeanInfo(JSONSerializer serializer, Object object, Object fieldName, int features, SerialContext parent) {
        if (!this.beanInfo.beanType.isEnum()) {
            serializer.setContext(parent, object, fieldName, this.beanInfo.features, features);
        }
    }

    private FieldSerializer[] getFieldSerializers(SerializeWriter out) {
        final FieldSerializer[] getteRinominato;
        if (out.sortField) {
            getteRinominato = this.sortedGetters;
        } else {
            getteRinominato = this.getters;
        }
        return getteRinominato;
    }

    private static boolean objectIsNull(Object object, SerializeWriter out) {
        if (object == null) {
            out.writeNull();
            return true;
        }
        return false;
    }

    protected void writeClassName(JSONSerializer serializer, String typeKey, Object object) {
        if (typeKey == null) {
            typeKey = serializer.config.typeKey;
        }
        serializer.out.writeFieldName2(typeKey);
        String typeName = this.beanInfo.typeName;
        if (typeName == null) {
            Class<?> clazz = object.getClass();

            if (TypeUtils.isProxy(clazz)) {
                clazz = clazz.getSuperclass();
            }

            typeName = clazz.getName();
        }
        serializer.write(typeName);
    }

    public boolean writeReference(JSONSerializer serializer, Object object, int fieldFeatures) {
        SerialContext context = serializer.context;
        int mask = SerializerFeature.DISABLE_CIRCULAR_REFERENCE_DETECT.mask;
        if (context == null || (context.features & mask) != 0 || (fieldFeatures & mask) != 0) {
            return false;
        }

        if (serializer.references != null && serializer.references.containsKey(object)) {
            serializer.writeReference(object);
            return true;
        } else {
            return false;
        }
    }

    protected boolean isWriteAsArray(JSONSerializer serializer) {
        return isWriteAsArray(serializer, 0);
    }

    protected boolean isWriteAsArray(JSONSerializer serializer, int fieldFeatrues) {
        final int mask = SerializerFeature.BEAN_TO_ARRAY.mask;
        return (beanInfo.features & mask) != 0 //
                || serializer.out.beanToArray //
                || (fieldFeatrues & mask) != 0;
    }

    public Object getFieldValue(Object object, String key) {
        FieldSerializer fieldDeser = getFieldSerializer(key);
        if (fieldDeser == null) {
            throw new JSONException("field not found. " + key);
        }

        try {
            return fieldDeser.getPropertyValue(object);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new JSONException(GET_FIELD_VALUE_ERROR + key, ex);
        }
    }

    public Object getFieldValue(Object object, String key, long keyHash, boolean throwFieldNotFoundException) {
        FieldSerializer fieldDeser = getFieldSerializer(keyHash);
        if (fieldDeser == null) {
            if (throwFieldNotFoundException) {
                throw new JSONException("field not found. " + key);
            }
            return null;
        }

        try {
            return fieldDeser.getPropertyValue(object);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new JSONException(GET_FIELD_VALUE_ERROR + key, ex);
        }
    }

    public FieldSerializer getFieldSerializer(String key) {
        if (key == null) {
            return null;
        }

        int low = 0;
        int high = sortedGetters.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;

            String fieldName = sortedGetters[mid].fieldInfo.name;

            int cmp = fieldName.compareTo(key);

            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return sortedGetters[mid]; // key found
            }
        }

        return null; // key not found.
    }

    public FieldSerializer getFieldSerializer(long hash) {
        PropertyNamingStrategy[] namingStrategies = null;

        int pos = -1;
        for (int i = 0; i < hashArray.length() ; i++ )
        {
            if (hashArray.get(i) == hash) {
                pos = i;
                break;
            }
        }

        if (pos < 0) {
            return null;
        }

        if (hashArrayMapping == null) {
            namingStrategies = PropertyNamingStrategy.values();
            AtomicReferenceArray<Integer> mapping = new AtomicReferenceArray<>(hashArray.length());
            for (int i = 0;i < mapping.length() ; i++) {
                mapping.set(i,-1);
            }
            sortedGettersValue(namingStrategies,mapping);
            hashArrayMapping = mapping;
        }

        int getterIndex = hashArrayMapping.get(pos);
        if (getterIndex != -1) {
            return sortedGetters[getterIndex];
        }

        return null; // key not found.
    }

    public PropertyNamingStrategy[] returnPropertyNamingStrategy(){
        PropertyNamingStrategy[] namingStrategiesp = null;
        if (this.hashArray == null) {
            namingStrategiesp = PropertyNamingStrategy.values();

            long[] hashArrayDue = new long[sortedGetters.length * namingStrategiesp.length];
            int index = 0;
            for (int i = 0; i < sortedGetters.length; i++) {
                String name = sortedGetters[i].fieldInfo.name;
                hashArrayDue[index++] = TypeUtils.fnv1a64(name);

                for (int j = 0; j < namingStrategiesp.length; j++) {
                    String nameT = namingStrategiesp[j].translate(name);
                    if (name.equals(nameT)) {
                        continue;
                    }
                    hashArrayDue[index++] = TypeUtils.fnv1a64(nameT);
                }
            }
            Arrays.sort(hashArrayDue, 0, index);

            this.hashArray = new AtomicLongArray(new long[index]);
            System.arraycopy(hashArrayDue, 0, this.hashArray, 0, index);
        }
        return namingStrategiesp;
    }


    public void sortedGettersValue(PropertyNamingStrategy[] namingStrategies,AtomicReferenceArray mapping){
        for (int i = 0; i < sortedGetters.length; i++) {
            String name = sortedGetters[i].fieldInfo.name;

            int p = -1;
            for (int z = 0; z < hashArray.length() ; z++ )
            {
                if (hashArray.get(z) == TypeUtils.fnv1a64(name)) {
                    p = z;
                    break;
                }
            }
            if (p >= 0) {
                mapping.set(p,(short) i);
            }
            sortedGettersValueFor(namingStrategies,mapping,name,i);

        }
    }

    public void sortedGettersValueFor(PropertyNamingStrategy[] namingStrategies,AtomicReferenceArray mapping,String name,int i){
        for (int j = 0; j < namingStrategies.length; j++) {
            String nameTvariabile = namingStrategies[j].translate(name);
            if (name.equals(nameTvariabile)) {
                continue;
            }

            int pT = -1;
            for (int y = 0; y < hashArray.length() ; y++ )
            {
                if (hashArray.get(y) == TypeUtils.fnv1a64(nameTvariabile)) {
                    pT = y;
                    break;
                }
            }
            if (pT >= 0) {
                mapping.set(pT,(short) i);
            }
        }
    }

    public List<Object> getFieldValues(Object object) throws InvocationTargetException, IllegalAccessException {
        List<Object> fieldValues = new ArrayList<>(sortedGetters.length);
        for (FieldSerializer getter : sortedGetters) {
            fieldValues.add(getter.getPropertyValue(object));
        }

        return fieldValues;
    }

    // for jsonpath deepSet
    public <T> List<Object> getObjectFieldValues(Object object) throws InvocationTargetException, IllegalAccessException {
        List<Object> fieldValues = new ArrayList<>(sortedGetters.length);
        for (FieldSerializer getter : sortedGetters) {
            Class<T> fieldClass = (Class<T>) getter.fieldInfo.fieldClass;
            if (fieldClass.isPrimitive()) {
                //non fa nulla
            }
            if (fieldClass.getName().startsWith("java.lang.")) {
                continue;
            }
            fieldValues.add(getter.getPropertyValue(object));
        }

        return fieldValues;
    }

    public int getSize(Object object) throws InvocationTargetException, IllegalAccessException {
        int size = 0;
        for (FieldSerializer getter : sortedGetters) {
            Object value = getter.getPropertyValueDirect(object);
            if (value != null) {
                size ++;
            }
        }
        return size;
    }

    /**
     * Get field names of not null fields. Keep the same logic as getSize.
     *
     * @param object the object to be checked
     * @return field name set
     * @throws Exception
     * @see #getSize(Object)
     */
    public Set<String> getFieldNames(Object object) throws InvocationTargetException, IllegalAccessException {
        Set<String> fieldNames = new HashSet<>();
        for (FieldSerializer getter : sortedGetters) {
            Object value = getter.getPropertyValueDirect(object);
            if (value != null) {
                fieldNames.add(getter.fieldInfo.name);
            }
        }
        return fieldNames;
    }

    public Map<String, Object> getFieldValuesMap(Object object) throws InvocationTargetException, IllegalAccessException {
        Map<String, Object> map = new LinkedHashMap<>(sortedGetters.length);
        boolean skipTransient = true;
        FieldInfo fieldInfo = null;

        for (FieldSerializer getter : sortedGetters) {
            skipTransient = SerializerFeature.isEnabled(getter.features, SerializerFeature.SKIP_TRANSIENT_FIELD);
            fieldInfo = getter.fieldInfo;

            if (skipTransient && fieldInfo != null && fieldInfo.fieldTransient) {
                continue;
            }

            if (getter.fieldInfo.unwrapped) {
                Object unwrappedValue = getter.getPropertyValue(object);
                Object map1 = JSON.toJSON(unwrappedValue);
                if (map1 instanceof Map) {
                    map.putAll((Map) map1);
                } else {
                    map.put(getter.fieldInfo.name, getter.getPropertyValue(object));
                }
            } else {
                map.put(getter.fieldInfo.name, getter.getPropertyValue(object));
            }
        }

        return map;
    }

    protected BeanContext getBeanContext(int orinal) {
        return sortedGetters[orinal].fieldContext;
    }

    protected Type getFieldType(int ordinal) {
        return sortedGetters[ordinal].fieldInfo.fieldType;
    }

    protected char writeBefore(JSONSerializer jsonBeanDeser, //
                               Object object, char seperator) {

        if (jsonBeanDeser.beforeFilters != null) {
            for (BeforeFilter beforeFilter : jsonBeanDeser.beforeFilters) {
                seperator = beforeFilter.writeBefore(jsonBeanDeser, object, seperator);
            }
        }

        if (this.beforeFilters != null) {
            for (BeforeFilter beforeFilter : this.beforeFilters) {
                seperator = beforeFilter.writeBefore(jsonBeanDeser, object, seperator);
            }
        }

        return seperator;
    }

    protected char writeAfter(JSONSerializer jsonBeanDeser, // 
                              Object object, char seperator) {
        if (jsonBeanDeser.afterFilters != null) {
            for (AfterFilter afterFilter : jsonBeanDeser.afterFilters) {
                seperator = afterFilter.writeAfter(jsonBeanDeser, object, seperator);
            }
        }

        if (this.afterFilters != null) {
            for (AfterFilter afterFilter : this.afterFilters) {
                seperator = afterFilter.writeAfter(jsonBeanDeser, object, seperator);
            }
        }

        return seperator;
    }

    protected boolean applyLabel(JSONSerializer jsonBeanDeser, String label) {
        if (jsonBeanDeser.labelFilters != null) {
            for (LabelFilter propertyFilter : jsonBeanDeser.labelFilters) {
                if (!propertyFilter.apply(label)) {
                    return false;
                }
            }
        }

        if (this.labelFilters != null) {
            for (LabelFilter propertyFilter : this.labelFilters) {
                if (!propertyFilter.apply(label)) {
                    return false;
                }
            }
        }

        return true;
    }
}
