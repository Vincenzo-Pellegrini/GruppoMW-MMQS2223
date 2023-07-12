package com.alibaba.fastjson.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.PropertyNamingStrategy;
import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONPOJOBuilder;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;

public class JavaBeanInfo {

    public final Class<?> clazz;
    public final Class<?> builderClass;
    public final Constructor<?> defaultConstructor;
    public final Constructor<?> creatorConstructor;
    public final Method factoryMethod;
    public final Method buildMethod;

    public int defaultConstructorParameterSize = 0;

    public final FieldInfo[] fields;
    public FieldInfo[] sortedFields = new FieldInfo[0];

    public final int parserFeatures;

    public final JSONType jsonType;

    public String typeName = null;
    public String typeKey;

    public String[] orders;


    public Type[] creatorConstructorParameterTypes;
    public String[] creatorConstructorParameters;

    public boolean kotlin;
    public Constructor<?> kotlinDefaultConstructor;

    public static final  String MULTI_JSONCREATOR = "multi-JSONCreator" ;

    public JavaBeanInfo(Class<?> clazz, //
                        Class<?> builderClass, //
                        Constructor<?> defaultConstructor, //
                        Constructor<?> creatorConstructor, //
                        Method factoryMethod, //
                        Method buildMethod, //
                        JSONType jsonType, //
                        List<FieldInfo> fieldList) {
        this.clazz = clazz;
        this.builderClass = builderClass;
        this.defaultConstructor = defaultConstructor;
        this.creatorConstructor = creatorConstructor;
        this.factoryMethod = factoryMethod;
        this.parserFeatures = TypeUtils.getParserFeatures(clazz);
        this.buildMethod = buildMethod;

        this.jsonType = jsonType;
        typeKey = getString(clazz, jsonType);

        fields = new FieldInfo[fieldList.size()];
        fieldList.toArray(fields);

        FieldInfo[] sortedCampi = new FieldInfo[fields.length];
        ordersNotNull(fieldList, sortedCampi);

        getSortedFields(defaultConstructor, factoryMethod, sortedCampi);

        if (creatorConstructor != null) {
            getConstructors(clazz, creatorConstructor);
        }
    }

    private void getConstructors(Class<?> clazz, Constructor<?> creatorConstructor) {
        this.creatorConstructorParameterTypes = creatorConstructor.getParameterTypes();


        kotlin = TypeUtils.isKotlin();
        if (kotlin) {
            this.creatorConstructorParameters = TypeUtils.getKoltinConstructorParameters(clazz);
            try {
                this.kotlinDefaultConstructor = clazz.getConstructor();
            } catch (Throwable ex) {
                // skip
            }

            getParamAnnotationArrays(creatorConstructor);
        } else {
            getCreator(creatorConstructor);
        }
    }

    private void getCreator(Constructor<?> creatorConstructor) {
        boolean match;
        if (creatorConstructorParameterTypes.length != fields.length) {
            match = false;
        } else {
            match = true;
            for (int i = 0; i < creatorConstructorParameterTypes.length; i++) {
                if (creatorConstructorParameterTypes[i] != fields[i].fieldClass) {
                    match = false;
                    break;
                }
            }
        }

        if (!match) {
            this.creatorConstructorParameters = ASMUtils.lookupParameterNames(creatorConstructor);
        }
    }

    private void getParamAnnotationArrays(Constructor<?> creatorConstructor) {
        Annotation[][] paramAnnotationArrays = TypeUtils.getParameterAnnotations(creatorConstructor);
        for (int i = 0; i < creatorConstructorParameters.length && i < paramAnnotationArrays.length; ++i) {
            Annotation[] paramAnnotations = paramAnnotationArrays[i];
            JSONField fieldAnnotation = null;
            for (Annotation paramAnnotation : paramAnnotations) {
                if (paramAnnotation instanceof JSONField) {
                    fieldAnnotation = (JSONField) paramAnnotation;
                    break;
                }
            }
            if (fieldAnnotation != null) {
                String fieldAnnotationName = fieldAnnotation.name();
                if (fieldAnnotationName.length() > 0) {
                    creatorConstructorParameters[i] = fieldAnnotationName;
                }
            }
        }
    }

    private void getSortedFields(Constructor<?> defaultConstructor, Method factoryMethod, FieldInfo[] sortedFields) {
        if (Arrays.equals(fields, sortedFields)) {
            sortedFields = fields;
        }
        this.sortedFields = sortedFields;

        if (defaultConstructor != null) {
            defaultConstructorParameterSize = defaultConstructor.getParameterTypes().length;
        } else if (factoryMethod != null) {
            defaultConstructorParameterSize = factoryMethod.getParameterTypes().length;
        } else {
            defaultConstructorParameterSize = 0;
        }
    }

