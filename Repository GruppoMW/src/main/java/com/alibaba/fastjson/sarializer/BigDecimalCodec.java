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
import java.math.BigDecimal;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.util.TypeUtils;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class BigDecimalCodec implements ObjectSerializer, ObjectDeserializer {
    static final BigDecimal LOW = BigDecimal.valueOf(-9007199254740991L);
    static final BigDecimal HIGH = BigDecimal.valueOf(9007199254740991L);

    public static final BigDecimalCodec instance = new BigDecimalCodec();

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        if (object == null) {
            out.writeNull(SerializerFeature.WRITE_NULL_NUMBER_AS_ZERO);
        } else {
            BigDecimal val = (BigDecimal) object;
            int scale = val.scale();

            String outText;
            if (serializerFeatureBoolean(scale , features , out)) {
                outText = val.toPlainString();
            } else {
                outText = val.toString();
            }

            if (scale == 0 && outText.length() >= 16
                    && SerializerFeature.isEnabled(features, out.features, SerializerFeature.BROWSER_COMPATIBLE)
                    && (val.compareTo(LOW) < 0
                    || val.compareTo(HIGH) > 0)) {
                out.writeString(outText);
                return;
            }

            out.write(outText);

            if (out.isEnabled(SerializerFeature.WRITE_CLASS_NAME) && fieldType != BigDecimal.class && val.scale() == 0) {
                out.write('.');
            }
        }
    }

    protected static boolean serializerFeatureBoolean(int scale , int features , SerializeWriter out){
       return SerializerFeature.isEnabled(features, out.features, SerializerFeature.WRITE_BIG_DECIMAL_AS_PLAIN)
                    && scale >= -100 && scale < 100;
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        try {
            return (T) deserialze(parser);
        } catch (Exception ex) {
            throw new JSONException("parseDecimal error, field : " + fieldName, ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialze(DefaultJSONParser parser) {
        final JSONLexer lexer = parser.lexer;
        if (lexer.token() == JSONToken.LITERAL_INT) {
            BigDecimal decimalValue = lexer.decimalValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) decimalValue;
        }

        if (lexer.token() == JSONToken.LITERAL_FLOAT) {
            BigDecimal val = lexer.decimalValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val;
        }

        Object value = parser.parse();
        return value == null //
            ? null //
            : (T) TypeUtils.castToBigDecimal(value);
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_INT;
    }
}
