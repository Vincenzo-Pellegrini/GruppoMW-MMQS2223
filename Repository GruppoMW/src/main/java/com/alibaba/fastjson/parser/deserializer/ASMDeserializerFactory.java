package com.alibaba.fastjson.parser.deserializer;

import static com.alibaba.fastjson.util.ASMUtils.desc;
import static com.alibaba.fastjson.util.ASMUtils.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.fastjson.asm.ClassWriter;
import com.alibaba.fastjson.asm.FieldWriter;
import com.alibaba.fastjson.asm.Label;
import com.alibaba.fastjson.asm.MethodVisitor;
import com.alibaba.fastjson.asm.MethodWriter;
import com.alibaba.fastjson.asm.Opcodes;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONLexerBase;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.ParseContext;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.SymbolTable;
import com.alibaba.fastjson.util.*;


public class ASMDeserializerFactory implements Opcodes {

    public final ASMClassLoader classLoader;
    protected final AtomicLong  seed              = new AtomicLong();

    static final String         DEFAULT_JSON_PARSER = type(DefaultJSONParser.class);
    static final String         JSON_LEXER_BASE     = type(JSONLexerBase.class);
    static final String         OPEN_C_I          = "([C)I";
    static final String         OPEN_C_Z          = "([C)Z";
    static final String         LEXER             = "lexer";
    static final String         CONTEXT           = "Context";
    static final String         DESERIALIZE       = "deserialze";
    static final String         INSTANCE          = "instance";
    static final String         TOKEN             = "token";
    static final String         INIT              = "<init>";
    static final String         SCAN_ENUM         = "scanenum";
    static final String         IS_ENABLED        = "isEnabled";
    static final String         MATCHED_COUNT     = "matchedCount";
    static final String         CHILD_CONTEXT     = "childContext";
    static final String         SET_CONTEXT       = "setContext";
    static final String         NEXT_TOKEN        = "nextToken";
    static final String         SCAN_LONG         = "scanLong";
    static final String         SET_TOKEN         = "setToken";
    static final String         GET_CONFIG        = "getConfig";
    static final String         GET_CURRENT       = "getCurrent";
    static final String         GET_CONTEXT       = "getContext";
    static final String         GET_FIELD_TYPE    = "getFieldType";
    static final String         RESOLVE_TASK      = "resolveTask";
    static final String         MATCH_STAT        = "matchStat";
    static final String         VALUE_OF          = "valueOf";
    static final String         SCAN_INT          = "scanInt";
    static final String         TYPE_NAME         = "typeName";
    static final String         USER_TYPE_DESER   = "userTypeDeser";
    static final String         FAST_MATCH_TOKEN  = "fastMatchToken";
    static final String         LIST_ITEM_VALUE   = "list_item_value";
    static final String         SCAN_FIELD_INT    = "scanFieldInt";
    static final  String         ASM_FLAG       = "_asm_flag_";
    static final String         DESERIALZE_ARRAY_MAPPING   = "deserialzeArrayMapping";
    static final String         LJAVA_LANG_OBJECT_Z        = "(Ljava/lang/Object;)Z";
    static final  String         ASM_LIST_ITEM_DESER     = "_asm_list_item_deser__";
    static final String         JAVA_LANG_INTEGER          = "java/lang/Integer";
    static final String         I_LJAVA_LANG_INTEGER       = "(I)Ljava/lang/Integer;";
    static final String         I_LJAVA_LANG_REFLECT_TYPE  = "(I)Ljava/lang/reflect/Type;";
    static final String LJAVA_LANG_REFLECT_TYPE_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT   = ";Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;";
    static final String LJAVA_LANG_REFLECT_TYPE_LJAVA_LANG_OBJECT_I_LJAVA_LANG_OBJECT = ";Ljava/lang/reflect/Type;Ljava/lang/Object;I)Ljava/lang/Object;";
    static final String LJAVA_LANG_REFLECT_TYPE_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT = ";Ljava/lang/reflect/Type;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

    public ASMDeserializerFactory(ClassLoader parentClassLoader){
        classLoader = parentClassLoader instanceof ASMClassLoader //
                ? (ASMClassLoader) parentClassLoader //
                : new ASMClassLoader(parentClassLoader);
    }

    public ObjectDeserializer createJavaBeanDeserializer(ParserConfig config, JavaBeanInfo beanInfo) throws Exception {
        Class<?> clazz = beanInfo.clazz;
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException("not support type :" + clazz.getName());
        }

        String className = "FastjsonASMDeserializer_" + seed.incrementAndGet() + "_" + clazz.getSimpleName();
        String classNameType;
        String classNameFull;

        Package pkg = ASMDeserializerFactory.class.getPackage();
        if (pkg != null) {
            String packageName = pkg.getName();
            classNameType = packageName.replace('.', '/') + "/" + className;
            classNameFull = packageName + "." + className;
        } else {
            classNameType = className;
            classNameFull = className;
        }

