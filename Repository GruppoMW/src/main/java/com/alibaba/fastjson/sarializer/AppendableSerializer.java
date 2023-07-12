package com.alibaba.fastjson.serializer;

import java.io.IOException;
import java.lang.reflect.Type;

public class AppendableSerializer implements ObjectSerializer {

    public static final AppendableSerializer instance = new AppendableSerializer();

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        if (object == null) {
            SerializeWriter out = serializer.out;
            out.writeNull(SerializerFeature.WRITE_NULL_STRING_AS_EMPTY);
            return;
        }

        serializer.write(object.toString());
    }

}
