package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.ParseContext;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.FieldInfo;

public class DefaultFieldDeserializer extends FieldDeserializer {

    protected ObjectDeserializer fieldValueDeserilizer;
    protected boolean            customDeserilizer     = false;

    public DefaultFieldDeserializer(Class<?> clazz, FieldInfo fieldInfo){
        super(clazz, fieldInfo);
        JSONField annotation = fieldInfo.getAnnotation();
        if (annotation != null) {
            Class<?> deserializeUsing = annotation.deserializeUsing();
            customDeserilizer = deserializeUsing != null && deserializeUsing != Void.class;
        }
    }

    public ObjectDeserializer getFieldValueDeserilizer(ParserConfig config) {
        if (fieldValueDeserilizer == null) {
            JSONField annotation = fieldInfo.getAnnotation();
            if (annotation != null && annotation.deserializeUsing() != Void.class) {
                Class<?> deserializeUsing = annotation.deserializeUsing();
                try {
                    fieldValueDeserilizer = (ObjectDeserializer) deserializeUsing.getDeclaredConstructor().newInstance();
                } catch (Exception ex) {
                    throw new JSONException("create deserializeUsing ObjectDeserializer error", ex);
                }
            } else {
                fieldValueDeserilizer = config.getDeserializer(fieldInfo.fieldClass, fieldInfo.fieldType);
            }
        }

        return fieldValueDeserilizer;
    }

    @Override
    public void parseField(DefaultJSONParser parser, Object object, Type objectType, Map<String, Object> fieldValues) {
        extracted(parser);

        ObjectDeserializer fieldValueDeserilizer1 = this.fieldValueDeserilizer;
        Type fieldType = fieldInfo.fieldType;
        if (objectType instanceof ParameterizedType) {
            extracted3(parser, objectType);
            if (fieldType != objectType) {
                fieldType = FieldInfo.getFieldType(this.clazz, objectType, fieldType);
                fieldValueDeserilizer1 = extracted2(parser, fieldValueDeserilizer1, fieldType);
            }
        }

        // ContextObjectDeserializer
        Object value = extracted6(parser, fieldValueDeserilizer1, fieldType);

        extracted11(parser, object, fieldValues, value);
    }

    private void extracted11(DefaultJSONParser parser, Object object, Map<String, Object> fieldValues, Object value) {
        if (parser.getResolveStatus() == DefaultJSONParser.NEED_TO_RESOLVE) {
            parser.setResolveStatus(DefaultJSONParser.NONE);
        } else {
            extracted10(object, fieldValues, value);
        }
    }

    private void extracted10(Object object, Map<String, Object> fieldValues, Object value) {
        if (object == null) {
            fieldValues.put(fieldInfo.name, value);
        } else {
            setValue(object, value);
        }
    }

    private Object extracted6(DefaultJSONParser parser, ObjectDeserializer fieldValueDeserilizer, Type fieldType) {
        Object value;
        if (fieldValueDeserilizer instanceof JavaBeanDeserializer && fieldInfo.parserFeatures != 0) {
            JavaBeanDeserializer javaBeanDeser = (JavaBeanDeserializer) fieldValueDeserilizer;
            value = javaBeanDeser.deserialze(parser, fieldType, fieldInfo.name, fieldInfo.parserFeatures);
        } else {
            value = extracted5(parser, fieldValueDeserilizer, fieldType);
        }
        return value;
    }

    private Object extracted5(DefaultJSONParser parser, ObjectDeserializer fieldValueDeserilizer, Type fieldType) {
        Object value;
        if (extracted4(fieldValueDeserilizer)) {
            value = ((ContextObjectDeserializer) fieldValueDeserilizer) //
                                    .deserialze(parser,
                                                fieldType,
                                                fieldInfo.name,
                                                fieldInfo.format,
                                                fieldInfo.parserFeatures);
        } else {
            value = fieldValueDeserilizer.deserialze(parser, fieldType, fieldInfo.name);
        }
        return value;
    }

    private boolean extracted4(ObjectDeserializer fieldValueDeserilizer) {
        return (this.fieldInfo.format != null || this.fieldInfo.parserFeatures != 0)
                && fieldValueDeserilizer instanceof ContextObjectDeserializer;
    }

    private void extracted3(DefaultJSONParser parser, Type objectType) {
        ParseContext objContext = parser.getContext();
        if (objContext != null) {
            objContext.type = objectType;
        }
    }

    private ObjectDeserializer extracted2(DefaultJSONParser parser, ObjectDeserializer fieldValueDeserilizer,
            Type fieldType) {
        if (fieldValueDeserilizer instanceof JavaObjectDeserializer) {
            fieldValueDeserilizer = parser.getConfig().getDeserializer(fieldType);
        }
        return fieldValueDeserilizer;
    }

    private void extracted(DefaultJSONParser parser) {
        if (this.fieldValueDeserilizer == null) {
            getFieldValueDeserilizer(parser.getConfig());
        }
    }
    @Override
    public int getFastMatchToken() {
        if (fieldValueDeserilizer != null) {
            return fieldValueDeserilizer.getFastMatchToken();
        }

        return JSONToken.LITERAL_INT;
    }

    public void parseFieldUnwrapped(DefaultJSONParser parser, Object object, Type objectType, Map<String, Object> fieldValues) {
        throw new JSONException("TODO");
    }
}