        ClassWriter cw = new ClassWriter();
        cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, classNameType, type(JavaBeanDeserializer.class), null);

        init(cw, new Context(classNameType, beanInfo, 3));
        createInstance(cw, new Context(classNameType, beanInfo, 3));
        deserialze(cw, new Context(classNameType, beanInfo, 5));

        deserialzeArrayMapping(cw, new Context(classNameType, beanInfo, 4));
        byte[] code = cw.toByteArray();

        Class<?> deserClass = classLoader.defineClassPublic(classNameFull, code, 0, code.length);
        Constructor<?> constructor = deserClass.getConstructor(ParserConfig.class, JavaBeanInfo.class);
        Object instance = constructor.newInstance(config, beanInfo);

        return (ObjectDeserializer) instance;
    }

    private void setFlag(MethodVisitor mw, Context context, int i) {
        String varName = ASM_FLAG + (i / 32);

        mw.visitVarInsn(ILOAD, context.var2(varName));
        mw.visitLdcInsn(1 << i);
        mw.visitInsn(IOR);
        mw.visitVarInsn(ISTORE, context.var2(varName));
    }

    private void isFlag(MethodVisitor mw, Context context, int i, Label label) {
        mw.visitVarInsn(ILOAD, context.var2(ASM_FLAG + (i / 32)));
        mw.visitLdcInsn(1 << i);
        mw.visitInsn(IAND);

        mw.visitJumpInsn(IFEQ, label);
    }

    private void deserialzeArrayMapping(ClassWriter cw, Context context) {
        MethodVisitor mw = new MethodWriter(cw, ACC_PUBLIC, DESERIALZE_ARRAY_MAPPING,
                "(L" + DEFAULT_JSON_PARSER + LJAVA_LANG_REFLECT_TYPE_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT, null);

        defineVarLexer(context, mw);

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitVarInsn(ALOAD, 1);
        mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, "getSymbolTable", "()" + desc(SymbolTable.class));
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanTypeName", "(" + desc(SymbolTable.class) + ")Ljava/lang/String;");
        mw.visitVarInsn(ASTORE, context.var2(TYPE_NAME));

        Label typeNameNotNull = new Label();
        mw.visitVarInsn(ALOAD, context.var2(TYPE_NAME));
        mw.visitJumpInsn(IFNULL, typeNameNotNull);

        mw.visitVarInsn(ALOAD, 1);
        mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, GET_CONFIG, "()" + desc(ParserConfig.class));
        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, type(JavaBeanDeserializer.class), "beanInfo", desc(JavaBeanInfo.class));
        mw.visitVarInsn(ALOAD, context.var2(TYPE_NAME));
        mw.visitMethodInsn(INVOKESTATIC, type(JavaBeanDeserializer.class), "getSeeAlso"
                , "(" + desc(ParserConfig.class) + desc(JavaBeanInfo.class) + "Ljava/lang/String;)" + desc(JavaBeanDeserializer.class));
        mw.visitVarInsn(ASTORE, context.var2(USER_TYPE_DESER));
        mw.visitVarInsn(ALOAD, context.var2(USER_TYPE_DESER));
        mw.visitTypeInsn(INSTANCEOF, type(JavaBeanDeserializer.class));
        mw.visitJumpInsn(IFEQ, typeNameNotNull);

        mw.visitVarInsn(ALOAD, context.var2(USER_TYPE_DESER));
        mw.visitVarInsn(ALOAD, Context.PARSER);
        mw.visitVarInsn(ALOAD, 2);
        mw.visitVarInsn(ALOAD, 3);
        mw.visitVarInsn(ALOAD, 4);
        mw.visitMethodInsn(INVOKEVIRTUAL, //
                type(JavaBeanDeserializer.class), //
                DESERIALZE_ARRAY_MAPPING, //
                "(L" + DEFAULT_JSON_PARSER + LJAVA_LANG_REFLECT_TYPE_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT);
        mw.visitInsn(ARETURN);

        mw.visitLabel(typeNameNotNull);

        createInstance(context, mw);

        FieldInfo[] sortedFieldInfoList = context.beanInfo.sortedFields;
        int fieldListSize = sortedFieldInfoList.length;
        for (int i = 0; i < fieldListSize; ++i) {
            final boolean last = (i == fieldListSize - 1);
            final char seperator = extracted13(last);

            FieldInfo fieldInfo = sortedFieldInfoList[i];
            Class<?> fieldClass = fieldInfo.fieldClass;
            Type fieldType = fieldInfo.fieldType;
            extracted14(context, mw, i, seperator, fieldInfo, fieldClass, fieldType);
        }

        batchSet(context, mw, false);

        Label quickElse = new Label(); 
        Label quickElseIf = new Label(); 
        Label quickElseIfEOI = new Label();
        Label quickEnd = new Label();
        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, GET_CURRENT, "()C");
        mw.visitInsn(DUP);
        mw.visitVarInsn(ISTORE, context.var2("ch"));
        mw.visitVarInsn(BIPUSH, ',');
        mw.visitJumpInsn(IF_ICMPNE, quickElseIf);

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "next", "()C");
        mw.visitInsn(POP);
        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitLdcInsn(JSONToken.COMMA);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SET_TOKEN, "(I)V");
        mw.visitJumpInsn(GOTO, quickEnd);

        mw.visitLabel(quickElseIf);
        mw.visitVarInsn(ILOAD, context.var2("ch"));
        mw.visitVarInsn(BIPUSH, ']');
        mw.visitJumpInsn(IF_ICMPNE, quickElseIfEOI);

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "next", "()C");
        mw.visitInsn(POP);
        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitLdcInsn(JSONToken.RBRACKET);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SET_TOKEN, "(I)V");
        mw.visitJumpInsn(GOTO, quickEnd);

        mw.visitLabel(quickElseIfEOI);
        mw.visitVarInsn(ILOAD, context.var2("ch"));
        mw.visitVarInsn(BIPUSH, JSONLexer.EOI);
        mw.visitJumpInsn(IF_ICMPNE, quickElse);

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "next", "()C");
        mw.visitInsn(POP);
        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitLdcInsn(JSONToken.EOF);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SET_TOKEN, "(I)V");
        mw.visitJumpInsn(GOTO, quickEnd);

        mw.visitLabel(quickElse);
        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitLdcInsn(JSONToken.COMMA);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, NEXT_TOKEN, "(I)V");

        mw.visitLabel(quickEnd);

        mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
        mw.visitInsn(ARETURN);
        mw.visitMaxs(5, context.variantIndex);
        mw.visitEnd();
    }

    private void extracted14(Context context, MethodVisitor mw, int i, final char seperator,
                             FieldInfo fieldInfo, Class<?> fieldClass, Type fieldType) {
        extracted9(context, mw, seperator, fieldInfo, fieldClass);
        extracted10(context, mw, seperator, fieldInfo, fieldClass);
        extracted11(context, mw, seperator, fieldInfo, fieldClass);
        extracted12(context, mw, seperator, fieldInfo, fieldClass);
        if (fieldClass == long.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_LONG, "(C)J");
            mw.visitVarInsn(LSTORE, context.varAsm(fieldInfo, 2));

        } else if (fieldClass == Long.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_LONG, "(C)J");
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Long", VALUE_OF, "(J)Ljava/lang/Long;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);
        } else if (fieldClass == boolean.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanBoolean", "(C)Z");
            mw.visitVarInsn(ISTORE, context.varAsm(fieldInfo));
        } else if (fieldClass == float.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFloat", "(C)F");
            mw.visitVarInsn(FSTORE, context.varAsm(fieldInfo));

        } else if (fieldClass == Float.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFloat", "(C)F");
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Float", VALUE_OF, "(F)Ljava/lang/Float;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);

        } else if (fieldClass == double.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanDouble", "(C)D");
            mw.visitVarInsn(DSTORE, context.varAsm(fieldInfo, 2));

        } else if (fieldClass == Double.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanDouble", "(C)D");
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Double", VALUE_OF, "(D)Ljava/lang/Double;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);

        } else if (fieldClass == char.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanString", "(C)Ljava/lang/String;");
            mw.visitInsn(ICONST_0);
            mw.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C");
            mw.visitVarInsn(ISTORE, context.varAsm(fieldInfo));
        } else if (fieldClass == String.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanString", "(C)Ljava/lang/String;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

        } else if (fieldClass == BigDecimal.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanDecimal", "(C)Ljava/math/BigDecimal;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

        } else if (fieldClass == java.util.Date.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanDate", "(C)Ljava/util/Date;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

        } else if (fieldClass == java.util.UUID.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanUUID", "(C)Ljava/util/UUID;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

        } else if (fieldClass.isEnum()) {
            Label enumNumIf = new Label();
            Label enumNumErr = new Label();
            Label enumStore = new Label();
            Label enumQuote = new Label();

            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, GET_CURRENT, "()C");
            mw.visitInsn(DUP);
            mw.visitVarInsn(ISTORE, context.var2("ch"));
            mw.visitLdcInsn((int) 'n');
            mw.visitJumpInsn(IF_ICMPEQ, enumQuote);

            mw.visitVarInsn(ILOAD, context.var2("ch"));
            mw.visitLdcInsn((int) '\"');
            mw.visitJumpInsn(IF_ICMPNE, enumNumIf);

            mw.visitLabel(enumQuote);
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldClass)));
            mw.visitVarInsn(ALOAD, 1);
            mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, "getSymbolTable", "()" + desc(SymbolTable.class));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_ENUM,
                    "(Ljava/lang/Class;" + desc(SymbolTable.class) + "C)Ljava/lang/Enum;");
            mw.visitJumpInsn(GOTO, enumStore);


            mw.visitLabel(enumNumIf);
            mw.visitVarInsn(ILOAD, context.var2("ch"));
            mw.visitLdcInsn((int) '0');
            mw.visitJumpInsn(IF_ICMPLT, enumNumErr);

            mw.visitVarInsn(ILOAD, context.var2("ch"));
            mw.visitLdcInsn((int) '9');
            mw.visitJumpInsn(IF_ICMPGT, enumNumErr);

            getFieldDeser(context, mw, fieldInfo);
            mw.visitTypeInsn(CHECKCAST, type(EnumDeserializer.class)); // cast
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_INT, "(C)I");
            mw.visitMethodInsn(INVOKEVIRTUAL, type(EnumDeserializer.class), VALUE_OF, "(I)Ljava/lang/Enum;");
            mw.visitJumpInsn(GOTO, enumStore);

            mw.visitLabel(enumNumErr);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, type(JavaBeanDeserializer.class), SCAN_ENUM,
                    "(L" + JSON_LEXER_BASE + ";C)Ljava/lang/Enum;");

            mw.visitLabel(enumStore);
            mw.visitTypeInsn(CHECKCAST, type(fieldClass)); // cast
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
        } else if (Collection.class.isAssignableFrom(fieldClass)) {

            Class<?> itemClass = TypeUtils.getCollectionItemClass(fieldType);
            extracted6(context, mw, i, seperator, fieldInfo, fieldClass, itemClass);
        } else if (fieldClass.isArray()) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONToken.LBRACKET);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, NEXT_TOKEN, "(I)V");

            mw.visitVarInsn(ALOAD, Context.PARSER);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitLdcInsn(i);
            mw.visitMethodInsn(INVOKEVIRTUAL, type(JavaBeanDeserializer.class), GET_FIELD_TYPE,
                    I_LJAVA_LANG_REFLECT_TYPE);
            mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, "parseObject",
                    "(Ljava/lang/reflect/Type;)Ljava/lang/Object;");

            mw.visitTypeInsn(CHECKCAST, type(fieldClass)); // cast
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
        }
    }

    private char extracted13(final boolean last) {
        return last ? ']' : ',';
    }

    private void extracted12(Context context, MethodVisitor mw, final char seperator, FieldInfo fieldInfo,
                             Class<?> fieldClass) {
        if (fieldClass == Integer.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_INT, "(C)I");
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_INTEGER, VALUE_OF, I_LJAVA_LANG_INTEGER);

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);
        }
    }

    private void extracted11(Context context, MethodVisitor mw, final char seperator, FieldInfo fieldInfo,
                             Class<?> fieldClass) {
        if (fieldClass == Short.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_INT, "(C)I");
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Short", VALUE_OF, "(S)Ljava/lang/Short;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);
        }
    }

    private void extracted10(Context context, MethodVisitor mw, final char seperator, FieldInfo fieldInfo,
                             Class<?> fieldClass) {
        if (fieldClass == Byte.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_INT, "(C)I");
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", VALUE_OF, "(B)Ljava/lang/Byte;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);
        }
    }

    private void extracted9(Context context, MethodVisitor mw, final char seperator, FieldInfo fieldInfo,
                            Class<?> fieldClass) {
        if (extracted3(fieldClass)) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_INT, "(C)I");
            mw.visitVarInsn(ISTORE, context.varAsm(fieldInfo));
        }
    }

    private void extracted6(Context context, MethodVisitor mw, int i, final char seperator, FieldInfo fieldInfo,
                            Class<?> fieldClass, Class<?> itemClass) {
        if (itemClass == String.class) {
            extracted5(mw, fieldClass);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, context.varAsm(fieldInfo));
            mw.visitVarInsn(BIPUSH, seperator);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanStringArray", "(Ljava/util/Collection;C)V");

            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

            mw.visitLabel(valueNullEnd);

        } else {
            Label notError = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, TOKEN, "()I");
            mw.visitVarInsn(ISTORE, context.var2(TOKEN));

            mw.visitVarInsn(ILOAD, context.var2(TOKEN));
            int token = i == 0 ? JSONToken.LBRACKET : JSONToken.COMMA;
            mw.visitLdcInsn(token);
            mw.visitJumpInsn(IF_ICMPEQ, notError);

            mw.visitVarInsn(ALOAD, 1); // DefaultJSONParser
            mw.visitLdcInsn(token);
            mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, "throwException", "(I)V");

            mw.visitLabel(notError);

            Label quickElse1 = new Label();
            Label quickEnd1 = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, GET_CURRENT, "()C");
            mw.visitVarInsn(BIPUSH, '[');
            mw.visitJumpInsn(IF_ICMPNE, quickElse1);

            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "next", "()C");
            mw.visitInsn(POP);
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitLdcInsn(JSONToken.LBRACKET);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SET_TOKEN, "(I)V");
            mw.visitJumpInsn(GOTO, quickEnd1);

            mw.visitLabel(quickElse1);
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitLdcInsn(JSONToken.LBRACKET);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, NEXT_TOKEN, "(I)V");
            mw.visitLabel(quickEnd1);

            newCollection(mw, fieldClass, i, false);
            mw.visitInsn(DUP);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            getCollectionFieldItemDeser(context, mw, fieldInfo, itemClass);
            mw.visitVarInsn(ALOAD, 1);
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(itemClass)));
            mw.visitVarInsn(ALOAD, 3);
            mw.visitMethodInsn(INVOKESTATIC, type(JavaBeanDeserializer.class),
                    "parseArray",
                    "(Ljava/util/Collection;" //
                            + desc(ObjectDeserializer.class) //
                            + "L" + DEFAULT_JSON_PARSER + ";" //
                            + "Ljava/lang/reflect/Type;Ljava/lang/Object;)V");
        }
    }

    private void extracted5(MethodVisitor mw, Class<?> fieldClass) {
        if (extracted4(fieldClass)) {
            mw.visitTypeInsn(NEW, type(ArrayList.class));
            mw.visitInsn(DUP);
            mw.visitMethodInsn(INVOKESPECIAL, type(ArrayList.class), INIT, "()V");
        } else {
            mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldClass)));
            mw.visitMethodInsn(INVOKESTATIC, type(TypeUtils.class), "createCollection",
                    "(Ljava/lang/Class;)Ljava/util/Collection;");
        }
    }

    private boolean extracted4(Class<?> fieldClass) {
        return fieldClass == List.class
                || fieldClass == Collections.class
                || fieldClass == ArrayList.class;
    }

    private boolean extracted3(Class<?> fieldClass) {
        return fieldClass == byte.class //
                || fieldClass == short.class //
                || fieldClass == int.class;
    }

    private void deserialze(ClassWriter cw, Context context) {
        JavaBeanInfo beanInfo = context.beanInfo;
        context.fieldInfoList = beanInfo.sortedFields;

        MethodVisitor mw = new MethodWriter(cw, ACC_PUBLIC, DESERIALIZE,
                "(L" + DEFAULT_JSON_PARSER + LJAVA_LANG_REFLECT_TYPE_LJAVA_LANG_OBJECT_I_LJAVA_LANG_OBJECT, null);

        Label reset = new Label();
        Label super1 = new Label();
        Label return1 = new Label();
        Label end = new Label();

        defineVarLexer(context, mw);

        
        extracted26(context, beanInfo, mw);
        

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitLdcInsn(Feature.SORT_FEID_FAST_MATCH.mask);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, IS_ENABLED, "(I)Z");

        Label continue1 = new Label();
        mw.visitJumpInsn(IFNE, continue1);
        mw.visitJumpInsn(GOTO_W, super1);
        mw.visitLabel(continue1);

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitLdcInsn(context.clazz.getName());
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanType", "(Ljava/lang/String;)I");

        mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.NOT_MATCH);

        Label continue2 = new Label();
        mw.visitJumpInsn(IF_ICMPNE, continue2);
        mw.visitJumpInsn(GOTO_W, super1);
        mw.visitLabel(continue2);

        mw.visitVarInsn(ALOAD, 1); // parser
        mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, GET_CONTEXT, "()" + desc(ParseContext.class));
        mw.visitVarInsn(ASTORE, context.var2("mark_context"));


        mw.visitInsn(ICONST_0);
        mw.visitVarInsn(ISTORE, context.var2(MATCHED_COUNT));

        createInstance(context, mw);

        
        extracted39(context, mw);
        

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
        mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.END);


        Label continue3 = new Label();
        mw.visitJumpInsn(IF_ICMPNE, continue3);
        mw.visitJumpInsn(GOTO_W, return1);
        mw.visitLabel(continue3);

        mw.visitInsn(ICONST_0); // UNKOWN
        mw.visitIntInsn(ISTORE, context.var2(MATCH_STAT));

        int fieldListSize = context.fieldInfoList.length;
        extracted8(context, mw, fieldListSize);

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitLdcInsn(Feature.INIT_STRING_FIELD_AS_EMPTY.mask);
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, IS_ENABLED, "(I)Z");
        mw.visitIntInsn(ISTORE, context.var2("INIT_STRING_FIELD_AS_EMPTY"));

        // declare and init
        extracted21(context, mw, fieldListSize);

        for (int i = 0; i < fieldListSize; ++i) {
            FieldInfo fieldInfo = context.fieldInfoList[i];
            Class<?> fieldClass = fieldInfo.fieldClass;

            Label notMatch = new Label();

            extracted22(context, mw, fieldInfo, fieldClass);
            extracted23(context, mw, fieldInfo, fieldClass);
            extracted24(context, mw, fieldInfo, fieldClass);
            extracted25(context, mw, fieldInfo, fieldClass);
            extracted35(context, mw, fieldInfo, fieldClass);
            extracted36(context, mw, fieldInfo, fieldClass);
            extracted37(context, mw, fieldInfo, fieldClass);
            extracted38(context, mw, fieldInfo, fieldClass);

            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            Label flag = new Label();

            mw.visitJumpInsn(IFLE, flag);
            setFlag(mw, context, i);
            mw.visitLabel(flag);

            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitInsn(DUP);
            mw.visitVarInsn(ISTORE, context.var2(MATCH_STAT));

            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.NOT_MATCH);
            mw.visitJumpInsn(IF_ICMPEQ, reset);

            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitJumpInsn(IFLE, notMatch);

            // increment matchedCount
            mw.visitVarInsn(ILOAD, context.var2(MATCHED_COUNT));
            mw.visitInsn(ICONST_1);
            mw.visitInsn(IADD);
            mw.visitVarInsn(ISTORE, context.var2(MATCHED_COUNT));

            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.END);
            mw.visitJumpInsn(IF_ICMPEQ, end);

            mw.visitLabel(notMatch);

            extracted27(context, mw, reset, fieldListSize, i);
        } // endFor

        mw.visitLabel(end);

        extracted34(context, mw);

        mw.visitLabel(return1);

        setContext(context, mw);
        mw.visitVarInsn(ALOAD, context.var2(INSTANCE));

        Method buildMethod = context.beanInfo.buildMethod;
        extracted29(context, mw, buildMethod);

        mw.visitInsn(ARETURN);

        mw.visitLabel(reset);

        batchSet(context, mw);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitVarInsn(ALOAD, 1);
        mw.visitVarInsn(ALOAD, 2);
        mw.visitVarInsn(ALOAD, 3);
        mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
        mw.visitVarInsn(ILOAD, 4);


        int flagSize = (fieldListSize / 32);

        flagSize = extracted33(fieldListSize, flagSize);

        extracted32(mw, flagSize);
        mw.visitIntInsn(NEWARRAY, T_INT);
        extracted31(context, mw, flagSize);

        mw.visitMethodInsn(INVOKEVIRTUAL, type(JavaBeanDeserializer.class),
                "parseRest", "(L" + DEFAULT_JSON_PARSER
                        + ";Ljava/lang/reflect/Type;Ljava/lang/Object;Ljava/lang/Object;I[I)Ljava/lang/Object;");
        mw.visitTypeInsn(CHECKCAST, type(context.clazz)); // cast
        mw.visitInsn(ARETURN);

        mw.visitLabel(super1);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitVarInsn(ALOAD, 1);
        mw.visitVarInsn(ALOAD, 2);
        mw.visitVarInsn(ALOAD, 3);
        mw.visitVarInsn(ILOAD, 4);
        mw.visitMethodInsn(INVOKESPECIAL, type(JavaBeanDeserializer.class), //
                DESERIALIZE, //
                "(L" + DEFAULT_JSON_PARSER + LJAVA_LANG_REFLECT_TYPE_LJAVA_LANG_OBJECT_I_LJAVA_LANG_OBJECT);
        mw.visitInsn(ARETURN);

        mw.visitMaxs(10, context.variantIndex);
        mw.visitEnd();

    }

    private void extracted39(Context context, MethodVisitor mw) {
        mw.visitVarInsn(ALOAD, 1); // parser
        mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, GET_CONTEXT, "()" + desc(ParseContext.class));
        mw.visitVarInsn(ASTORE, context.var2(CONTEXT));

        mw.visitVarInsn(ALOAD, 1); // parser
        mw.visitVarInsn(ALOAD, context.var2(CONTEXT));
        mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
        mw.visitVarInsn(ALOAD, 3); // fieldName
        mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, SET_CONTEXT, //
                "(" + desc(ParseContext.class) + "Ljava/lang/Object;Ljava/lang/Object;)"
                        + desc(ParseContext.class));
        mw.visitVarInsn(ASTORE, context.var2(CHILD_CONTEXT));
    }

    private void extracted26(Context context, JavaBeanInfo beanInfo, MethodVisitor mw) {
        Label next = new Label();

        // isSUPPORT_ARRAY_TO_BEAN

        mw.visitVarInsn(ALOAD, context.var2(LEXER));
        mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, TOKEN, "()I");
        mw.visitLdcInsn(JSONToken.LBRACKET);
        mw.visitJumpInsn(IF_ICMPNE, next);

        extracted7(context, beanInfo, mw, next);

        mw.visitVarInsn(ALOAD, 0);
        mw.visitVarInsn(ALOAD, Context.PARSER);
        mw.visitVarInsn(ALOAD, 2);
        mw.visitVarInsn(ALOAD, 3);
        mw.visitInsn(ACONST_NULL);
        mw.visitMethodInsn(INVOKESPECIAL, //
                context.className, //
                DESERIALZE_ARRAY_MAPPING, //
                "(L" + DEFAULT_JSON_PARSER + LJAVA_LANG_REFLECT_TYPE_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT_LJAVA_LANG_OBJECT);
        mw.visitInsn(ARETURN);

        mw.visitLabel(next);
        // deserialzeArrayMapping
    }

    private void extracted38(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == long.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldLong", "([C)J");
            mw.visitVarInsn(LSTORE, context.varAsm(fieldInfo, 2));

        } else if (fieldClass == Long.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldLong", "([C)J");
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Long", VALUE_OF, "(J)Ljava/lang/Long;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);

        } else if (fieldClass == float.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldFloat", "([C)F");
            mw.visitVarInsn(FSTORE, context.varAsm(fieldInfo));

        } else if (fieldClass == Float.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldFloat", "([C)F");
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Float", VALUE_OF, "(F)Ljava/lang/Float;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);
        } else if (fieldClass == double.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldDouble", "([C)D");
            mw.visitVarInsn(DSTORE, context.varAsm(fieldInfo, 2));

        } else if (fieldClass == Double.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldDouble", "([C)D");
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Double", VALUE_OF, "(D)Ljava/lang/Double;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

            mw.visitLabel(valueNullEnd);
        } else if (fieldClass == String.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldString", "([C)Ljava/lang/String;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

        } else if (fieldClass == java.util.Date.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldDate", "([C)Ljava/util/Date;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

        } else if (fieldClass == java.util.UUID.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldUUID", "([C)Ljava/util/UUID;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));

        } else if (fieldClass == BigDecimal.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldDecimal", "([C)Ljava/math/BigDecimal;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
        } else if (fieldClass == BigInteger.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldBigInteger", "([C)Ljava/math/BigInteger;");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
        } else if (fieldClass == int[].class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldIntArray", "([C)[I");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
        } else if (fieldClass == float[].class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldFloatArray", "([C)[F");
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
        }
    }

    private void extracted37(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == Integer.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_FIELD_INT, OPEN_C_I);
            mw.visitMethodInsn(INVOKESTATIC, JAVA_LANG_INTEGER, VALUE_OF, I_LJAVA_LANG_INTEGER);

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);

        }
    }

    private void extracted36(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == int.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_FIELD_INT, OPEN_C_I);
            mw.visitVarInsn(ISTORE, context.varAsm(fieldInfo));

        }
    }

    private void extracted35(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == Short.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_FIELD_INT, OPEN_C_I);
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Short", VALUE_OF, "(S)Ljava/lang/Short;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);

        }
    }

    private void extracted34(Context context, MethodVisitor mw) {
        if (extracted28(context)) {
            batchSet(context, mw);
        }
    }

    private int extracted33(int fieldListSize, int flagSize) {
        if (fieldListSize != 0 && (fieldListSize % 32) != 0) {
            flagSize += 1;
        }
        return flagSize;
    }

    private void extracted32(MethodVisitor mw, int flagSize) {
        if (flagSize == 1) {
            mw.visitInsn(ICONST_1);
        } else {
            mw.visitIntInsn(BIPUSH, flagSize);
        }
    }

    private void extracted31(Context context, MethodVisitor mw, int flagSize) {
        for (int i = 0; i < flagSize; ++i) {
            mw.visitInsn(DUP);
            extracted30(mw, i);
            mw.visitVarInsn(ILOAD, context.var2(ASM_FLAG + i));
            mw.visitInsn(IASTORE);
        }
    }

    private void extracted30(MethodVisitor mw, int i) {
        if (i == 0) {
            mw.visitInsn(ICONST_0);
        } else
            extracted32(mw, i);
    }

    private void extracted29(Context context, MethodVisitor mw, Method buildMethod) {
        if (buildMethod != null) {
            mw.visitMethodInsn(INVOKEVIRTUAL, type(context.getInstClass()), buildMethod.getName(),
                    "()" + desc(buildMethod.getReturnType()));
        }
    }

    private boolean extracted28(Context context) {
        return !context.clazz.isInterface() && !Modifier.isAbstract(context.clazz.getModifiers());
    }

    private void extracted27(Context context, MethodVisitor mw, Label reset, int fieldListSize, int i) {
        if (i == fieldListSize - 1) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.END);
            mw.visitJumpInsn(IF_ICMPNE, reset);
        }
    }

    private void extracted25(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == short.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_FIELD_INT, OPEN_C_I);
            mw.visitVarInsn(ISTORE, context.varAsm(fieldInfo));

        }
    }

    private void extracted24(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == Byte.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_FIELD_INT, OPEN_C_I);
            mw.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", VALUE_OF, "(B)Ljava/lang/Byte;");

            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            Label valueNullEnd = new Label();
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitFieldInsn(GETFIELD, JSON_LEXER_BASE, MATCH_STAT, "I");
            mw.visitLdcInsn(com.alibaba.fastjson.parser.JSONLexer.VALUE_NULL);
            mw.visitJumpInsn(IF_ICMPNE, valueNullEnd);
            mw.visitInsn(ACONST_NULL);
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
            mw.visitLabel(valueNullEnd);

        }
    }

    private void extracted23(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == byte.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, SCAN_FIELD_INT, OPEN_C_I);
            mw.visitVarInsn(ISTORE, context.varAsm(fieldInfo));

        }
    }

    private void extracted22(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == boolean.class) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, context.className, context.fieldName(fieldInfo), "[C");
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "scanFieldBoolean", OPEN_C_Z);
            mw.visitVarInsn(ISTORE, context.varAsm(fieldInfo));
        }
    }

    private void extracted21(Context context, MethodVisitor mw, int fieldListSize) {
        for (int i = 0; i < fieldListSize; ++i) {
            FieldInfo fieldInfo = context.fieldInfoList[i];
            Class<?> fieldClass = fieldInfo.fieldClass;

            extracted16(context, mw, fieldInfo, fieldClass);
            extracted17(context, mw, fieldInfo, fieldClass);
            extracted19(context, mw, fieldInfo, fieldClass);
            extracted20(context, mw, i, fieldInfo, fieldClass);
        }
    }

    private void extracted20(Context context, MethodVisitor mw, int i, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == double.class) {
            mw.visitInsn(DCONST_0);
            mw.visitVarInsn(DSTORE, context.varAsm(fieldInfo, 2));
        } else {
            extracted18(context, mw, i, fieldClass);

            mw.visitTypeInsn(CHECKCAST, type(fieldClass)); // cast
            mw.visitVarInsn(ASTORE, context.varAsm(fieldInfo));
        }
    }

    private void extracted19(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == float.class) {
            mw.visitInsn(FCONST_0);
            mw.visitVarInsn(FSTORE, context.varAsm(fieldInfo));
        }
    }

    private void extracted18(Context context, MethodVisitor mw, int i, Class<?> fieldClass) {
        if (fieldClass == String.class) {
            Label flagEnd = new Label();
            Label flagElse = new Label();
            mw.visitVarInsn(ILOAD, context.var2("INIT_STRING_FIELD_AS_EMPTY"));
            mw.visitJumpInsn(IFEQ, flagElse);
            setFlag(mw, context, i);
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, "stringDefaultValue", "()Ljava/lang/String;");
            mw.visitJumpInsn(GOTO, flagEnd);

            mw.visitLabel(flagElse);
            mw.visitInsn(ACONST_NULL);

            mw.visitLabel(flagEnd);
        } else {
            mw.visitInsn(ACONST_NULL);
        }
    }

    private void extracted17(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (fieldClass == long.class) {
            mw.visitInsn(LCONST_0);
            mw.visitVarInsn(LSTORE, context.varAsm(fieldInfo, 2));
        }
    }

    private void extracted16(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass) {
        if (extracted15(fieldClass)) {
            mw.visitInsn(ICONST_0);
            mw.visitVarInsn(ISTORE, context.varAsm(fieldInfo));
        }
    }

    private boolean extracted15(Class<?> fieldClass) {
        return fieldClass == boolean.class //
                || fieldClass == byte.class //
                || fieldClass == short.class //
                || fieldClass == int.class;
    }

    private void extracted8(Context context, MethodVisitor mw, int fieldListSize) {
        for (int i = 0; i < fieldListSize; i += 32) {
            mw.visitInsn(ICONST_0);
            mw.visitVarInsn(ISTORE, context.var2(ASM_FLAG + (i / 32)));
        }
    }

    private void extracted7(Context context, JavaBeanInfo beanInfo, MethodVisitor mw, Label next) {
        if ((beanInfo.parserFeatures & Feature.SUPPORT_ARRAY_TO_BEAN.mask) == 0) {
            mw.visitVarInsn(ALOAD, context.var2(LEXER));
            mw.visitVarInsn(ILOAD, 4);
            mw.visitLdcInsn(Feature.SUPPORT_ARRAY_TO_BEAN.mask);
            mw.visitMethodInsn(INVOKEVIRTUAL, JSON_LEXER_BASE, IS_ENABLED, "(II)Z");
            mw.visitJumpInsn(IFEQ, next);
        }
    }

    private void defineVarLexer(Context context, MethodVisitor mw) {
        mw.visitVarInsn(ALOAD, 1);
        mw.visitFieldInsn(GETFIELD, DEFAULT_JSON_PARSER, LEXER, desc(JSONLexer.class));
        mw.visitTypeInsn(CHECKCAST, JSON_LEXER_BASE); // cast
        mw.visitVarInsn(ASTORE, context.var2(LEXER));
    }

    private void createInstance(Context context, MethodVisitor mw) {
        JavaBeanInfo beanInfo = context.beanInfo;
        Constructor<?> defaultConstructor = beanInfo.defaultConstructor;
        if (Modifier.isPublic(defaultConstructor.getModifiers())) {
            mw.visitTypeInsn(NEW, type(context.getInstClass()));
            mw.visitInsn(DUP);

            mw.visitMethodInsn(INVOKESPECIAL, type(defaultConstructor.getDeclaringClass()), INIT, "()V");
        } else {
            mw.visitVarInsn(ALOAD, 0);
            mw.visitVarInsn(ALOAD, 1);
            mw.visitVarInsn(ALOAD, 0);
            mw.visitFieldInsn(GETFIELD, type(JavaBeanDeserializer.class), "clazz", "Ljava/lang/Class;");
            mw.visitMethodInsn(INVOKESPECIAL, type(JavaBeanDeserializer.class), "createInstance",
                    "(L" + DEFAULT_JSON_PARSER + ";Ljava/lang/reflect/Type;)Ljava/lang/Object;");
            mw.visitTypeInsn(CHECKCAST, type(context.getInstClass())); // cast
        }

        mw.visitVarInsn(ASTORE, context.var2(INSTANCE));
    }

    private void batchSet(Context context, MethodVisitor mw) {
        batchSet(context, mw, true);
    }

    private void batchSet(Context context, MethodVisitor mw, boolean flag) {
        for (int i = 0, size = context.fieldInfoList.length; i < size; ++i) {
            Label notSet = new Label();

            if (flag) {
                isFlag(mw, context, i, notSet);
            }

            FieldInfo fieldInfo = context.fieldInfoList[i];
            loadAndSet(context, mw, fieldInfo);

            if (flag) {
                mw.visitLabel(notSet);
            }
        }
    }

    private void loadAndSet(Context context, MethodVisitor mw, FieldInfo fieldInfo) {
        Class<?> fieldClass = fieldInfo.fieldClass;
        Type fieldType = fieldInfo.fieldType;

        if (fieldClass == boolean.class) {
            mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
            mw.visitVarInsn(ILOAD, context.varAsm(fieldInfo));
            set1(mw, fieldInfo);
        }else if (fieldClass == long.class) {
            mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
            mw.visitVarInsn(LLOAD, context.varAsm(fieldInfo, 2));
            if (fieldInfo.method != null) {
                mw.visitMethodInsn(INVOKEVIRTUAL, type(context.getInstClass()), fieldInfo.method.getName(),
                        desc(fieldInfo.method));
                extracted(mw, fieldInfo);
            } else {
                mw.visitFieldInsn(PUTFIELD, type(fieldInfo.declaringClass), fieldInfo.field.getName(),
                        desc(fieldInfo.fieldClass));
            }
        } else if (fieldClass == float.class) {
            mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
            mw.visitVarInsn(FLOAD, context.varAsm(fieldInfo));
            set1(mw, fieldInfo);
        } else if (fieldClass == double.class) {
            mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
            mw.visitVarInsn(DLOAD, context.varAsm(fieldInfo, 2));
            set1(mw, fieldInfo);
        } else if (fieldClass == String.class) {
            mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
            mw.visitVarInsn(ALOAD, context.varAsm(fieldInfo));
            set1(mw, fieldInfo);
        }else if (Collection.class.isAssignableFrom(fieldClass)) {
            mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
            Type itemType = TypeUtils.getCollectionItemClass(fieldType);
            extracted2(context, mw, fieldInfo, fieldClass, itemType);

        }
    }

    private void extracted2(Context context, MethodVisitor mw, FieldInfo fieldInfo, Class<?> fieldClass,
                            Type itemType) {
        if (itemType == String.class) {
            mw.visitVarInsn(ALOAD, context.varAsm(fieldInfo));
            mw.visitTypeInsn(CHECKCAST, type(fieldClass)); // cast
        } else {
            mw.visitVarInsn(ALOAD, context.varAsm(fieldInfo));
        }
        set1(mw, fieldInfo);
    }

    private void extracted(MethodVisitor mw, FieldInfo fieldInfo) {
        if (!fieldInfo.method.getReturnType().equals(Void.TYPE)) {
            mw.visitInsn(POP);
        }
    }

    private void set1(MethodVisitor mw, FieldInfo fieldInfo) {
        Method method = fieldInfo.method;
        if (method != null) {
            Class<?> declaringClass = method.getDeclaringClass();
            mw.visitMethodInsn(declaringClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, type(fieldInfo.declaringClass), method.getName(), desc(method));

            extracted(mw, fieldInfo);
        } else {
            mw.visitFieldInsn(PUTFIELD, type(fieldInfo.declaringClass), fieldInfo.field.getName(),
                    desc(fieldInfo.fieldClass));
        }
    }

    private void setContext(Context context, MethodVisitor mw) { 
        mw.visitVarInsn(ALOAD, 1); // parser
        mw.visitVarInsn(ALOAD, context.var2(CONTEXT));
        mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, SET_CONTEXT, "(" + desc(ParseContext.class) + ")V");

        Label endIf = new Label();
        mw.visitVarInsn(ALOAD, context.var2(CHILD_CONTEXT));
        mw.visitJumpInsn(IFNULL, endIf);

        mw.visitVarInsn(ALOAD, context.var2(CHILD_CONTEXT));
        mw.visitVarInsn(ALOAD, context.var2(INSTANCE));
        mw.visitFieldInsn(PUTFIELD, type(ParseContext.class), "object", "Ljava/lang/Object;");

        mw.visitLabel(endIf);
    }

    private void getCollectionFieldItemDeser(Context context, MethodVisitor mw, FieldInfo fieldInfo,
                                              Class<?> itemType) {
        Label notNull = new Label();
        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_LIST_ITEM_DESER,
                desc(ObjectDeserializer.class));
        mw.visitJumpInsn(IFNONNULL, notNull);

        mw.visitVarInsn(ALOAD, 0);

        mw.visitVarInsn(ALOAD, 1);
        mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, GET_CONFIG, "()" + desc(ParserConfig.class));
        mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(itemType)));
        mw.visitMethodInsn(INVOKEVIRTUAL, type(ParserConfig.class), "getDeserializer",
                "(Ljava/lang/reflect/Type;)" + desc(ObjectDeserializer.class));

        mw.visitFieldInsn(PUTFIELD, context.className, fieldInfo.name + ASM_LIST_ITEM_DESER,
                desc(ObjectDeserializer.class));

        mw.visitLabel(notNull);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, context.className, fieldInfo.name + ASM_LIST_ITEM_DESER,
                desc(ObjectDeserializer.class));
    }

    private void newCollection(MethodVisitor mw, Class<?> fieldClass, int i, boolean set) {
        if (fieldClass.isAssignableFrom(ArrayList.class) && !set) {
            mw.visitTypeInsn(NEW, "java/util/ArrayList");
            mw.visitInsn(DUP);
            mw.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", INIT, "()V");
        } else if (fieldClass.isAssignableFrom(LinkedList.class) && !set) {
            mw.visitTypeInsn(NEW, type(LinkedList.class));
            mw.visitInsn(DUP);
            mw.visitMethodInsn(INVOKESPECIAL, type(LinkedList.class), INIT, "()V");
        } else if (fieldClass.isAssignableFrom(HashSet.class)) {
            mw.visitTypeInsn(NEW, type(HashSet.class));
            mw.visitInsn(DUP);
            mw.visitMethodInsn(INVOKESPECIAL, type(HashSet.class), INIT, "()V");
        } else if (fieldClass.isAssignableFrom(TreeSet.class)) {
            mw.visitTypeInsn(NEW, type(TreeSet.class));
            mw.visitInsn(DUP);
            mw.visitMethodInsn(INVOKESPECIAL, type(TreeSet.class), INIT, "()V");
        } else if (fieldClass.isAssignableFrom(LinkedHashSet.class)) {
            mw.visitTypeInsn(NEW, type(LinkedHashSet.class));
            mw.visitInsn(DUP);
            mw.visitMethodInsn(INVOKESPECIAL, type(LinkedHashSet.class), INIT, "()V");
        }else {
            mw.visitVarInsn(ALOAD, 0);
            mw.visitLdcInsn(i);
            mw.visitMethodInsn(INVOKEVIRTUAL, type(JavaBeanDeserializer.class), GET_FIELD_TYPE,
                    I_LJAVA_LANG_REFLECT_TYPE);
            mw.visitMethodInsn(INVOKESTATIC, type(TypeUtils.class), "createCollection",
                    "(Ljava/lang/reflect/Type;)Ljava/util/Collection;");
        }
        mw.visitTypeInsn(CHECKCAST, type(fieldClass)); // cast
    }

    private void getFieldDeser(Context context, MethodVisitor mw, FieldInfo fieldInfo) {
        Label notNull = new Label();
        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, context.className, context.fieldDeserName(fieldInfo), desc(ObjectDeserializer.class));
        mw.visitJumpInsn(IFNONNULL, notNull);

        mw.visitVarInsn(ALOAD, 0);

        mw.visitVarInsn(ALOAD, 1);
        mw.visitMethodInsn(INVOKEVIRTUAL, DEFAULT_JSON_PARSER, GET_CONFIG, "()" + desc(ParserConfig.class));
        mw.visitLdcInsn(com.alibaba.fastjson.asm.Type.getType(desc(fieldInfo.fieldClass)));
        mw.visitMethodInsn(INVOKEVIRTUAL, type(ParserConfig.class), "getDeserializer",
                "(Ljava/lang/reflect/Type;)" + desc(ObjectDeserializer.class));

        mw.visitFieldInsn(PUTFIELD, context.className, context.fieldDeserName(fieldInfo), desc(ObjectDeserializer.class));

        mw.visitLabel(notNull);

        mw.visitVarInsn(ALOAD, 0);
        mw.visitFieldInsn(GETFIELD, context.className, context.fieldDeserName(fieldInfo), desc(ObjectDeserializer.class));
    }

    static class Context {

        static final int                   PARSER       = 1;
        static final int                   TYPE         = 2;
        static final int                   FIELD_NAME   = 3;

        private int                        variantIndex = -1;
        private final Map<String, Integer> variants     = new HashMap<>();

        private final Class<?>             clazz;
        private final JavaBeanInfo         beanInfo;
        private final String               className;
        private FieldInfo[]                fieldInfoList;

        public Context(String className, JavaBeanInfo beanInfo, int initVariantIndex){
            this.className = className;
            this.clazz = beanInfo.clazz;
            this.variantIndex = initVariantIndex;
            this.beanInfo = beanInfo;
            fieldInfoList = beanInfo.fields;
        }

        public Class<?> getInstClass() {
            Class<?> instClass = beanInfo.builderClass;
            if (instClass == null) {
                instClass = clazz;
            }

            return instClass;
        }

        public int var1(String name, int increment) {
            Integer i = variants.get(name);
            if (i == null) {
                variants.put(name, variantIndex);
                variantIndex += increment;
            }
            i = variants.get(name);
            return i.intValue();
        }

        public int var2(String name) {
            Integer i = variants.get(name);
            if (i == null) {
                variants.put(name, variantIndex++);
            }
            i = variants.get(name);
            return i.intValue();
        }

        public int varAsm(FieldInfo fieldInfo) {
            return var2(fieldInfo.name + "_asm");
        }

        public int varAsm(FieldInfo fieldInfo, int increment) {
            return var1(fieldInfo.name + "_asm", increment);
        }

        public String fieldName(FieldInfo fieldInfo) {
            return validIdent(fieldInfo.name)
                    ? fieldInfo.name + "_asm_prefix__"
                    : "asm_field_" + TypeUtils.fnv1a64Extract(fieldInfo.name);
        }


        public String fieldDeserName(FieldInfo fieldInfo) {
            return validIdent(fieldInfo.name)
                    ? fieldInfo.name + "_asm_deser__"
                    : "_asm_deser__" + TypeUtils.fnv1a64Extract(fieldInfo.name);
        }


        boolean validIdent(String name) {
            for (int i = 0; i < name.length(); ++i) {
                char ch = name.charAt(i);
                if (ch == 0) {
                    if (!IOUtils.firstIdentifier(ch)) {
                        return false;
                    }
                } else {
                    if (!IOUtils.isIdent(ch)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    private void init(ClassWriter cw, Context context) {
        for (int i = 0, size = context.fieldInfoList.length; i < size; ++i) {
            FieldInfo fieldInfo = context.fieldInfoList[i];

            FieldWriter fw = new FieldWriter(cw, ACC_PUBLIC, context.fieldName(fieldInfo), "[C");
            fw.visitEnd();
        }

        for (int i = 0, size = context.fieldInfoList.length; i < size; ++i) {
            FieldInfo fieldInfo = context.fieldInfoList[i];
            Class<?> fieldClass = fieldInfo.fieldClass;

            if (fieldClass.isPrimitive()) {
                continue;
            }

            if (Collection.class.isAssignableFrom(fieldClass)) {
                FieldWriter fw = new FieldWriter(cw, ACC_PUBLIC, fieldInfo.name + ASM_LIST_ITEM_DESER,
                        desc(ObjectDeserializer.class));
                fw.visitEnd();
            } else {
                FieldWriter fw = new FieldWriter(cw, ACC_PUBLIC, context.fieldDeserName(fieldInfo),
                        desc(ObjectDeserializer.class));
                fw.visitEnd();
            }
        }

        MethodVisitor mw = new MethodWriter(cw, ACC_PUBLIC, INIT,
                "(" + desc(ParserConfig.class) + desc(JavaBeanInfo.class) + ")V", null);
        mw.visitVarInsn(ALOAD, 0);
        mw.visitVarInsn(ALOAD, 1);
        mw.visitVarInsn(ALOAD, 2);
        mw.visitMethodInsn(INVOKESPECIAL, type(JavaBeanDeserializer.class), INIT,
                "(" + desc(ParserConfig.class) + desc(JavaBeanInfo.class) + ")V");

        // init fieldNamePrefix
        for (int i = 0, size = context.fieldInfoList.length; i < size; ++i) {
            FieldInfo fieldInfo = context.fieldInfoList[i];

            mw.visitVarInsn(ALOAD, 0);
            mw.visitLdcInsn("\"" + fieldInfo.name + "\":"); // public char[] toCharArray()
            mw.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C");
            mw.visitFieldInsn(PUTFIELD, context.className, context.fieldName(fieldInfo), "[C");

        }

        mw.visitInsn(RETURN);
        mw.visitMaxs(4, 4);
        mw.visitEnd();
    }

    private void createInstance(ClassWriter cw, Context context) {
        Constructor<?> defaultConstructor = context.beanInfo.defaultConstructor;
        if (!Modifier.isPublic(defaultConstructor.getModifiers())) {
            return;
        }

        MethodVisitor mw = new MethodWriter(cw, ACC_PUBLIC, "createInstance",
                "(L" + DEFAULT_JSON_PARSER + ";Ljava/lang/reflect/Type;)Ljava/lang/Object;", null);

        mw.visitTypeInsn(NEW, type(context.getInstClass()));
        mw.visitInsn(DUP);
        mw.visitMethodInsn(INVOKESPECIAL, type(context.getInstClass()), INIT, "()V");

        mw.visitInsn(ARETURN);
        mw.visitMaxs(3, 3);
        mw.visitEnd();
    }

}
