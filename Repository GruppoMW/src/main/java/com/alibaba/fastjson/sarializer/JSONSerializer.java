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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;


/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class JSONSerializer extends SerializeFilterable {

    protected final SerializeConfig                  config;
    public final SerializeWriter                     out;

    private int                                      indentCount = 0;

    private String                                   indent      = "\t";

    /**
     * #1868 为了区分全局配置（FastJsonConfig）的日期格式配置以及toJSONString传入的日期格式配置
     * 建议使用以下调整：
     * 1. dateFormatPattern、dateFormat只作为toJSONString传入配置使用；
     * 2. 新增fastJsonConfigDateFormatPattern，用于存储通过（FastJsonConfig）配置的日期格式
     */

    private String                                   dateFormatPattern;
    private DateFormat                               dateFormat;
    private String                                   fastJsonConfigDateFormatPattern;
    protected IdentityHashMap<Object, SerialContext> references  = null;
    protected SerialContext                          context;
    protected TimeZone                               timeZone    = JSON.defaultTimeZone;
    protected Locale                                 locale      = JSON.defaultLocale;

    public JSONSerializer(){

        this(new SerializeWriter(), SerializeConfig.getGlobalInstance());

    }

    public JSONSerializer(SerializeWriter out){

        this(out, SerializeConfig.getGlobalInstance());

    }

    public JSONSerializer(SerializeConfig config){

        this(new SerializeWriter(), config);

    }
    public JSONSerializer(SerializeWriter out, SerializeConfig config){

        this.out = out;
        this.config = config;

    }
    public String getDateFormatPattern() {

        if (dateFormat instanceof SimpleDateFormat) {
            return ((SimpleDateFormat) dateFormat).toPattern();
        }

        return dateFormatPattern;
    }

    public DateFormat getDateFormat() {

        if (dateFormat == null && dateFormatPattern != null) {
            dateFormat = this.generateDateFormat(dateFormatPattern);
        }

        return dateFormat;
    }

    private DateFormat generateDateFormat(String dateFormatPattern) {

        DateFormat dateFormat1 = new SimpleDateFormat(dateFormatPattern, locale);
        dateFormat1.setTimeZone(timeZone);
        return dateFormat1;
    }

    public void setDateFormat(DateFormat dateFormat) {

        this.dateFormat = dateFormat;
        if (dateFormatPattern != null) {
            dateFormatPattern = null;
        }
    }

    public void setDateFormat(String dateFormat) {

        this.dateFormatPattern = dateFormat;

        if (this.dateFormat != null) {
            this.dateFormat = null;
        }
    }
    /**
     * Set global date format pattern in FastJsonConfig
     *
     * @param dateFormatPattern global date format pattern
     */
    public void setFastJsonConfigDateFormatPattern(String dateFormatPattern) {
        this.fastJsonConfigDateFormatPattern = dateFormatPattern;
    }
    
    public String getFastJsonConfigDateFormatPattern() {
        return this.fastJsonConfigDateFormatPattern;
    }

    public SerialContext getContext() {
        return context;
    }

    public void setContext(SerialContext context) {
        this.context = context;
    }

    public void setContext(SerialContext parent, Object object, Object fieldName, int features) {
        this.setContext(parent, object, fieldName, features, 0);
    }

    public void setContext(SerialContext parent, Object object, Object fieldName, int features, int fieldFeatures) {
        if (out.disableCircularReferenceDetect) {
            return;
        }
        
        if (references == null) {
            references = new IdentityHashMap<>();
        }
        this.references.put(object, context);
    }

    public void setContext(Object object, Object fieldName) {
        this.setContext(context, object, fieldName, 0);
    }

    public void popContext() {
        if (context != null) {
            this.context = this.context.parent;
        }
    }

    public final boolean isWriteClassName(Type fieldType) {
        return out.isEnabled(SerializerFeature.WRITE_CLASS_NAME) //
                && (fieldType != null //
                || (!out.isEnabled(SerializerFeature.NOT_WRITE_ROOT_CLASS_NAME)) //
                || (context != null && (context.parent != null)));
    }

    public boolean containsReference(Object value) {
        if (references == null) {
            return false;
        }
        SerialContext refContext = references.get(value);
        if (refContext == null) {
            return false;
        }
        if (value == Collections.emptyMap()) {
            return false;
        }
        Object fieldName = refContext.fieldName;
        return fieldName == null || fieldName instanceof Integer || fieldName instanceof String;
    }

    public void writeReference(Object object) {
        SerialContext context1 = this.context;
        Object current = context1.object;
        if (object == current) {
            out.write("{\"$ref\":\"@\"}");
            return;
        }
        SerialContext parentContext = context1.parent;
        if (parentContext != null && object == parentContext.object) {
            out.write("{\"$ref\":\"..\"}");
            return;
        }
        SerialContext rootContext = context1;
        for (;;) {
            if (rootContext.parent == null) {
                break;
            }
            rootContext = rootContext.parent;
        }
        if (object == rootContext.object) {
            out.write("{\"$ref\":\"$\"}");
        } else {
            out.write("{\"$ref\":\"");
            String path = references.get(object).toString();
            out.write(path);
            out.write("\"}");
        }
    }

    public boolean checkValue(SerializeFilterable filterable) {
        return (valueFilters != null && !valueFilters.isEmpty()) //
                || (contextValueFilters != null && !contextValueFilters.isEmpty()) //
                || (filterable.valueFilters != null && !filterable.valueFilters.isEmpty())
                || (filterable.contextValueFilters != null && !filterable.contextValueFilters.isEmpty())
                || out.writeNonStringValueAsString;
    }
    public boolean hasNameFilters(SerializeFilterable filterable) {
        return (nameFilters != null && !nameFilters.isEmpty()) //
                || (filterable.nameFilters != null && !filterable.nameFilters.isEmpty());
    }
    public boolean hasPropertyFilters(SerializeFilterable filterable) {
        return (propertyFilters != null && !propertyFilters.isEmpty()) //
                || (filterable.propertyFilters != null && !filterable.propertyFilters.isEmpty());
    }

    public int getIndentCount() {
        return indentCount;
    }

    public void incrementIndent() {
        indentCount++;
    }

    public void decrementIdent() {
        indentCount--;
    }

    public void println() {
        out.write('\n');
        for (int i = 0; i < indentCount; ++i) {
            out.write(indent);
        }
    }

    public SerializeWriter getWriter() {
        return out;
    }

    public String toString() {
        return out.toString();
    }

    public void config(SerializerFeature feature, boolean state) {
        out.config(feature, state);
    }

    public boolean isEnabled(SerializerFeature feature) {
        return out.isEnabled(feature);
    }

    public void writeNull() {
        this.out.writeNull();
    }

    public SerializeConfig getMapping() {
        return config;
    }

    public static void write(Writer out, Object object) {
        try ( SerializeWriter writer = new SerializeWriter() ){
            JSONSerializer serializer = new JSONSerializer(writer);
            serializer.write(object);
            writer.writeTo(out);
        } catch (IOException ex) {
            throw new JSONException(ex.getMessage(), ex);
        }
    }

    public static void write(SerializeWriter out, Object object) {
        JSONSerializer serializer = new JSONSerializer(out);
        serializer.write(object);
    }

    public final void write(Object object) {
        if (object == null) {
            out.writeNull();
            return;
        }
        Class<?> clazz = object.getClass();
        ObjectSerializer writer = getObjectWriter(clazz);
        try {
            writer.write(this, object, null, null, 0);
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }
    /**
     * @since 1.2.57
     *
     */
    public final <T> void writeAs(Object object, Class<T> type) {

        if (object == null) {
            out.writeNull();
            return;
        }
        ObjectSerializer writer = getObjectWriter(type);
        try {
            writer.write(this, object, null, null, 0);
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }

    public final void writeWithFieldName(Object object, Object fieldName) {
        writeWithFieldName(object, fieldName, null, 0);
    }

    protected final void writeKeyValue(char seperator, String key, Object value) {
        if (seperator != '\0') {
            out.write(seperator);
        }
        out.writeFieldName(key);
        write(value);
    }

    public final void writeWithFieldName(Object object, Object fieldName, Type fieldType, int fieldFeatures) {
        try {
            Class<?> clazz = object.getClass();
            ObjectSerializer writer = getObjectWriter(clazz);
            writer.write(this, object, fieldName, fieldType, fieldFeatures);
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }

    public final <T> void writeWithFormat(Object object, String format) {

        outText(object,format);

        if (object instanceof byte[]) {

            byte[] bytes = (byte[]) object;

            if ("gzip".equals(format) || "gzip,base64".equals(format)) {

                try ( ByteArrayOutputStream byteOut = new ByteArrayOutputStream() ){
                    out.writeByteArray(byteOut.toByteArray());
                } catch (IOException ex) {
                    throw new JSONException("write gzipBytes error", ex);
                }

            } else if ("hex".equals(format)) {
                out.writeHex(bytes);
            } else {
                out.writeByteArray(bytes);
            }

            return;
        }

        if (object instanceof Collection) {
            Collection<T> collection = (Collection<T>) object;
            Iterator<T> iterator = collection.iterator();
            out.write('[');
            for (int i = 0; i < collection.size(); i++) {
                Object item = iterator.next();
                if (i != 0) {
                    out.write(',');
                }
                writeWithFormat(item, format);
            }
            out.write(']');
            return;
        }
        write(object);
    }
    public void outText(Object object,String format){

        if (object instanceof Date) {

            if ("millis".equals(format)) {
                out.writeLong(((Date) object).getTime());
            }

            DateFormat dateFormat2 = this.getDateFormat();

            if (dateFormat2 == null) {

                if (format != null) {

                    try {
                        dateFormat2 = this.generateDateFormat(format);
                    } catch (IllegalArgumentException e) {
                        String format2 = format.replace("T", "'T'");
                        dateFormat2 = this.generateDateFormat(format2);
                    }

                } else if (fastJsonConfigDateFormatPattern != null) {
                    dateFormat2 = this.generateDateFormat(fastJsonConfigDateFormatPattern);
                } else {
                    dateFormat2 = this.generateDateFormat(JSON.DEFAULT_DATE_FORMAT);
                }
            }

            String text = dateFormat2.format((Date) object);
            out.writeString(text);
        }
    }

    public final void write(String text) {

        StringCodec.instance.write(this, text);
    }

    public ObjectSerializer getObjectWriter(Class<?> clazz) {

        return config.getObjectWriter(clazz);
    }

    public void close() {

        this.out.close();
    }
}