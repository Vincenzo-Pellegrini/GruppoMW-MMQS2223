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
import java.util.Collection;
import java.util.HashSet;
import java.util.TreeSet;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.util.TypeUtils;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class CollectionCodec implements ObjectSerializer, ObjectDeserializer {

    public static final CollectionCodec instance = new CollectionCodec();

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;
        if (object == null) {
            out.writeNull(SerializerFeature.WRITE_NULL_LIST_AS_EMPTY);
            return;
        }
        
        Collection<?> collection = (Collection<?>) object;
        
        // Imposta il contesto per la serializzazione
        SerialContext context = serializer.context;
        serializer.setContext(context, object, fieldName, 0);
        
        // Scrive il nome della classe, se richiesto
        if (out.isEnabled(SerializerFeature.WRITE_CLASS_NAME)) {
            writeClassName(out, collection);
        }
        
        // Scrive gli elementi della collezione
        try {
            out.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    out.append(',');
                }
                writeItem(serializer, out, item, features);
                first = false;
            }
            out.append(']');
        } finally {
            // Ripristina il contesto precedente
            serializer.context = context;
        }
    }

    private void writeClassName(SerializeWriter out, Collection<?> collection) {
        Class<?> clazz = collection.getClass();
       if (HashSet.class.isAssignableFrom(clazz)) {
            out.append("Set");
       } else if (TreeSet.class.isAssignableFrom(clazz)) {
            out.append("TreeSet");
       }
    }
       
    private void writeItem(JSONSerializer serializer, SerializeWriter out, Object item, int features) throws IOException {
        if (item == null) {
        out.writeNull();
        return;
        }
        Class<?> clazz = item.getClass();
        if (clazz == Integer.class) {
                out.writeInt(((Integer) item).intValue());
                return;
        }
        if (clazz == Long.class) {
                out.writeLong(((Long) item).longValue());
        if (out.isEnabled(SerializerFeature.WRITE_CLASS_NAME)) {
                out.write('L');
        }
            return;
        }
        ObjectSerializer itemSerializer = serializer.getObjectWriter(clazz);
        if (SerializerFeature.isEnabled(features, SerializerFeature.WRITE_CLASS_NAME) && itemSerializer instanceof JavaBeanSerializer) {
            JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) itemSerializer;
            javaBeanSerializer.writeNoneASM(serializer, item, 0, null, features);
        } else {
            itemSerializer.write(serializer, item, null, null, features);
        }
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        if (parser.lexer.token() == JSONToken.NULL) {
            parser.lexer.nextToken(JSONToken.COMMA);
            return null;
        }
        
        if (type == JSONArray.class) {
            JSONArray array = new JSONArray();
            parser.parseArray(array);
            return (T) array;
        }

        Collection list;
        if (parser.lexer.token() == JSONToken.SET) {
            parser.lexer.nextToken();
            list = TypeUtils.createSet(type);
        } else {
            list = TypeUtils.createCollection(type);
        }

        Type itemType = TypeUtils.getCollectionItemType(type);
        parser.parseArray(itemType, list, fieldName);

        return (T) list;
    }

  

    public int getFastMatchToken() {
        return JSONToken.LBRACKET;
    }
}
