package com.alibaba.fastjson.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.annotation.JSONField;

public class FieldInfo implements Comparable<FieldInfo> {


    public final String     name;
    public final Method     method;
    public final Field      field;

    private final int             ordinal;
    public Class<?>   fieldClass;
    public Type       fieldType;
    public final Class<?>   declaringClass;
    public final boolean    getOnly;
    public final int        serialzeFeatures;
    public final int        parserFeatures;
    public String     label = null;

    private final JSONField fieldAnnotation;
    private final JSONField methodAnnotation;

    public final boolean    fieldAccess;
    public final boolean    fieldTransient;

    public final char[]     nameChars;

    boolean    isEnumerico;
    public final boolean    jsonDirect;
    public final boolean    unwrapped;

    public final String     format;

    public final String[]  alternateNames;

    public final long nameHashCode;

    private static final String JAVA_INFO = "java.";

    public FieldInfo(String name, //
                     Class<?> declaringClass, //
                     Class<?> fieldClass, //
                     Type fieldType, //
                     Field field, //
                     int ordinal, //
                     int serialzeFeatures, //
                     int parserFeatures){
        ordinal = getOrdinal(ordinal);

        this.name = name;
        this.declaringClass = declaringClass;
        this.fieldClass = fieldClass;
        this.fieldType = fieldType;
        this.method = null;
        this.field = field;
        this.ordinal = ordinal;
        this.serialzeFeatures = serialzeFeatures;
        this.parserFeatures = parserFeatures;

        isEnumerico = fieldClass.isEnum();

        if (field != null) {
            int modifiers = field.getModifiers();
            fieldAccess = true;
            fieldTransient = Modifier.isTransient(modifiers);
        } else {
            fieldTransient = false;
            fieldAccess = false;
        }

        nameChars = genFieldNameChars();

        typeUtilsSetAccessibleWithField(field);

        this.label = "";
        fieldAnnotation = field == null ? null : TypeUtils.getAnnotation(field, JSONField.class);
        methodAnnotation = null;
        this.getOnly = false;
        this.jsonDirect = false;
        this.unwrapped = false;
        this.format = null;
        this.alternateNames = new String[0];

        nameHashCode = nameHashCode64(name, fieldAnnotation);
    }

    public FieldInfo(String name, //
                     Method method, //
                     Field field, //
                     Class<?> clazz, //
                     Type type, //
                     int ordinal, //
                     int serialzeFeatures, //
                     int parserFeatures, //
                     JSONField fieldAnnotation, //
                     JSONField methodAnnotation //
    ){
        this(name, method, field, clazz, type, ordinal, serialzeFeatures, parserFeatures,
                fieldAnnotation, methodAnnotation, null);
    }

    public FieldInfo(String name, //
                     Method method, //
                     Field field, //
                     Class<?> clazz, //
                     Type type, //
                     int ordinal, //
                     int serialzeFeatures, //
                     int parserFeatures, //
                     JSONField fieldAnnotation, //
                     JSONField methodAnnotation, //
                     Map<TypeVariable<?>, Type> genericInfo){
        name = getString(name, field);

        ordinal = getOrdinal(ordinal);

        this.name = name;
        this.method = method;
        this.field = field;
        this.ordinal = ordinal;
        this.serialzeFeatures = serialzeFeatures;
        this.parserFeatures = parserFeatures;
        this.fieldAnnotation = fieldAnnotation;
        this.methodAnnotation = methodAnnotation;

        if (field != null) {
            int modifiers = field.getModifiers();
            fieldAccess = ((modifiers & Modifier.PUBLIC) != 0 || method == null);
            fieldTransient = isFieldTransient(method, modifiers);
        } else {
            fieldAccess = false;
            fieldTransient = TypeUtils.isTransient(method);
        }

        String formato = null;
        JSONField annotation = getAnnotation();

        nameHashCode = nameHashCode64(name, annotation);

        boolean jsonDiretto;
        if (annotation != null) {
            formato = annotation.format();
            formato = getFormat(formato);
            jsonDiretto = annotation.jsonDirect();
            unwrapped = annotation.unwrapped();
            alternateNames = annotation.alternateNames();
        } else {
            jsonDiretto = false;
            unwrapped = false;
            alternateNames = new String[0];
        }
        this.format = formato;

        nameChars = genFieldNameChars();

        typeUtilsSetAccessible(method);

        typeUtilsSetAccessibleWithField(field);

        boolean getSolo = false;
        Type fieldTipo;
        Class<?> fieldClasse;
        if (method != null) {
            Class<?>[] types;
            types = method.getParameterTypes();
            if ((types).length == 1) {
                fieldClasse = types[0];
                fieldTipo = method.getGenericParameterTypes()[0];
            } else if (isaBoolean(types)) {
                fieldTipo = fieldClasse = types[0];
            } else {
                fieldClasse = method.getReturnType();
                fieldTipo = method.getGenericReturnType();
                getSolo = true;
            }
            this.declaringClass = method.getDeclaringClass();
        } else {
            fieldClasse = field.getType();
            fieldTipo = field.getGenericType();
            this.declaringClass = field.getDeclaringClass();
            getSolo = Modifier.isFinal(field.getModifiers());
        }
        this.getOnly = getSolo;
        this.jsonDirect = jsonDiretto && fieldClasse == String.class;

        if (isaBoolean(clazz, fieldTipo, fieldClasse)) {
            TypeVariable<?> tv = (TypeVariable<?>) fieldTipo;
            Type genericFieldType = getInheritGenericType(clazz, type, tv);
            if (genericFieldType != null) {
                this.fieldClass = TypeUtils.getClass(genericFieldType);
                this.fieldType = genericFieldType;

                isEnumerico = fieldClasse.isEnum();
            }
        }

        Type genericFieldType = fieldTipo;

        fieldClasse = getaClass(clazz, type, genericInfo, fieldTipo, fieldClasse);

        this.fieldType = genericFieldType;
        this.fieldClass = fieldClasse;

        isEnumerico = getIsEnum(fieldClasse);
    }

