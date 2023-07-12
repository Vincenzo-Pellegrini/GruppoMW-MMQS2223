package com.alibaba.fastjson.util;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

public class GenericArrayTypeImpl implements GenericArrayType {
    Type genericComponentType;

    public GenericArrayTypeImpl(Type genericComponentType) {
        if(genericComponentType != null)
        {
            this.genericComponentType = genericComponentType;
        }
    }

    @Override
    public Type getGenericComponentType() {
        return this.genericComponentType;
    }

    @Override
    public String toString() {
        Type genericComponentTipo = this.getGenericComponentType();
        StringBuilder builder = new StringBuilder();
        if (genericComponentTipo instanceof Class) {
            builder.append(((Class) genericComponentTipo).getName());
        } else {
            builder.append(genericComponentTipo.toString());
        }
        builder.append("[]");
        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GenericArrayType) {
            GenericArrayType that = (GenericArrayType) obj;
            return this.genericComponentType.equals(that.getGenericComponentType());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.genericComponentType.hashCode();
    }
}