    private void ordersNotNull(List<FieldInfo> fieldList, FieldInfo[] sortedFields) {
        if (orders != null) {
            LinkedHashMap<String, FieldInfo> map = new LinkedHashMap<>(fieldList.size());
            for (FieldInfo field : fields) {
                map.put(field.name, field);
            }
            int i = 0;
            for (String item : orders) {
                FieldInfo field = map.get(item);
                if (field != null) {
                    sortedFields[i++] = field;
                    map.remove(item);
                }
            }
            for (FieldInfo field : map.values()) {
                sortedFields[i++] = field;
            }
        } else {
            System.arraycopy(fields, 0, sortedFields, 0, fields.length);
            Arrays.sort(sortedFields);
        }
    }

    private String getString(Class<?> clazz, JSONType jsonType) {
        if (jsonType != null) {
            String typeNome = jsonType.typeName();
            typeKey = jsonType.typeKey();
            this.typeKey = typeKey.length() > 0 ? typeKey : null;

            if (typeName.length() != 0) {
                this.typeName = typeNome;
            } else {
                this.typeName = clazz.getName();
            }
            String[] ordini = jsonType.orders();
            this.orders = ordini.length == 0 ? null : orders;
        } else {
            this.typeName = clazz.getName();
            this.typeKey = null;
            this.orders = null;
        }
        return typeKey;
    }

    private static FieldInfo getField(List<FieldInfo> fieldList, String propertyName) {
        for (FieldInfo item : fieldList) {
            if (item.name.equals(propertyName)) {
                return item;
            }

            Field field = item.field;
            if (field != null && item.getAnnotation() != null && field.getName().equals(propertyName)) {
                return item;
            }
        }
        return null;
    }


    static boolean add(List<FieldInfo> fieldList, FieldInfo field) {
        for (int i = fieldList.size() - 1; i >= 0; --i) {
            FieldInfo item = fieldList.get(i);

            if (item.name.equals(field.name)) {
                if (item.getOnly && !field.getOnly) {
                    continue;
                }

                if (item.fieldClass.isAssignableFrom(field.fieldClass)) {
                    fieldList.set(i, field);
                    return true;
                }

                int result = item.compareTo(field);

                if (result < 0) {
                    fieldList.set(i, field);
                    return true;
                } else {
                    return false;
                }
            }
        }
        fieldList.add(field);

        return true;
    }

    public static JavaBeanInfo build(Class<?> clazz, Type type, PropertyNamingStrategy propertyNamingStrategy) {
        return build(clazz, type, propertyNamingStrategy, false, false);
    }

    private static Map<TypeVariable, Type> buildGenericInfo(Class<?> clazz) {
        Class<?> childClass = clazz;
        Class<?> currentClass = clazz.getSuperclass();
        if (currentClass == null) {
            return Collections.emptyMap();
        }

        Map<TypeVariable, Type> typeVarMap = null;

        //analyse the whole generic info from the class inheritance
        for (; currentClass != null && currentClass != Object.class; childClass = currentClass, currentClass = currentClass.getSuperclass()) {
            if (childClass.getGenericSuperclass() instanceof ParameterizedType) {
                Type[] childGenericParentActualTypeArgs = ((ParameterizedType) childClass.getGenericSuperclass()).getActualTypeArguments();
                for (int i = 0; i < childGenericParentActualTypeArgs.length; i++) {
                    //if the child class's generic super class actual args is defined in the child class type parameters
                    if (typeVarMap == null) {
                        typeVarMap = new HashMap<>();
                    }
                }
            }
        }

        return typeVarMap;
    }

    public static Map<TypeVariable , Type> typeVarMapContainsKey(Map<TypeVariable , Type> typeVarMapp , Type[] childGenericParentActualTypeArgs , TypeVariable[] currentTypeParameters,int i){
        if (typeVarMapp.containsKey(childGenericParentActualTypeArgs[i])) {
            Type actualArg = typeVarMapp.get(childGenericParentActualTypeArgs[i]);
            typeVarMapp.put(currentTypeParameters[i], actualArg);
        } else {
            typeVarMapp.put(currentTypeParameters[i], childGenericParentActualTypeArgs[i]);
        }
        return typeVarMapp;
    }




