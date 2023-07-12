package com.alibaba.fastjson.serializer;

/**
 * @since 1.1.35
 */
public abstract class AfterFilter implements SerializeFilter {

    private static final ThreadLocal<JSONSerializer> serializerLocal = new ThreadLocal<>();
    private static final ThreadLocal<Character>      seperatorLocal  = new ThreadLocal<>();

    private static final Character                   COMMA           = Character.valueOf(',');

    final char writeAfter(JSONSerializer serializer, Object object, char seperator) {
        JSONSerializer last = serializerLocal.get();
        serializerLocal.set(serializer);
        seperatorLocal.set(seperator);
        writeAfter(object);
        serializerLocal.set(last);
        return seperatorLocal.get();
    }

    protected final void writeKeyValue(String key, Object value) {
        JSONSerializer serializer = serializerLocal.get();
        char seperator = seperatorLocal.get();

        boolean ref = serializer.containsReference(value);
        serializer.writeKeyValue(seperator, key, value);
        if (!ref && serializer.references != null) {
            serializer.references.remove(value);
        }
        if (seperator != ',') {
            seperatorLocal.set(COMMA);
        }
    }

    public abstract void writeAfter(Object object);

    public void unloadSerializerLocal() {
        serializerLocal.remove(); 
    }

    public void unloadSeperatorLocal() {
        seperatorLocal.remove(); 
    }    
}
