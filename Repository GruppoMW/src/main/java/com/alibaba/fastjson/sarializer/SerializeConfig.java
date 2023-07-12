/*
 * Copyright 1999-2018 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.fastjson.serializer;

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.deserializer.Jdk8DateCodec;
import com.alibaba.fastjson.parser.deserializer.OptionalCodec;
import com.alibaba.fastjson.spi.Module;
import com.alibaba.fastjson.support.moneta.MonetaCodec;
import com.alibaba.fastjson.support.springfox.SwaggerJsonSerializer;
import com.alibaba.fastjson.util.ASMUtils;
import com.alibaba.fastjson.util.IdentityHashMap;
import com.alibaba.fastjson.util.ServiceLoader;
import com.alibaba.fastjson.util.TypeUtils;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.File;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;

/**
 * circular references detect
 *
 * @author wenshao[szujobs@hotmail.com]
 */
public class SerializeConfig {

    public static final SerializeConfig                   globalInstance  = new SerializeConfig();

    private  boolean                                awtError        = false;
    private  boolean                                jdk8Error       = false;
    private  boolean                                oracleJdbcError = false;
    private  boolean                                springfoxError  = false;
    private  boolean                                guavaError      = false;

    private  boolean                                jodaError       = false;

    private boolean                                       asm             = !ASMUtils.IS_ANDROID;
    private ASMSerializerFactory                          asmFactory;
    protected String                                      typeKey         = JSON.DEFAULT_TYPE_KEY;
    private PropertyNamingStrategy                         propertyNamingStrategy;

    private final IdentityHashMap<Type, ObjectSerializer> serializers;
    private final IdentityHashMap<Type, IdentityHashMap<Type, ObjectSerializer>> mixInSerializers;

    private final boolean                                 fieldBased;

    private final long[]                                        denyClasses =
            {
                    4165360493669296979L,
                    4446674157046724083L
            };

    private final List<Module>                                    modules                = new ArrayList<>();

    public String getTypeKey() {
        return typeKey;
    }

    public void setTypeKey(String typeKey) {
        this.typeKey = typeKey;
    }

    private JavaBeanSerializer createASMSerializer(SerializeBeanInfo beanInfo) throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        JavaBeanSerializer serializer = asmFactory.createJavaBeanSerializer(beanInfo);

        for (int i = 0; i < serializer.sortedGetters.length; ++i) {
            FieldSerializer fieldDeser = serializer.sortedGetters[i];
            Class<?> fieldClass = fieldDeser.fieldInfo.fieldClass;
            if (fieldClass.isEnum()) {
                ObjectSerializer fieldSer = this.getObjectWriter(fieldClass);
                if (!(fieldSer instanceof EnumSerializer)) {
                    serializer.writeDirect = false;
                }
            }
        }