    public static <T> JavaBeanInfo build(Class<?> clazz //
            , Type type //
            , PropertyNamingStrategy propertyNamingStrategy //
            , boolean fieldBased //
            , boolean jacksonCompatible
    ) {
        JSONType jsonType = TypeUtils.getAnnotation(clazz,JSONType.class);
        if (jsonType != null) {
            PropertyNamingStrategy jsonTypeNaming = jsonType.naming();
            if (jsonTypeNaming != null && jsonTypeNaming != PropertyNamingStrategy.CAMEL_CASE) {
                propertyNamingStrategy = jsonTypeNaming;
            }
        }

        Class<?> builderClass = getBuilderClass(clazz, jsonType);

        Field[] declaredFields = clazz.getDeclaredFields();
        Method[] methods = clazz.getMethods();

        boolean kotlin = TypeUtils.isKotlin();
        Constructor[] constructors = clazz.getDeclaredConstructors();

        Constructor<?> defaultConstructor = null;
        if ((!kotlin) || constructors.length == 1) {
            if (builderClass == null) {
                defaultConstructor = getDefaultConstructor(clazz, constructors);
            } else {
                defaultConstructor = getDefaultConstructor(builderClass, (Constructor<T>[])builderClass.getDeclaredConstructors());
            }
        }

        Constructor<?> creatorConstructor = null;
        Method buildMethod = null;
        Method factoryMethod = null;

        List<FieldInfo> fieldList = new ArrayList<>();

        if (fieldBased) {
            for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
                Field[] fields = currentClass.getDeclaredFields();

                computeFields(clazz, type, propertyNamingStrategy, fieldList, fields);
            }

            if (defaultConstructor != null) {
                TypeUtils.setAccessible(defaultConstructor);
            }

            return new JavaBeanInfo(clazz, builderClass, defaultConstructor, null, factoryMethod, buildMethod, jsonType, fieldList);
        }

