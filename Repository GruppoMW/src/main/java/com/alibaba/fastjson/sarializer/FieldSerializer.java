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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.TypeUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class FieldSerializer implements Comparable<FieldSerializer> {

    public final FieldInfo        fieldInfo;
    protected  boolean       writeNull;
    protected int                 features;

    private String                doubleQuotedFieldPrefix;
    private String                singleQuotedFieldPrefix;
    private String                unQuotedFieldPrefix;

    protected BeanContext         fieldContext;

    private String                format;
    protected boolean             writeEnumUsingToString  = false;
    protected boolean             writeEnumUsingName      = false;
    protected boolean             disableCircularReferenceDetect = false;

    protected boolean             serializeUsing          = false;

    protected boolean             persistenceXToMany      = false; // OneToMany or ManyToMany
    protected boolean             browserCompatible;

    private RuntimeSerializerInfo runtimeInfo;

    public FieldSerializer(Class<?> beanType, FieldInfo fieldInfo) {
        this.fieldInfo = fieldInfo;
        this.fieldContext = new BeanContext(beanType, fieldInfo);
        parseJsonTypeAnnotation(beanType);
        setFieldInfoAccessible();
        setDoubleQuotedFieldPrefix();
        parseJsonFieldAnnotation();
        setPersistenceXToMany();
    }

    private void parseJsonTypeAnnotation(Class<?> beanType) {
        if (beanType != null) {
            JSONType jsonType = TypeUtils.getAnnotation(beanType, JSONType.class);
            if (jsonType != null) {
                for (SerializerFeature feature : jsonType.serialzeFeatures()) {
                    switch (feature) {
                        case WRITE_ENUM_USING_TO_STRING:
                            writeEnumUsingToString = true;
                            break;
                        case WRITE_ENUM_USING_NAME:
                            writeEnumUsingName = true;
                            break;
                        case DISABLE_CIRCULAR_REFERENCE_DETECT:
                            disableCircularReferenceDetect = true;
                            break;
                        case BROWSER_COMPATIBLE:
                            features |= SerializerFeature.BROWSER_COMPATIBLE.mask;
                            browserCompatible = true;
                            break;
                        case WRITE_MAP_NULL_VALUE:
                            features |= SerializerFeature.WRITE_MAP_NULL_VALUE.mask;
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    private void setFieldInfoAccessible() {
        fieldInfo.setAccessible();
    }

    private void setDoubleQuotedFieldPrefix() {

        this.doubleQuotedFieldPrefix = '"' + fieldInfo.name + "\":";
    }

    private void parseJsonFieldAnnotation() {
        boolean writeNull1 = false;
        JSONField annotation = fieldInfo.getAnnotation();
        if (annotation != null) {
            for (SerializerFeature feature : annotation.serialzeFeatures()) {
                if ((feature.getMask() & SerializerFeature.WRITE_MAP_NULL_FEATURES) != 0) {
                    writeNull1 = true;
                    break;
                }
            }

            format = annotation.format();
            if (format.trim().length() == 0) {
                format = null;
            }

            for (SerializerFeature feature : annotation.serialzeFeatures()) {
                switch (feature) {
                    case WRITE_ENUM_USING_TO_STRING:
                        writeEnumUsingToString = true;
                        break;
                    case WRITE_ENUM_USING_NAME:
                        writeEnumUsingName = true;
                        break;
                    case DISABLE_CIRCULAR_REFERENCE_DETECT:
                        disableCircularReferenceDetect = true;
                        break;
                    case BROWSER_COMPATIBLE:
                        browserCompatible = true;
                        break;
                    default:
                        break;
                }
            }

            features |= SerializerFeature.of(annotation.serialzeFeatures());
        }

        this.writeNull = writeNull1;
    }

    private void setPersistenceXToMany() {
        persistenceXToMany = TypeUtils.isAnnotationPresentOneToMany(fieldInfo.method)
                || TypeUtils.isAnnotationPresentManyToMany(fieldInfo.method);
    }



    public void writePrefix(JSONSerializer serializer){
        SerializeWriter out = serializer.out;

        if (out.quoteFieldNames) {
            boolean useSingleQuotes = SerializerFeature.isEnabled(out.features, fieldInfo.serialzeFeatures, SerializerFeature.USE_SINGLE_QUOTES);
            if (useSingleQuotes) {
                if (singleQuotedFieldPrefix == null) {
                    singleQuotedFieldPrefix = '\'' + fieldInfo.name + "\':";
                }
                out.write(singleQuotedFieldPrefix);
            } else {
                out.write(doubleQuotedFieldPrefix);
            }
        } else {
            if (unQuotedFieldPrefix == null) {
                this.unQuotedFieldPrefix = fieldInfo.name + ":";
            }
            out.write(unQuotedFieldPrefix);
        }
    }

    public Object getPropertyValueDirect(Object object) throws InvocationTargetException, IllegalAccessException {
        Object fieldValue =  fieldInfo.get(object);
        if (persistenceXToMany && !TypeUtils.isHibernateInitialized(fieldValue)) {
            return null;
        }
        return fieldValue;
    }

    public Object getPropertyValue(Object object) throws InvocationTargetException, IllegalAccessException {
        Object propertyValue =  fieldInfo.get(object);
        if (format != null && propertyValue != null && (fieldInfo.fieldClass == java.util.Date.class || fieldInfo.fieldClass == java.sql.Date.class)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(format, JSON.defaultLocale);
            dateFormat.setTimeZone(JSON.defaultTimeZone);
            return dateFormat.format(propertyValue);
        }
        return propertyValue;
    }

    public int compareTo(FieldSerializer o) {
        return this.fieldInfo.compareTo(o.fieldInfo);
    }


    public void writeValue(JSONSerializer serializer, Object propertyValue) throws IOException {
        if (runtimeInfo == null) {

            Class<?> runtimeFieldClass;

            runtimeFieldClass = returnRuntimeFieldClass(propertyValue);

            ObjectSerializer fieldSerializer = null;

            fieldInfo.getAnnotation();

            fieldSerializer = returnFieldSerializer(fieldSerializer,serializer,runtimeFieldClass);

            runtimeInfo = new RuntimeSerializerInfo(fieldSerializer, runtimeFieldClass);
        }

        final RuntimeSerializerInfo runtimeInfo1 = this.runtimeInfo;

        final int fieldFeatures
                = (disableCircularReferenceDetect
                ? (fieldInfo.serialzeFeatures | SerializerFeature.DISABLE_CIRCULAR_REFERENCE_DETECT.mask)
                : fieldInfo.serialzeFeatures) | features;


        Class<?> valueClass = propertyValue.getClass();
        ObjectSerializer valueSerializer;
        if (valueClass == runtimeInfo1.runtimeFieldClass || serializeUsing) {
            valueSerializer = runtimeInfo1.fieldSerializer;
        } else {
            valueSerializer = serializer.getObjectWriter(valueClass);
        }

        formatPrint(valueSerializer,serializer,propertyValue);

        if (fieldInfo.unwrapped) {
            if (valueSerializer instanceof JavaBeanSerializer) {
                JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) valueSerializer;
                javaBeanSerializer.write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures, true);
                return;
            }

            if (valueSerializer instanceof MapSerializer) {
                MapSerializer mapSerializer = (MapSerializer) valueSerializer;
                mapSerializer.write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures, true);
                return;
            }
        }

        featuresPrint(serializer,propertyValue);

        valueSerializer.write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures);
    }

    public Class<?> returnRuntimeFieldClass(Object propertyValue){
        Class<?> runtimeFieldClass;
        if (propertyValue == null) {
            runtimeFieldClass = this.fieldInfo.fieldClass;
            if (runtimeFieldClass == byte.class) {
                runtimeFieldClass = Byte.class;
            } else if (runtimeFieldClass == short.class) {
                runtimeFieldClass = Short.class;
            } else if (runtimeFieldClass == int.class) {
                runtimeFieldClass = Integer.class;
            } else if (runtimeFieldClass == long.class) {
                runtimeFieldClass = Long.class;
            } else if (runtimeFieldClass == float.class) {
                runtimeFieldClass = Float.class;
            } else if (runtimeFieldClass == double.class) {
                runtimeFieldClass = Double.class;
            } else if (runtimeFieldClass == boolean.class) {
                runtimeFieldClass = Boolean.class;
            }
        } else {
            runtimeFieldClass = propertyValue.getClass();
        }
        return runtimeFieldClass;
    }

    public ObjectSerializer returnFieldSerializer(ObjectSerializer fieldSerializerp,JSONSerializer serializer,Class<?> runtimeFieldClass){
        if (format != null) {
            if (runtimeFieldClass == double.class || runtimeFieldClass == Double.class) {
                fieldSerializerp = new DoubleSerializer(format);
            } else if (runtimeFieldClass == float.class || runtimeFieldClass == Float.class) {
                fieldSerializerp = new FloatCodec(format);
            }
        }

        if (fieldSerializerp == null) {
            fieldSerializerp = serializer.getObjectWriter(runtimeFieldClass);
        }
        return fieldSerializerp;
    }

    public void featuresPrint(JSONSerializer serializer,Object propertyValue){
        if (browserCompatible && (fieldInfo.fieldClass == long.class || fieldInfo.fieldClass == Long.class)) {
            long value = (Long) propertyValue;
            if (value > 9007199254740991L || value < -9007199254740991L) {
                serializer.getWriter().writeString(Long.toString(value));
            }
        }
    }

    public void formatPrint(ObjectSerializer valueSerializer,JSONSerializer serializer,Object propertyValue){
        if (format != null && !(valueSerializer instanceof DoubleSerializer || valueSerializer instanceof FloatCodec)) {
            serializer.writeWithFormat(propertyValue, format);
        }
    }

    static class RuntimeSerializerInfo {
        final ObjectSerializer fieldSerializer;
        final Class<?>         runtimeFieldClass;

        public RuntimeSerializerInfo(ObjectSerializer fieldSerializer, Class<?> runtimeFieldClass){
            this.fieldSerializer = fieldSerializer;
            this.runtimeFieldClass = runtimeFieldClass;
        }
    }
}
