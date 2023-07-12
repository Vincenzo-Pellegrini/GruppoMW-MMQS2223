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
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public final class ListSerializer implements ObjectSerializer {

    public static final ListSerializer instance = new ListSerializer();

    public final void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
            throws IOException {

        boolean writeClassName = serializer.out.isEnabled(SerializerFeature.WRITE_CLASS_NAME)
                || SerializerFeature.isEnabled(features, SerializerFeature.WRITE_CLASS_NAME);

        SerializeWriter out = serializer.out;



        if (object == null) {
            out.writeNull(SerializerFeature.WRITE_NULL_LIST_AS_EMPTY);
            return;
        }

        List<?> list = (List<?>) object;

        if (list.isEmpty()) {
            out.append("[]");
            return;
        }

        SerialContext context = serializer.context;
        serializer.setContext(context, object, fieldName, 0);


        try {
            serializerIncrementIncident(serializer,object,fieldName,out,context);

            out.append('[');
            for (int i = 0, size = list.size(); i < size; ++i) {
                Object item = list.get(i);
                if (i != 0) {
                    out.append(',');
                }

                if (item == null) {
                    out.append("null");
                } else {
                    writeLong(out,item,writeClassName);
                }
            }
            out.append(']');
        } finally {
            serializer.context = context;
        }
    }

    public void serializerWriteReference(JSONSerializer serializer, Object object, Object fieldName,SerializeWriter out,Object item,SerialContext context){

        if (serializer.containsReference(item)) {
            serializer.writeReference(item);
        }
    }

    public void writeLong(SerializeWriter out,Object item,boolean writeClassName){
        Class<?> clazz = item.getClass();

        if (clazz == Integer.class) {
            out.writeInt(((Integer) item).intValue());
        } else if (clazz == Long.class) {
            long val = ((Long) item).longValue();
            if (writeClassName) {
                out.writeLong(val);
                out.write('L');
            } else {
                out.writeLong(val);
            }
        }
    }

    public void serializerIncrementIncident(JSONSerializer serializer, Object object, Object fieldName,SerializeWriter out,SerialContext context){
        List<?> list = (List<?>) object;
        if (out.isEnabled(SerializerFeature.PRETTY_FORMAT)) {
            out.append('[');
            serializer.incrementIndent();

            int i = 0;
            for (Object item : list) {
                if (i != 0) {
                    out.append(',');
                }

                serializer.println();
                if (item != null) {
                    if (serializer.containsReference(item)) {
                        serializer.writeReference(item);
                    }
                } else {
                    serializer.out.writeNull();
                }
                i++;
            }

            serializer.decrementIdent();
            serializer.println();
            out.append(']');
        }
    }

}
