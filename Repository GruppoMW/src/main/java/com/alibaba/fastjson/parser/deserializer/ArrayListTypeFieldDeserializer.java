package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Map;

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.ParseContext;
import com.alibaba.fastjson.util.FieldInfo;

public class ArrayListTypeFieldDeserializer extends FieldDeserializer {

    public ArrayListTypeFieldDeserializer(Class<?> clazz, FieldInfo fieldInfo){
        super(clazz, fieldInfo);

        Type fieldType = fieldInfo.fieldType;
        if (fieldType instanceof ParameterizedType) {
            Type argType = ((ParameterizedType) fieldInfo.fieldType).getActualTypeArguments()[0];
            if (argType instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) argType;
                Type[] upperBounds = wildcardType.getUpperBounds();
                if (upperBounds.length == 1) {
                    argType = upperBounds[0];
                }
            }
        }
    }
    @Override
    public int getFastMatchToken() {
        return JSONToken.LBRACKET;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void parseField(DefaultJSONParser parser, Object object, Type objectType, Map<String, Object> fieldValues) {
        JSONLexer lexer = parser.lexer;
        final int token = lexer.token();
        if (token == JSONToken.NULL
                || (token == JSONToken.LITERAL_STRING && lexer.stringVal().length() == 0)) {
            if (object == null) {
                fieldValues.put(fieldInfo.name, null);
            } else {
                setValue(object, null);
            }
            return;
        }

        ArrayList list = new ArrayList();

        ParseContext context = parser.getContext();

        parser.setContext(context, object, fieldInfo.name);
        parser.setContext(context);

        if (object == null) {
            fieldValues.put(fieldInfo.name, list);
        } else {
            setValue(object, list);
        }
    }    
}
