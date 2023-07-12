package com.alibaba.fastjson.serializer;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.asm.*;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.ASMClassLoader;
import com.alibaba.fastjson.util.ASMUtils;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.TypeUtils;

import java.io.Serializable;
import java.lang.reflect.*;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.alibaba.fastjson.util.ASMUtils.desc;
import static com.alibaba.fastjson.util.ASMUtils.type;

public class ASMSerializerFactory implements Opcodes {

    protected final ASMClassLoader classLoader             = new ASMClassLoader();

    private final AtomicLong       seed                    = new AtomicLong();

    static final String            JSONSERIALIZER           = type(JSONSerializer.class);
    static final String            OBJECT_SERIALIZER        = type(ObjectSerializer.class);
    static final String            OBJECT_SERIALIZER_DESC   = "L" + OBJECT_SERIALIZER + ";";
    static final String            SERIALIZE_WRITER         = type(SerializeWriter.class);
    static final String            SERIALIZE_WRITER_DESC    = "L" + SERIALIZE_WRITER + ";";
    static final String            JAVA_BEAN_SERIALIZER     = type(JavaBeanSerializer.class);
    static final String            JAVA_BEAN_SERIALIZER_DESC= "L" + JAVA_BEAN_SERIALIZER + ";";
    static final String            SERIAL_CONTEXT_DESC      = desc(SerialContext.class);
    static final String            SERIALIZE_FILTERABLE_DESC= desc(SerializeFilterable.class);
    static final String            S_LJAVA_LANG_SHORT       = "(S)Ljava/lang/Short;";
    static final String            B_LJAVA_LANG_BYTE        = "(B)Ljava/lang/Byte;";
    static final String            JAVA_LANG_BYTE           = "java/lang/Byte";
    static final String            WRITE                    = "write";
    static final String            SHORT                    = "short";
    static final String            STRING                   = "string";
    static final String            DECIMAL                  = "decimal";
    static final String            DOUBLE                   = "double";
    static final String            FLOAT                    = "float";
    static final String            OBJECT                   = "object";
    static final String            BOOLEAN                  = "boolean";
    static final String            SEPERATOR                = "seperator";
    static final String            PARENT                   = "parent";
    static final String            ENTITY                   = "entity";
    static final String            VALUE_OF                 = "valueOf";
    static final String            SET_CONTEXT              = "setContext";
    static final String            FIED_SER                 = "fied_ser";
    static final String            FIELD                    = "field_";
    static final String            ASM_SER                  = "_asm_ser_";
    static final String            CHECK_VALUE              = "checkValue";
    static final String            IS_ENABLED               = "isEnabled";
    static final String            GET_CLASS                = "getClass";
    static final String            LIST_ITEM                = "list_item";
    static final String            WRITE_NULL               = "writeNull";
    static final String            HAS_NAME_FILTERS         = "hasNameFilters";
    static final String            LIST_ITEM_DESC           = "list_item_desc";
    static final String            JAVA_LANG_CHARACTER      = "java/lang/Character";
    static final String            JAVA_LANG_INTEGER        = "java/lang/Integer";
    static final String            JAVA_LANG_OBJECT         = "java/lang/Object";
    static final String            WRITE_FIELD_VALUE        = "writeFieldValue";
    static final String            JAVA_UTIL_LIST           = "java/util/List";
    static final String            WRITE_AS_ARRAY           = "writeAsArray";
    static final String            JAVA_IO_IOEXCEPTION      = "java/io/IOException";
    static final String            WRITE_WITH_FIELD_NAME    = "writeWithFieldName";
    static final String            ASM_LIST_ITEM_SER        = "_asm_list_item_ser_";
    static final String            ASM_FIELDTYPE            = "_asm_fieldType";
    static final String            LJAVA_LANG_CLASS         = "()Ljava/lang/Class;";
    static final String            I_LJAVA_LANG_INTEGER     = "(I)Ljava/lang/Integer;";
    static final String            WRITE_DIRECTION_NON_CONTEXT                   = "writeDirectNonContext";
    static final String            WRITE_AS_ARRAY_NON_CONTEXT                    = "writeAsArrayNonContext";
    static final String            LJAVA_LANG_REFLECT_TYPE                       = "Ljava/lang/reflect/Type;";
    static final String            CLJAVA_LANG_STRING_LJAVA_LANG_STRING_V        = "(CLjava/lang/String;Ljava/lang/String;)V";
    static final String            LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_V         = "(Ljava/lang/Object;Ljava/lang/Object;)V";
    static final String LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/reflect/Type;I)V";
    static final String COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V = ";Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/reflect/Type;I)V";
    static final String JAVA_LANG_SHORT = "java/lang/Short";
    static final String  C_JAVA_LANG_CHARACTER = "(C)Ljava/lang/Character;";
    static final String J_JAVA_LANG_LONG = "(J)Ljava/lang/Long;";
    static final String JAVA_LANG_LONG = "java/lang/Long";
    static final String JAVA_LANG_FLOAT = "java/lang/Float";
    static final String F_JAVA_LANG_FLOAT = "(F)Ljava/lang/Float;";
    static final String D_JAVA_LANG_DOUBLE = "(D)Ljava/lang/Double;";
    static final String JAVA_LANG_DOUBLE = "java/lang/Double";
    static final String JAVA_LANG_BOOLEAN = "java/lang/Boolean";
    static final String Z_JAVA_LANG_BOOLEAN = "(Z)Ljava/lang/Boolean;";
    
    static class Context {

        static final int              SERIALIZER        = 1;
        static final int              OBJ               = 2;
        static final int              PARAM_FIELD_NAME  = 3;
        static final int              PARAM_FIELD_TYPE  = 4;
        static final int              FEATURES          = 5;
        static int                    fieldName         = 6;
        static int                    original          = 7;
        static int                    processValue      = 8;

        private final FieldInfo[]       getters;
        private final String            className;
        private final SerializeBeanInfo beanInfo;
        private final boolean           writeDirect;

        private Map<String, Integer>    variants       = new HashMap<>();
        private int                     variantIndex   = 9;
        private final boolean           nonContext;

        public Context(FieldInfo[] getters, //
                       SerializeBeanInfo beanInfo, //
                       String className, //
                       boolean writeDirect, //
                       boolean nonContext){
            this.getters = getters;
            this.className = className;
            this.beanInfo = beanInfo;
            this.writeDirect = writeDirect;
            this.nonContext = nonContext || beanInfo.beanType.isEnum();
        }

        public int variants(String name) {
            Integer i = variants.get(name);
            if (i == null) {
                variants.put(name, variantIndex++);
            }
            i = variants.get(name);
            return i.intValue();
        }

        public int variants(String name, int increment) {
            Integer i = variants.get(name);
            if (i == null) {
                variants.put(name, variantIndex);
                variantIndex += increment;
            }
            i = variants.get(name);
            return i.intValue();
        }
        
        public int getFieldOrinal(String name) {
            int fieldIndex = -1;
            for (int i = 0, size = getters.length; i < size; ++i) {
                FieldInfo item = getters[i];
                if (item.name.equals(name)) {
                    fieldIndex = i;
                    break;
                }
            }
            return fieldIndex;
        }
    }

    public JavaBeanSerializer createJavaBeanSerializer(SerializeBeanInfo beanInfo) throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        Class<?> clazz = beanInfo.beanType;
        extracted34(clazz);

        JSONType jsonType = TypeUtils.getAnnotation(clazz, JSONType.class);

        FieldInfo[] unsortedGetters = beanInfo.fields;

        for (FieldInfo fieldInfo : unsortedGetters) {
            if (extracted35(fieldInfo)) {
                return new JavaBeanSerializer(beanInfo);
            }
        }

        FieldInfo[] getters = beanInfo.sortedFields;

        boolean nativeSorted = beanInfo.sortedFields == beanInfo.fields;

        if (getters.length > 256) {
            return new JavaBeanSerializer(beanInfo);
        }

        for (FieldInfo getter : getters) {
            if (!ASMUtils.checkName(getter.getMember().getName())) {
                return new JavaBeanSerializer(beanInfo);
            }
        }

        String className = "ASMSerializer_" + seed.incrementAndGet() + "_" + clazz.getSimpleName();
        String classNameType;
        String classNameFull;
        Package pkg = ASMSerializerFactory.class.getPackage();
        if (pkg != null) {
            String packageName = pkg.getName();
            classNameType = packageName.replace('.', '/') + "/" + className;
            classNameFull = packageName + "." + className;
        } else {
            classNameType = className;
            classNameFull = className;
        }

        ClassWriter cw = new ClassWriter();
        extracted50(classNameType, cw);

        extracted38(getters, cw);

        MethodVisitor mw = new MethodWriter(cw, ACC_PUBLIC, "<init>", "(" + desc(SerializeBeanInfo.class) + ")V", null);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitVarInsn(ALOAD, 1);
        mw.visitMethodInsn(INVOKESPECIAL, JAVA_BEAN_SERIALIZER, "<init>", "(" + desc(SerializeBeanInfo.class) + ")V");

        // init _asm_fieldType
        extracted41(getters, classNameType, mw);

        mw.visitInsn(RETURN);
        mw.visitMaxs(4, 4);
        mw.visitEnd();

        boolean disableCircularReferenceDetect = false;
        disableCircularReferenceDetect = extracted43(jsonType, disableCircularReferenceDetect);

        // 0 write
        // 1 writeNormal
        // 2 writeNonContext
        for (int i = 0; i < 3; ++i) {
            String methodName;
            boolean nonContext = disableCircularReferenceDetect;
            boolean writeDirect = false;
            writeDirect = extracted55(i, writeDirect);
            if (i == 1) {
                methodName = "writeNormal";
            } else {
                writeDirect = true;
                nonContext = true;
                methodName = WRITE_DIRECTION_NON_CONTEXT;
            }

            Context context = new Context(getters, beanInfo, classNameType, writeDirect,
                                          nonContext);

            mw = extracted44(cw, methodName);

            extracted54(jsonType, nativeSorted, classNameType, mw, context);

            // isWriteDoubleQuoteDirect
            extracted53(clazz, getters, classNameType, mw, nonContext, context);
        }

        if (!nativeSorted) {
            // sortField support
            Context context = new Context(getters, beanInfo, classNameType, false,
                                          disableCircularReferenceDetect);

            mw = new MethodWriter(cw, ACC_PUBLIC, "writeUnsorted",
                                  "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V, new String[] { JAVA_IO_IOEXCEPTION });

            extracted52(mw, context);

            extracted51(clazz, unsortedGetters, mw, context);
        }

        // 0 writeAsArray
        // 1 writeAsArrayNormal
        // 2 writeAsArrayNonContext
        extracted49(beanInfo, clazz, getters, classNameType, cw, disableCircularReferenceDetect);

        byte[] code = cw.toByteArray();

        Class<?> serializerClass = classLoader.defineClassPublic(classNameFull, code, 0, code.length);
        Constructor<?> constructor = serializerClass.getConstructor(SerializeBeanInfo.class);
        Object instance = constructor.newInstance(beanInfo);

        return (JavaBeanSerializer) instance;
    }

    private boolean extracted55(int i, boolean writeDirect) {
        if (i == 0) {
            writeDirect = true;
        }
        return writeDirect;
    }

    private void extracted54(JSONType jsonType, boolean nativeSorted, String classNameType, MethodVisitor mw,
            Context context) {
        extracted52(mw, context);

        extracted47(jsonType, nativeSorted, classNameType, mw, context);
    }

    private void extracted53(Class<?> clazz, FieldInfo[] getters, String classNameType, MethodVisitor mw,
            boolean nonContext, Context context) {
        extracted48(classNameType, mw, nonContext, context);

        extracted51(clazz, getters, mw, context);
    }

    private void extracted52(MethodVisitor mw, Context context) {
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitFieldInsn(GETFIELD, JSONSERIALIZER, "out", SERIALIZE_WRITER_DESC);
        mw.visitVarInsn(ASTORE, context.variants("out"));
    }

    private void extracted51(Class<?> clazz, FieldInfo[] getters, MethodVisitor mw, Context context) {
        mw.visitVarInsn(ALOAD, Context.OBJ); // obj
        mw.visitTypeInsn(CHECKCAST, type(clazz)); // serializer
        mw.visitVarInsn(ASTORE, context.variants(ENTITY)); // obj
        generateWriteMethod(mw, getters, context);
        mw.visitInsn(RETURN);
        mw.visitMaxs(7, context.variantIndex + 2);
        mw.visitEnd();
    }

    private void extracted50(String classNameType, ClassWriter cw) {
        cw.visit(V1_5 //
                 , ACC_PUBLIC + ACC_SUPER //
                 , classNameType //
                 , JAVA_BEAN_SERIALIZER //
                 , new String[] { OBJECT_SERIALIZER } //
        );
    }

    private void extracted49(SerializeBeanInfo beanInfo, Class<?> clazz, FieldInfo[] getters, String classNameType,
            ClassWriter cw, boolean disableCircularReferenceDetect) {
        MethodVisitor mw;
        for (int i = 0; i < 3; ++i) {
            String methodName;
            boolean nonContext = disableCircularReferenceDetect;
            boolean writeDirect = false;
            if (i == 0) {
                methodName = WRITE_AS_ARRAY;
                writeDirect = true;
            } else if (i == 1) {
                methodName = "writeAsArrayNormal";
            } else {
                writeDirect = true;
                nonContext = true;
                methodName = WRITE_AS_ARRAY_NON_CONTEXT;
            }

            Context context = new Context(getters, beanInfo, classNameType, writeDirect,
                                          nonContext);

            mw = new MethodWriter(cw, ACC_PUBLIC, methodName,
                                  "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V, new String[] { JAVA_IO_IOEXCEPTION });

            extracted52(mw, context);

            mw.visitVarInsn(ALOAD, Context.OBJ); // obj
            mw.visitTypeInsn(CHECKCAST, type(clazz)); // serializer
            mw.visitVarInsn(ASTORE, context.variants(ENTITY)); // obj
            generateWriteAsArray(mw, getters, context);
            mw.visitInsn(RETURN);
            mw.visitMaxs(7, context.variantIndex + 2);
            mw.visitEnd();
        }
    }

    private void extracted48(String classNameType, MethodVisitor mw, boolean nonContext, Context context) {
        if (context.writeDirect && !nonContext) {
            Label direct = new Label();
            Label directElse = new Label();

            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "writeDirect", "(L" + JSONSERIALIZER + ";)Z");
            mw.visitJumpInsn(IFNE, directElse);

            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, 1);
            mw.visitVarInsn(ALOAD, 2);
            mw.visitVarInsn(ALOAD, 3);
            mw.visitVarInsn(ALOAD, 4);
            mw.visitVarInsn(ILOAD, 5);
            mw.visitMethodInsn(INVOKEVIRTUAL, classNameType,
                               "writeNormal", "(L" + JSONSERIALIZER
                                              + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitInsn(RETURN);

            mw.visitLabel(directElse);
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitLdcInsn(SerializerFeature.DISABLE_CIRCULAR_REFERENCE_DETECT.mask);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, IS_ENABLED, "(I)Z");
            mw.visitJumpInsn(IFEQ, direct);

            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, 1);
            mw.visitVarInsn(ALOAD, 2);
            mw.visitVarInsn(ALOAD, 3);
            mw.visitVarInsn(ALOAD, 4);
            mw.visitVarInsn(ILOAD, 5);
            mw.visitMethodInsn(INVOKEVIRTUAL, classNameType, WRITE_DIRECTION_NON_CONTEXT,
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitInsn(RETURN);

            mw.visitLabel(direct);
        }
    }

    private void extracted47(JSONType jsonType, boolean nativeSorted, String classNameType, MethodVisitor mw,
            Context context) {
        if (extracted45(nativeSorted, context)) {

            extracted46(jsonType, classNameType, mw, context);
        }
    }

    private void extracted46(JSONType jsonType, String classNameType, MethodVisitor mw, Context context) {
        if (jsonType == null || jsonType.alphabetic()) {
            Label elseVariabile = new Label();

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "isSortField", "()Z");

            mw.visitJumpInsn(IFNE, elseVariabile);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, 1);
            mw.visitVarInsn(ALOAD, 2);
            mw.visitVarInsn(ALOAD, 3);
            mw.visitVarInsn(ALOAD, 4);
            mw.visitVarInsn(ILOAD, 5);
            mw.visitMethodInsn(INVOKEVIRTUAL, classNameType,
                               "writeUnsorted", "(L" + JSONSERIALIZER
                                                + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitInsn(RETURN);

            mw.visitLabel(elseVariabile);
        }
    }

    private boolean extracted45(boolean nativeSorted, Context context) {
        return (!nativeSorted) //
            && !context.writeDirect;
    }

    private MethodVisitor extracted44(ClassWriter cw, String methodName) {
        MethodVisitor mw;
        mw = new MethodWriter(cw, //
                              ACC_PUBLIC, //
                              methodName, //
                              "(L" + JSONSERIALIZER
                                          + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V, //
                              new String[] { JAVA_IO_IOEXCEPTION } //
        );
            Label endIf = new Label();
         mw.visitVarInsn(ALOAD, Context.OBJ);

        mw.visitJumpInsn(IFNONNULL, endIf);
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER,
            WRITE_NULL, "()V");

        mw.visitInsn(RETURN);
         mw.visitLabel(endIf);
        return mw;
    }

    private boolean extracted43(JSONType jsonType, boolean disableCircularReferenceDetect) {
        if (jsonType != null) {
            disableCircularReferenceDetect = extracted42(jsonType, disableCircularReferenceDetect);
        }
        return disableCircularReferenceDetect;
    }

    private boolean extracted42(JSONType jsonType, boolean disableCircularReferenceDetect) {
        for (SerializerFeature featrues : jsonType.serialzeFeatures()) {
            if (featrues == SerializerFeature.DISABLE_CIRCULAR_REFERENCE_DETECT) {
                disableCircularReferenceDetect = true;
                break;
            }
        }
        return disableCircularReferenceDetect;
    }

    private void extracted41(FieldInfo[] getters, String classNameType, MethodVisitor mw) {
        for (int i = 0; i < getters.length; ++i) {
            FieldInfo fieldInfo = getters[i];
            if (extracted39(fieldInfo)) {
                continue;
            }

            mw.visitVarInsn(ALOAD, 0);

            extracted40(mw, i, fieldInfo);

            mw.visitFieldInsn(PUTFIELD, classNameType, fieldInfo.name + ASM_FIELDTYPE, LJAVA_LANG_REFLECT_TYPE);
        }
    }

    private void extracted40(MethodVisitor mw, int i, FieldInfo fieldInfo) {
        if (fieldInfo.method != null) {
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldInfo.declaringClass)));
            mw.visitLdcInsn(fieldInfo.method.getName());
            mw.visitMethodInsn(INVOKESTATIC, type(ASMUtils.class), "getMethodType",
                               "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Type;");

        } else {
            mw.visitVarInsn(ALOAD, 0);
            mw.visitLdcInsn(i);
            mw.visitMethodInsn(INVOKESPECIAL, JAVA_BEAN_SERIALIZER, "getFieldType", "(I)Ljava/lang/reflect/Type;");
        }
    }

    private boolean extracted39(FieldInfo fieldInfo) {
        return fieldInfo.fieldClass.isPrimitive() //
//                || fieldInfo.fieldClass.isEnum() //
            || fieldInfo.fieldClass == String.class;
    }

    private void extracted38(FieldInfo[] getters, ClassWriter cw) {
        for (FieldInfo fieldInfo : getters) {
            if (extracted36(fieldInfo)) {
                continue;
            }

            new FieldWriter(cw, ACC_PUBLIC, fieldInfo.name + ASM_FIELDTYPE, LJAVA_LANG_REFLECT_TYPE) //
                                                                                                           .visitEnd();

            extracted37(cw, fieldInfo);

            new FieldWriter(cw, ACC_PUBLIC, fieldInfo.name + ASM_SER, OBJECT_SERIALIZER_DESC) //
                                                                                                        .visitEnd();
        }
    }

    private void extracted37(ClassWriter cw, FieldInfo fieldInfo) {
        if (List.class.isAssignableFrom(fieldInfo.fieldClass)) {
            new FieldWriter(cw, ACC_PUBLIC, fieldInfo.name + ASM_LIST_ITEM_SER,
                            OBJECT_SERIALIZER_DESC) //
                                                   .visitEnd();
        }
    }

    private boolean extracted36(FieldInfo fieldInfo) {
        return fieldInfo.fieldClass.isPrimitive() //
            //|| fieldInfo.fieldClass.isEnum() //
            || fieldInfo.fieldClass == String.class;
    }

    private boolean extracted35(FieldInfo fieldInfo) {
        return fieldInfo.field == null //
            && fieldInfo.method != null //
            && fieldInfo.method.getDeclaringClass().isInterface();
    }

    private void extracted34(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            throw new JSONException("unsupportd class " + clazz.getName());
        }
    }

    private void generateWriteAsArray(MethodVisitor mw, FieldInfo[] getters,
                                      Context context) {

        Label nonPropertyFilters = new Label();
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "hasPropertyFilters", "(" + SERIALIZE_FILTERABLE_DESC + ")Z");
        mw.visitJumpInsn(IFNE, nonPropertyFilters);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitVarInsn(ALOAD, 1);
        mw.visitVarInsn(ALOAD, 2);
        mw.visitVarInsn(ALOAD, 3);
        mw.visitVarInsn(ALOAD, 4);
        mw.visitVarInsn(ILOAD, 5);
        mw.visitMethodInsn(INVOKESPECIAL, JAVA_BEAN_SERIALIZER,
                "writeNoneASM", "(L" + JSONSERIALIZER
                        + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
        mw.visitInsn(RETURN);

        mw.visitLabel(nonPropertyFilters);
        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(BIPUSH, '[');
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

        int size = getters.length;

        if (size == 0) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, ']');
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
            return;
        }

        extracted67(mw, getters, context, size);
    }

    private void extracted67(MethodVisitor mw, FieldInfo[] getters, Context context, int size) {
        for (int i = 0; i < size; ++i) {
            final char seperator = (i == size - 1) ? ']' : ',';

            FieldInfo fieldInfo = getters[i];
            Class<?> fieldClass = fieldInfo.fieldClass;

            mw.visitLdcInsn(fieldInfo.name);
            mw.visitVarInsn(ASTORE, Context.fieldName);

            extracted66(mw, context, seperator, fieldInfo, fieldClass);
        }
    }

    private void extracted66(MethodVisitor mw, Context context, final char seperator, FieldInfo fieldInfo,
            Class<?> fieldClass) {
        if (extracted27(fieldClass)) {

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitInsn(DUP);
            getMetodo(mw, context, fieldInfo);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeInt", "(I)V");
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        } else if (fieldClass == long.class) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitInsn(DUP);
            getMetodo(mw, context, fieldInfo);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeLong", "(J)V");
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        } else if (fieldClass == float.class) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitInsn(DUP);
            getMetodo(mw, context, fieldInfo);
            mw.visitInsn(ICONST_1);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeFloat", "(FZ)V");
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        } else if (fieldClass == double.class) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitInsn(DUP);
            getMetodo(mw, context, fieldInfo);
            mw.visitInsn(ICONST_1);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeDouble", "(DZ)V");
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        } else if (fieldClass == boolean.class) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitInsn(DUP);
            getMetodo(mw, context, fieldInfo);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(Z)V");
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        } else if (fieldClass == char.class) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            getMetodo(mw, context, fieldInfo); // Character.toString(value)
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_CHARACTER, "toString", "(C)Ljava/lang/String;");
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeString", "(Ljava/lang/String;C)V");

        } else if (fieldClass == String.class) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            getMetodo(mw, context, fieldInfo);
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeString", "(Ljava/lang/String;C)V");
        } else if (fieldClass.isEnum()) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitInsn(DUP);
            getMetodo(mw, context, fieldInfo);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeEnum", "(Ljava/lang/Enum;)V");
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        } else if (List.class.isAssignableFrom(fieldClass)) {
            Type fieldType = fieldInfo.fieldType;

            Type elementType;
            elementType = extracted65(fieldType);

            Class<?> elementClass = extracted64(elementType);
            
            getMetodo(mw, context, fieldInfo);
            mw.visitTypeInsn(CHECKCAST, JAVA_UTIL_LIST); // cast
            mw.visitVarInsn(ASTORE, context.variants("list"));

            extracted59(mw, context, fieldInfo, elementType, elementClass);

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        } else {
            Label notNullEnd = new Label();
            Label notNullElse = new Label();

            getMetodo(mw, context, fieldInfo);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, context.variants(FIELD + fieldInfo.fieldClass.getName()));
            mw.visitJumpInsn(IFNONNULL, notNullElse);

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_NULL, "()V");
            mw.visitJumpInsn(GOTO, notNullEnd);

            mw.visitLabel(notNullElse);

            Label classIfEnd = new Label();
            Label classIfElse = new Label();
            mw.visitVarInsn(ALOAD, context.variants(FIELD + fieldInfo.fieldClass.getName()));
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_OBJECT, GET_CLASS, LJAVA_LANG_CLASS);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldClass)));
            mw.visitJumpInsn(IF_ACMPNE, classIfElse);

            getFieldSerMetodo (context, mw, fieldInfo);
            mw.visitVarInsn(ASTORE, context.variants(FIED_SER));

            Label instanceOfElse = new Label();
            Label instanceOfEnd = new Label();
            extracted60(mw, context, fieldInfo, fieldClass, instanceOfElse, instanceOfEnd);
            mw.visitVarInsn(ALOAD, context.variants(FIED_SER));
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(FIELD + fieldInfo.fieldClass.getName()));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldClass))); // fieldType
            mw.visitLdcInsn(fieldInfo.serialzeFeatures); // features
            mw.visitMethodInsn(INVOKEINTERFACE, OBJECT_SERIALIZER, WRITE, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitLabel(instanceOfEnd);
            mw.visitJumpInsn(GOTO, classIfEnd);

            mw.visitLabel(classIfElse);
            String format = fieldInfo.getFormat();

            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(FIELD + fieldInfo.fieldClass.getName()));
            extracted62(mw, context, fieldInfo, format);
            mw.visitLabel(classIfEnd);
            mw.visitLabel(notNullEnd);
            

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        }
    }

    private Type extracted65(Type fieldType) {
        Type elementType;
        if (fieldType instanceof Class) {
            elementType = Object.class;
        } else {
            elementType = ((ParameterizedType) fieldType).getActualTypeArguments()[0];
        }
        return elementType;
    }

    private Class<?> extracted64(Type elementType) {
        Class<?> elementClass = null;
        if (elementType instanceof Class<?>) {
            elementClass = (Class<?>) elementType;

            elementClass = extracted63(elementClass);
        }
        return elementClass;
    }

    private Class<?> extracted63(Class<?> elementClass) {
        if (elementClass == Object.class) {
            elementClass = null;
        }
        return elementClass;
    }

    private void extracted62(MethodVisitor mw, Context context, FieldInfo fieldInfo, String format) {
        if (format != null) {
            mw.visitLdcInsn(format);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "writeWithFormat",
                               "(Ljava/lang/Object;Ljava/lang/String;)V");
        } else {
            extracted61(mw, context, fieldInfo);
        }
    }

    private void extracted61(MethodVisitor mw, Context context, FieldInfo fieldInfo) {
        mw.visitVarInsn(ALOAD, Context.fieldName);
        if (fieldInfo.fieldType instanceof Class<?> //
            && ((Class<?>) fieldInfo.fieldType).isPrimitive()) {
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                               LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_V);
        } else {
            mw.visitVarInsn(ALOAD, 0); // this
            mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_FIELDTYPE,
                              LJAVA_LANG_REFLECT_TYPE);
            mw.visitLdcInsn(fieldInfo.serialzeFeatures);

            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                               LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
        }
    }

    private void extracted60(MethodVisitor mw, Context context, FieldInfo fieldInfo, Class<?> fieldClass,
            Label instanceOfElse, Label instanceOfEnd) {
        if (context.writeDirect && Modifier.isPublic(fieldClass.getModifiers())) {
            mw.visitVarInsn(ALOAD, context.variants(FIED_SER));
            mw.visitTypeInsn(INSTANCEOF, JAVA_BEAN_SERIALIZER);
            mw.visitJumpInsn(IFEQ, instanceOfElse);

            mw.visitVarInsn(ALOAD, context.variants(FIED_SER));
            mw.visitTypeInsn(CHECKCAST, JAVA_BEAN_SERIALIZER); // cast
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(FIELD + fieldInfo.fieldClass.getName()));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldClass))); // fieldType
            mw.visitLdcInsn(fieldInfo.serialzeFeatures); // features
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, WRITE_AS_ARRAY_NON_CONTEXT, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitJumpInsn(GOTO, instanceOfEnd);

            mw.visitLabel(instanceOfElse);
        }
    }

    private void extracted59(MethodVisitor mw, Context context, FieldInfo fieldInfo, Type elementType,
            Class<?> elementClass) {
        if (elementClass == String.class //
            && context.writeDirect) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ALOAD, context.variants("list"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(Ljava/util/List;)V");
        } else {
            Label nullEnd = new Label();
            Label nullElse = new Label();

            mw.visitVarInsn(ALOAD, context.variants("list"));
            mw.visitJumpInsn(IFNONNULL, nullElse);

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_NULL, "()V");
            mw.visitJumpInsn(GOTO, nullEnd);

            mw.visitLabel(nullElse);

            mw.visitVarInsn(ALOAD, context.variants("list"));
            mw.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_LIST, "size", "()I");
            mw.visitVarInsn(ISTORE, context.variants("size"));

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, '[');
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

            Label forVariabile = new Label();
            Label forFirst = new Label();
            Label forEnd = new Label();

            mw.visitInsn(ICONST_0);
            mw.visitVarInsn(ISTORE, context.variants("i"));


            mw.visitLabel(forVariabile);
            mw.visitVarInsn(ILOAD, context.variants("i"));
            mw.visitVarInsn(ILOAD, context.variants("size"));
            mw.visitJumpInsn(IF_ICMPGE, forEnd); // i < list.size - 1

            mw.visitVarInsn(ILOAD, context.variants("i"));
            mw.visitJumpInsn(IFEQ, forFirst); // i < list.size - 1

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, ',');
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

            mw.visitLabel(forFirst);

            mw.visitVarInsn(ALOAD, context.variants("list"));
            mw.visitVarInsn(ILOAD, context.variants("i"));
            mw.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_LIST, "get", "(I)Ljava/lang/Object;");
            mw.visitVarInsn(ASTORE, context.variants(LIST_ITEM));

            Label forItemNullEnd = new Label();
            Label forItemNullElse = new Label();

            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM));
            mw.visitJumpInsn(IFNONNULL, forItemNullElse);

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_NULL, "()V");
            mw.visitJumpInsn(GOTO, forItemNullEnd);

            mw.visitLabel(forItemNullElse);

            Label forItemClassIfEnd = new Label();
            Label forItemClassIfElse = new Label();
            extracted57(mw, context, fieldInfo, elementClass, forItemClassIfEnd, forItemClassIfElse);

            mw.visitLabel(forItemClassIfElse);
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM));
            extracted10(mw, context);
            extracted58(mw, fieldInfo, elementType, elementClass);
            mw.visitLabel(forItemClassIfEnd);
            mw.visitLabel(forItemNullEnd);

            mw.visitIincInsn(context.variants("i"), 1);
            mw.visitJumpInsn(GOTO, forVariabile);

            mw.visitLabel(forEnd);

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, ']');
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

            mw.visitLabel(nullEnd);
        }
    }

    private void extracted58(MethodVisitor mw, FieldInfo fieldInfo, Type elementType, Class<?> elementClass) {
        if (elementClass != null && Modifier.isPublic(elementClass.getModifiers())) {
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc((Class<?>) elementType)));
            mw.visitLdcInsn(fieldInfo.serialzeFeatures);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                               LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
        } else {
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                               LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_V);
        }
    }

    private void extracted57(MethodVisitor mw, Context context, FieldInfo fieldInfo, Class<?> elementClass,
            Label forItemClassIfEnd, Label forItemClassIfElse) {
        if (elementClass != null && Modifier.isPublic(elementClass.getModifiers())) {
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM));
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_OBJECT, GET_CLASS, LJAVA_LANG_CLASS);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(elementClass)));
            mw.visitJumpInsn(IF_ACMPNE, forItemClassIfElse);

            getListFieldItemSerMetodo (context, mw, fieldInfo, elementClass);
            mw.visitVarInsn(ASTORE, context.variants(LIST_ITEM_DESC));

            Label instanceOfElse = new Label();
            Label instanceOfEnd = new Label();

            extracted56(mw, context, fieldInfo, elementClass, instanceOfElse, instanceOfEnd);

            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM_DESC));
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM)); // object
            extracted10(mw, context);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(elementClass))); // fieldType
            mw.visitLdcInsn(fieldInfo.serialzeFeatures); // features
            mw.visitMethodInsn(INVOKEINTERFACE, OBJECT_SERIALIZER, WRITE, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitLabel(instanceOfEnd);
            mw.visitJumpInsn(GOTO, forItemClassIfEnd);
        }
    }

    private void extracted56(MethodVisitor mw, Context context, FieldInfo fieldInfo, Class<?> elementClass,
            Label instanceOfElse, Label instanceOfEnd) {
        if (context.writeDirect) {
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM_DESC));
            mw.visitTypeInsn(INSTANCEOF, JAVA_BEAN_SERIALIZER);
            mw.visitJumpInsn(IFEQ, instanceOfElse);

            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM_DESC));
            mw.visitTypeInsn(CHECKCAST, JAVA_BEAN_SERIALIZER); // cast
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM)); // object
            extracted10(mw, context);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(elementClass))); // fieldType
            mw.visitLdcInsn(fieldInfo.serialzeFeatures); // features
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, WRITE_AS_ARRAY_NON_CONTEXT, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitJumpInsn(GOTO, instanceOfEnd);

            mw.visitLabel(instanceOfElse);
        }
    }

    private void generateWriteMethod(MethodVisitor mw, FieldInfo[] getters,
                                     Context context) {


        Label end = new Label();

        int size = getters.length;

        extracted33(mw, getters, context);

        extracted16(mw, context);

        final String writeAsArrayMethodName;

        writeAsArrayMethodName = extracted18(context);

        extracted20(mw, context, writeAsArrayMethodName);

        extracted21(mw, context);

        boolean writeClasName = (context.beanInfo.features & SerializerFeature.WRITE_CLASS_NAME.mask) != 0;

        // SEPERATO
        extracted24(mw, context, writeClasName);

        mw.visitVarInsn(ISTORE, context.variants(SEPERATOR));

        extracted25(mw, context);

        extracted26(mw, context);

        extracted29(mw, getters, context, size);

        extracted30(mw, context);

        Label elseVariabile = new Label();
        Label endIfVariabile = new Label();

        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitIntInsn(BIPUSH, '{');
        mw.visitJumpInsn(IF_ICMPNE, elseVariabile);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(BIPUSH, '{');
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

        mw.visitLabel(elseVariabile);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(BIPUSH, '}');
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

        mw.visitLabel(endIfVariabile);
        mw.visitLabel(end);

        extracted31(mw, context);

    }

    private void extracted33(MethodVisitor mw, FieldInfo[] getters, Context context) {
        if (!context.writeDirect) {
            // pretty format not byte code optimized
            Label endSupper = new Label();
            Label supper = new Label();
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitLdcInsn(SerializerFeature.PRETTY_FORMAT.mask);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, IS_ENABLED, "(I)Z");
            mw.visitJumpInsn(IFNE, supper);

            boolean hasMethod = extracted15(getters);

            extracted32(mw, context, endSupper, hasMethod);

            mw.visitLabel(supper);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, 1);
            mw.visitVarInsn(ALOAD, 2);
            mw.visitVarInsn(ALOAD, 3);
            mw.visitVarInsn(ALOAD, 4);
            mw.visitVarInsn(ILOAD, 5);
            mw.visitMethodInsn(INVOKESPECIAL, JAVA_BEAN_SERIALIZER,
                               WRITE, "(L" + JSONSERIALIZER
                                        + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitInsn(RETURN);

            mw.visitLabel(endSupper);
        }
    }

    private void extracted32(MethodVisitor mw, Context context, Label endSupper, boolean hasMethod) {
        if (hasMethod) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, IS_ENABLED, "(I)Z");
            mw.visitJumpInsn(IFEQ, endSupper);
        } else {
            mw.visitJumpInsn(GOTO, endSupper);
        }
    }

    private void extracted31(MethodVisitor mw, Context context) {
        if (!context.nonContext) {
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(PARENT));
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, SET_CONTEXT, "(" + SERIAL_CONTEXT_DESC + ")V");
        }
    }

    private void extracted30(MethodVisitor mw, Context context) {
        if (!context.writeDirect) {
            afterMetodo(mw, context);
        }
    }

    private void extracted29(MethodVisitor mw, FieldInfo[] getters, Context context, int size) {
        for (int i = 0; i < size; ++i) {
            FieldInfo property = getters[i];
            Class<?> propertyClass = property.fieldClass;

            mw.visitLdcInsn(property.name);
            mw.visitVarInsn(ASTORE, Context.fieldName);

            extracted28(mw, context, property, propertyClass);
        }
    }

    private void extracted28(MethodVisitor mw, Context context, FieldInfo property,
                             Class<?> propertyClass) {
        if (extracted27(propertyClass)) {
            intMetodo(mw, property, context, context.variants(propertyClass.getName()), 'I');
        } else if (propertyClass == long.class) {
            longMetodo(mw, property, context);
        } else if (propertyClass == float.class) {
            floatMetodo(mw, property, context);
        } else if (propertyClass == double.class) {
            doubleMetodo(mw, property, context);
        } else if (propertyClass == boolean.class) {
            intMetodo(mw, property, context, context.variants(BOOLEAN), 'Z');
        } else if (propertyClass == char.class) {
            intMetodo(mw, property, context, context.variants("char"), 'C');
        } else if (propertyClass == String.class) {
            stringMetodo(mw, property, context);
        } else if (propertyClass == BigDecimal.class) {
            decimal(mw, property, context);
        } else if (List.class.isAssignableFrom(propertyClass)) {
            listMetodo(mw, property, context);
        } else if (propertyClass.isEnum()) {
            enumMetodo(mw, property, context);
        } else {
            objectMetodo(mw, property, context);
        }
    }

    private boolean extracted27(Class<?> propertyClass) {
        return propertyClass == byte.class //
            || propertyClass == short.class //
            || propertyClass == int.class;
    }

    private void extracted26(MethodVisitor mw, Context context) {
        if (!context.writeDirect) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "isNotWriteDefaultValue", "()Z");
            mw.visitVarInsn(ISTORE, context.variants("notWriteDefaultValue"));

            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, CHECK_VALUE, "(" + SERIALIZE_FILTERABLE_DESC + ")Z");
            mw.visitVarInsn(ISTORE, context.variants(CHECK_VALUE));

            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, HAS_NAME_FILTERS, "(" + SERIALIZE_FILTERABLE_DESC + ")Z");
            mw.visitVarInsn(ISTORE, context.variants(HAS_NAME_FILTERS));
        }
    }

    private void extracted25(MethodVisitor mw, Context context) {
        if (!context.writeDirect) {
            beforeMetodo(mw, context);
        }
    }

    private void extracted24(MethodVisitor mw, Context context, boolean writeClasName) {
        if (writeClasName || !context.writeDirect) {
            Label endVariabile = new Label();
            Label elseVariabile = new Label();
            Label writeClass = new Label();

            extracted22(mw, writeClasName, elseVariabile);

            // IFNULL
            mw.visitVarInsn(ALOAD, Context.PARAM_FIELD_TYPE);
            mw.visitVarInsn(ALOAD, Context.OBJ);
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_OBJECT, GET_CLASS, LJAVA_LANG_CLASS);
            mw.visitJumpInsn(IF_ACMPEQ, elseVariabile);

            mw.visitLabel(writeClass);
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, '{');
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
            
            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            extracted23(mw, context);
            mw.visitVarInsn(ALOAD, Context.OBJ);

            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "writeClassName", "(L" + JSONSERIALIZER + ";Ljava/lang/String;Ljava/lang/Object;)V");
            mw.visitVarInsn(BIPUSH, ',');
            mw.visitJumpInsn(GOTO, endVariabile);

            mw.visitLabel(elseVariabile);
            mw.visitVarInsn(BIPUSH, '{');

            mw.visitLabel(endVariabile);
        } else {
            mw.visitVarInsn(BIPUSH, '{');
        }
    }

    private void extracted23(MethodVisitor mw, Context context) {
        if (context.beanInfo.typeKey != null) {
            mw.visitLdcInsn(context.beanInfo.typeKey);
        } else {
            mw.visitInsn(ACONST_NULL);
        }
    }

    private void extracted22(MethodVisitor mw, boolean writeClasName, Label elsePersonale) {
        if (!writeClasName) {
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, Context.PARAM_FIELD_TYPE);
            mw.visitVarInsn(ALOAD, Context.OBJ);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "isWriteClassName",
                    "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Z");
            mw.visitJumpInsn(IFEQ, elsePersonale);
        }
    }

    private void extracted21(MethodVisitor mw, Context context) {
        if (!context.nonContext) {
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "getContext", "()" + SERIAL_CONTEXT_DESC);
            mw.visitVarInsn(ASTORE, context.variants(PARENT));

            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(PARENT));
            mw.visitVarInsn(ALOAD, Context.OBJ);
            mw.visitVarInsn(ALOAD, Context.PARAM_FIELD_NAME);
            mw.visitLdcInsn(context.beanInfo.features);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, SET_CONTEXT,
                               "(" + SERIAL_CONTEXT_DESC + "Ljava/lang/Object;Ljava/lang/Object;I)V");
        }
    }

    private void extracted20(MethodVisitor mw, Context context, final String writeAsArrayMethodName) {
        if (extracted19(context)) {
            Label endWriteAsArray = new Label();

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitLdcInsn(SerializerFeature.BEAN_TO_ARRAY.mask);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, IS_ENABLED, "(I)Z");
            mw.visitJumpInsn(IFEQ, endWriteAsArray);

            // /////
            mw.visitVarInsn(ALOAD, 0); // this
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, 2); // obj
            mw.visitVarInsn(ALOAD, 3); // fieldObj
            mw.visitVarInsn(ALOAD, 4); // fieldType
            mw.visitVarInsn(ILOAD, 5); // features
            mw.visitMethodInsn(INVOKEVIRTUAL, //
                               context.className, //
                               writeAsArrayMethodName, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);

            mw.visitInsn(RETURN);

            mw.visitLabel(endWriteAsArray);
        } else {
            mw.visitVarInsn(ALOAD, 0); // this
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, 2); // obj
            mw.visitVarInsn(ALOAD, 3); // fieldObj
            mw.visitVarInsn(ALOAD, 4); // fieldType
            mw.visitVarInsn(ILOAD, 5); // features
            mw.visitMethodInsn(INVOKEVIRTUAL, //
                               context.className, //
                               writeAsArrayMethodName, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitInsn(RETURN);
        }
    }

    private boolean extracted19(Context context) {
        return (context.beanInfo.features & SerializerFeature.BEAN_TO_ARRAY.mask) == 0;
    }

    private String extracted18(Context context) {
        final String writeAsArrayMethodName;
        if (context.writeDirect) {
            writeAsArrayMethodName = extracted17(context);
        } else {
            writeAsArrayMethodName = "writeAsArrayNormal";
        }
        return writeAsArrayMethodName;
    }

    private String extracted17(Context context) {
        final String writeAsArrayMethodName;
        if (context.nonContext) {
            writeAsArrayMethodName = WRITE_AS_ARRAY_NON_CONTEXT;
        } else {
            writeAsArrayMethodName = WRITE_AS_ARRAY;
        }
        return writeAsArrayMethodName;
    }

    private void extracted16(MethodVisitor mw, Context context) {
        if (!context.nonContext) {
            Label endRef = new Label();

            // /////
            mw.visitVarInsn(ALOAD, 0); // this
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, Context.OBJ);
            mw.visitVarInsn(ILOAD, Context.FEATURES);
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "writeReference",
                               "(L" + JSONSERIALIZER + ";Ljava/lang/Object;I)Z");

            mw.visitJumpInsn(IFEQ, endRef);

            mw.visitInsn(RETURN);

            mw.visitLabel(endRef);
        }
    }

    private boolean extracted15(FieldInfo[] getters) {
        boolean hasMethod = false;
        for (FieldInfo getter : getters) {
            if (getter.method != null) {
                hasMethod = true;
                break;
            }
        }
        return hasMethod;
    }

    private void objectMetodo (MethodVisitor mw, FieldInfo property, Context context) {
        Label end = new Label();

        nameApplyMetodo(mw, property, context, end);
        getMetodo(mw, context, property);
        mw.visitVarInsn(ASTORE, context.variants(OBJECT));

        filtersMetodo(mw, property, context, end);

        writeObejectMetodo(mw, property, context, end);

        mw.visitLabel(end);
    }

    private void enumMetodo (MethodVisitor mw, FieldInfo fieldInfo, Context context) {
        Label notNull = new Label();
        Label endIf = new Label();
        Label end = new Label();

        nameApplyMetodo(mw, fieldInfo, context, end);
        getMetodo(mw, context, fieldInfo);
        mw.visitTypeInsn(CHECKCAST, "java/lang/Enum"); // cast
        mw.visitVarInsn(ASTORE, context.variants("enum"));

        filtersMetodo(mw, fieldInfo, context, end);

        mw.visitVarInsn(ALOAD, context.variants("enum"));
        mw.visitJumpInsn(IFNONNULL, notNull);
        ifWriteNullMetodo(mw, fieldInfo, context);
        mw.visitJumpInsn(GOTO, endIf);

        mw.visitLabel(notNull);

       if (context.writeDirect) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitVarInsn(ALOAD, context.variants("enum"));
            mw.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Enum", "name", "()Ljava/lang/String;");
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeFieldValueStringWithDoubleQuote",
                               CLJAVA_LANG_STRING_LJAVA_LANG_STRING_V);
        } else {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
            
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitInsn(ICONST_0);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeFieldName", "(Ljava/lang/String;Z)V");
            
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants("enum"));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldInfo.fieldClass)));
            mw.visitLdcInsn(fieldInfo.serialzeFeatures);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                    LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
        }

        seperatorMetodo(mw, context);

        mw.visitLabel(endIf);
        mw.visitLabel(end);
    }
    
    private void intMetodo(MethodVisitor mw, FieldInfo property, Context context, int var1, char type) {
        Label end = new Label();

        nameApplyMetodo(mw, property, context, end);
        getMetodo(mw, context, property);
        mw.visitVarInsn(ISTORE, var1);

        filtersMetodo(mw, property, context, end);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitVarInsn(ALOAD, Context.fieldName);
        mw.visitVarInsn(ILOAD, var1);

        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_FIELD_VALUE, "(CLjava/lang/String;" + type + ")V");

        seperatorMetodo(mw, context);

        mw.visitLabel(end);
    }

    private void longMetodo(MethodVisitor mw, FieldInfo property, Context context) {
        Label end = new Label();

        nameApplyMetodo(mw, property, context, end);
        getMetodo(mw, context, property);
        mw.visitVarInsn(LSTORE, context.variants("long", 2));

        filtersMetodo(mw, property, context, end);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitVarInsn(ALOAD, Context.fieldName);
        mw.visitVarInsn(LLOAD, context.variants("long", 2));
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_FIELD_VALUE, "(CLjava/lang/String;J)V");

        seperatorMetodo(mw, context);

        mw.visitLabel(end);
    }
    
    private void floatMetodo(MethodVisitor mw, FieldInfo property, Context context) {
        Label end = new Label();

        nameApplyMetodo(mw, property, context, end);
        getMetodo(mw, context, property);
        mw.visitVarInsn(FSTORE, context.variants(FLOAT));

        filtersMetodo(mw, property, context, end);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitVarInsn(ALOAD, Context.fieldName);
        mw.visitVarInsn(FLOAD, context.variants(FLOAT));
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_FIELD_VALUE, "(CLjava/lang/String;F)V");

        seperatorMetodo(mw, context);

        mw.visitLabel(end);
    }

    private void doubleMetodo(MethodVisitor mw, FieldInfo property, Context context) {
        Label end = new Label();

        nameApplyMetodo(mw, property, context, end);
        getMetodo(mw, context, property);
        mw.visitVarInsn(DSTORE, context.variants(DOUBLE, 2));

        filtersMetodo(mw, property, context, end);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitVarInsn(ALOAD, Context.fieldName);
        mw.visitVarInsn(DLOAD, context.variants(DOUBLE, 2));
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_FIELD_VALUE, "(CLjava/lang/String;D)V");

        seperatorMetodo(mw, context);

        mw.visitLabel(end);
    }
    
    private void getMetodo(MethodVisitor mw, Context context, FieldInfo fieldInfo) {
        Method method = fieldInfo.method;
        if (method != null) {
            mw.visitVarInsn(ALOAD, context.variants(ENTITY));
            Class<?> declaringClass = method.getDeclaringClass();
            mw.visitMethodInsn(declaringClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, type(declaringClass), method.getName(), desc(method));
            if (!method.getReturnType().equals(fieldInfo.fieldClass)) {
                mw.visitTypeInsn(CHECKCAST, type(fieldInfo.fieldClass)); // cast
            }
        } else {
            mw.visitVarInsn(ALOAD, context.variants(ENTITY));
            Field field = fieldInfo.field;
            mw.visitFieldInsn(GETFIELD, type(fieldInfo.declaringClass), field.getName(),
                              desc(field.getType()));
            if (!field.getType().equals(fieldInfo.fieldClass)) {
                mw.visitTypeInsn(CHECKCAST, type(fieldInfo.fieldClass)); // cast
            }
        }
    }

    private void decimal(MethodVisitor mw, FieldInfo property, Context context) {
        Label end = new Label();

        nameApplyMetodo(mw, property, context, end);
        getMetodo(mw, context, property);
        mw.visitVarInsn(ASTORE, context.variants(DECIMAL));

        filtersMetodo(mw, property, context, end);

        Label ifVariabile = new Label();
        Label elseVariabile = new Label();
        Label endIfVariabile = new Label();

        mw.visitLabel(ifVariabile);


        mw.visitVarInsn(ALOAD, context.variants(DECIMAL));
        mw.visitJumpInsn(IFNONNULL, elseVariabile);
        ifWriteNullMetodo(mw, property, context);
        mw.visitJumpInsn(GOTO, endIfVariabile);

        mw.visitLabel(elseVariabile);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitVarInsn(ALOAD, Context.fieldName);
        mw.visitVarInsn(ALOAD, context.variants(DECIMAL));
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_FIELD_VALUE,
                           "(CLjava/lang/String;Ljava/math/BigDecimal;)V");

        seperatorMetodo(mw, context);
        mw.visitJumpInsn(GOTO, endIfVariabile);

        mw.visitLabel(endIfVariabile);

        mw.visitLabel(end);
    }

    private void stringMetodo(MethodVisitor mw, FieldInfo property, Context context) {
        Label endVariabile = new Label();

        if (property.name.equals(context.beanInfo.typeKey)) {
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, Context.PARAM_FIELD_TYPE);
            mw.visitVarInsn(ALOAD, Context.OBJ);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "isWriteClassName",
                    "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Z");
            mw.visitJumpInsn(IFNE, endVariabile);
        }

        nameApplyMetodo(mw, property, context, endVariabile);
        getMetodo(mw, context, property);
        mw.visitVarInsn(ASTORE, context.variants(STRING));

        filtersMetodo(mw, property, context, endVariabile);

        Label elseVariabile = new Label();
        Label endIfVariabile = new Label();


        mw.visitVarInsn(ALOAD, context.variants(STRING));
        mw.visitJumpInsn(IFNONNULL, elseVariabile);

        ifWriteNullMetodo(mw, property, context);

        mw.visitJumpInsn(GOTO, endIfVariabile);

        mw.visitLabel(elseVariabile);


        if ("trim".equals(property.format)) {
            mw.visitVarInsn(ALOAD, context.variants(STRING));
            mw.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;");
            mw.visitVarInsn(ASTORE, context.variants(STRING));
        }

        if (context.writeDirect) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitVarInsn(ALOAD, context.variants(STRING));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeFieldValueStringWithDoubleQuoteCheck",
                               CLJAVA_LANG_STRING_LJAVA_LANG_STRING_V);
        } else {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitVarInsn(ALOAD, context.variants(STRING));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_FIELD_VALUE,
                               CLJAVA_LANG_STRING_LJAVA_LANG_STRING_V);
        }
        seperatorMetodo(mw, context);

        mw.visitLabel(endIfVariabile);

        mw.visitLabel(endVariabile);
    }

    private void listMetodo(MethodVisitor mw, FieldInfo fieldInfo, Context context) {
        Type propertyType = fieldInfo.fieldType;

        Type elementType = TypeUtils.getCollectionItemType(propertyType);

        Class<?> elementClass = null;
        elementClass = extracted7(elementType, elementClass);
        
        elementClass = extracted8(elementClass);

        Label endVariabile = new Label();
        Label elseVariabile = new Label();
        Label endIfVariabile = new Label();

        nameApplyMetodo(mw, fieldInfo, context, endVariabile);
        getMetodo(mw, context, fieldInfo);
        mw.visitTypeInsn(CHECKCAST, JAVA_UTIL_LIST); // cast
        mw.visitVarInsn(ASTORE, context.variants("list"));

        filtersMetodo(mw, fieldInfo, context, endVariabile);

        mw.visitVarInsn(ALOAD, context.variants("list"));
        mw.visitJumpInsn(IFNONNULL, elseVariabile);
        ifWriteNullMetodo(mw, fieldInfo, context);
        mw.visitJumpInsn(GOTO, endIfVariabile);

        mw.visitLabel(elseVariabile);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

        writeFieldNameMetodo(mw, context);

        //
        mw.visitVarInsn(ALOAD, context.variants("list"));
        mw.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_LIST, "size", "()I");
        mw.visitVarInsn(ISTORE, context.variants("size"));

        Label elseTre = new Label();
        Label endIfTre = new Label();

        mw.visitVarInsn(ILOAD, context.variants("size"));
        mw.visitInsn(ICONST_0);
        mw.visitJumpInsn(IF_ICMPNE, elseTre);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitLdcInsn("[]");
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(Ljava/lang/String;)V");

        mw.visitJumpInsn(GOTO, endIfTre);

        mw.visitLabel(elseTre);

        extracted13(mw, context);

        if (extracted14(context, elementType)) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ALOAD, context.variants("list"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(Ljava/util/List;)V");
        } else {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, '[');
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

            Label forVariabile = new Label();
            Label forFirst = new Label();
            Label forEnd = new Label();

            mw.visitInsn(ICONST_0);
            mw.visitVarInsn(ISTORE, context.variants("i"));


            mw.visitLabel(forVariabile);
            mw.visitVarInsn(ILOAD, context.variants("i"));
            mw.visitVarInsn(ILOAD, context.variants("size"));
            mw.visitJumpInsn(IF_ICMPGE, forEnd); // i < list.size - 1

            mw.visitVarInsn(ILOAD, context.variants("i"));
            mw.visitJumpInsn(IFEQ, forFirst); // i < list.size - 1

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, ',');
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

            mw.visitLabel(forFirst);

            mw.visitVarInsn(ALOAD, context.variants("list"));
            mw.visitVarInsn(ILOAD, context.variants("i"));
            mw.visitMethodInsn(INVOKEINTERFACE, JAVA_UTIL_LIST, "get", "(I)Ljava/lang/Object;");
            mw.visitVarInsn(ASTORE, context.variants(LIST_ITEM));

            Label forItemNullEnd = new Label();
            Label forItemNullElseVariabile = new Label();

            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM));
            mw.visitJumpInsn(IFNONNULL, forItemNullElseVariabile);

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_NULL, "()V");
            mw.visitJumpInsn(GOTO, forItemNullEnd);

            mw.visitLabel(forItemNullElseVariabile);

            Label forItemClassIfEndVariabile = new Label();
            Label forItemClassIfElseVariabile = new Label();
            extracted12(mw, fieldInfo, context, elementClass, forItemClassIfEndVariabile, forItemClassIfElseVariabile);

            mw.visitLabel(forItemClassIfElseVariabile);

            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM));
            extracted10(mw, context);

            extracted9(mw, fieldInfo, elementType, elementClass);

            mw.visitLabel(forItemClassIfEndVariabile);
            mw.visitLabel(forItemNullEnd);

            mw.visitIincInsn(context.variants("i"), 1);
            mw.visitJumpInsn(GOTO, forVariabile);

            mw.visitLabel(forEnd);

            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(BIPUSH, ']');
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");
        }

        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "popContext", "()V");

        mw.visitLabel(endIfTre);

        seperatorMetodo(mw, context);

        mw.visitLabel(endIfVariabile);

        mw.visitLabel(endVariabile);
    }

    private boolean extracted14(Context context, Type elementType) {
        return elementType == String.class //
            && context.writeDirect;
    }

    private void extracted13(MethodVisitor mw, Context context) {
        if (!context.nonContext) {
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants("list"));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, SET_CONTEXT, LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_V);
        }
    }

    private void extracted12(MethodVisitor mw, FieldInfo fieldInfo, Context context, Class<?> elementClass,
            Label forItemClassIfEnd, Label forItemClassIfElse) {
        if (elementClass != null && Modifier.isPublic(elementClass.getModifiers())) {
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM));
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_OBJECT, GET_CLASS, LJAVA_LANG_CLASS);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(elementClass)));
            mw.visitJumpInsn(IF_ACMPNE, forItemClassIfElse);

            getListFieldItemSerMetodo(context, mw, fieldInfo, elementClass);

            mw.visitVarInsn(ASTORE, context.variants(LIST_ITEM_DESC));

            Label instanceOfElseVariabile = new Label();
            Label instanceOfEndVariabile = new Label();

            extracted11(mw, fieldInfo, context, elementClass, instanceOfElseVariabile, instanceOfEndVariabile);
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM_DESC));
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM)); // object
            extracted10(mw, context);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(elementClass))); // fieldType
            mw.visitLdcInsn(fieldInfo.serialzeFeatures); // features
            mw.visitMethodInsn(INVOKEINTERFACE, OBJECT_SERIALIZER, WRITE, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);

            mw.visitLabel(instanceOfEndVariabile);
            mw.visitJumpInsn(GOTO, forItemClassIfEnd);
        }
    }

    private void extracted11(MethodVisitor mw, FieldInfo fieldInfo, Context context, Class<?> elementClass,
            Label instanceOfElse, Label instanceOfEnd) {
        if (context.writeDirect) {
            String writeMethodName = context.nonContext && context.writeDirect ? //
                WRITE_DIRECTION_NON_CONTEXT //
                : WRITE;
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM_DESC));
            mw.visitTypeInsn(INSTANCEOF, JAVA_BEAN_SERIALIZER);
            mw.visitJumpInsn(IFEQ, instanceOfElse);

            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM_DESC));
            mw.visitTypeInsn(CHECKCAST, JAVA_BEAN_SERIALIZER); // cast
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(LIST_ITEM)); // object
            extracted10(mw, context);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(elementClass))); // fieldType
            mw.visitLdcInsn(fieldInfo.serialzeFeatures); // features
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, writeMethodName, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitJumpInsn(GOTO, instanceOfEnd);

            mw.visitLabel(instanceOfElse);
        }
    }

    private void extracted10(MethodVisitor mw, Context context) {
        if (context.nonContext) {
            mw.visitInsn(ACONST_NULL);
        } else {
            mw.visitVarInsn(ILOAD, context.variants("i"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_INTEGER, VALUE_OF, I_LJAVA_LANG_INTEGER);
        }
    }

    private void extracted9(MethodVisitor mw, FieldInfo fieldInfo, Type elementType, Class<?> elementClass) {
        if (elementClass != null && Modifier.isPublic(elementClass.getModifiers())) {
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc((Class<?>) elementType)));
            mw.visitLdcInsn(fieldInfo.serialzeFeatures);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                               LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
        } else {
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                               LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_V);
        }
    }

    private Class<?> extracted8(Class<?> elementClass) {
        if (elementClass == Object.class //
            || elementClass == Serializable.class) {
            elementClass = null;
        }
        return elementClass;
    }

    private Class<?> extracted7(Type elementType, Class<?> elementClass) {
        if (elementType instanceof Class<?>) {
            elementClass = (Class<?>) elementType;
        }
        return elementClass;
    }

    private void filtersMetodo(MethodVisitor mw, FieldInfo property, Context context, Label endVariabile) {
        if (property.fieldTransient) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitLdcInsn(SerializerFeature.SKIP_TRANSIENT_FIELD.mask);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, IS_ENABLED, "(I)Z");
            mw.visitJumpInsn(IFNE, endVariabile);
        }

        notWriteDefaultMetodo(mw, property, context, endVariabile);

        if (context.writeDirect) {
            return;
        }

        applyMetodo(mw, property, context);
        mw.visitJumpInsn(IFEQ, endVariabile);

        processKeyMetodo(mw, property, context);

        processValueMetodo(mw, property, context, endVariabile);
    }

    private void nameApplyMetodo(MethodVisitor mw, FieldInfo property, Context context, Label endVariabile) {
        if (!context.writeDirect) {
            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, Context.OBJ);
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "applyName",
                               "(L" + JSONSERIALIZER + ";Ljava/lang/Object;Ljava/lang/String;)Z");
            mw.visitJumpInsn(IFEQ, endVariabile);

            labelApplyMetodo(mw, property, endVariabile);
        }

        if (property.field == null) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, IS_ENABLED, "(I)Z");

            // if true
            mw.visitJumpInsn(IFNE, endVariabile);
        }
    }

    private void labelApplyMetodo(MethodVisitor mw, FieldInfo property, Label endVariabile) {
        mw.visitVarInsn(ALOAD, 0); // this
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitLdcInsn(property.label);
        mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "applyLabel",
                           "(L" + JSONSERIALIZER + ";Ljava/lang/String;)Z");
        mw.visitJumpInsn(IFEQ, endVariabile);
    }

    private void writeObejectMetodo(MethodVisitor mw, FieldInfo fieldInfo, Context context, Label endVariabile) {
        String format = fieldInfo.getFormat();
        Class<?> fieldClass = fieldInfo.fieldClass;

        Label notNullVariabile = new Label();


        extracted(mw, context);
        mw.visitInsn(DUP);
        mw.visitVarInsn(ASTORE, context.variants(OBJECT));
        mw.visitJumpInsn(IFNONNULL, notNullVariabile);
        ifWriteNullMetodo(mw, fieldInfo, context);
        mw.visitJumpInsn(GOTO, endVariabile);

        mw.visitLabel(notNullVariabile);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

        writeFieldNameMetodo(mw, context);

        Label classIfEndVariabile = new Label();
        Label classIfElseVariabile = new Label();
        extracted6(mw, fieldInfo, context, fieldClass, classIfEndVariabile, classIfElseVariabile);

        mw.visitLabel(classIfElseVariabile);

        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        extracted(mw, context);
        extracted5(mw, fieldInfo, context, format);
        mw.visitLabel(classIfEndVariabile);

        seperatorMetodo(mw, context);
    }

    private void extracted6(MethodVisitor mw, FieldInfo fieldInfo, Context context, Class<?> fieldClass,
            Label classIfEndExt, Label classIfElsExt) {
        if (Modifier.isPublic(fieldClass.getModifiers()) //
            && !ParserConfig.isPrimitive2(fieldClass) //
        ) {
            mw.visitVarInsn(ALOAD, context.variants(OBJECT));
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_OBJECT, GET_CLASS, LJAVA_LANG_CLASS);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldClass)));
            mw.visitJumpInsn(IF_ACMPNE, classIfElsExt);

            getFieldSerMetodo (context, mw, fieldInfo);
            mw.visitVarInsn(ASTORE, context.variants(FIED_SER));

            Label instanceOfElseVariabile = new Label();
            Label instanceOfEndVariabile = new Label();
            mw.visitVarInsn(ALOAD, context.variants(FIED_SER));
            mw.visitTypeInsn(INSTANCEOF, JAVA_BEAN_SERIALIZER);
            mw.visitJumpInsn(IFEQ, instanceOfElseVariabile);

            boolean disableCircularReferenceDetect = (fieldInfo.serialzeFeatures & SerializerFeature.DISABLE_CIRCULAR_REFERENCE_DETECT.mask) != 0;
            boolean fieldBeanToArray = (fieldInfo.serialzeFeatures & SerializerFeature.BEAN_TO_ARRAY.mask) != 0;
            String writeMethodName;
            writeMethodName = extracted2(context, disableCircularReferenceDetect, fieldBeanToArray);
            
            mw.visitVarInsn(ALOAD, context.variants(FIED_SER));
            mw.visitTypeInsn(CHECKCAST, JAVA_BEAN_SERIALIZER); // cast
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(OBJECT));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_FIELDTYPE,
                              LJAVA_LANG_REFLECT_TYPE);
            mw.visitLdcInsn(fieldInfo.serialzeFeatures); // features
            mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, writeMethodName, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
            mw.visitJumpInsn(GOTO, instanceOfEndVariabile);

            mw.visitLabel(instanceOfElseVariabile);

            mw.visitVarInsn(ALOAD, context.variants(FIED_SER));
            mw.visitVarInsn(ALOAD, Context.SERIALIZER);
            mw.visitVarInsn(ALOAD, context.variants(OBJECT));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_FIELDTYPE,
                              LJAVA_LANG_REFLECT_TYPE);
            mw.visitLdcInsn(fieldInfo.serialzeFeatures); // features
            mw.visitMethodInsn(INVOKEINTERFACE, OBJECT_SERIALIZER, WRITE, //
                               "(L" + JSONSERIALIZER + COMMA_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);

            mw.visitLabel(instanceOfEndVariabile);
            mw.visitJumpInsn(GOTO, classIfEndExt);
        }
    }

    private void extracted5(MethodVisitor mw, FieldInfo fieldInfo, Context context, String format) {
        if (format != null) {
            mw.visitLdcInsn(format);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "writeWithFormat",
                               "(Ljava/lang/Object;Ljava/lang/String;)V");
        } else {
            mw.visitVarInsn(ALOAD, Context.fieldName);
            extracted4(mw, fieldInfo, context);
        }
    }

    private void extracted4(MethodVisitor mw, FieldInfo fieldInfo, Context context) {
        if (fieldInfo.fieldType instanceof Class<?> //
            && ((Class<?>) fieldInfo.fieldType).isPrimitive()) {
            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                               LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_V);
        } else {
            extracted3(mw, fieldInfo, context);
            mw.visitLdcInsn(fieldInfo.serialzeFeatures);

            mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, WRITE_WITH_FIELD_NAME,
                               LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_REFLECT_TYPE_I_V);
        }
    }

    private void extracted3(MethodVisitor mw, FieldInfo fieldInfo, Context context) {
        if (fieldInfo.fieldClass == String.class) {
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(String.class)));
        } else {
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_FIELDTYPE,
                              LJAVA_LANG_REFLECT_TYPE);
        }
    }

    private String extracted2(Context context, boolean disableCircularReferenceDetect, boolean fieldBeanToArray) {
        String writeMethodName;
        if (disableCircularReferenceDetect || (context.nonContext && context.writeDirect)) {
            writeMethodName = fieldBeanToArray ? WRITE_AS_ARRAY_NON_CONTEXT : WRITE_DIRECTION_NON_CONTEXT;
        } else {
            writeMethodName = fieldBeanToArray ? WRITE_AS_ARRAY : WRITE;
        }
        return writeMethodName;
    }

    private void extracted(MethodVisitor mw, Context context) {
        if (context.writeDirect) {
            mw.visitVarInsn(ALOAD, context.variants(OBJECT));
        } else {
            mw.visitVarInsn(ALOAD, Context.processValue);
        }
    }

    private void beforeMetodo (MethodVisitor mw, Context context) {
        mw.visitVarInsn(ALOAD, 0); // this
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitVarInsn(ALOAD, Context.OBJ);
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "writeBefore",
                           "(L" + JSONSERIALIZER + ";Ljava/lang/Object;C)C");
        mw.visitVarInsn(ISTORE, context.variants(SEPERATOR));
    }

    private void afterMetodo(MethodVisitor mw, Context context) {
        mw.visitVarInsn(ALOAD, 0); // this
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitVarInsn(ALOAD, 2); // obj
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "writeAfter",
                           "(L" + JSONSERIALIZER + ";Ljava/lang/Object;C)C");
        mw.visitVarInsn(ISTORE, context.variants(SEPERATOR));
    }

    private void notWriteDefaultMetodo(MethodVisitor mw, FieldInfo property, Context context, Label endVariabile) {
        if (context.writeDirect) {
            return;
        }

        Label elseLabel = new Label();

        mw.visitVarInsn(ILOAD, context.variants("notWriteDefaultValue"));
        mw.visitJumpInsn(IFEQ, elseLabel);

        Class<?> propertyClass = property.fieldClass;
        if (propertyClass == boolean.class) {
            mw.visitVarInsn(ILOAD, context.variants(BOOLEAN));
            mw.visitJumpInsn(IFEQ, endVariabile);
        } else if (propertyClass == byte.class) {
            mw.visitVarInsn(ILOAD, context.variants("byte"));
            mw.visitJumpInsn(IFEQ, endVariabile);
        } else if (propertyClass == short.class) {
            mw.visitVarInsn(ILOAD, context.variants(SHORT));
            mw.visitJumpInsn(IFEQ, endVariabile);
        } else if (propertyClass == int.class) {
            mw.visitVarInsn(ILOAD, context.variants("int"));
            mw.visitJumpInsn(IFEQ, endVariabile);
        } else if (propertyClass == long.class) {
            mw.visitVarInsn(LLOAD, context.variants("long"));
            mw.visitInsn(LCONST_0);
            mw.visitInsn(LCMP);
            mw.visitJumpInsn(IFEQ, endVariabile);
        } else if (propertyClass == float.class) {
            mw.visitVarInsn(FLOAD, context.variants(FLOAT));
            mw.visitInsn(FCONST_0);
            mw.visitInsn(FCMPL);
            mw.visitJumpInsn(IFEQ, endVariabile);
        } else if (propertyClass == double.class) {
            mw.visitVarInsn(DLOAD, context.variants(DOUBLE));
            mw.visitInsn(DCONST_0);
            mw.visitInsn(DCMPL);
            mw.visitJumpInsn(IFEQ, endVariabile);
        }

        mw.visitLabel(elseLabel);
    }

    private void applyMetodo(MethodVisitor mw, FieldInfo property, Context context) {
        Class<?> propertyClass = property.fieldClass;

        mw.visitVarInsn(ALOAD, 0); // this
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitVarInsn(ALOAD, Context.OBJ);
        mw.visitVarInsn(ALOAD, Context.fieldName);

        if (propertyClass == byte.class) {
            mw.visitVarInsn(ILOAD, context.variants("byte"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_BYTE, VALUE_OF, B_LJAVA_LANG_BYTE);
        } else if (propertyClass == short.class) {
            mw.visitVarInsn(ILOAD, context.variants(SHORT));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_SHORT, VALUE_OF, S_LJAVA_LANG_SHORT);
        } else if (propertyClass == int.class) {
            mw.visitVarInsn(ILOAD, context.variants("int"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_INTEGER, VALUE_OF, I_LJAVA_LANG_INTEGER);
        } else if (propertyClass == char.class) {
            mw.visitVarInsn(ILOAD, context.variants("char"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_CHARACTER, VALUE_OF, C_JAVA_LANG_CHARACTER);
        } else if (propertyClass == long.class) {
            mw.visitVarInsn(LLOAD, context.variants("long", 2));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_LONG, VALUE_OF, J_JAVA_LANG_LONG);
        } else if (propertyClass == float.class) {
            mw.visitVarInsn(FLOAD, context.variants(FLOAT));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_FLOAT, VALUE_OF, F_JAVA_LANG_FLOAT);
        } else if (propertyClass == double.class) {
            mw.visitVarInsn(DLOAD, context.variants(DOUBLE, 2));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_DOUBLE, VALUE_OF, D_JAVA_LANG_DOUBLE);
        } else if (propertyClass == boolean.class) {
            mw.visitVarInsn(ILOAD, context.variants(BOOLEAN));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_BOOLEAN, VALUE_OF, Z_JAVA_LANG_BOOLEAN);
        } else if (propertyClass == BigDecimal.class) {
            mw.visitVarInsn(ALOAD, context.variants(DECIMAL));
        } else if (propertyClass == String.class) {
            mw.visitVarInsn(ALOAD, context.variants(STRING));
        } else if (propertyClass.isEnum()) {
            mw.visitVarInsn(ALOAD, context.variants("enum"));
        } else if (List.class.isAssignableFrom(propertyClass)) {
            mw.visitVarInsn(ALOAD, context.variants("list"));
        } else {
            mw.visitVarInsn(ALOAD, context.variants(OBJECT));
        }
        mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER,
                           "apply", "(L" + JSONSERIALIZER
                                    + ";Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Z");
    }

    private void processValueMetodo(MethodVisitor mw, FieldInfo fieldInfo, Context context, Label endVariabile) {
        Label processKeyElse = new Label();

        Class<?> fieldClass = fieldInfo.fieldClass;

        if (fieldClass.isPrimitive()) {
            Label checkValueEnd = new Label();
            mw.visitVarInsn(ILOAD, context.variants(CHECK_VALUE));
            mw.visitJumpInsn(IFNE, checkValueEnd);

            mw.visitInsn(ACONST_NULL);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
            mw.visitVarInsn(ASTORE, Context.processValue);
            mw.visitJumpInsn(GOTO, processKeyElse);

            mw.visitLabel(checkValueEnd);
        }

        mw.visitVarInsn(ALOAD, 0);
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitLdcInsn(context.getFieldOrinal(fieldInfo.name));
        mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "getBeanContext", "(I)" + desc(BeanContext.class));
        mw.visitVarInsn(ALOAD, Context.OBJ);
        mw.visitVarInsn(ALOAD, Context.fieldName);

        String valueDesc = "Ljava/lang/Object;";
        if (fieldClass == byte.class) {
            mw.visitVarInsn(ILOAD, context.variants("byte"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_BYTE, VALUE_OF, B_LJAVA_LANG_BYTE);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
        } else if (fieldClass == short.class) {
            mw.visitVarInsn(ILOAD, context.variants(SHORT));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_SHORT, VALUE_OF, S_LJAVA_LANG_SHORT);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
        } else if (fieldClass == int.class) {
            mw.visitVarInsn(ILOAD, context.variants("int"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_INTEGER, VALUE_OF, I_LJAVA_LANG_INTEGER);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
        } else if (fieldClass == char.class) {
            mw.visitVarInsn(ILOAD, context.variants("char"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_CHARACTER, VALUE_OF, C_JAVA_LANG_CHARACTER);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
        } else if (fieldClass == long.class) {
            mw.visitVarInsn(LLOAD, context.variants("long", 2));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_LONG, VALUE_OF, J_JAVA_LANG_LONG);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
        } else if (fieldClass == float.class) {
            mw.visitVarInsn(FLOAD, context.variants(FLOAT));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_FLOAT, VALUE_OF, F_JAVA_LANG_FLOAT);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
        } else if (fieldClass == double.class) {
            mw.visitVarInsn(DLOAD, context.variants(DOUBLE, 2));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_DOUBLE, VALUE_OF, D_JAVA_LANG_DOUBLE);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
        } else if (fieldClass == boolean.class) {
            mw.visitVarInsn(ILOAD, context.variants(BOOLEAN));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_BOOLEAN, VALUE_OF, Z_JAVA_LANG_BOOLEAN);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, Context.original);
        } else if (fieldClass == BigDecimal.class) {
            mw.visitVarInsn(ALOAD, context.variants(DECIMAL));
            mw.visitVarInsn(ASTORE, Context.original);
            mw.visitVarInsn(ALOAD, Context.original);
        } else if (fieldClass == String.class) {
            mw.visitVarInsn(ALOAD, context.variants(STRING));
            mw.visitVarInsn(ASTORE, Context.original);
            mw.visitVarInsn(ALOAD, Context.original);
        } else if (fieldClass.isEnum()) {
            mw.visitVarInsn(ALOAD, context.variants("enum"));
            mw.visitVarInsn(ASTORE, Context.original);
            mw.visitVarInsn(ALOAD, Context.original);
        } else if (List.class.isAssignableFrom(fieldClass)) {
            mw.visitVarInsn(ALOAD, context.variants("list"));
            mw.visitVarInsn(ASTORE, Context.original);
            mw.visitVarInsn(ALOAD, Context.original);
        } else {
            mw.visitVarInsn(ALOAD, context.variants(OBJECT));
            mw.visitVarInsn(ASTORE, Context.original);
            mw.visitVarInsn(ALOAD, Context.original);
        }

        mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER, "processValue",
                           "(L" + JSONSERIALIZER  + ";" //
                                                                          + desc(BeanContext.class) //
                                                                          + "Ljava/lang/Object;Ljava/lang/String;" //
                                                                          + valueDesc + ")Ljava/lang/Object;");

        mw.visitVarInsn(ASTORE, Context.processValue);

        mw.visitVarInsn(ALOAD, Context.original);
        mw.visitVarInsn(ALOAD, Context.processValue);
        mw.visitJumpInsn(IF_ACMPEQ, processKeyElse);
        writeObejectMetodo(mw, fieldInfo, context, endVariabile);
        mw.visitJumpInsn(GOTO, endVariabile);

        mw.visitLabel(processKeyElse);
    }

    private void processKeyMetodo(MethodVisitor mw, FieldInfo property, Context context) {
        Label elseProcessKey = new Label();

        mw.visitVarInsn(ILOAD, context.variants(HAS_NAME_FILTERS));
        mw.visitJumpInsn(IFEQ, elseProcessKey);

        Class<?> propertyClass = property.fieldClass;

        mw.visitVarInsn(ALOAD, 0);
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitVarInsn(ALOAD, Context.OBJ);
        mw.visitVarInsn(ALOAD, Context.fieldName);

        if (propertyClass == byte.class) {
            mw.visitVarInsn(ILOAD, context.variants("byte"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_BYTE, VALUE_OF, B_LJAVA_LANG_BYTE);
        } else if (propertyClass == short.class) {
            mw.visitVarInsn(ILOAD, context.variants(SHORT));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_SHORT, VALUE_OF, S_LJAVA_LANG_SHORT);
        } else if (propertyClass == int.class) {
            mw.visitVarInsn(ILOAD, context.variants("int"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_INTEGER, VALUE_OF, I_LJAVA_LANG_INTEGER);
        } else if (propertyClass == char.class) {
            mw.visitVarInsn(ILOAD, context.variants("char"));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_CHARACTER, VALUE_OF, C_JAVA_LANG_CHARACTER);
        } else if (propertyClass == long.class) {
            mw.visitVarInsn(LLOAD, context.variants("long", 2));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_LONG, VALUE_OF, J_JAVA_LANG_LONG);
        } else if (propertyClass == float.class) {
            mw.visitVarInsn(FLOAD, context.variants(FLOAT));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_FLOAT, VALUE_OF, F_JAVA_LANG_FLOAT);
        } else if (propertyClass == double.class) {
            mw.visitVarInsn(DLOAD, context.variants(DOUBLE, 2));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_DOUBLE, VALUE_OF, D_JAVA_LANG_DOUBLE);
        } else if (propertyClass == boolean.class) {
            mw.visitVarInsn(ILOAD, context.variants(BOOLEAN));
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_BOOLEAN, VALUE_OF, Z_JAVA_LANG_BOOLEAN);
        } else if (propertyClass == BigDecimal.class) {
            mw.visitVarInsn(ALOAD, context.variants(DECIMAL));
        } else if (propertyClass == String.class) {
            mw.visitVarInsn(ALOAD, context.variants(STRING));
        } else if (propertyClass.isEnum()) {
            mw.visitVarInsn(ALOAD, context.variants("enum"));
        } else if (List.class.isAssignableFrom(propertyClass)) {
            mw.visitVarInsn(ALOAD, context.variants("list"));
        } else {
            mw.visitVarInsn(ALOAD, context.variants(OBJECT));
        }

        mw.visitMethodInsn(INVOKEVIRTUAL, JAVA_BEAN_SERIALIZER,
                           "processKey", "(L" + JSONSERIALIZER
                                         + ";Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;");

        mw.visitVarInsn(ASTORE, Context.fieldName);

        mw.visitLabel(elseProcessKey);
    }

    private void ifWriteNullMetodo(MethodVisitor mw, FieldInfo fieldInfo, Context context) {
        Class<?> propertyClass = fieldInfo.fieldClass;

        Label ifVariabile = new Label();
        Label elseVariabile = new Label();
        Label writeNull = new Label();
        Label endIf = new Label();

        mw.visitLabel(ifVariabile);

        JSONField annotation = fieldInfo.getAnnotation();
        int features = 0;
        if (annotation != null) {
            features = SerializerFeature.of(annotation.serialzeFeatures());
        }
        JSONType jsonType = context.beanInfo.jsonType;
        if (jsonType != null) {
            features |= SerializerFeature.of(jsonType.serialzeFeatures());
        }

        int writeNullFeatures;
        if (propertyClass == String.class) {
            writeNullFeatures = SerializerFeature.WRITE_MAP_NULL_VALUE.getMask()
                    | SerializerFeature.WRITE_NULL_STRING_AS_EMPTY.getMask();
        } else if (Number.class.isAssignableFrom(propertyClass)) {
            writeNullFeatures = SerializerFeature.WRITE_MAP_NULL_VALUE.getMask()
                    | SerializerFeature.WRITE_NULL_NUMBER_AS_ZERO.getMask();
        } else if (Collection.class.isAssignableFrom(propertyClass)) {
            writeNullFeatures = SerializerFeature.WRITE_MAP_NULL_VALUE.getMask()
                    | SerializerFeature.WRITE_NULL_LIST_AS_EMPTY.getMask();
        } else if (Boolean.class == propertyClass) {
            writeNullFeatures = SerializerFeature.WRITE_MAP_NULL_VALUE.getMask()
                    | SerializerFeature.WRITE_NULL_BOOLEAN_AS_FALSE.getMask();
        } else {
            writeNullFeatures = SerializerFeature.WRITE_MAP_NULL_FEATURES;
        }

        if ((features & writeNullFeatures) == 0) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitLdcInsn(writeNullFeatures);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, IS_ENABLED, "(I)Z");
            mw.visitJumpInsn(IFEQ, elseVariabile);
        }

        mw.visitLabel(writeNull);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitVarInsn(ILOAD, context.variants(SEPERATOR));
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE, "(I)V");

        writeFieldNameMetodo(mw, context);

        mw.visitVarInsn(ALOAD, context.variants("out"));
        mw.visitLdcInsn(features);
        // features

        if (propertyClass == String.class || propertyClass == Character.class) {
            mw.visitLdcInsn(SerializerFeature.WRITE_NULL_STRING_AS_EMPTY.mask);
        } else if (Number.class.isAssignableFrom(propertyClass)) {
            mw.visitLdcInsn(SerializerFeature.WRITE_NULL_NUMBER_AS_ZERO.mask);
        } else if (propertyClass == Boolean.class) {
            mw.visitLdcInsn(SerializerFeature.WRITE_NULL_BOOLEAN_AS_FALSE.mask);
        } else if (Collection.class.isAssignableFrom(propertyClass) || propertyClass.isArray()) {
            mw.visitLdcInsn(SerializerFeature.WRITE_NULL_LIST_AS_EMPTY.mask);
        } else {
            mw.visitLdcInsn(0);
        }
        mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, WRITE_NULL, "(II)V");


        seperatorMetodo(mw, context);

        mw.visitJumpInsn(GOTO, endIf);

        mw.visitLabel(elseVariabile);

        mw.visitLabel(endIf);
    }

    private void writeFieldNameMetodo(MethodVisitor mw, Context context) {
        if (context.writeDirect) {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeFieldNameDirect", "(Ljava/lang/String;)V");
        } else {
            mw.visitVarInsn(ALOAD, context.variants("out"));
            mw.visitVarInsn(ALOAD, Context.fieldName);
            mw.visitInsn(ICONST_0);
            mw.visitMethodInsn(INVOKEVIRTUAL, SERIALIZE_WRITER, "writeFieldName", "(Ljava/lang/String;Z)V");
        }
    }

    private void seperatorMetodo(MethodVisitor mw, Context context) {
        mw.visitVarInsn(BIPUSH, ',');
        mw.visitVarInsn(ISTORE, context.variants(SEPERATOR));
    }

    private void getListFieldItemSerMetodo (Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> itemType) {
        Label notNull = new Label();
        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_LIST_ITEM_SER,
                          OBJECT_SERIALIZER_DESC);
        mw.visitJumpInsn(IFNONNULL, notNull);

        mw.visitVarInsn(ALOAD, 0); // this
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(itemType)));
        mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "getObjectWriter",
                           "(Ljava/lang/Class;)" + OBJECT_SERIALIZER_DESC);

        mw.visitFieldInsn(PUTFIELD, context.className, fieldInfo.name + ASM_LIST_ITEM_SER,
                          OBJECT_SERIALIZER_DESC);

        mw.visitLabel(notNull);

        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_LIST_ITEM_SER,
                          OBJECT_SERIALIZER_DESC);
    }

    private void getFieldSerMetodo (Context context, MethodVisitor mw, FieldInfo fieldInfo) {
        Label notNull = new Label();
        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_SER, OBJECT_SERIALIZER_DESC);
        mw.visitJumpInsn(IFNONNULL, notNull);

        mw.visitVarInsn(ALOAD, 0); // this
        mw.visitVarInsn(ALOAD, Context.SERIALIZER);
        mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldInfo.fieldClass)));
        mw.visitMethodInsn(INVOKEVIRTUAL, JSONSERIALIZER, "getObjectWriter",
                           "(Ljava/lang/Class;)" + OBJECT_SERIALIZER_DESC);

        mw.visitFieldInsn(PUTFIELD, context.className, fieldInfo.name + ASM_SER, OBJECT_SERIALIZER_DESC);

        mw.visitLabel(notNull);

        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_SER, OBJECT_SERIALIZER_DESC);
    }
}
