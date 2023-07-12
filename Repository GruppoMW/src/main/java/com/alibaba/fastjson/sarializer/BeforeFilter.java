package com.alibaba.fastjson.serializer;

public abstract class BeforeFilter implements SerializeFilter {

    private static final ThreadLocal<JSONSerializer> serializerLocal = new ThreadLocal<>();
    private static final ThreadLocal<Character>      seperatorLocal  = new ThreadLocal<>();

    private static final Character                   COMMA           = Character.valueOf(',');

    public void unloadSerializerLocal(){
        serializerLocal.remove();
    }

    public void unloadSeperatorLocal(){
        seperatorLocal.remove();
    }

    final char writeBefore(JSONSerializer serializer, Object object, char seperator) {
        JSONSerializer last = serializerLocal.get();
        serializerLocal.set(serializer);
        seperatorLocal.set(seperator);
        writeBefore(object);
        serializerLocal.set(last);
        return seperatorLocal.get();
    }

    protected final void writeKeyValue(String key, Object value) {
        JSONSerializer serializer = serializerLocal.get();
        char seperator = seperatorLocal.get();

        boolean ref = serializer.references.containsKey(value);
        serializer.writeKeyValue(seperator, key, value);
        if (!ref) {
            serializer.references.remove(value);
        }

        if (seperator != ',') {
            seperatorLocal.set(COMMA);
        }
    }

    public abstract void writeBefore(Object object);
}
