package com.alibaba.fastjson.serializer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Created by wenshao on 10/05/2017.
 */
public class AnnotationSerializer implements ObjectSerializer {


    public static final AnnotationSerializer instance = new AnnotationSerializer();

    private static final String NOT_SUPPORT_TYPE_ANNOTATION =  "not support Type Annotation.";

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        if (!isAnnotation(object)) {
        return;
        }
        Class<?> annotationClass = getAnnotationClass(object);

         Map<String, Method> annotationMembers = getAnnotationMembers(annotationClass);

        JSONObject json = getJsonFromMembers(annotationMembers);

        serializer.write(json);
    }

    private boolean isAnnotation(Object object) {
        Class<?> objClass = object.getClass();
         Class<?>[] interfaces = objClass.getInterfaces();
        return interfaces.length == 1 && interfaces[0].isAnnotation();
        }
        
        private Class<?> getAnnotationClass(Object object) {
         Class<?> objClass = object.getClass();
        Class<?>[] interfaces = objClass.getInterfaces();
        return interfaces[0];
        }
        
        private Map<String, Method> getAnnotationMembers(Class<?> annotationClass) {
            try {
                Class<?> annotationType = Class.forName("sun.reflect.annotation.AnnotationType");
                Object type = annotationType.getMethod("getInstance", Class.class).invoke(null, annotationClass);
                return (Map<String, Method>) annotationType.getMethod("members").invoke(type);
            } catch (Exception e) {
                throw new JSONException(NOT_SUPPORT_TYPE_ANNOTATION, e);
            }
        }
        
        private JSONObject getJsonFromMembers(Map<String, Method> members) {
            JSONObject json = new JSONObject(members.size());
            for (Map.Entry<String, Method> entry : members.entrySet()) {
                Object val = null;
                json.put(entry.getKey(), JSON.toJSON(val));
            }
            return json;
        }
}
