package com.alibaba.fastjson.serializer;

public class SerialContext {

    public final SerialContext parent;
    public final Object        object;
    public final Object        fieldName;
    public final int           features;

    public SerialContext(SerialContext parent, Object object, Object fieldName, int features){
        this.parent = parent;
        this.object = object;
        this.fieldName = fieldName;
        this.features = features;
    }

    public String toString() {
        if (parent == null) {
            return "$";
        } else {
            StringBuilder buf = new StringBuilder();
            toString(buf);
            return buf.toString();
        }
    }

    protected void toString(StringBuilder buf) {
        if (parent == null) {
            buf.append('$');
        } else {
            parent.toString(buf);
            if (fieldName == null) {
                buf.append(".null");
            } else if (fieldName instanceof Integer) {
                buf.append('[');
                buf.append(((Integer)fieldName).intValue());
                buf.append(']');
            } else {
                buf.append('.');

                String fieldName1 = this.fieldName.toString();
                boolean special = false;
                for (int i = 0; i < fieldName1.length(); ++i) {
                    char ch = fieldName1.charAt(i);
                    if (chLen(ch)) {
                        continue;
                    }
                    special = true;
                }

                bufAppend(special, fieldName1,buf);
            }
        }
    }

    public boolean chLen(char ch){
        return (ch >= '0' && ch <='9') || (ch >= 'A' && ch <='Z') || (ch >= 'a' && ch <='z') || ch > 128;
    }

    public void bufAppend(boolean special,String fieldName,StringBuilder buf){
        if (special) {
            for (int i = 0; i < fieldName.length(); ++i) {
                char ch = fieldName.charAt(i);
                if (ch == '\\') {
                    bufAppend(buf);
                } else if (chLen(ch)) {
                    buf.append(ch);
                    continue;
                } else if(ch == '\"'){
                    bufAppend(buf);
                } else {
                    buf.append('\\');
                    buf.append('\\');
                }
                buf.append(ch);
            }
        } else {
            buf.append(fieldName);
        }
    }

    private static void bufAppend(StringBuilder buf) {
        buf.append('\\');
        buf.append('\\');
        buf.append('\\');
    }

    public SerialContext getParent() {
        return parent;
    }

    public Object getObject() {
        return object;
    }

    public Object getFieldName() {
        return fieldName;
    }

    public String getPath() {
        return toString();
    }
}
