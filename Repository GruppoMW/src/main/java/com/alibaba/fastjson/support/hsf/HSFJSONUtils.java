package com.alibaba.fastjson.support.hsf;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.*;


import java.lang.reflect.Method;
import java.lang.reflect.Type;



public class HSFJSONUtils {
    private HSFJSONUtils() {
        throw new IllegalStateException("Utility class");
    }
    static final SymbolTable typeSymbolTable      = new SymbolTable(1024);
    static final char[]      fieldName_argsTypes  = "\"argsTypes\"".toCharArray();
    static final char[]      fieldName_argsObjs   = "\"argsObjs\"".toCharArray();

    static final char[]      fieldName_type       = "\"@type\":".toCharArray();

    public static Object[] parseInvocationArguments(String json, MethodLocator methodLocator) {
        JSONObject jsonObject = JSON.parseObject(json);
        String[] argTypes = jsonObject.getObject("argsTypes", String[].class);
        Method method = methodLocator.findMethod(argTypes);
        JSONArray argObjs = jsonObject.getJSONArray("argsObjs");
        Type[] paramTypes = method.getGenericParameterTypes();
        Object[] values = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            values[i] = argObjs.getObject(i, paramTypes[i]);
        }
        return values;
    }
}
