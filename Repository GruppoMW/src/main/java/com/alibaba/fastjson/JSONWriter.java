package com.alibaba.fastjson;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;

import static com.alibaba.fastjson.JSONStreamContext.*;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;

public class JSONWriter implements Closeable, Flushable {

    private SerializeWriter   writer;

    private JSONSerializer    serializer;

    private JSONStreamContext context;

    public JSONWriter(Writer out){
        writer = new SerializeWriter(out);
        serializer = new JSONSerializer(writer);
    }

    public void config(SerializerFeature feature, boolean state) {
        this.writer.config(feature, state);
    }

    public void startObject() {
        if (context != null) {
            beginStructure();
        }
        context = new JSONStreamContext(context, JSONStreamContext.START_OBJECT);
        writer.write('{');
    }

    public void endObject() {
        writer.write('}');
        endStructure();
    }

    public void writeKey(String key) {
        writeObject(key);
    }

    public void writeValue(Object object) {
        writeObject(object);
    }

    public void writeObject(String object) {
        beforeWrite();

        serializer.write(object);

        afterWrite();
    }

    public void writeObject(Object object) {
        beforeWrite();
        serializer.write(object);
        afterWrite();
    }

    public void startArray() {
        if (context != null) {
            beginStructure();
        }

        context = new JSONStreamContext(context, START_ARRAY);
        writer.write('[');
    }

    private void beginStructure() {
        final int state = context.state;
        switch (context.state) {
            case PROPERTY_KEY:
                writer.write(':');
                break;
            case ARRAY_VALUE:
                writer.write(',');
                break;
            case START_OBJECT:
                break;
            case START_ARRAY:
                break;
            default:
                throw new JSONException("illegal state : " + state);
        }
    }

    public void endArray() {
        writer.write(']');
        endStructure();
    }

    private void endStructure() {
        context = context.parent;

        if (context == null) {
            return;
        }
        
        int newState = -1;
        switch (context.state) {
            case PROPERTY_KEY:
                newState = PROPERTY_VALUE;
                break;
            case START_ARRAY:
                newState = ARRAY_VALUE;
                break;
            case ARRAY_VALUE:
                break;
            case START_OBJECT:
                newState = PROPERTY_KEY;
                break;
            default:
                break;
        }
        if (newState != -1) {
            context.state = newState;
        }
    }

    private void beforeWrite() {
        if (context == null) {
            return;
        }
        
        switch (context.state) {
            case START_OBJECT:
            case START_ARRAY:
                break;
            case PROPERTY_KEY:
                writer.write(':');
                break;
            case PROPERTY_VALUE:
                writer.write(',');
                break;
            case ARRAY_VALUE:
                writer.write(',');
                break;
            default:
                break;
        }
    }

    private void afterWrite() {
        if (context == null) {
            return;
        }

        final int state = context.state;
        int newState = -1;
        switch (state) {
            case PROPERTY_KEY:
                newState = PROPERTY_VALUE;
                break;
            case START_OBJECT:
            case PROPERTY_VALUE:
                newState = PROPERTY_KEY;
                break;
            case START_ARRAY:
                newState = ARRAY_VALUE;
                break;
            case ARRAY_VALUE:
                break;
            default:
                break;
        }

        if (newState != -1) {
            context.state = newState;
        }
    }

    public void flush() throws IOException {
        writer.flush();
    }

    public void close() throws IOException {
        writer.close();
    }

    public void writeStartObject() {
        startObject();
    }

    public void writeEndObject() {
        endObject();
    }

    public void writeStartArray() {
        startArray();
    }

    public void writeEndArray() {
        endArray();
    }
}