    private static boolean isFieldTransient(Method method, int modifiers) {
        return Modifier.isTransient(modifiers)
                || TypeUtils.isTransient(method);
    }

    private static boolean isaBoolean(Class<?>[] types) {
        return types.length == 2 && types[0] == String.class && types[1] == Object.class;
    }

    private static boolean isaBoolean(Class<?> clazz, Type fieldType, Class<?> fieldClass) {
        return clazz != null && fieldClass == Object.class && fieldType instanceof TypeVariable;
    }

    private boolean getIsEnum(Class<?> fieldClass) {
        if(fieldClass !=null){
            isEnumerico = fieldClass.isEnum();
        }
        else{
            isEnumerico = false;
        }
        return isEnumerico;
    }

    private static Class<?> getaClass(Class<?> clazz, Type type, Map<TypeVariable<?>, Type> genericInfo, Type fieldType, Class<?> fieldClass) {
        Type genericFieldType;
        if (fieldType instanceof Class) {
            return fieldClass;
        }
        genericFieldType = getFieldType(clazz, type != null ? type : clazz, fieldType, genericInfo);

        if (genericFieldType == fieldType) {
            return fieldClass;
        }
        return fieldClass;
    }

    private static String getFormat(String format) {
        if (format.trim().length() == 0) {
            format = null;
        }
        return format;
    }

    private static void typeUtilsSetAccessibleWithField(Field field) {
        if (field != null) {
            TypeUtils.setAccessible(field);
        }
    }

    private static void typeUtilsSetAccessible(Method method) {
        if (method != null) {
            TypeUtils.setAccessible(method);
        }
    }

    private static int getOrdinal(int ordinal) {
        if (ordinal < 0) {
            ordinal = 0;
        }
        return ordinal;
    }

    private static String getString(String name, Field field) {
        if (field != null) {
            String fieldName = field.getName();
            if (fieldName.equals(name)) {
                name = fieldName;
            }
        }
        return name;
    }

    private long nameHashCode64(String name, JSONField annotation)
    {
        if (annotation != null && annotation.name().length() != 0) {
            return TypeUtils.fnv1a64Lower(name);
        }
        return TypeUtils.fnv1a64Extract(name);
    }

    protected char[] genFieldNameChars() {
        int nameLen = this.name.length();
        char[] nameCarlo = new char[nameLen + 3];
        this.name.getChars(0, this.name.length(), nameCarlo, 1);
        nameCarlo[0] = '"';
        nameCarlo[nameLen + 1] = '"';
        nameCarlo[nameLen + 2] = ':';
        return nameCarlo;
    }

    @SuppressWarnings("unchecked")
    public <T extends Annotation> T getAnnation(Class<T> annotationClass) {
        if (annotationClass == JSONField.class) {
            return (T) getAnnotation();
        }

        T annotatition = null;
        if (method != null) {
            annotatition = TypeUtils.getAnnotation(method, annotationClass);
        }

        if (annotatition == null && field != null) {
            annotatition = TypeUtils.getAnnotation(field, annotationClass);
        }

        return annotatition;
    }