        boolean isInterfaceOrAbstract = clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers());
        if ((defaultConstructor == null && builderClass == null) || isInterfaceOrAbstract) {

            Type mixInType = JSON.getMixInAnnotations(clazz);
            if (mixInType instanceof Class) {
                Constructor<?>[] mixInConstructors = ((Class<?>) mixInType).getConstructors();
                Constructor<?> mixInCreator = getCreatorConstructor(mixInConstructors);
                if (mixInCreator != null) {
                    try {
                        creatorConstructor = clazz.getConstructor(mixInCreator.getParameterTypes());
                    } catch (NoSuchMethodException e) {
                        // skip
                    }
                }
            }

            if (creatorConstructor == null) {
                creatorConstructor = getCreatorConstructor(constructors);
            }

            if (creatorConstructor != null && !isInterfaceOrAbstract) { // 基于标记 JSONCreator 注解的构造方法
                TypeUtils.setAccessible(creatorConstructor);

                Class<?>[] types = creatorConstructor.getParameterTypes();

                String[] lookupParameterNames = null;
                if (types.length > 0) {
                    Annotation[][] paramAnnotationArrays = TypeUtils.getParameterAnnotations(creatorConstructor);
                    for (int i = 0; i < types.length && i < paramAnnotationArrays.length; ++i) {
                        Annotation[] paramAnnotations = paramAnnotationArrays[i];
                        JSONField fieldAnnotation = null;
                        for (Annotation paramAnnotation : paramAnnotations) {
                            if (paramAnnotation instanceof JSONField) {
                                fieldAnnotation = (JSONField) paramAnnotation;
                                break;
                            }
                        }

                        Class<?> fieldClass = types[i];
                        Type fieldType = creatorConstructor.getGenericParameterTypes()[i];

                        String fieldName = null;
                        Field field = null;
                        int ordinal = 0;
                        int serialzeFeatures = 0;
                        int parserFeatures = 0;
                        if (fieldAnnotation != null) {
                            field = TypeUtils.getField(clazz, fieldAnnotation.name(), declaredFields);
                            ordinal = fieldAnnotation.ordinal();
                            serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
                            parserFeatures = Feature.of(fieldAnnotation.parseFeatures());
                            fieldName = fieldAnnotation.name();
                        }

                        if (fieldName == null || fieldName.length() == 0) {
                            if (lookupParameterNames == null) {
                                lookupParameterNames = ASMUtils.lookupParameterNames(creatorConstructor);
                            }
                            fieldName = lookupParameterNames[i];
                        }

                        if (field == null) {
                            if (lookupParameterNames == null) {
                                if (kotlin) {
                                    lookupParameterNames = TypeUtils.getKoltinConstructorParameters(clazz);
                                } else {
                                    lookupParameterNames = ASMUtils.lookupParameterNames(creatorConstructor);
                                }
                            }

                            if (lookupParameterNames.length > i) {
                                String parameterName = lookupParameterNames[i];
                                field = TypeUtils.getField(clazz, parameterName, declaredFields);
                            }
                        }

                        FieldInfo fieldInfo = new FieldInfo(fieldName, clazz, fieldClass, fieldType, field,
                                ordinal, serialzeFeatures, parserFeatures);
                        add(fieldList, fieldInfo);
                    }
                }

            } else if ((factoryMethod = getFactoryMethod(methods, jacksonCompatible)) != null) {
                TypeUtils.setAccessible(factoryMethod);

                String[] lookupParameterNames = null;
                Class<?>[] types = factoryMethod.getParameterTypes();
                if (types.length > 0) {
                    Annotation[][] paramAnnotationArrays = TypeUtils.getParameterAnnotations(factoryMethod);
                    for (int i = 0; i < types.length; ++i) {
                        Annotation[] paramAnnotations = paramAnnotationArrays[i];
                        JSONField fieldAnnotation = null;
                        for (Annotation paramAnnotation : paramAnnotations) {
                            if (paramAnnotation instanceof JSONField) {
                                fieldAnnotation = (JSONField) paramAnnotation;
                                break;
                            }
                        }
                        if (fieldAnnotation == null && !(jacksonCompatible && TypeUtils.isJacksonCreator(factoryMethod))) {
                            throw new JSONException("illegal json creator");
                        }

                        String fieldName = null;
                        int ordinal = 0;
                        int serialzeFeatures = 0;
                        int parserFeatures = 0;

                        if (fieldAnnotation != null) {
                            fieldName = fieldAnnotation.name();
                            ordinal = fieldAnnotation.ordinal();
                            serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
                            parserFeatures = Feature.of(fieldAnnotation.parseFeatures());
                        }

                        if (fieldName == null || fieldName.length() == 0) {
                            if (lookupParameterNames == null) {
                                lookupParameterNames = ASMUtils.lookupParameterNames(factoryMethod);
                            }
                            fieldName = lookupParameterNames[i];
                        }

                        Class<?> fieldClass = types[i];
                        Type fieldType = factoryMethod.getGenericParameterTypes()[i];

                        Field field = TypeUtils.getField(clazz, fieldName, declaredFields);
                        FieldInfo fieldInfo = new FieldInfo(fieldName, clazz, fieldClass, fieldType, field,
                                ordinal, serialzeFeatures, parserFeatures);
                        add(fieldList, fieldInfo);
                    }

                    return new JavaBeanInfo(clazz, builderClass, null, null, factoryMethod, null, jsonType, fieldList);
                }
            } else if (!isInterfaceOrAbstract) {
                String className = clazz.getName();

                String[] paramNames = null;
                if (kotlin && constructors.length > 0) {
                    paramNames = TypeUtils.getKoltinConstructorParameters(clazz);
                    creatorConstructor = TypeUtils.getKotlinConstructor(constructors, paramNames);
                    TypeUtils.setAccessible(creatorConstructor);
                } else {

                    for (Constructor<?> constructor : constructors) {

                        String[] lookupParameterNames = ASMUtils.lookupParameterNames(constructor);

                        paramNames = lookupParameterNames;
                        creatorConstructor = constructor;
                    }
                }

                Class<?>[] types = null;
                if (paramNames != null) {
                    types = creatorConstructor.getParameterTypes();
                }

                if (paramNames != null
                        && types.length == paramNames.length) {
                    Annotation[][] paramAnnotationArrays = TypeUtils.getParameterAnnotations(creatorConstructor);
                    for (int i = 0; i < types.length; ++i) {
                        Annotation[] paramAnnotations = paramAnnotationArrays[i];
                        String paramName = paramNames[i];

                        JSONField fieldAnnotation = null;
                        for (Annotation paramAnnotation : paramAnnotations) {
                            if (paramAnnotation instanceof JSONField) {
                                fieldAnnotation = (JSONField) paramAnnotation;
                                break;
                            }
                        }

                        Class<?> fieldClass = types[i];
                        Type fieldType = creatorConstructor.getGenericParameterTypes()[i];
                        Field field = TypeUtils.getField(clazz, paramName, declaredFields);
                        if (field != null &&  (fieldAnnotation == null)) {
                            fieldAnnotation = TypeUtils.getAnnotation(field, JSONField.class);

                        }
                        final int ordinal;
                        final int serialzeFeatures;
                        final int parserFeatures;
                        if (fieldAnnotation == null) {
                            ordinal = 0;
                            serialzeFeatures = 0;

                            if ("org.springframework.security.core.userdetails.User".equals(className)
                                    && "password".equals(paramName)) {
                                parserFeatures = Feature.INIT_STRING_FIELD_AS_EMPTY.mask;
                            } else {
                                parserFeatures = 0;
                            }
                        } else {
                            String nameAnnotated = fieldAnnotation.name();
                            if (nameAnnotated.length() != 0) {
                                paramName = nameAnnotated;
                            }
                            ordinal = fieldAnnotation.ordinal();
                            serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
                            parserFeatures = Feature.of(fieldAnnotation.parseFeatures());
                        }
                        FieldInfo fieldInfo = new FieldInfo(paramName, clazz, fieldClass, fieldType, field,
                                ordinal, serialzeFeatures, parserFeatures);
                        add(fieldList, fieldInfo);
                    }

                    if ((!kotlin) && !clazz.isInstance("javax.servlet.http.Cookie")) {
                        return new JavaBeanInfo(clazz, builderClass, null, creatorConstructor, null, null, jsonType, fieldList);
                    }
                } else {
                    throw new JSONException("default constructor not found. " + clazz);
                }
            }
        }

        if (defaultConstructor != null) {
            TypeUtils.setAccessible(defaultConstructor);
        }

        if (builderClass != null) {
            String withPrefix = null;

            JSONPOJOBuilder builderAnno = TypeUtils.getAnnotation(builderClass, JSONPOJOBuilder.class);
            if (builderAnno != null) {
                withPrefix = builderAnno.withPrefix();
            }

            if (withPrefix == null) {
                withPrefix = "with";
            }

            for (Method method : builderClass.getMethods()) {


                int ordinal = 0;
                int serialzeFeatures = 0;
                int parserFeatures = 0;

                JSONField annotation = TypeUtils.getAnnotation(method, JSONField.class);

                if (annotation == null) {
                    annotation = TypeUtils.getSuperMethodAnnotation(clazz, method);
                }

                if (annotation != null) {


                    ordinal = annotation.ordinal();
                    serialzeFeatures = SerializerFeature.of(annotation.serialzeFeatures());
                    parserFeatures = Feature.of(annotation.parseFeatures());

                    if (annotation.name().length() != 0) {
                        String propertyName = annotation.name();
                        add(fieldList, new FieldInfo(propertyName, method, null, clazz, type, ordinal, serialzeFeatures, parserFeatures,
                                annotation, null, null));
                    }
                }

                String methodName = method.getName();
                StringBuilder properNameBuilder;
                if (methodName.startsWith("set") && methodName.length() > 3) {
                    properNameBuilder = new StringBuilder(methodName.substring(3));
                } else {
                    if (withPrefix.length() == 0){
                        properNameBuilder = new StringBuilder(methodName);
                    } else {

                        properNameBuilder = new StringBuilder(methodName.substring(withPrefix.length()));
                    }
                }

                char c0 = properNameBuilder.charAt(0);


                properNameBuilder.setCharAt(0, Character.toLowerCase(c0));

                String propertyName = properNameBuilder.toString();

                add(fieldList, new FieldInfo(propertyName, method, null, clazz, type, ordinal, serialzeFeatures, parserFeatures,
                        annotation, null, null));
            }

            if (builderClass != null) {
                JSONPOJOBuilder builderAnnotation = TypeUtils.getAnnotation(builderClass, JSONPOJOBuilder.class);

                String buildMethodName = null;
                if (builderAnnotation != null) {
                    buildMethodName = builderAnnotation.buildMethod();
                }

                if (buildMethodName == null || buildMethodName.length() == 0) {
                    buildMethodName = "build";
                }

                try {
                    buildMethod = builderClass.getMethod(buildMethodName);
                } catch (NoSuchMethodException | SecurityException e) {
                    // skip
                }

                if (buildMethod == null) {
                    try {
                        buildMethod = builderClass.getMethod("create");
                    } catch (NoSuchMethodException | SecurityException e) {
                        // skip
                    }
                }

                if (buildMethod == null) {
                    throw new JSONException("buildMethod not found.");
                }

                TypeUtils.setAccessible(buildMethod);
            }
        }

        for (Method method : methods) { //
            int ordinal = 0;
            int serialzeFeatures = 0;
            int parserFeatures = 0;
            String methodName = method.getName();





            Class<?>[] types = method.getParameterTypes();



            JSONField annotation = TypeUtils.getAnnotation(method, JSONField.class);
            if (annotation != null
                    && types.length == 2
                    && types[0] == String.class
                    && types[1] == Object.class) {
                add(fieldList, new FieldInfo("", method, null, clazz, type, ordinal,
                        serialzeFeatures, parserFeatures, annotation, null, null));
            }


            if (annotation == null) {
                annotation = TypeUtils.getSuperMethodAnnotation(clazz, method);
            }



            if (annotation != null) {

                ordinal = annotation.ordinal();
                serialzeFeatures = SerializerFeature.of(annotation.serialzeFeatures());
                parserFeatures = Feature.of(annotation.parseFeatures());

                if (annotation.name().length() != 0) {
                    String propertyName = annotation.name();
                    add(fieldList, new FieldInfo(propertyName, method, null, clazz, type, ordinal, serialzeFeatures, parserFeatures,
                            annotation, null, null));
                }
            }

            if (annotation == null && !methodName.startsWith("set") || builderClass != null) {
                continue;
            }

            char c3 = methodName.charAt(3);

            String propertyName;
            Field field = null;
            // 用于存储KotlinBean中所有的get方法, 方便后续判断
            List<String> getMethodNameList = null;

            if (kotlin) {
                getMethodNameList = new ArrayList();
                for (int i = 0; i < methods.length; i++) {
                    if (methods[i].getName().startsWith("get")) {
                        getMethodNameList.add(methods[i].getName());
                    }
                }
            }

            if (Character.isUpperCase(c3) //
                    || c3 > 512 // for unicode method name
            ) {
                // 这里本身的逻辑是通过setAbc这类方法名解析出成员变量名为abc或者Abc, 但是在kotlin中, isAbc, abc成员变量的set方法都是setAbc
                // 因此如果是kotlin的话还需要进行不一样的判断, 判断的方式是通过get方法进行判断, isAbc的get方法名为isAbc(), abc的get方法名为getAbc()
                if (kotlin) {
                    String getMethodName = "g" + methodName.substring(1);
                    propertyName = TypeUtils.getPropertyNameByMethodName(getMethodName);
                } else {
                    if (TypeUtils.compatibleWithJavaBean) {
                        propertyName = TypeUtils.decapitalize(methodName.substring(3));
                    } else {
                        propertyName = TypeUtils.getPropertyNameByMethodName(methodName);
                    }
                }

            } else if (c3 == '_') {
                // 这里本身的逻辑是通过set_abc这类方法名解析出成员变量名为abc, 但是在kotlin中, is_abc和_abc成员变量的set方法都是set_abc
                // 因此如果是kotlin的话还需要进行不一样的判断, 判断的方式是通过get方法进行判断, is_abc的get方法名为is_abc(), _abc的get方法名为get_abc()
                if (kotlin) {
                    String getMethodName = "g" + methodName.substring(1);
                    if (getMethodNameList.contains(getMethodName)) {
                        propertyName = methodName.substring(3);
                    } else {
                        propertyName = "is" + methodName.substring(3);
                    }
                    field = TypeUtils.getField(clazz, propertyName, declaredFields);
                } else {
                    propertyName = methodName.substring(4);
                    field = TypeUtils.getField(clazz, propertyName, declaredFields);
                    if (field == null) {
                        String temp = propertyName;
                        propertyName = methodName.substring(3);
                        field = TypeUtils.getField(clazz, propertyName, declaredFields);
                        if (field == null) {
                            propertyName = temp; //减少修改代码带来的影响
                        }
                    }
                }

            } else if (c3 == 'f') {
                propertyName = methodName.substring(3);
            } else if (methodName.length() >= 5 && Character.isUpperCase(methodName.charAt(4))) {
                propertyName = TypeUtils.decapitalize(methodName.substring(3));
            } else {
                propertyName = methodName.substring(3);
                field = TypeUtils.getField(clazz, propertyName, declaredFields);

            }

            if (field == null) {
                field = TypeUtils.getField(clazz, propertyName, declaredFields);
            }

            if (field == null && types[0] == boolean.class) {
                String isFieldName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                field = TypeUtils.getField(clazz, isFieldName, declaredFields);
            }

            JSONField fieldAnnotation = null;
            if (field != null) {
                fieldAnnotation = TypeUtils.getAnnotation(field, JSONField.class);

                if (fieldAnnotation != null) {


                    ordinal = fieldAnnotation.ordinal();
                    serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
                    parserFeatures = Feature.of(fieldAnnotation.parseFeatures());

                    if (fieldAnnotation.name().length() != 0) {
                        propertyName = fieldAnnotation.name();
                        add(fieldList, new FieldInfo(propertyName, method, field, clazz, type, ordinal,
                                serialzeFeatures, parserFeatures, annotation, fieldAnnotation, null));
                    }
                }

            }

            if (propertyNamingStrategy != null) {
                propertyName = propertyNamingStrategy.translate(propertyName);
            }

            add(fieldList, new FieldInfo(propertyName, method, field, clazz, type, ordinal, serialzeFeatures, parserFeatures,
                    annotation, fieldAnnotation, null));
        }

        Field[] fields = clazz.getFields();
        computeFields(clazz, type, propertyNamingStrategy, fieldList, fields);

        for (Method method : clazz.getMethods()) { // getter methods
            String methodName = method.getName();


            if (builderClass == null && methodName.startsWith("get") && Character.isUpperCase(methodName.charAt(3)) && (Collection.class.isAssignableFrom(method.getReturnType()) //
                    || Map.class.isAssignableFrom(method.getReturnType()) //
                    || AtomicBoolean.class == method.getReturnType() //
                    || AtomicInteger.class == method.getReturnType() //
                    || AtomicLong.class == method.getReturnType() //
            )) {
                String propertyName;
                Field collectionField = null;

                JSONField annotation = TypeUtils.getAnnotation(method, JSONField.class);


                if (annotation != null && annotation.name().length() > 0) {
                    propertyName = annotation.name();
                } else {
                    propertyName = TypeUtils.getPropertyNameByMethodName(methodName);
                }

                if (propertyNamingStrategy != null) {
                    propertyName = propertyNamingStrategy.translate(propertyName);
                }

                FieldInfo fieldInfo = getField(fieldList, propertyName);
                if (fieldInfo != null) {
                    continue;
                }

                add(fieldList, new FieldInfo(propertyName, method, collectionField, clazz, type, 0, 0, 0, annotation, null, null));

            }
        }

        if (fieldList.isEmpty()) {
            if (TypeUtils.isXmlField(clazz)) {
                fieldBased = true;
            }

            if (fieldBased) {
                for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
                    computeFields(clazz, type, propertyNamingStrategy, fieldList, declaredFields);
                }
            }
        }

        return new JavaBeanInfo(clazz, builderClass, defaultConstructor, creatorConstructor, factoryMethod, buildMethod, jsonType, fieldList);
    }

    private static void computeFields(Class<?> clazz, Type type, PropertyNamingStrategy propertyNamingStrategy, List<FieldInfo> fieldList, Field[] fields) {

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (shouldSkipField(field) || isReadOnlyField(field)) {
                continue;
            }

            FieldInfo fieldInfo = createFieldInfo(field, clazz, type, propertyNamingStrategy);
            try {
                add(fieldList, fieldInfo);
            } catch (NullPointerException e) {
                //Skip
            }
        }
    }

    private static boolean shouldSkipField(Field field) {
        int modifiers = field.getModifiers();
        return (modifiers & Modifier.STATIC) != 0;
    }

    private static boolean isReadOnlyField(Field field) {
        int modifiers = field.getModifiers();
        return (modifiers & Modifier.FINAL) != 0;
    }

    private static boolean supportsReadOnlyField(Class<?> fieldType) {
        return Map.class.isAssignableFrom(fieldType)
                || Collection.class.isAssignableFrom(fieldType)
                || AtomicLong.class.equals(fieldType)
                || AtomicInteger.class.equals(fieldType)
                || AtomicBoolean.class.equals(fieldType);
    }

    private static boolean isDuplicateField(Field field, List<FieldInfo> fieldList) {
        for (FieldInfo item : fieldList) {
            if (item.name.equals(field.getName())) {
                return true;
            }
        }
        return false;
    }

    private static FieldInfo createFieldInfo(Field field, Class<?> clazz, Type type, PropertyNamingStrategy propertyNamingStrategy) {
        String propertyName = field.getName();
        int ordinal = 0;
        int serialzeFeatures = 0;
        int parserFeatures = 0;
        JSONField fieldAnnotation = TypeUtils.getAnnotation(field, JSONField.class);

        if (fieldAnnotation != null) {
            if (!fieldAnnotation.deserialize()) {
                return null;
            }

            ordinal = fieldAnnotation.ordinal();
            serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
            parserFeatures = Feature.of(fieldAnnotation.parseFeatures());

            if (fieldAnnotation.name().length() != 0) {
                propertyName = fieldAnnotation.name();
            }
        }

        if (propertyNamingStrategy != null) {
            propertyName = propertyNamingStrategy.translate(propertyName);
        }

        return new FieldInfo(propertyName, null, field, clazz, type, ordinal, serialzeFeatures, parserFeatures, null,
                fieldAnnotation, null);
    }


    public static <T>Constructor<T> getDefaultConstructor(Class<?> clazz, Constructor<T>[] constructors) {
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return null;
        }
        // Cerca il costruttore senza argomenti
        Constructor<T> defaultConstructor = null;
        for (Constructor<T> constructor : constructors) {
            if (constructor.getParameterCount() == 0) {
                defaultConstructor = constructor;
                break;
            }
        }

        // Cerca il costruttore della classe esterna (se la classe corrente è una classe interna non statica)
        if (defaultConstructor == null && clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            for (Constructor<T> constructor : constructors) {
                if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].equals(clazz.getDeclaringClass())) {
                    defaultConstructor = constructor;
                    break;
                }
            }
        }

        return defaultConstructor;
    }



    public static Constructor<?> getCreatorConstructor(Constructor[] constructors) {
        Constructor<?> creatorConstructor = null;

        creatorConstructor = findConstructorWithJSONCreatorAnnotation(constructors);

        if (creatorConstructor == null) {
            creatorConstructor = findConstructorWithAllParamsAnnotated(constructors);
        }

        return creatorConstructor;
    }

    private static Constructor<?> findConstructorWithJSONCreatorAnnotation(Constructor[] constructors) {
        Constructor<?> creatorConstructor = null;

        for (Constructor<?> constructor : constructors) {
            JSONCreator annotation = constructor.getAnnotation(JSONCreator.class);
            if (annotation != null) {
                if (creatorConstructor != null) {
                    throw new JSONException(MULTI_JSONCREATOR);
                }
                creatorConstructor = constructor;
            }
        }

        return creatorConstructor;
    }

    private static Constructor<?> findConstructorWithAllParamsAnnotated(Constructor[] constructors) {
        Constructor<?> creatorConstructor = null;

        for (Constructor<?> constructor : constructors) {
            Annotation[][] paramAnnotationArrays = TypeUtils.getParameterAnnotations(constructor);

            if (paramAnnotationArrays.length == 0) {
                continue;
            }
            boolean match = allParamsAnnotatedWithJSONField(paramAnnotationArrays);

            if (match) {
                if (creatorConstructor != null) {
                    throw new JSONException(MULTI_JSONCREATOR);
                }
                creatorConstructor = constructor;
            }
        }

        return creatorConstructor;
    }

    private static boolean allParamsAnnotatedWithJSONField(Annotation[][] paramAnnotationArrays) {
        boolean match = true;
        for (Annotation[] paramAnnotationArray : paramAnnotationArrays) {
            boolean paramMatch = false;
            for (Annotation paramAnnotation : paramAnnotationArray) {
                if (paramAnnotation instanceof JSONField) {
                    paramMatch = true;
                    break;
                }
            }
            if (!paramMatch) {
                match = false;
                break;
            }
        }

        return match;
    }


    private static Method getFactoryMethod( Method[] methods, boolean jacksonCompatible) {
        Method factoryMethod = null;

        for (Method method : methods) {

            JSONCreator annotation = TypeUtils.getAnnotation(method, JSONCreator.class);
            if (annotation != null) {
                if (factoryMethod != null) {
                    throw new JSONException(MULTI_JSONCREATOR);
                }

                factoryMethod = method;
                // 不应该break，否则多个静态工厂方法上存在 JSONCreator 注解时，并不会触发上述异常抛出
            }
        }

        if (factoryMethod == null && jacksonCompatible) {
            for (Method method : methods) {
                if (TypeUtils.isJacksonCreator(method)) {
                    factoryMethod = method;
                    break;
                }
            }
        }
        return factoryMethod;
    }

    public static Class<?> getBuilderClass(JSONType type) {
        return getBuilderClass(null, type);
    }

    public static Class<?> getBuilderClass(Class<?> clazz, JSONType type) {
        if (clazz != null && clazz.isInstance("org.springframework.security.web.savedrequest.DefaultSavedRequest")) {
            return TypeUtils.loadClass("org.springframework.security.web.savedrequest.DefaultSavedRequest$Builder");
        }
        if (clazz == null)
        {
            return null;
        }

        if (type == null) {
            return null;
        }

        Class<?> builderClass = type.builder();

        if (builderClass == Void.class) {
            return null;
        }

        return builderClass;
    }
}
