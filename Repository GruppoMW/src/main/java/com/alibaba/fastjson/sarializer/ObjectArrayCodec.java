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

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.util.TypeUtils;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class ObjectArrayCodec implements ObjectSerializer, ObjectDeserializer {

    public static final ObjectArrayCodec instance = new ObjectArrayCodec();

    public ObjectArrayCodec(){
        //I don't know what this function do
    }

    public final void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
            throws IOException {
        SerializeWriter out = serializer.out;

        Object[] array = (Object[]) object;

        if (object == null) {
            out.writeNull(SerializerFeature.WRITE_NULL_LIST_AS_EMPTY);
            return;
        }

        int size = array.length;

        int end = size - 1;

        if (end == -1) {
            out.append("[]");
            return;
        }

        SerialContext context = serializer.context;
        serializer.setContext(context, object, fieldName, 0);

        try {

            tryBlockCode(serializer,out,array,size);

        } finally {
            serializer.context = context;
        }
    }

    public void tryBlockCode(JSONSerializer serializer,SerializeWriter out,Object[] array,int size){
        out.append('[');

        if (out.isEnabled(SerializerFeature.PRETTY_FORMAT)) {
            serializer.incrementIndent();
            serializer.println();
            for (int i = 0; i < size; ++i) {
                if (i != 0) {
                    out.write(',');
                    serializer.println();
                }
                serializer.writeWithFieldName(array[i], Integer.valueOf(i));
            }
            serializer.decrementIdent();
            serializer.println();
            out.write(']');
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        final JSONLexer lexer = parser.lexer;
        int token = lexer.token();
        if (token == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
            return null;
        }

        if (token == JSONToken.LITERAL_STRING || token == JSONToken.HEX) {
            byte[] bytes = lexer.bytesValue();
            lexer.nextToken(JSONToken.COMMA);

            if (bytes.length == 0 && type != byte[].class) {
                return null;
            }

            return (T) bytes;
        }

        Class componentClass;
        Type componentType;
        if (type instanceof GenericArrayType) {
            GenericArrayType clazz = (GenericArrayType) type;
            componentType = clazz.getGenericComponentType();
            componentClass = returnComponentClass(componentType,parser);
        } else {
            Class clazz = (Class) type;
            componentType = componentClass = clazz.getComponentType();
        }
        JSONArray array = new JSONArray();
        parser.parseArray(componentType, array, fieldName);

        return (T) toObjectArray(parser, componentClass, array);
    }

    public Type returnActualType(TypeVariable<?>[] objTypeParams,ParameterizedType objParamType,TypeVariable<?> typeVar){
        Type actualType = null;

        for (int i = 0; i < objTypeParams.length; ++i) {
            if (objTypeParams[i].getName().equals(typeVar.getName())) {
                actualType = objParamType.getActualTypeArguments()[i];
            }
        }
        return actualType;
    }

    public <T extends GenericDeclaration> Class<T> returnComponentClass(Type componentType, DefaultJSONParser parser){
        Class<?> componentClass = null;
        if (componentType instanceof TypeVariable) {
            TypeVariable<T> typeVar = (TypeVariable) componentType;
            Type objType = parser.getContext().type;
            if (objType instanceof ParameterizedType) {
                ParameterizedType objParamType = (ParameterizedType) objType;
                Type objRawType = objParamType.getRawType();
                Type actualType = null;
                if (objRawType instanceof Class) {
                    TypeVariable[] objTypeParams = ((Class) objRawType).getTypeParameters();
                    actualType = returnActualType(objTypeParams,objParamType,typeVar);
                }
                if (actualType instanceof Class) {
                    componentClass = (Class) actualType;
                } else {
                    componentClass = Object.class;
                }
            } else {
                componentClass = TypeUtils.getClass(typeVar.getBounds()[0]);
            }
        } else {
            componentClass = TypeUtils.getClass(componentType);
        }
        return (Class<T>) componentClass;
    }

    @SuppressWarnings("unchecked")
    private <T> T toObjectArray(DefaultJSONParser parser, Class<?> componentType, JSONArray array) {
        if (array == null) {
            return null;
        }

        int size = array.size();

        Object objArray = Array.newInstance(componentType, size);
        for (int i = 0; i < size; ++i) {
            Object value = array.get(i);

            objArray = returnObjectArray(parser,componentType,array,objArray,value,i);
        }

        array.setRelatedArray(objArray);
        array.setComponentType(componentType);
        return (T) objArray;
    }

    public Object returnObjectArray(DefaultJSONParser parser, Class<?> componentType, JSONArray array,Object objArrayp,Object value,int i){
        if (value == array) {
            Array.set(objArrayp, i, objArrayp);
        }
        if (componentType.isArray()) {
            Object element;
            if (componentType.isInstance(value)) {
                element = value;
            } else {
                element = toObjectArray(parser, componentType, (JSONArray) value);
            }

            Array.set(objArrayp, i, element);
        } else {
            Object element = null;

            element = TypeUtils.cast(value, componentType, parser.getConfig());

            Array.set(objArrayp, i, element);

        }
        return objArrayp;
    }

    public int getFastMatchToken() {
        return JSONToken.LBRACKET;
    }
}