    public static Type getFieldType(final Class<?> clazz, final Type type, Type fieldType){
        return getFieldType(clazz, type, fieldType, null);
    }

    public static Type getFieldType(final Class<?> clazz, final Type type, Type fieldType, Map<TypeVariable<?>, Type> genericInfo) {
        if(clazz!=null){
            fieldType = returnFieldTypebyClazz(fieldType,clazz);
        }
        else{
            fieldType = null;
        }
        returnFieldTypebyGenericArrayType(fieldType, clazz, type, genericInfo);

        fieldType = returnFieldTypebyTypeUtils(fieldType,type);

        return fieldType;
    }

    public static Type returnFieldTypebyClazz(Type fieldTypep,final Class<?> clazz){
        if (clazz == null) {
            return fieldTypep;
        }
        else{
            return null;
        }
    }

    public static void returnFieldTypebyGenericArrayType(Type fieldTypep, final Class<?> clazz, Type type, Map<TypeVariable<?>, Type> genericInfo){
        if (fieldTypep instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) fieldTypep;
            Type componentType = genericArrayType.getGenericComponentType();
            Type componentTypeX = getFieldType(clazz, type, componentType, genericInfo);
            if (componentType != componentTypeX) {
                TypeUtils.getClass(componentTypeX);
            }
        }
    }

    public static Type returnFieldTypebyTypeUtils(Type fieldTypep, Type type){
        if (!TypeUtils.isGenericParamType(type)) {
            return fieldTypep;
        }
        else{
            return null;
        }
    }


    private static boolean getArgument(Type[] typeArgs, TypeVariable[] typeVariables, Type[] arguments) {
        if (argumentsBool(arguments,typeVariables)) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < typeArgs.length; ++i) {
            Type typeArg = typeArgs[i];
            if (typeArg instanceof ParameterizedType) {
                ParameterizedType pTypeArg = (ParameterizedType) typeArg;
                Type[] pTypeArgArgs = pTypeArg.getActualTypeArguments();
                boolean pChanged = getArgument(pTypeArgArgs, typeVariables, arguments);
                if (pChanged) {
                    typeArgs[i] = TypeReference.intern(
                            new ParameterizedTypeImpl(pTypeArgArgs, pTypeArg.getOwnerType(), pTypeArg.getRawType())
                    );
                    changed = true;
                }
            } else if (typeArg instanceof TypeVariable) {
                return getChanged(typeArgs, typeVariables, arguments, changed, i, typeArg);
            }
        }
        return changed;
    }

    private static boolean getChanged(Type[] typeArgs, TypeVariable[] typeVariables, Type[] arguments, boolean changed, int i, Type typeArg) {
        for (int j = 0; j < typeVariables.length; ++j) {
            if (typeArg.equals(typeVariables[j])) {
                typeArgs[i] = arguments[j];
                changed = true;
            }
        }
        return changed;
    }

    public static boolean argumentsBool(Type[] arguments,TypeVariable[] typeVariables){
        return arguments == null || typeVariables.length == 0;
    }

    private static Type getInheritGenericType(Class<?> clazz, Type type, TypeVariable<?> tv) {
        GenericDeclaration gd = tv.getGenericDeclaration();

        Class<?> classGd = null;
        classGd = getaClass(tv, gd, classGd);

        Type[] arguments = null;
        if (classGd == clazz && type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            arguments = ptype.getActualTypeArguments();
        } else {
            arguments = getTypes(clazz, classGd, arguments);
        }

        if (arguments == null || classGd == null) {
            return null;
        }

        Type actualType = null;
        TypeVariable<?>[] typeVariables = classGd.getTypeParameters();
        actualType = getType(tv, arguments, actualType, typeVariables);

        return actualType;
    }

    private static Type getType(TypeVariable<?> tv, Type[] arguments, Type actualType, TypeVariable<?>[] typeVariables) {
        for (int j = 0; j < typeVariables.length; ++j) {
            if (tv.equals(typeVariables[j])) {
                actualType = arguments[j];
                break;
            }
        }
        return actualType;
    }

    private static Type[] getTypes(Class<?> clazz, Class<?> classGd, Type[] arguments) {
        for (Class<?> c = clazz; c != null && c != Object.class && c != classGd; c = c.getSuperclass()) {
            Type superType = c.getGenericSuperclass();

            arguments = getTypes(arguments, c, superType);
        }
        return arguments;
    }

    private static Type[] getTypes(Type[] arguments, Class<?> c, Type superType) {
        if (superType instanceof ParameterizedType) {
            ParameterizedType pSuperType = (ParameterizedType) superType;
            Type[] pSuperTypeArgs = pSuperType.getActualTypeArguments();
            getArgument(pSuperTypeArgs, c.getTypeParameters(), arguments);
            arguments = pSuperTypeArgs;
        }
        return arguments;
    }

    private static Class<?> getaClass(TypeVariable<?> tv, GenericDeclaration gd, Class<?> classGd) {
        if (gd instanceof Class) {
            classGd = (Class<?>) tv.getGenericDeclaration();
        }
        return classGd;
    }

    public String toString() {
        return this.name;
    }

    public Member getMember() {
        if (method != null) {
            return method;
        } else {
            return field;
        }
    }

    protected Class<?> getDeclaredClass() {
        if (this.method != null) {
            return this.method.getDeclaringClass();
        }

        if (this.field != null) {
            return this.field.getDeclaringClass();
        }

        return null;
    }

    public int compareTo(FieldInfo o) {
        // Deal extend bridge
        int value;
        value = returnOrdinal(o);

        int result = this.name.compareTo(o.name);

        if (result != 0) {
            return result;
        }

        Class<?> thisDeclaringClass = this.getDeclaredClass();
        Class<?> otherDeclaringClass = o.getDeclaredClass();

        if (thisDeclaringClass != null && otherDeclaringClass != null && thisDeclaringClass != otherDeclaringClass) {
            if (thisDeclaringClass.isAssignableFrom(otherDeclaringClass)) {
                value = -1;
            }

            if (otherDeclaringClass.isAssignableFrom(thisDeclaringClass)) {
                value = 1;
            }
        }
        boolean isSampeType = this.field != null && this.field.getType() == this.fieldClass;
        boolean oSameType = o.field != null && o.field.getType() == o.fieldClass;

        if (isSampeType(isSampeType,oSameType)) {
            value = 1;
        }

        if (isOsameType(isSampeType,oSameType)) {
            value =  -1;
        }

        if (prmitiveCondition1(o)) {
            value = 1;
        }

        if (prmitiveCondition2(o)) {
            value = -1;
        }

        if (fieldClassStartsWithJavaInfo1(o)) {
            value = 1;
        }

        if (fieldClassStartsWithJavaInfo2(o)) {
            value =  -1;
        }

        return value;
    }

    public int returnOrdinal(FieldInfo o){
        if (o.method != null && this.method != null
                && o.method.isBridge() && !this.method.isBridge()
                && o.method.getName().equals(this.method.getName())) {
            return 1;
        }

        if (this.ordinal < o.ordinal) {
            return -1;
        }

        if (this.ordinal > o.ordinal) {
            return 1;
        }
        else{
            return 0;
        }
    }

    public boolean prmitiveCondition1(FieldInfo o){
        return o.fieldClass.isPrimitive() && !this.fieldClass.isPrimitive();
    }

    public boolean prmitiveCondition2(FieldInfo o){
        return this.fieldClass.isPrimitive() && !o.fieldClass.isPrimitive();
    }

    public boolean fieldClassStartsWithJavaInfo1(FieldInfo o){
        return o.fieldClass.getName().startsWith(JAVA_INFO) && !this.fieldClass.getName().startsWith(JAVA_INFO);
    }

    public boolean fieldClassStartsWithJavaInfo2(FieldInfo o){
        return this.fieldClass.getName().startsWith(JAVA_INFO) && !o.fieldClass.getName().startsWith(JAVA_INFO);
    }

    public boolean isSampeType(boolean isSampeType, boolean oSameType){
        return isSampeType && !oSameType;
    }

    public boolean isOsameType(boolean isSampeType, boolean oSameType){
        return oSameType && !isSampeType;
    }

    public JSONField getAnnotation() {
        if (this.fieldAnnotation != null) {
            return this.fieldAnnotation;
        }

        return this.methodAnnotation;
    }

    public String getFormat() {
        return format;
    }

    public Object get(Object javaObject) throws IllegalAccessException, InvocationTargetException {
        return method != null
                ? method.invoke(javaObject)
                : field.get(javaObject);
    }

    public void set(Object javaObject, Object value) throws IllegalAccessException, InvocationTargetException {
        if (method != null) {
            method.invoke(javaObject,  (Object[]) value );

        }

    }

    public void setAccessible() throws SecurityException {
        if (method != null) {
            TypeUtils.setAccessible(method);
            return;
        }

        TypeUtils.setAccessible(field);
    }
}