        return serializer;
    }

    public final ObjectSerializer createJavaBeanSerializer(Class<?> clazz) {
        String className = clazz.getName();
        long hashCode64 = TypeUtils.fnv1a64(className);
        if (Arrays.binarySearch(denyClasses, hashCode64) >= 0) {
            throw new JSONException("not support class : " + className);
        }

        SerializeBeanInfo beanInfo = TypeUtils.buildBeanInfo(clazz, null, propertyNamingStrategy, fieldBased);
        if (beanInfo.fields.length == 0 && Iterable.class.isAssignableFrom(clazz)) {
            return MiscCodec.instance;
        }

        return createJavaBeanSerializer(beanInfo,clazz);
    }

    public ObjectSerializer createJavaBeanSerializer(SerializeBeanInfo beanInfo,Class<?> clazz) {
        JSONType jsonType = beanInfo.jsonType;
        boolean asm1 = this.asm && !fieldBased;

        if (jsonType != null) {
            asm1 = checkJsonType(jsonType, asm1);
        }

        asm1 = checkClass(clazz, asm1 , beanInfo);

        if (asm1) {
            // skip
            try {
                return createASMSerializer(beanInfo);
            } catch (ReflectiveOperationException e) {
                throw new JSONException("create asm serializer error, verson " + JSON.VERSION + ", class " + clazz, e);
            }
        }

        return new JavaBeanSerializer(beanInfo);
    }

    private boolean checkJsonType(JSONType jsonType, boolean asm) {

        if (!jsonType.asm()) {
            asm = false;
        }

        if (asm) {
            for (SerializerFeature feature : jsonType.serialzeFeatures()) {
                if (SerializerFeature.WRITE_NON_STRING_VALUE_AS_STRING == feature ||
                        SerializerFeature.WRITE_ENUM_USING_TO_STRING == feature ||
                        SerializerFeature.NOT_WRITE_DEFAULT_VALUE == feature ||
                        SerializerFeature.BROWSER_COMPATIBLE == feature) {
                    asm = false;
                    break;
                }
            }

            if (asm) {
                final Class<? extends SerializeFilter>[] filterClasses = jsonType.serialzeFilters();
                if (filterClasses.length != 0) {
                    asm = false;
                }
            }
        }

        return asm;
    }

    private boolean checkClass(Class<?> clazz, boolean asm, SerializeBeanInfo beanInfo) {
        if (!Modifier.isPublic(beanInfo.beanType.getModifiers())) {
            return true;
        }

        if (asm && asmFactory.classLoader.isExternalClass(clazz)
                || clazz == Serializable.class || clazz == Object.class) {
            asm = false;
        }

        if (asm && !ASMUtils.checkName(clazz.getSimpleName())) {
            asm = false;
        }

        if (asm && beanInfo.beanType.isInterface()) {
            asm = false;
        }

        return asm;
    }

    public void setAsmEnable(boolean asmEnable) {
        if (ASMUtils.IS_ANDROID) {
            return;
        }
        this.asm = asmEnable;
    }

    public static SerializeConfig getGlobalInstance() {
        return globalInstance;
    }

    public SerializeConfig() {
        this(IdentityHashMap.DEFAULT_SIZE);
    }

    public SerializeConfig(boolean fieldBase) {
        this(IdentityHashMap.DEFAULT_SIZE, fieldBase);
    }

    public SerializeConfig(int tableSize) {
        this(tableSize, false);
    }

    public SerializeConfig(int tableSize, boolean fieldBase) {
        this.fieldBased = fieldBase;
        serializers = new IdentityHashMap<>(tableSize);
        this.mixInSerializers = new IdentityHashMap<>(16);
        if (asm) {
            asmFactory = new ASMSerializerFactory();
        }

        initSerializers();
    }

    private void initSerializers() {
        put(Boolean.class, BooleanCodec.instance);
        put(Character.class, CharacterCodec.instance);
        put(Byte.class, IntegerCodec.instance);
        put(Short.class, IntegerCodec.instance);
        put(Integer.class, IntegerCodec.instance);
        put(Long.class, LongCodec.instance);
        put(Float.class, FloatCodec.instance);
        put(Double.class, DoubleSerializer.instance);
        put(BigDecimal.class, BigDecimalCodec.instance);
        put(BigInteger.class, BigIntegerCodec.instance);
        put(String.class, StringCodec.instance);
        put(byte[].class, PrimitiveArraySerializer.instance);
        put(short[].class, PrimitiveArraySerializer.instance);
        put(int[].class, PrimitiveArraySerializer.instance);
        put(long[].class, PrimitiveArraySerializer.instance);
        put(float[].class, PrimitiveArraySerializer.instance);
        put(double[].class, PrimitiveArraySerializer.instance);
        put(boolean[].class, PrimitiveArraySerializer.instance);
        put(char[].class, PrimitiveArraySerializer.instance);
        put(Object[].class, ObjectArrayCodec.instance);
        put(Class.class, MiscCodec.instance);

        put(SimpleDateFormat.class, MiscCodec.instance);
        put(Currency.class, new MiscCodec());
        put(TimeZone.class, MiscCodec.instance);
        put(InetAddress.class, MiscCodec.instance);
        put(Inet4Address.class, MiscCodec.instance);
        put(Inet6Address.class, MiscCodec.instance);
        put(InetSocketAddress.class, MiscCodec.instance);
        put(File.class, MiscCodec.instance);
        put(Appendable.class, AppendableSerializer.instance);
        put(StringBuffer.class, AppendableSerializer.instance);
        put(StringBuilder.class, AppendableSerializer.instance);
        put(Charset.class, ToStringSerializer.instance);
        put(Pattern.class, ToStringSerializer.instance);
        put(Locale.class, ToStringSerializer.instance);
        put(URI.class, ToStringSerializer.instance);
        put(URL.class, ToStringSerializer.instance);
        put(UUID.class, ToStringSerializer.instance);

        // atomic
        put(AtomicBoolean.class, AtomicCodec.instance);
        put(AtomicInteger.class, AtomicCodec.instance);
        put(AtomicLong.class, AtomicCodec.instance);
        put(AtomicReference.class, ReferenceCodec.instance);
        put(AtomicIntegerArray.class, AtomicCodec.instance);
        put(AtomicLongArray.class, AtomicCodec.instance);

        put(WeakReference.class, ReferenceCodec.instance);
        put(SoftReference.class, ReferenceCodec.instance);

        put(LinkedList.class, CollectionCodec.instance);
    }

    /**
     * add class level serialize filter
     * @since 1.2.10
     */
    public void addFilter(Class<?> clazz, SerializeFilter filter) {
        ObjectSerializer serializer = getObjectWriter(clazz);

        if (serializer instanceof SerializeFilterable) {
            SerializeFilterable filterable = (SerializeFilterable) serializer;

            if (this != SerializeConfig.globalInstance && filterable == MapSerializer.instance) {
                MapSerializer newMapSer = new MapSerializer();
                this.put(clazz, newMapSer);
                newMapSer.addFilter(filter);
                return;
            }

            filterable.addFilter(filter);
        }
    }

    /** class level serializer feature config
     * @since 1.2.12
     */
    public void config(Class<?> clazz, SerializerFeature feature, boolean value) {
        ObjectSerializer serializer = getObjectWriter(clazz, false);

        if (serializer == null) {
            SerializeBeanInfo beanInfo = TypeUtils.buildBeanInfo(clazz, null, propertyNamingStrategy);

            if (value) {
                beanInfo.features |= feature.mask;
            } else {
                beanInfo.features &= ~feature.mask;
            }
        }
    }

    public ObjectSerializer getObjectWriter(Class<?> clazz) {
        return getObjectWriter(clazz, true);
    }

    public ObjectSerializer getObjectWriter(Class<?> clazz, boolean create) {
        ObjectSerializer writer = get(clazz);

        ObjectSerializer writer1 = getObjectSerializer(writer);
        if (writer1 != null) return writer1;

        classLoaderVoid();

        writer = get(clazz);

        writer = getObjectSerializer(clazz, writer);

        if(getObjectSerializer(clazz)!=null){
            return getObjectSerializer(clazz);
        }

        return getSerializer(clazz, create, writer);
    }

    private ObjectSerializer getSerializer(Class<?> clazz, boolean create, ObjectSerializer writer) {
        if (writer == null) {
            String className = clazz.getName();

            getObjectSerializer19(clazz, create, className);

            writer = getSerializer(clazz, writer);
        }
        return writer;
    }

    private void getObjectSerializer19(Class<?> clazz, boolean create, String className) {
        if (Map.class.isAssignableFrom(clazz)) {
            put(clazz, MapSerializer.instance);
        } else {
            getObjectSerializer18(clazz, create, className);
        }
    }

    private void getObjectSerializer18(Class<?> clazz, boolean create, String className) {
        if (List.class.isAssignableFrom(clazz)) {
            put(clazz, ListSerializer.instance);
        } else {
            getObjectSerializer17(clazz, create, className);
        }
    }

    private void getObjectSerializer17(Class<?> clazz, boolean create, String className) {
        if (Collection.class.isAssignableFrom(clazz)) {
            put(clazz, CollectionCodec.instance);
        } else {
            getObjectSerializer16(clazz, create, className);
        }
    }

    private void getObjectSerializer16(Class<?> clazz, boolean create, String className) {
        if (Date.class.isAssignableFrom(clazz)) {
            put(clazz, DateCodec.instance);
        } else {
            getObjectSerializer15(clazz, create, className);

        }
    }

    private void getObjectSerializer15(Class<?> clazz, boolean create, String className) {
        if (JSONAware.class.isAssignableFrom(clazz)) {
            put(clazz, JSONAwareSerializer.instance);
        } else {
            getObjectSerializer14(clazz, create, className);
        }
    }

    private void getObjectSerializer14(Class<?> clazz, boolean create, String className) {
        if (JSONSerializable.class.isAssignableFrom(clazz)) {
            put(clazz, JSONSerializableSerializer.instance);
        } else {
            getObjectSerializer13(clazz, create, className);
        }
    }

    private void getObjectSerializer13(Class<?> clazz, boolean create, String className) {
        if (JSONStreamAware.class.isAssignableFrom(clazz)) {
            put(clazz, MiscCodec.instance);
        } else {
            getObjectSerializer12(clazz, create, className);
        }
    }

    private <T> void getObjectSerializer12(Class<?> clazz, boolean create, String className) {
        if (clazz.isEnum()) {
            Class<T> mixedInType = (Class<T>) JSON.getMixInAnnotations(clazz);

            JSONType jsonType;
            jsonType = getJsonType(clazz, mixedInType);

            getObjectSerializer(clazz, mixedInType, jsonType);
        } else {
            getObjectSerializer11(clazz, create, className);
        }
    }

    private void getObjectSerializer11(Class<?> clazz, boolean create, String className) {
        Class<?> superClass;
        if ((superClass = clazz.getSuperclass()) != null && superClass.isEnum()) {
            JSONType jsonType = TypeUtils.getAnnotation(superClass, JSONType.class);
            getObjectSerializer(clazz, jsonType);
        } else {
            getObjectSerializer10(clazz, create, className);
        }
    }

    private void getObjectSerializer10(Class<?> clazz, boolean create, String className) {
        if (clazz.isArray()) {
            Class<?> componentType = clazz.getComponentType();
            ObjectSerializer compObjectSerializer = getObjectWriter(componentType);
            put(clazz, new ArraySerializer(componentType, compObjectSerializer));
        } else {
            getObjectSerializer9(clazz, create, className);
        }
    }

    private void getObjectSerializer9(Class<?> clazz, boolean create, String className) {
        if (Throwable.class.isAssignableFrom(clazz)) getSerializer(clazz);
        else {
            getObjectSerializer8(clazz, create, className);
        }
    }

    private void getObjectSerializer8(Class<?> clazz, boolean create, String className) {
        if (TimeZone.class.isAssignableFrom(clazz) || Map.Entry.class.isAssignableFrom(clazz)) {
            getWriter(clazz);
        } else {
            getObjectSerializer7(clazz, create, className);
        }
    }

    private void getObjectSerializer7(Class<?> clazz, boolean create, String className) {
        if (Appendable.class.isAssignableFrom(clazz)) {
            getObjectSerializer1(clazz);
        } else {
            getObjectSerializer6(clazz, create, className);
        }
    }

    private void getObjectSerializer6(Class<?> clazz, boolean create, String className) {
        if (Charset.class.isAssignableFrom(clazz)) {
            getObjectSerializer2(clazz);
        } else {
            getObjectSerializer5(clazz, create, className);
        }
    }

    private void getObjectSerializer5(Class<?> clazz, boolean create, String className) {
        if (Enumeration.class.isAssignableFrom(clazz)) {
            getObjectSerializer3(clazz);
        } else {
            getObjectSerializer4(clazz, create, className);
        }
    }

    private void getObjectSerializer4(Class<?> clazz, boolean create, String className) {
        if (Calendar.class.isAssignableFrom(clazz) //
                || XMLGregorianCalendar.class.isAssignableFrom(clazz)) {
            getObjectSerializer4(clazz);
        } else {
            getObjectSerializer3(clazz, create, className);
        }
    }

    private void getObjectSerializer3(Class<?> clazz, boolean create, String className) {
        if (TypeUtils.isClob(clazz)) {
            getObjectSerializer5(clazz);
        } else {
            getObjectSerializer2(clazz, create, className);
        }
    }

    private void getObjectSerializer2(Class<?> clazz, boolean create, String className) {
        if (TypeUtils.isPath(clazz)) {
            getObjectSerializer6(clazz);
        } else {
            getObjectSerializer1(clazz, create, className);
        }
    }

    private void getObjectSerializer1(Class<?> clazz, boolean create, String className) {
        if (Iterator.class.isAssignableFrom(clazz)) {
            getObjectSerializer7(clazz);
        } else {
            getSerializer(clazz, create, className);
        }
    }

    private void getSerializer(Class<?> clazz, boolean create, String className) {
        if (org.w3c.dom.Node.class.isAssignableFrom(clazz)) {
            getObjectSerializer8(clazz);
        } else {
            getObjectSerializer(clazz, create, className);
        }
    }

    private void getObjectSerializer(Class<?> clazz, boolean create, String className) {
        if (isABoolean30(clazz, className)) {
            getObjectSerializer(className);
            return;
        }

        // jdk8
        if (isABoolean4(className)) {
            getSerializer(className);
            return;
        }

        if (isABoolean1(className)) {
            getObjectSerializer1(className);
            return;
        }

        if(getObjectSerializer2(className)!=null) {
            getObjectSerializer2(className);
            return;
        }

        if (isABoolean9(className)) {
            getObjectSerializer3(className);
            return;
        }

        getObjectSerializer(clazz, className);

        if (isABoolean3(className)) {
            getObjectSerializer4(className);
            return;
        }

        getSerializer(clazz, className);

        Class[] interfaces = clazz.getInterfaces();
        if (putClassVoid(clazz, interfaces)) return;

        if(getObjectSerializer9(clazz)!=null) {
            getObjectSerializer9(clazz);
            return;
        }

        getObjectSerializer(clazz, create);
    }

    private ObjectSerializer getSerializer(Class<?> clazz, ObjectSerializer writer) {
        if (writer == null) {
            writer = get(clazz);
        }
        return writer;
    }

    private boolean isABoolean4(String className) {
        if (isaBoolean2(className)) {
            return getSerializerNotNull(className);
        }
        return false;
    }

    private boolean isaBoolean2(String className) {
        return (!jdk8Error) //
                && (className.startsWith("java.time.") //
                || className.startsWith("java.util.Optional") //
                || className.equals("java.util.concurrent.atomic.LongAdder")
                || className.equals("java.util.concurrent.atomic.DoubleAdder")
        );
    }

    private boolean getSerializerNotNull(String className) {
        return getSerializer(className) != null;
    }

    private void getObjectSerializer(Class<?> clazz, boolean create) {
        if (create) {
            ObjectSerializer writer = createJavaBeanSerializer(clazz);
            put(clazz, writer);
        }
    }

    private ObjectSerializer getObjectSerializer9(Class<?> clazz) {
        if (TypeUtils.isProxy(clazz)) {
            Class<?> superClazz = clazz.getSuperclass();

            ObjectSerializer superWriter = getObjectWriter(superClazz);
            put(clazz, superWriter);
            return superWriter;
        }
        return null;
    }

    private boolean putClassVoid(Class<?> clazz, Class[] interfaces) {
        if (interfaces.length == 1 && interfaces[0].isAnnotation()) {
            put(clazz, AnnotationSerializer.instance);
            return true;
        }
        return false;
    }

    private void getSerializer(Class<?> clazz, String className) {
        if ("java.nio.HeapByteBuffer".equals(className)) {
            put(clazz, ByteBufferCodec.instance);
            return;
        }

        if ("org.javamoney.moneta.Money".equals(className)) {
            put(clazz, MonetaCodec.instance);
            return;
        }

        if ("com.google.protobuf.Descriptors$FieldDescriptor".equals(className)) {
            put(clazz, ToStringSerializer.instance);
        }
    }

    private boolean isABoolean3(String className) {
        if ((!jodaError) && className.startsWith("org.joda.")) {
            return getObjectSerializer4(className) != null;
        }
        return false;
    }

    private ObjectSerializer getObjectSerializer4(String className) {
        ObjectSerializer writer;
        try {
            String[] names = new String[] {
                    "org.joda.time.LocalDate",
                    "org.joda.time.LocalDateTime",
                    "org.joda.time.LocalTime",
                    "org.joda.time.Instant",
                    "org.joda.time.DateTime",
                    "org.joda.time.Period",
                    "org.joda.time.Duration",
                    "org.joda.time.DateTimeZone",
                    "org.joda.time.UTCDateTimeZone",
                    "org.joda.time.tz.CachedDateTimeZone",
                    "org.joda.time.tz.FixedDateTimeZone",
            };

            for (String name : names) {
                if (name.equals(className)) {
                    writer = JodaCodec.instance;
                    put(Class.forName(name), writer);
                    return writer;
                }
            }
        } catch (ClassNotFoundException e) {
            // skip
            jodaError = true;
        }
        return null;
    }

    private void getObjectSerializer(Class<?> clazz, String className) {
        if (className.equals("net.sf.json.JSONNull")) {
            put(clazz, MiscCodec.instance);
            return;
        }

        if (className.equals("org.json.JSONObject")) {
            put(clazz, JSONObjectCodec.instance);
        }
    }

    private boolean isABoolean9(String className) {
        if (isaBoolean11(className)) {
            return getObjectSerializer3(className) != null;
        }
        return false;
    }

    private boolean isaBoolean11(String className) {
        return (!guavaError) //
                && className.startsWith("com.google.common.collect.");
    }

    private ObjectSerializer getObjectSerializer3(String className) {
        ObjectSerializer writer;
        try {
            String[] names = new String[] {
                    "com.google.common.collect.HashMultimap",
                    "com.google.common.collect.LinkedListMultimap",
                    "com.google.common.collect.LinkedHashMultimap",
                    "com.google.common.collect.ArrayListMultimap",
                    "com.google.common.collect.TreeMultimap"
            };

            for (String name : names) {
                if (name.equals(className)) {
                    writer = GuavaCodec.instance;
                    put(Class.forName(name), writer);
                    return writer;
                }
            }
        } catch (ClassNotFoundException e) {
            // skip
            guavaError = true;
        }
        return null;
    }

    private ObjectSerializer getObjectSerializer2(String className) {
        ObjectSerializer writer;
        if ((!springfoxError) //
                && className.equals("springfox.documentation.spring.web.json.Json")) {
            try {
                writer = SwaggerJsonSerializer.instance;
                put(Class.forName("springfox.documentation.spring.web.json.Json"), //
                        writer);
                return writer;
            } catch (ClassNotFoundException e) {
                // skip
                springfoxError = true;
            }
        }
        return null;
    }

    private boolean isABoolean1(String className) {
        if (isaBoolean20(className)) {
            return getObjectSerializer1(className) != null;
        }
        return false;
    }

    private boolean isaBoolean20(String className) {
        return (!oracleJdbcError) //
                && className.startsWith("oracle.sql.");
    }

    private ObjectSerializer getObjectSerializer1(String className) {
        ObjectSerializer writer;
        try {
            String[] names = new String[]{
                    "oracle.sql.DATE",
                    "oracle.sql.TIMESTAMP"
            };

            for (String name : names) {
                if (name.equals(className)) {
                    writer = DateCodec.instance;
                    put(Class.forName(name), writer);
                    return writer;
                }
            }
        } catch (ClassNotFoundException e) {
            // skip
            oracleJdbcError = true;
        }
        return null;
    }

    private ObjectSerializer getSerializer(String className) {
        ObjectSerializer writer;
        try {
            
                String[] names1 = new String[]{
                        "java.time.LocalDateTime",
                        "java.time.LocalDate",
                        "java.time.LocalTime",
                        "java.time.ZonedDateTime",
                        "java.time.OffsetDateTime",
                        "java.time.OffsetTime",
                        "java.time.ZoneOffset",
                        "java.time.ZoneRegion",
                        "java.time.Period",
                        "java.time.Duration",
                        "java.time.Instant"
                };
                for (String name : names1) {
                    if (name.equals(className)) {
                        writer = Jdk8DateCodec.instance;
                        put(Class.forName(name), writer);
                        return writer;
                    }
                }
            
            
                String[] names2 = new String[]{
                        "java.util.Optional",
                        "java.util.OptionalDouble",
                        "java.util.OptionalInt",
                        "java.util.OptionalLong"
                };
                for (String name : names2) {
                    if (name.equals(className)) {
                        writer = OptionalCodec.instance;
                        put(Class.forName(name), writer);
                        return writer;
                    }
                }
            
                String[] names = new String[]{
                        "java.util.concurrent.atomic.LongAdder",
                        "java.util.concurrent.atomic.DoubleAdder"
                };
                for (String name : names) {
                    if (name.equals(className)) {
                        writer = AdderSerializer.instance;
                        put(Class.forName(name), writer);
                        return writer;
                    }
                }
        } catch (ClassNotFoundException e) {
            // skip
            jdk8Error = true;
        }
        return null;
    }

    private boolean isABoolean30(Class<?> clazz, String className) {
        if (isaBoolean20(clazz, className)) {
            // awt
            return getObjectSerializer(className) != null;
        }
        return false;
    }

    private ObjectSerializer getObjectSerializer(String className) {
        ObjectSerializer writer;
        if (!awtError) {
            try {
                String[] names = new String[]{
                        "java.awt.Color",
                        "java.awt.Font",
                        "java.awt.Point",
                        "java.awt.Rectangle"
                };
                for (String name : names) {
                    if (name.equals(className)) {
                        writer = AwtCodec.instance;
                        put(Class.forName(name), writer);
                        return writer;
                    }
                }
            } catch (ClassNotFoundException e) {
                awtError = true;
                // skip
            }
        }
        return null;
    }

    private static boolean isaBoolean20(Class<?> clazz, String className) {
        return className.startsWith("java.awt.") //
                && AwtCodec.support(clazz);
    }

    private void getObjectSerializer8(Class<?> clazz) {
        put(clazz, MiscCodec.instance);
    }

    private void getObjectSerializer7(Class<?> clazz) {
        put(clazz, MiscCodec.instance);
    }

    private void getObjectSerializer6(Class<?> clazz) {
        put(clazz, ToStringSerializer.instance);
    }

    private void getObjectSerializer5(Class<?> clazz) {
        put(clazz, ClobSerializer.instance);
    }

    private void getObjectSerializer4(Class<?> clazz) {
        put(clazz, CalendarCodec.instance);
    }

    private void getObjectSerializer3(Class<?> clazz) {
        put(clazz, EnumerationSerializer.instance);
    }

    private void getObjectSerializer2(Class<?> clazz) {
        put(clazz, ToStringSerializer.instance);
    }

    private void getObjectSerializer1(Class<?> clazz) {
        put(clazz, AppendableSerializer.instance);
    }

    private void getWriter(Class<?> clazz) {
        put(clazz, MiscCodec.instance);
    }

    private void getSerializer(Class<?> clazz) {
        SerializeBeanInfo beanInfo = TypeUtils.buildBeanInfo(clazz, null, propertyNamingStrategy);
        beanInfo.features |= SerializerFeature.WRITE_CLASS_NAME.mask;
        put(clazz, new JavaBeanSerializer(beanInfo));
    }

    private void getObjectSerializer(Class<?> clazz, Class<?> mixedInType, JSONType jsonType) {
        if (jsonType != null && jsonType.serializeEnumAsJavaBean()) {
            put(clazz, createJavaBeanSerializer(clazz));
        } else {
            Member member = null;
            member = getMember(clazz, mixedInType, member);
            getObjectSerializer(clazz, member);
        }
    }

    private static JSONType getJsonType(Class<?> clazz, Class<?> mixedInType) {
        JSONType jsonType;
        if (mixedInType != null) {
            jsonType = TypeUtils.getAnnotation(mixedInType, JSONType.class);
        } else {
            jsonType = TypeUtils.getAnnotation(clazz, JSONType.class);
        }
        return jsonType;
    }

    private void getObjectSerializer(Class<?> clazz, JSONType jsonType) {
        if (jsonType != null && jsonType.serializeEnumAsJavaBean()) {
            put(clazz, createJavaBeanSerializer(clazz));
        } else {
            put(clazz, getEnumSerializer());
        }
    }

    private void getObjectSerializer(Class<?> clazz, Member member) {
        if (member != null) {
            put(clazz, new EnumSerializer(member));
        } else {
            put(clazz, getEnumSerializer());
        }
    }

    private static Member getMember(Class<?> clazz, Class<?> mixedInType, Member member) {
        if (mixedInType != null) {
            Member mixedInMember = getEnumValueField(mixedInType);
            member = getMember1(clazz, member, mixedInMember);
        } else {
            member = getEnumValueField(clazz);
        }
        return member;
    }

    private static Member getMember1(Class<?> clazz, Member member, Member mixedInMember) {
        if (mixedInMember != null) {
            member = getMember(clazz, member, mixedInMember);
        }
        return member;
    }

    private static Member getMember(Class<?> clazz, Member member, Member mixedInMember) {
        try {
            if (mixedInMember instanceof Method) {
                member = getMember(clazz, (Method) mixedInMember);
            }
        } catch (Exception e) {
            // skip
        }
        return member;
    }

    private static Member getMember(Class<?> clazz, Method mixedInMember) throws NoSuchMethodException {
        return clazz.getMethod(mixedInMember.getName(), mixedInMember.getParameterTypes());
    }

    private ObjectSerializer getObjectSerializer(Class<?> clazz) {
        ObjectSerializer writer;
        for (Module module : modules) {
            writer = module.createSerializer(this, clazz);
            if (writer != null) {
                put(clazz, writer);
                return writer;
            }
        }
        return null;
    }

    private ObjectSerializer getObjectSerializer(Class<?> clazz, ObjectSerializer writer) {
        if (writer == null) {
            final ClassLoader classLoader = JSON.class.getClassLoader();
            if (classLoader != Thread.currentThread().getContextClassLoader()) {
                try {
                    autoWiredObject(classLoader);
                } catch (ClassCastException ex) {
                    // skip
                }

                writer = get(clazz);
            }
        }
        return writer;
    }

    private void classLoaderVoid() {
        try {
            final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            autoWiredObject(classLoader);
        } catch (ClassCastException ex) {
            // skip
        }
    }

    private void autoWiredObject(ClassLoader classLoader) {
        for (AutowiredObjectSerializer o : ServiceLoader.load(AutowiredObjectSerializer.class, classLoader)) {
            if (o == null) {
                continue;
            }

            autoWired(o);
        }
    }

    private void autoWired(AutowiredObjectSerializer o) {
        for (Type forType : o.getAutowiredFor()) put(forType, o);
    }

    private static ObjectSerializer getObjectSerializer(ObjectSerializer writer) {
        return writer;
    }

    private static <T> Member getEnumValueField(Class<T> clazz) {
        Member member = null;

        Method[] methods = clazz.getMethods();

        for (Method method : methods) {
            if (method.getReturnType() == Void.class) {
                continue;
            }
            JSONField jsonField = method.getAnnotation(JSONField.class);
            if (jsonField != null) {
                if (member != null) {
                    return null;
                }

                member = method;
            }
        }

        for (Field field : clazz.getFields()) {
            JSONField jsonField = field.getAnnotation(JSONField.class);

            if (jsonField != null) {
                if (member != null) {
                    return null;
                }

                member = field;
            }
        }

        return member;
    }

    /**
     * 可以通过重写这个方法，定义自己的枚举序列化实现
     * @return 返回一个枚举的反序列化实现
     * @author zhu.xiaojie
     * @time 2020-4-5
     */
    protected ObjectSerializer getEnumSerializer(){
        return EnumSerializer.instance;
    }

    public final ObjectSerializer get(Type type) {
        Type mixin = JSON.getMixInAnnotations(type);
        if (null == mixin) {
            return this.serializers.get(type);
        }
        IdentityHashMap<Type, ObjectSerializer> mixInClasses = this.mixInSerializers.get(type);
        if (mixInClasses == null) {
            return null;
        }
        return mixInClasses.get(mixin);
    }

    public boolean put(Object type, Object value) {
        return put((Type)type, (ObjectSerializer)value);
    }

    public boolean put(Type type, ObjectSerializer value) {
        Type mixin = JSON.getMixInAnnotations(type);
        if (mixin != null) {
            IdentityHashMap<Type, ObjectSerializer> mixInClasses = this.mixInSerializers.get(type);
            if (mixInClasses == null) {
                //多线程下可能会重复创建，但不影响正确性
                mixInClasses = new IdentityHashMap<>(4);
                mixInSerializers.put(type, mixInClasses);
            }
            return mixInClasses.put(mixin, value);
        }
        return this.serializers.put(type, value);
    }

    /**
     * for spring config support
     */
    public void setPropertyNamingStrategy(PropertyNamingStrategy propertyNamingStrategy) {
        this.propertyNamingStrategy = propertyNamingStrategy;
    }
}