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

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class PrimitiveArraySerializer implements ObjectSerializer {

    public static final PrimitiveArraySerializer instance = new PrimitiveArraySerializer();

    public final void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;
        
        outWrite1(out,object);
        
        outWrite2(out,object);
        
        outWrite3(out,object);
        
        outWrite4(out,object);

        if (object instanceof byte[]) {
            byte[] array = (byte[]) object;
            out.writeByteArray(array);
            return;
        }
        
        char[] chars = (char[]) object;
        out.writeString(chars);
    }

    public void outWrite1(SerializeWriter out,Object object){
        if (object == null) {
            out.writeNull(SerializerFeature.WRITE_NULL_LIST_AS_EMPTY);
        }
        
        if (object instanceof int[]) {
            int[] array = (int[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                out.writeInt(array[i]);
            }
            out.write(']');
            return;
        }
        
        if (object instanceof short[]) {
            short[] array = (short[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                out.writeInt(array[i]);
            }
            out.write(']');
        }
    }

    public void outWrite2(SerializeWriter out,Object object){
        if (object instanceof long[]) {
            long[] array = (long[]) object;

            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                out.writeLong(array[i]);
            }
            out.write(']');
        }
        
        if (object instanceof boolean[]) {
            boolean[] array = (boolean[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                out.write(array[i]);
            }
            out.write(']');
        }
    }

    public void outWrite3(SerializeWriter out,Object object){
        if (object instanceof float[]) {
            float[] array = (float[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                
                float item = array[i];
                if (Float.isNaN(item)) {
                    out.writeNull();
                } else {
                    out.append(Float.toString(item));
                }
            }
            out.write(']');
        }
    }

    public void outWrite4(SerializeWriter out,Object object){
        if (object instanceof double[]) {
            double[] array = (double[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                
                double item = array[i];
                if (Double.isNaN(item)) {
                    out.writeNull();
                } else {
                    out.append(Double.toString(item));
                }
            }
            out.write(']');
        }
    }
}
