package com.alibaba.fastjson;

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONLexerBase;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.FieldDeserializer;
import com.alibaba.fastjson.parser.deserializer.JavaBeanDeserializer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.FieldSerializer;
import com.alibaba.fastjson.serializer.JavaBeanSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.util.IOUtils;
import com.alibaba.fastjson.util.TypeUtils;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wenshao[szujobs@hotmail.com]
 * @since 1.2.0
 */
public class JSONPath implements JSONAware {
    private static ConcurrentMap<String, JSONPath> pathCache  = new ConcurrentHashMap<>(128, 0.75f, 1);
    private static final String JSON_PATH_ERROR = "jsonpath error, path ";
    private static final String NOT_SUPPORT_JSON_PATH = "not support jsonpath : ";
    private static final String SEGEMENT = ", segement ";

    private final String                           path;
    private Segment[]                              segments;
    private boolean                                hasRefSegment;

    private SerializeConfig                        serializeConfig;
    private ParserConfig                           parserConfig;

    private boolean                                ignoreNullValue;

    public JSONPath(String path){
        this(path, SerializeConfig.getGlobalInstance(), ParserConfig.getGlobalInstance(), true);
    }

    public JSONPath(String path, boolean ignoreNullValue){
        this(path, SerializeConfig.getGlobalInstance(), ParserConfig.getGlobalInstance(), ignoreNullValue);
    }

    public JSONPath(String path, SerializeConfig serializeConfig, ParserConfig parserConfig, boolean ignoreNullValue){
        if (path == null || path.length() == 0) {
            throw new JSONPathException("json-path can not be null or empty");
        }

        this.path = path;
        this.serializeConfig = serializeConfig;
        this.parserConfig = parserConfig;
        this.ignoreNullValue = ignoreNullValue;
    }

    protected void init() {
        if (segments != null) {
            return;
        }

        if ("*".equals(path)) {
            this.segments = new Segment[] { WildCardSegment.instance };
        } else {
            JSONPathParser parser = new JSONPathParser(path);
            this.segments = parser.explain();
            this.hasRefSegment = parser.hasRefSegment;
        }
    }

    public boolean isRef() {
        try {
            init();
            for (int i = 0; i < segments.length; ++i) {
                Segment segment = segments[i];
                Class<?> segmentType = segment.getClass();
                if (segmentType == ArrayAccessSegment.class
                        || segmentType == PropertySegment.class) {
                    continue;
                }
                return false;
            }
            return true;
        } catch (JSONPathException ex) {
            // skip
            return false;
        }
    }

    public Object eval(Object rootObject) {
        if (rootObject == null) {
            return null;
        }

        init();

        Object currentObject = rootObject;
        for (int i = 0; i < segments.length; ++i) {
            Segment segment = segments[i];
            currentObject = segment.eval(this, rootObject, currentObject);
        }
        return currentObject;
    }

    /**
     * @since 1.2.76
     * @param rootObject
     * @param clazz
     * @param parserConfig
     * @return
     */
    public <T> T eval(Object rootObject, Type clazz, ParserConfig parserConfig) {
        Object obj = this.eval(rootObject);
        return TypeUtils.cast(obj, clazz, parserConfig);
    }

    /**
     * @since 1.2.76
     * @param rootObject
     * @param clazz
     * @return
     */
    public <T> T eval(Object rootObject, Type clazz) {
        return this.eval(rootObject, clazz, ParserConfig.getGlobalInstance());
    }

    public Object extract(DefaultJSONParser parser) {
        if (parser == null) {
            return null;
        }

        init();

        if (hasRefSegment) {
            Object root = parser.parse();
            return this.eval(root);
        }

        if (segments.length == 0) {
            return parser.parse();
        }

        Segment lastSegment = segments[segments.length - 1];
        if (extracted8(lastSegment)) {
            return eval(parser.parse());
        }

        Context context = null;
        for (int i = 0; i < segments.length; ++i) {
            Segment segment = segments[i];
            boolean last = i == segments.length - 1;

            if (extracted9(context)) {
                context.object = segment.eval(this, null, context.object);
                continue;
            }

            boolean eval;

            if (!last) {
                Segment nextSegment = segments[i + 1];
                eval = extracted11(segment, nextSegment);
            } else {
                eval = true;
            }

            context = new Context(context, eval);
            segment.extract(this, parser, context);
        }
        return extracted12(context);


    }

    private Object extracted12(Context context) {
        if(context == null){
            throw new NullPointerException("context è nullo");
        }else{
            return context.object;
        }
    }

    private boolean extracted11(Segment segment, Segment nextSegment) {
        boolean eval;
        if (extracted10(segment, nextSegment))
        {
            eval = true;
        } else if (nextSegment instanceof ArrayAccessSegment
                && ((ArrayAccessSegment) nextSegment).index < 0) {
            eval = true;
        } else if (nextSegment instanceof FilterSegment) {
            eval = true;
        } else if (segment instanceof WildCardSegment) {
            eval = true;
        }else if(segment instanceof MultiIndexSegment){
            eval = true;
        } else {
            eval = false;
        }
        return eval;
    }

    private boolean extracted10(Segment segment, Segment nextSegment) {
        return segment instanceof PropertySegment
                && ((PropertySegment) segment).deep
                && (nextSegment instanceof ArrayAccessSegment
                || nextSegment instanceof MultiIndexSegment
                || nextSegment instanceof MultiPropertySegment
                || nextSegment instanceof SizeSegment
                || nextSegment instanceof PropertySegment
                || nextSegment instanceof FilterSegment);
    }

    private boolean extracted9(Context context) {
        return context != null && context.object != null;
    }

    private boolean extracted8(Segment lastSegment) {
        return lastSegment instanceof TypeSegment
                || lastSegment instanceof FloorSegment
                || lastSegment instanceof MultiIndexSegment;
    }

    private static class Context {
        final Context parent;
        final boolean eval;
        Object object;

        public Context(Context parent, boolean eval) {
            this.parent = parent;
            this.eval = eval;
        }
    }

    public boolean contains(Object rootObject) {
        if (rootObject == null) {
            return false;
        }

        init();

        Object currentObject = rootObject;
        for (int i = 0; i < segments.length; ++i) {
            Object parentObject = currentObject;
            currentObject = segments[i].eval(this, rootObject, currentObject);
            if (currentObject == null) {
                return false;
            }

            if (currentObject == Collections.emptyList() && parentObject instanceof List) {
                return ((List) parentObject).contains(currentObject);
            }
        }

        return true;
    }

    @SuppressWarnings("rawtypes")
    public boolean containsValue(Object rootObject, Object value) {
        Object currentObject = eval(rootObject);

        if (currentObject == value) {
            return true;
        }

        if (currentObject == null) {
            return false;
        }

        if (currentObject instanceof Iterable) {
            Iterator it = ((Iterable) currentObject).iterator();
            while (it.hasNext()) {
                Object item = it.next();
                if (eq(item, value)) {
                    return true;
                }
            }

            return false;
        }

        return eq(currentObject, value);
    }

    public int size(Object rootObject) {
        if (rootObject == null) {
            return -1;
        }

        init();

        Object currentObject = rootObject;
        for (int i = 0; i < segments.length; ++i) {
            currentObject = segments[i].eval(this, rootObject, currentObject);
        }

        return evalSize(currentObject);
    }

    /**
     * Extract keySet or field names from rootObject on this JSONPath.
     *
     * @param rootObject Can be a map or custom object. Array and Collection are not supported.
     * @return Set of keys, or <code>null</code> if not supported.
     */
    public Set<String> keySet(Object rootObject) {
        if (rootObject == null) {
            return Collections.emptySet();
        }

        init();

        Object currentObject = rootObject;
        for (int i = 0; i < segments.length; ++i) {
            currentObject = segments[i].eval(this, rootObject, currentObject);
        }

        return evalKeySet(currentObject);
    }

    public void patchAdd(Object rootObject, Object value, boolean replace) {
        if (rootObject == null) {
            return;
        }

        init();

        Object currentObject = rootObject;
        Object parentObject = null;
        for (int i = 0; i < segments.length; ++i) {
            parentObject = currentObject;
            Segment segment = segments[i];
            currentObject = segment.eval(this, rootObject, currentObject);
            currentObject = extracted4(currentObject, parentObject, i, segment);
        }

        Object result = currentObject;

        if ((!replace) && result instanceof Collection) {
            Collection collection = (Collection) result;
            collection.add(value);
            return;
        }

        Object newResult;

        if (result != null && !replace) {
            Class<?> resultClass = result.getClass();

            if (resultClass.isArray()) {
                int length = Array.getLength(result);
                Object descArray = Array.newInstance(resultClass.getComponentType(), length + 1);

                System.arraycopy(result, 0, descArray, 0, length);
                Array.set(descArray, length, value);
                newResult = descArray;
            }
            else if (Map.class.isAssignableFrom(resultClass)) {
                newResult = value;
            } else {
                throw new JSONException("unsupported array put operation. " + resultClass);
            }
        } else {
            newResult = value;
        }

        Segment lastSegment = segments[segments.length - 1];
        if (lastSegment instanceof PropertySegment) {
            PropertySegment propertySegment = (PropertySegment) lastSegment;
            propertySegment.setValue(this, parentObject, newResult);
            return;
        }

        if (lastSegment instanceof ArrayAccessSegment) {
            ((ArrayAccessSegment) lastSegment).setValue(this, parentObject, newResult);
            return;
        }

        throw new UnsupportedOperationException();
    }

    private Object extracted4(Object currentObject, Object parentObject, int i, Segment segment) {
        if (currentObject == null && i != segments.length - 1) {
            currentObject = extracted3(currentObject, parentObject, segment);
        }
        return currentObject;
    }

    private Object extracted3(Object currentObject, Object parentObject, Segment segment) {
        if (segment instanceof PropertySegment) {
            currentObject = new JSONObject();
            ((PropertySegment) segment).setValue(this, parentObject, currentObject);
        }
        return currentObject;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void arrayAdd(Object rootObject, Object... values) {
        if (values == null || values.length == 0) {
            return;
        }

        if (rootObject == null) {
            return;
        }

        init();

        Object currentObject = rootObject;
        Object parentObject = null;
        for (int i = 0; i < segments.length; ++i) {
            parentObject = extracted(currentObject, parentObject, i);
            currentObject = segments[i].eval(this, rootObject, currentObject);
        }

        Object result = currentObject;

        if (result == null) {
            throw new JSONPathException("value not found in path " + path);
        }

        if (result instanceof Collection) {
            for (Object value : values) {
                Arrays.asList(value);
            }
            return;
        }

        Class<?> resultClass = result.getClass();

        Object newResult;
        if (resultClass.isArray()) {
            int length = Array.getLength(result);
            Object descArray = Array.newInstance(resultClass.getComponentType(), length + values.length);

            System.arraycopy(result, 0, descArray, 0, length);
            for (int i = 0; i < values.length; ++i) {
                Array.set(descArray, length + i, values[i]);

            }
            newResult = descArray;
        } else {
            throw new JSONException("unsupported array put operation. " + resultClass);
        }

        Segment lastSegment = segments[segments.length - 1];
        if (lastSegment instanceof PropertySegment) {
            PropertySegment propertySegment = (PropertySegment) lastSegment;
            propertySegment.setValue(this, parentObject, newResult);
            return;
        }

        if (lastSegment instanceof ArrayAccessSegment) {
            ((ArrayAccessSegment) lastSegment).setValue(this, parentObject, newResult);
            return;
        }

        throw new UnsupportedOperationException();
    }

    private Object extracted(Object currentObject, Object parentObject, int i) {
        if (i == segments.length - 1) {
            parentObject = currentObject;
        }
        return parentObject;
    }

    public boolean remove(Object rootObject) {
        if (rootObject == null) {
            return false;
        }

        init();

        Object currentObject = rootObject;
        Object parentObject = null;

        Segment lastSegment = segments[segments.length - 1];
        for (int i = 0; i < segments.length; ++i) {
            if (i == segments.length - 1) {
                parentObject = currentObject;
            }
            Segment segement = segments[i];


            currentObject = segement.eval(this, rootObject, currentObject);
            if (currentObject == null) {
                break;
            }
        }

        if (parentObject == null) {
            return false;
        }


        if (lastSegment instanceof PropertySegment) {
            PropertySegment propertySegment = (PropertySegment) lastSegment;


            return propertySegment.remove(this, parentObject);
        }

        if (lastSegment instanceof ArrayAccessSegment) {
            return ((ArrayAccessSegment) lastSegment).remove(this, parentObject);
        }

        if (lastSegment instanceof FilterSegment) {
            FilterSegment filterSegment = (FilterSegment) lastSegment;
            return filterSegment.remove(this, rootObject, parentObject);
        }

        throw new UnsupportedOperationException();
    }

    public boolean set(Object rootObject, Object value) {
        if (rootObject == null) {
            return false;
        }

        init();

        Object currentObject = rootObject;
        Object parentObject = null;
        for (int i = 0; i < segments.length; ++i) {
            parentObject = currentObject;
            Segment segment = segments[i];
            currentObject = segment.eval(this, rootObject, currentObject);
            if (currentObject == null) {
                Segment nextSegment = null;
                nextSegment = extracted13(i, nextSegment);

                Object newObj = null;
                newObj = extracted14(parentObject, segment, nextSegment, newObj);

                if (newObj != null) {
                    if (segment instanceof PropertySegment) {
                        PropertySegment propSegement = (PropertySegment) segment;
                        propSegement.setValue(this, parentObject, newObj);

                    } else if (segment instanceof ArrayAccessSegment) {
                        ArrayAccessSegment arrayAccessSegement = (ArrayAccessSegment) segment;
                        arrayAccessSegement.setValue(this, parentObject, newObj);

                    }
                }

                break;
            }
        }

        if (parentObject == null) {
            return false;
        }

        Segment lastSegment = segments[segments.length - 1];
        if (lastSegment instanceof PropertySegment) {
            PropertySegment propertySegment = (PropertySegment) lastSegment;
            propertySegment.setValue(this, parentObject, value);
            return true;
        }

        if (lastSegment instanceof ArrayAccessSegment) {
            return ((ArrayAccessSegment) lastSegment).setValue(this, parentObject, value);
        }

        throw new UnsupportedOperationException();
    }

    private Object extracted14(Object parentObject, Segment segment, Segment nextSegment, Object newObj) {
        if (nextSegment instanceof PropertySegment) {
            JavaBeanDeserializer beanDeserializer = null;
            Class<?> fieldClass = null;

            beanDeserializer = extr(segment,beanDeserializer, parentObject);
            newObj = extr1(beanDeserializer,fieldClass);

        } else if (nextSegment instanceof ArrayAccessSegment) {
            newObj = new JSONArray();
        }
        return newObj;
    }

    private Object extr1(JavaBeanDeserializer beanDeserializer2,Class<?> fieldClass2 ){
        Object newObj1;
        if (beanDeserializer2 != null) {

            if (beanDeserializer2.beanInfo.defaultConstructor != null) {
                newObj1 = beanDeserializer2.createInstance(null, fieldClass2);
            } else {
                return false;
            }
        } else {
            newObj1 = new JSONObject();
        }

        return newObj1;
    }

    private JavaBeanDeserializer extr(Segment seg1, JavaBeanDeserializer beanDeserializer1, Object parentObject1){
        if (seg1 instanceof PropertySegment) {
            String propertyName = ((PropertySegment) seg1).propertyName;
            Class<?> parentClass = parentObject1.getClass();
            JavaBeanDeserializer parentBeanDeserializer = getJavaBeanDeserializer(parentClass);
            if (parentBeanDeserializer != null) {
                FieldDeserializer fieldDeserializer = parentBeanDeserializer.getFieldDeserializer(propertyName);
                Class<?> fieldClass1 = fieldDeserializer.fieldInfo.fieldClass;
                beanDeserializer1 = getJavaBeanDeserializer(fieldClass1);
            }
        }
        return beanDeserializer1;
    }
    private Segment extracted13(int i, Segment nextSegment) {
        if (i < segments.length - 1) {
            nextSegment = segments[i + 1];
        }
        return nextSegment;
    }

    public static Object eval(Object rootObject, String path) {
        JSONPath jsonpath = compile(path);
        return jsonpath.eval(rootObject);
    }

    public static Object eval(Object rootObject, String path, boolean ignoreNullValue) {
        JSONPath jsonpath = compile(path, ignoreNullValue);
        return jsonpath.eval(rootObject);
    }

    public static int size(Object rootObject, String path) {
        JSONPath jsonpath = compile(path);
        Object result = jsonpath.eval(rootObject);
        return jsonpath.evalSize(result);
    }

    /**
     * Compile jsonPath and use it to extract keySet or field names from rootObject.
     *
     * @param rootObject Can be a map or custom object. Array and Collection are not supported.
     * @param path JSONPath string to be compiled.
     * @return Set of keys, or <code>null</code> if not supported.
     */
    public static Set<String> keySet(Object rootObject, String path) {
        JSONPath jsonpath = compile(path);
        Object result = jsonpath.eval(rootObject);
        return jsonpath.evalKeySet(result);
    }

    public static boolean contains(Object rootObject, String path) {
        if (rootObject == null) {
            return false;
        }

        JSONPath jsonpath = compile(path);
        return jsonpath.contains(rootObject);
    }

    public static boolean containsValue(Object rootObject, String path, Object value) {
        JSONPath jsonpath = compile(path);
        return jsonpath.containsValue(rootObject, value);
    }

    public static void arrayAdd(Object rootObject, String path, Object... values) {
        JSONPath jsonpath = compile(path);
        jsonpath.arrayAdd(rootObject, values);
    }

    public static boolean set(Object rootObject, String path, Object value) {
        JSONPath jsonpath = compile(path);
        return jsonpath.set(rootObject, value);
    }

    public static boolean remove(Object root, String path) {
        JSONPath jsonpath = compile(path);
        return jsonpath.remove(root);
    }

    public static JSONPath compile(String path) {
        if (path == null) {
            throw new JSONPathException("jsonpath can not be null");
        }

        JSONPath jsonpath = pathCache.get(path);
        if (jsonpath == null) {
            jsonpath = new JSONPath(path);
            if (pathCache.size() < 1024) {
                pathCache.putIfAbsent(path, jsonpath);
                jsonpath = pathCache.get(path);
            }
        }
        return jsonpath;
    }

    public static JSONPath compile(String path, boolean ignoreNullValue) {
        if (path == null) {
            throw new JSONPathException("jsonpath can not be null");
        }

        JSONPath jsonpath = pathCache.get(path);
        if (jsonpath == null) {
            jsonpath = new JSONPath(path, ignoreNullValue);
            if (pathCache.size() < 1024) {
                pathCache.putIfAbsent(path, jsonpath);
                jsonpath = pathCache.get(path);
            }
        }
        return jsonpath;
    }

    /**
     * @since 1.2.9
     * @param json
     * @param path
     * @return
     */
    public static Object read(String json, String path) {
        return compile(path)
                .eval(
                        JSON.parse(json)
                );
    }

    /**
     * @since 1.2.76
     * @param json
     * @param path
     * @param clazz
     * @param parserConfig
     * @return
     */
    public static <T> T read(String json, String path, Type clazz, ParserConfig parserConfig) {
        return compile(path).eval(JSON.parse(json), clazz, parserConfig);
    }

    /**
     * @since 1.2.76
     * @param json
     * @param path
     * @param clazz
     * @return
     */
    public static <T> T read(String json, String path, Type clazz) {
        return read(json, path, clazz, null);
    }

    /**
     * @since 1.2.51
     * @param json
     * @param path
     * @return
     */
    public static Object extract(String json, String path, ParserConfig config, int features) {
        features |= Feature.ORDERED_FIELD.mask;
        DefaultJSONParser parser = new DefaultJSONParser(json, config, features);
        JSONPath jsonPath = compile(path);
        Object result = jsonPath.extract(parser);
        parser.lexer.close();
        return result;
    }

    public static Object extract(String json, String path) {
        return extract(json, path, ParserConfig.global, JSON.DEFAULT_PARSER_FEATURE);
    }

    public static Map<String, Object> paths(Object javaObject) {
        return paths(javaObject, SerializeConfig.globalInstance);
    }

    public static Map<String, Object> paths(Object javaObject, SerializeConfig config) {
        Map<Object, String> values = new IdentityHashMap<>();
        Map<String, Object> paths = new HashMap<>();

        paths(values, paths, "/", javaObject, config);
        return paths;
    }

    private static void paths(Map<Object, String> values, Map<String, Object> paths, String parent, Object javaObject, SerializeConfig config) {
        if (javaObject == null) {
            return;
        }

        String p = values.put(javaObject, parent);
        if (p != null) {
            Class<?> type = javaObject.getClass();
            boolean basicType =  extracted45(javaObject, type);

            if (!basicType) {
                return;
            }
        }

        paths.put(parent, javaObject);

        if (javaObject instanceof Map) {
            Map map = (Map) javaObject;

            extracted40(values, paths, parent, config, map);
            return;
        }

        if (javaObject instanceof Collection) {
            Collection<?> collection = (Collection) javaObject;

            int i = 0;
            extracted41(values, paths, parent, config, collection, i);

            return;
        }

        Class<?> clazz = javaObject.getClass();

        if (clazz.isArray()) {
            int len = Array.getLength(javaObject);

            extracted42(values, paths, parent, javaObject, config, len);

            return;
        }

        if (ParserConfig.isPrimitive2(clazz) || clazz.isEnum()) {
            return;
        }

        ObjectSerializer serializer = config.getObjectWriter(clazz);
        if (serializer instanceof JavaBeanSerializer) {
            JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) serializer;

            extracted44(values, paths, parent, javaObject, config, javaBeanSerializer);

        }

    }

    private static boolean extracted45(Object javaObject, Class<?> type) {
        return type == String.class
                || type == Boolean.class
                || type == Character.class
                || type == UUID.class
                || type.isEnum()
                || javaObject instanceof Number
                || javaObject instanceof Date;
    }

    private static void extracted44(Map<Object, String> values, Map<String, Object> paths, String parent,
                                    Object javaObject, SerializeConfig config, JavaBeanSerializer javaBeanSerializer) throws JSONException {
        try {
            Map<String, Object> fieldValues = javaBeanSerializer.getFieldValuesMap(javaObject);
            for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                String key = entry.getKey();

                extracted43(values, paths, parent, config, entry, key);
            }
        } catch (Exception e) {
            throw new JSONException("toJSON error", e);
        }
    }

    private static void extracted43(Map<Object, String> values, Map<String, Object> paths, String parent,
                                    SerializeConfig config, Map.Entry<String, Object> entry, String key) {
        if (key instanceof String) {
            String path = parent.equals("/") ?  key : parent + key;
            paths(values, paths, path, entry.getValue(), config);
        }
    }

    private static void extracted42(Map<Object, String> values, Map<String, Object> paths, String parent,
                                    Object javaObject, SerializeConfig config, int len) {
        for (int i = 0; i < len; ++i) {
            Object item = Array.get(javaObject, i);

            String path = parent.equals("/") ?   String.valueOf(i) : parent +  i;
            paths(values, paths, path, item, config);
        }
    }

    private static void extracted41(Map<Object, String> values, Map<String, Object> paths, String parent,
                                    SerializeConfig config, Collection<?> collection, int i) {
        for (Object item : collection) {
            String path = parent.equals("/") ? String.valueOf(i) : parent +  i;
            paths(values, paths, path, item, config);
            ++i;
        }
    }

    private static void extracted40(Map<Object, String> values, Map<String, Object> paths, String parent,
                                    SerializeConfig config, Map<Object, ?> map) {
        for (Map.Entry<?,?> entryObj : map.entrySet()) {
            Map.Entry entry =  entryObj;
            Object key = entry.getKey();

            extracted39(values, paths, parent, config, entry, key);
        }
    }

    private static void extracted39(Map<Object, String> values, Map<String, Object> paths, String parent,
                                    SerializeConfig config, Map.Entry<?, ?> entry, Object key) {
        if (key instanceof String) {
            String path = parent.equals("/") ?   key.toString() : parent +  key;
            paths(values, paths, path, entry.getValue(), config);
        }
    }

    public String getPath() {
        return path;
    }

    static class JSONPathParser {

        private final String path;
        private int          pos;
        private char         ch;
        private int          level;
        private boolean      hasRefSegment;
        private static final String STR_ARRAY_REGEX = "\'\\s*,\\s*\'";
        private static final Pattern strArrayPatternx = Pattern.compile(STR_ARRAY_REGEX);

        public JSONPathParser(String path){
            this.path = path;
            next();
        }

        void next() {
            ch = path.charAt(pos++);
        }

        char getNextChar() {
            return path.charAt(pos);
        }

        boolean isEOF() {
            return pos >= path.length();
        }

        Segment readSegement() {
            if (extracted19()) {
                if (isDigitFirst(ch)) {
                    int index = ch - '0';
                    return new ArrayAccessSegment(index);
                } else if (extracted18()) {
                    return new PropertySegment(Character.toString(ch), false);
                }
            }

            skipWhitespace();

            if (ch == '$') {
                next();
                skipWhitespace();
                if (ch == '?') {
                    return new FilterSegment(
                            (Filter) parseArrayAccessFilter(false));
                }
            }

            if (extracted25()) {
                int c0 = ch;
                boolean deep = false;
                next();
                deep = extracted21(c0, deep);
                if (extracted26(deep)) {
                    boolean objectOnly = ch == '[';
                    extracted24();

                    return extracted23(deep, objectOnly);
                }

                if (isDigitFirst(ch)) {
                    return parseArrayAccess(false);
                }

                String propertyName = readName();
                if (ch == '(') {
                    next();

                    throw new JSONPathException(NOT_SUPPORT_JSON_PATH + path);
                }

                return new PropertySegment(propertyName, deep);
            }

            if (ch == '[') {
                return parseArrayAccess(true);
            }

            return null;
        }

        private boolean extracted26(boolean deep) {
            return ch == '*' || (deep && ch == '[');
        }

        private boolean extracted25() {
            return ch == '.' || ch == '/';
        }

        private void extracted24() {
            if (!isEOF()) {
                next();
            }
        }

        private Segment extracted23(boolean deep, boolean objectOnly) {
            if (deep) {
                return extracted22(objectOnly);
            } else {
                return WildCardSegment.instance;
            }
        }

        private Segment extracted22(boolean objectOnly) {
            if (objectOnly) {
                return WildCardSegment.instance_deep_objectOnly;
            } else {
                return WildCardSegment.instance_deep;
            }
        }

        private boolean extracted21(int c0, boolean deep) {
            if (c0 == '.' && ch == '.') {
                next();
                deep = true;
                extracted20();
            }
            return deep;
        }

        private void extracted20() {
            if (path.length() > pos + 3
                    && ch == '['
                    && path.charAt(pos) == '*'
                    && path.charAt(pos + 1) == ']'
                    && path.charAt(pos + 2) == '.') {
                next();
                next();
                next();
                next();
            }
        }

        private boolean extracted19() {
            return level == 0 && path.length() == 1;
        }

        private boolean extracted18() {
            return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
        }

        public final void skipWhitespace() {
            for (;;) {
                if (ch <= ' ' && (ch == ' ' || ch == '\r' || ch == '\n' || ch == '\t' || ch == '\f' || ch == '\b')) {
                    next();
                } else {
                    break;
                }
            }
        }

        Segment parseArrayAccess(boolean acceptBracket) {
            Object object = parseArrayAccessFilter(acceptBracket);
            if (object instanceof Segment) {
                return ((Segment) object);
            }
            return new FilterSegment((Filter) object);
        }

        Object parseArrayAccessFilter(boolean acceptBracket) {
            if (acceptBracket) {
                accept('[');
            }

            boolean predicateFlag = false;
            int lparanCount = 0;

            if (ch == '?') {
                next();
                accept('(');
                lparanCount++;
                while (ch == '(') {
                    next();
                    lparanCount++;
                }
                predicateFlag = true;
            }

            skipWhitespace();

            if (predicateFlag
                    || IOUtils.firstIdentifier(ch)
                    || Character.isJavaIdentifierStart(ch)
                    || ch == '\\'
                    || ch == '@') {
                if (ch == '@') {
                    next();
                    accept('.');
                }
                String propertyName = readName();

                skipWhitespace();

                if (predicateFlag && ch == ')') {
                    next();

                    Filter filter = new NotNullSegement(propertyName, false);
                    while (ch == ' ') {
                        next();
                    }

                    if (ch == '&' || ch == '|') {
                        filter = filterRest(filter);
                    }

                    if (acceptBracket) {
                        accept(']');
                    }
                    return filter;
                }

                if (acceptBracket && ch == ']') {
                    if (isEOF() &&  (propertyName.equals("last"))) {
                        return new MultiIndexSegment(new int[]{-1});
                    }

                    next();
                    Filter filter = new NotNullSegement(propertyName, false);
                    while (ch == ' ') {
                        next();
                    }

                    if (ch == '&' || ch == '|') {
                        filter = filterRest(filter);
                    }

                    accept(')');
                    if (predicateFlag) {
                        accept(')');
                    }

                    if (acceptBracket) {
                        accept(']');
                    }
                    return filter;
                }

                boolean function = false;
                skipWhitespace();
                if (ch == '(') {
                    next();
                    accept(')');
                    skipWhitespace();
                    function = true;
                }

                Operator op = readOp();

                skipWhitespace();

                if (op == Operator.BETWEEN || op == Operator.NOT_BETWEEN) {
                    final boolean not = (op == Operator.NOT_BETWEEN);

                    Object startValue = readValue();

                    String name = readName();

                    if (!"and".equalsIgnoreCase(name)) {
                        throw new JSONPathException(path);
                    }

                    Object endValue = readValue();

                    if (startValue == null || endValue == null) {
                        throw new JSONPathException(path);
                    }

                    if (isInt(startValue.getClass()) && isInt(endValue.getClass())) {
                        return new IntBetweenSegement(propertyName
                                , function
                                , TypeUtils.longExtractValue((Number) startValue)
                                , TypeUtils.longExtractValue((Number) endValue)
                                , not);
                    }

                    throw new JSONPathException(path);
                }

                if (op == Operator.IN || op == Operator.NOT_IN) {
                    final boolean not = (op == Operator.NOT_IN);
                    accept('(');

                    List<Object> valueList = new JSONArray();
                    extracted27(valueList);

                    boolean isInt = true;
                    boolean isIntObj = true;
                    boolean isString = true;
                    for (Object item : valueList) {
                        if (item == null) {
                            if (isInt) {
                                isInt = false;
                            }
                            continue;
                        }

                        Class<?> clazz = item.getClass();
                        if (isInt && !(clazz == Byte.class || clazz == Short.class || clazz == Integer.class
                                || clazz == Long.class)) {
                            isInt = false;
                            isIntObj = false;
                        }

                        if (isString && clazz != String.class) {
                            isString = false;
                        }
                    }

                    if (valueList.size() == 1 && valueList.get(0) == null) {
                        Filter filter;
                        if (not) {
                            filter = new NotNullSegement(propertyName, function);
                        } else {
                            filter = new NullSegement(propertyName, function);
                        }

                        while (ch == ' ') {
                            next();
                        }

                        if (ch == '&' || ch == '|') {
                            filter = filterRest(filter);
                        }

                        accept(')');
                        if (predicateFlag) {
                            accept(')');
                        }

                        if (acceptBracket) {
                            accept(']');
                        }

                        return filter;
                    }

                    if (isInt) {
                        if (valueList.size() == 1) {
                            long value = TypeUtils.longExtractValue((Number) valueList.get(0));
                            Operator intOp = not ? Operator.NE : Operator.EQ;
                            Filter filter = new IntOpSegement(propertyName, function, value, intOp);
                            while (ch == ' ') {
                                next();
                            }

                            if (ch == '&' || ch == '|') {
                                filter = filterRest(filter);
                            }

                            accept(')');
                            if (predicateFlag) {
                                accept(')');
                            }

                            if (acceptBracket) {
                                accept(']');
                            }

                            return filter;
                        }

                        long[] values = new long[valueList.size()];
                        for (int i = 0; i < values.length; ++i) {
                            values[i] = TypeUtils.longExtractValue((Number) valueList.get(i));
                        }

                        Filter filter = new IntInSegement(propertyName, function, values, not);

                        while (ch == ' ') {
                            next();
                        }

                        if (ch == '&' || ch == '|') {
                            filter = filterRest(filter);
                        }

                        accept(')');
                        if (predicateFlag) {
                            accept(')');
                        }

                        if (acceptBracket) {
                            accept(']');
                        }

                        return filter;
                    }

                    if (isString) {
                        if (valueList.size() == 1) {
                            String value = (String) valueList.get(0);

                            Operator intOp = not ? Operator.NE : Operator.EQ;
                            Filter filter = new StringOpSegement(propertyName, function, value, intOp);

                            while (ch == ' ') {
                                next();
                            }

                            if (ch == '&' || ch == '|') {
                                filter = filterRest(filter);
                            }

                            accept(')');
                            if (predicateFlag) {
                                accept(')');
                            }

                            if (acceptBracket) {
                                accept(']');
                            }

                            return filter;
                        }

                        String[] values = new String[valueList.size()];
                        valueList.toArray(values);

                        Filter filter = new StringInSegement(propertyName, function, values, not);

                        while (ch == ' ') {
                            next();
                        }

                        if (ch == '&' || ch == '|') {
                            filter = filterRest(filter);
                        }

                        accept(')');
                        if (predicateFlag) {
                            accept(')');
                        }

                        if (acceptBracket) {
                            accept(']');
                        }

                        return filter;
                    }

                    if (isIntObj) {
                        Long[] values = new Long[valueList.size()];
                        for (int i = 0; i < values.length; ++i) {
                            Number item = (Number) valueList.get(i);
                            if (item != null) {
                                values[i] = TypeUtils.longExtractValue(item);
                            }
                        }

                        Filter filter = new IntObjInSegement(propertyName, function, values, not);

                        while (ch == ' ') {
                            next();
                        }

                        if (ch == '&' || ch == '|') {
                            filter = filterRest(filter);
                        }

                        accept(')');
                        if (predicateFlag) {
                            accept(')');
                        }

                        if (acceptBracket) {
                            accept(']');
                        }

                        return filter;
                    }

                    throw new UnsupportedOperationException();
                }

                if (ch == '\'' || ch == '"') {
                    String strValue = readString();

                    Filter filter = null;
                    if (op == Operator.RLIKE) {
                        filter = new RlikeSegement(propertyName, function, strValue, false);
                    } else if (op == Operator.NOT_RLIKE) {
                        filter = new RlikeSegement(propertyName, function, strValue, true);
                    } else  if (op == Operator.LIKE || op == Operator.NOT_LIKE) {
                        while (strValue.indexOf("%%") != -1) {
                            strValue = strValue.replace("%%", "%");
                        }

                        final boolean not = (op == Operator.NOT_LIKE);

                        int p0 = strValue.indexOf('%');
                        if (p0 == -1) {
                            if (op == Operator.LIKE) {
                                op = Operator.EQ;
                            } else {
                                op = Operator.NE;
                            }
                            filter = new StringOpSegement(propertyName, function, strValue, op);
                        } else {
                            String[] items = strValue.split("%");

                            String startsWithValue = null;
                            String endsWithValue = null;
                            String[] containsValues = null;
                            if (p0 == 0) {
                                if (strValue.charAt(strValue.length() - 1) == '%') {
                                    containsValues = new String[items.length - 1];
                                    System.arraycopy(items, 1, containsValues, 0, containsValues.length);
                                } else {
                                    endsWithValue = items[items.length - 1];
                                    if (items.length > 2) {
                                        containsValues = new String[items.length - 2];
                                        System.arraycopy(items, 1, containsValues, 0, containsValues.length);
                                    }
                                }
                            } else if (strValue.charAt(strValue.length() - 1) == '%') {
                                if (items.length == 1) {
                                    startsWithValue = items[0];
                                } else {
                                    containsValues = items;
                                }
                            } else {
                                if (items.length == 1) {
                                    startsWithValue = items[0];
                                } else if (items.length == 2) {
                                    startsWithValue = items[0];
                                    endsWithValue = items[1];
                                } else {
                                    startsWithValue = items[0];
                                    endsWithValue = items[items.length - 1];
                                    containsValues = new String[items.length - 2];
                                    System.arraycopy(items, 1, containsValues, 0, containsValues.length);
                                }
                            }

                            filter = new MatchSegement(propertyName, function, startsWithValue, endsWithValue,
                                    containsValues, not);
                        }
                    } else {
                        filter = new StringOpSegement(propertyName, function, strValue, op);
                    }

                    while (ch == ' ') {
                        next();
                    }

                    if (ch == '&' || ch == '|') {
                        filter = filterRest(filter);
                    }

                    if (predicateFlag) {
                        accept(')');
                    }

                    if (acceptBracket) {
                        accept(']');
                    }

                    return filter;
                }

                if (isDigitFirst(ch)) {
                    long value = readLongValue();
                    double doubleValue = 0D;
                    if (ch == '.') {
                        doubleValue = readDoubleValue(value);

                    }

                    Filter filter;

                    if (doubleValue == 0) {
                        filter = new IntOpSegement(propertyName, function, value, op);
                    } else {
                        filter = new DoubleOpSegement(propertyName, function, doubleValue, op);
                    }

                    while (ch == ' ') {
                        next();
                    }

                    if (lparanCount > 1 && ch == ')') {
                        next();
                    }

                    if (ch == '&' || ch == '|') {
                        filter = filterRest(filter);
                    }

                    if (predicateFlag) {
                        accept(')');
                    }

                    if (acceptBracket) {
                        accept(']');
                    }

                    return filter;
                } else if (ch == '$') {
                    Segment segment = readSegement();
                    RefOpSegement filter = new RefOpSegement(propertyName, function, segment, op);
                    hasRefSegment = true;
                    while (ch == ' ') {
                        next();
                    }

                    if (predicateFlag) {
                        accept(')');
                    }

                    if (acceptBracket) {
                        accept(']');
                    }

                    return filter;
                } else if (ch == '/') {
                    int flags = 0;
                    StringBuilder regBuf = new StringBuilder();
                    for (;;) {
                        next();
                        if (ch == '/') {
                            next();
                            if (ch == 'i') {
                                next();
                                flags |= Pattern.CASE_INSENSITIVE;
                            }
                            break;
                        }

                        if (ch == '\\') {
                            next();
                            regBuf.append(ch);
                        } else {
                            regBuf.append(ch);
                        }
                    }

                    Pattern pattern = Pattern.compile(regBuf.toString(), flags);
                    RegMatchSegement filter = new RegMatchSegement(propertyName, function, pattern);

                    if (predicateFlag) {
                        accept(')');
                    }

                    if (acceptBracket) {
                        accept(']');
                    }

                    return filter;
                }

                if (ch == 'n') {
                    String name = readName();
                    if ("null".equals(name)) {
                        Filter filter = null;
                        if (op == Operator.EQ) {
                            filter = new NullSegement(propertyName, function);
                        } else if (op == Operator.NE) {
                            filter = new NotNullSegement(propertyName, function);
                        }

                        if (filter != null) {
                            while (ch == ' ') {
                                next();
                            }

                            if (ch == '&' || ch == '|') {
                                filter = filterRest(filter);
                            }
                        }

                        if (predicateFlag) {
                            accept(')');
                        }
                        accept(']');

                        if (filter != null) {
                            return filter;
                        }

                        throw new UnsupportedOperationException();
                    }
                } else if (ch == 't') {
                    String name = readName();

                    if ("true".equals(name)) {
                        Filter filter = null;

                        if (op == Operator.EQ) {
                            filter = new ValueSegment(propertyName, function, Boolean.TRUE, true);
                        } else if (op == Operator.NE) {
                            filter = new ValueSegment(propertyName, function, Boolean.TRUE, false);
                        }

                        if (filter != null) {
                            while (ch == ' ') {
                                next();
                            }

                            if (ch == '&' || ch == '|') {
                                filter = filterRest(filter);
                            }
                        }

                        if (predicateFlag) {
                            accept(')');
                        }
                        accept(']');

                        if (filter != null) {
                            return filter;
                        }

                        throw new UnsupportedOperationException();
                    }
                } else if (ch == 'f') {
                    String name = readName();

                    if ("false".equals(name)) {
                        Filter filter = null;

                        if (op == Operator.EQ) {
                            filter = new ValueSegment(propertyName, function, Boolean.FALSE, true);
                        } else if (op == Operator.NE) {
                            filter = new ValueSegment(propertyName, function, Boolean.FALSE, false);
                        }

                        if (filter != null) {
                            while (ch == ' ') {
                                next();
                            }

                            if (ch == '&' || ch == '|') {
                                filter = filterRest(filter);
                            }
                        }

                        if (predicateFlag) {
                            accept(')');
                        }
                        accept(']');

                        if (filter != null) {
                            return filter;
                        }

                        throw new UnsupportedOperationException();
                    }
                }

                throw new UnsupportedOperationException();
            }

            int start = pos - 1;
            char startCh = ch;
            while (ch != ']' && ch != '/' && !isEOF()) {
                if (ch == '.' //
                        && (!predicateFlag) // 
                        && !predicateFlag
                        && startCh != '\''
                ) {
                    break;
                }

                if (ch == '\\') {
                    next();
                }
                next();
            }

            int end;
            if (acceptBracket) {
                end = pos - 1;
            } else {
                if (ch == '/' || ch == '.') {
                    end = pos - 1;
                } else {
                    end = pos;
                }
            }

            String text = path.substring(start, end);

            if (text.indexOf('\\') != 0) {
                StringBuilder buf = new StringBuilder(text.length());
                for (int i = 0; i < text.length(); ++i) {
                    char ch2 = text.charAt(i);
                    if (ch == '\\' && i < text.length() - 1) {
                        char c2 = text.charAt(i + 1);
                        if (c2 == '@' || ch2 == '\\' || ch2 == '\"') {
                            buf.append(c2);
                            continue;
                        }
                    }

                    buf.append(ch);
                }
                text = buf.toString();
            }

            if (text.indexOf("\\.") != -1) {
                String propName;
                if (startCh == '\'' && text.length() > 2 && text.charAt(text.length() - 1) == startCh) {
                    propName = text.substring(1, text.length() - 1);
                } else {
                    propName = text.replace("\\\\\\.", "\\.");
                    if (propName.indexOf("\\-") != -1) {
                        propName = propName.replace("\\\\-", "-");
                    }
                }

                if (predicateFlag) {
                    accept(')');
                }

                return new PropertySegment(propName, false);
            }

            Segment segment = buildArraySegement(text);

            if (acceptBracket && !isEOF()) {
                accept(']');
            }

            return segment;
        }

        private void extracted27(List<Object> valueList) {
            Object value = readValue();
            valueList.add(value);
            for (;;) {
                skipWhitespace();
                if (ch != ',') {
                    break;
                }
                next();

                value = readValue();
                valueList.add(value);
            }
        }

        Filter filterRest(Filter filter) {
            boolean and = ch == '&';
            if ((ch == '&' && getNextChar() == '&') || (ch == '|' && getNextChar() == '|')) {
                next();
                next();

                boolean paren = false;
                if (ch == '(') {
                    paren = true;
                    next();
                }

                while (ch == ' ') {
                    next();
                }

                Filter right = (Filter) parseArrayAccessFilter(false);

                filter = new FilterGroup(filter, right, and);

                if (paren && ch == ')') {
                    next();
                }
            }
            return filter;
        }

        protected long readLongValue() {
            int beginIndex = pos - 1;
            if (ch == '+' || ch == '-') {
                next();
            }

            while (ch >= '0' && ch <= '9') {
                next();
            }

            int endIndex = pos - 1;
            String text = path.substring(beginIndex, endIndex);
            return Long.parseLong(text);
        }

        protected double readDoubleValue(long longValue) {
            int beginIndex = pos - 1;

            next();
            while (ch >= '0' && ch <= '9') {
                next();
            }

            int endIndex = pos - 1;
            String text = path.substring(beginIndex, endIndex);
            double value = Double.parseDouble(text);
            value += longValue;
            return value;
        }

        protected Object readValue() {
            skipWhitespace();

            if (isDigitFirst(ch)) {
                return readLongValue();
            }

            if (ch == '"' || ch == '\'') {
                return readString();
            }

            if (ch == 'n') {
                String name = readName();

                if ("null".equals(name)) {
                    return null;
                } else {
                    throw new JSONPathException(path);
                }
            }

            throw new UnsupportedOperationException();
        }

        static boolean isDigitFirst(char ch) {
            return ch == '-' || ch == '+' || (ch >= '0' && ch <= '9');
        }

        protected Operator readOp() {
            Operator op = null;
            if (ch == '=') {
                next();
                op = extracted12();
            } else if (ch == '!') {
                next();
                accept('=');
                op = Operator.NE;
            } else if (ch == '<') {
                next();
                op = extracted13();
            } else if (ch == '>') {
                next();
                op = extracted14();
            }

            if (op == null) {
                String name = readName();

                op = extracted17(name);
            }
            return op;
        }

        private Operator extracted17( String name) {
            Operator op;
            if ("not".equalsIgnoreCase(name)) {
                skipWhitespace();

                name = readName();

                op = extracted15(name);
            } else if ("nin".equalsIgnoreCase(name)) {
                op = Operator.NOT_IN;
            } else {
                op = extracted16(name);
            }
            return op;
        }

        private Operator extracted16( String name) {
            Operator op;
            if ("like".equalsIgnoreCase(name)) {
                op = Operator.LIKE;
            } else if ("rlike".equalsIgnoreCase(name)) {
                op = Operator.RLIKE;
            } else if ("in".equalsIgnoreCase(name)) {
                op = Operator.IN;
            } else if ("between".equalsIgnoreCase(name)) {
                op = Operator.BETWEEN;
            } else {
                throw new UnsupportedOperationException();
            }
            return op;
        }

        private Operator extracted15(String name) {
            Operator op;
            if ("like".equalsIgnoreCase(name)) {
                op = Operator.NOT_LIKE;
            } else if ("rlike".equalsIgnoreCase(name)) {
                op = Operator.NOT_RLIKE;
            } else if ("in".equalsIgnoreCase(name)) {
                op = Operator.NOT_IN;
            } else if ("between".equalsIgnoreCase(name)) {
                op = Operator.NOT_BETWEEN;
            } else {
                throw new UnsupportedOperationException();
            }
            return op;
        }

        private Operator extracted14() {
            Operator op;
            if (ch == '=') {
                next();
                op = Operator.GE;
            } else {
                op = Operator.GT;
            }
            return op;
        }

        private Operator extracted13() {
            Operator op;
            if (ch == '=') {
                next();
                op = Operator.LE;
            } else {
                op = Operator.LT;
            }
            return op;
        }

        private Operator extracted12() {
            Operator op;
            if (ch == '~') {
                next();
                op = Operator.REG_MATCH;
            } else if (ch == '=') {
                next();
                op = Operator.EQ;
            } else {
                op = Operator.EQ;
            }
            return op;
        }

        String readName() {
            skipWhitespace();

            if (ch != '\\' && !Character.isJavaIdentifierStart(ch)) {
                throw new JSONPathException("illeal jsonpath syntax. " + path);
            }

            StringBuilder buf = new StringBuilder();
            while (!isEOF()) {
                if (ch == '\\') {
                    next();
                    buf.append(ch);
                    if (isEOF()) {
                        return buf.toString();
                    }
                    next();
                }

                boolean identifierFlag = Character.isJavaIdentifierPart(ch);
                if (!identifierFlag) {
                    break;
                }
                buf.append(ch);
                next();
            }

            if (isEOF() && Character.isJavaIdentifierPart(ch)) {
                buf.append(ch);
            }

            return buf.toString();
        }

        String readString() {
            char quoate = ch;
            next();

            int beginIndex = pos - 1;
            while (ch != quoate && !isEOF()) {
                next();
            }

            String strValue = path.substring(beginIndex, isEOF() ? pos : pos - 1);

            accept(quoate);

            return strValue;
        }

        void accept(char expect) {
            if (ch == ' ') {
                next();
            }

            if (ch != expect) {
                throw new JSONPathException("expect '" + expect + ", but '" + ch + "'");
            }

            extracted24();
        }

        public Segment[] explain() {
            if (path == null || path.length() == 0) {
                throw new IllegalArgumentException();
            }

            Segment[] segments = new Segment[8];

            for (;;) {
                Segment segment = readSegement();
                if (segment == null) {
                    break;
                }

                if (segment instanceof PropertySegment) {
                    PropertySegment propertySegment = (PropertySegment) segment;
                    if ((!propertySegment.deep) && propertySegment.propertyName.equals("*")) {
                        //skip
                    }
                }

                if (level == segments.length) {
                    Segment[] t = new Segment[level * 3 / 2];
                    System.arraycopy(segments, 0, t, 0, level);
                    segments = t;
                }
                segments[level++] = segment;
            }

            if (level == segments.length) {
                return segments;
            }

            Segment[] result = new Segment[level];
            System.arraycopy(segments, 0, result, 0, level);
            return result;
        }

        Segment buildArraySegement(String indexText) {
            final int indexTextLen = indexText.length();
            final char firstChar = indexText.charAt(0);
            final char lastChar = indexText.charAt(indexTextLen - 1);

            int commaIndex = indexText.indexOf(',');

            if (extracted(indexText, firstChar, lastChar)) {

                String propertyName = indexText.substring(1, indexTextLen - 1);

                if (commaIndex == -1 || !strArrayPatternx.matcher(indexText).find()) {
                    return new PropertySegment(propertyName, false);
                }

                String[] propertyNames = propertyName.split(STR_ARRAY_REGEX);
                return new MultiPropertySegment(propertyNames);
            }

            int colonIndex = indexText.indexOf(':');

            if (commaIndex == -1 && colonIndex == -1) {
                if (TypeUtils.isNumber(indexText)) {
                    return extracted2(indexText);
                } else {
                    indexText = extracted3(indexText);
                    return new PropertySegment(indexText, false);
                }
            }

            if (commaIndex != -1) {
                String[] indexesText = indexText.split(",");
                int[] indexes = new int[indexesText.length];
                extracted4(indexesText, indexes);
                return new MultiIndexSegment(indexes);
            }

            if (colonIndex != -1) {
                String[] indexesText = indexText.split(":");
                int[] indexes = new int[indexesText.length];
                extracted7(indexesText, indexes);

                int start = indexes[0];
                int end;
                end = extracted8(indexes);
                int step;
                step = extracted9(indexes);

                extracted10(start, end);

                extracted11(step);
                return new RangeSegment(start, end, step);
            }

            throw new UnsupportedOperationException();
        }

        private void extracted11(int step) {
            if (step <= 0) {
                throw new UnsupportedOperationException("step must greater than zero : " + step);
            }
        }

        private void extracted10(int start, int end) {
            if (end >= 0 && end < start) {
                throw new UnsupportedOperationException("end must greater than or equals start. start " + start
                        + ",  end " + end);
            }
        }

        private int extracted9(int[] indexes) {
            int step;
            if (indexes.length == 3) {
                step = indexes[2];
            } else {
                step = 1;
            }
            return step;
        }

        private int extracted8(int[] indexes) {
            int end;
            if (indexes.length > 1) {
                end = indexes[1];
            } else {
                end = -1;
            }
            return end;
        }

        private void extracted7(String[] indexesText, int[] indexes) {
            for (int i = 0; i < indexesText.length; ++i) {
                String str = indexesText[i];
                extracted6(indexes, i, str);
            }
        }

        private void extracted6(int[] indexes, int i, String str) {
            if (str.length() == 0) {
                extracted5(indexes, i);
            } else {
                indexes[i] = Integer.parseInt(str);
            }
        }

        private void extracted5(int[] indexes, int i) {
            if (i == 0) {
                indexes[i] = 0;
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private void extracted4(String[] indexesText, int[] indexes) {
            for (int i = 0; i < indexesText.length; ++i) {
                indexes[i] = Integer.parseInt(indexesText[i]);
            }
        }

        private String extracted3(String indexText) {
            if (indexText.charAt(0) == '"' && indexText.charAt(indexText.length() - 1) == '"') {
                indexText = indexText.substring(1, indexText.length() - 1);
            }
            return indexText;
        }

        private Segment extracted2(String indexText) {
            try {
                int index = Integer.parseInt(indexText);
                return new ArrayAccessSegment(index);
            }catch (NumberFormatException ex){
                return new PropertySegment(indexText, false); // fix ISSUE-1208
            }
        }

        private boolean extracted(String indexText, final char firstChar, final char lastChar) {
            return indexText.length() > 2 && firstChar == '\'' && lastChar == '\'';
        }
    }

    interface Segment {

        Object eval(JSONPath path, Object rootObject, Object currentObject);
        void extract(JSONPath path, DefaultJSONParser parser, Context context);
    }


    static class SizeSegment implements Segment {
        public static final SizeSegment instance = new SizeSegment();
        public Integer eval(JSONPath path, Object rootObject, Object currentObject) {
            return path.evalSize(currentObject);
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            Object object = parser.parse();
            context.object = path.evalSize(object);
        }
    }

    static class TypeSegment implements Segment {
        public static final TypeSegment instance = new TypeSegment();

        public String eval(JSONPath path, Object rootObject, Object currentObject) {
            if (currentObject == null) {
                return "null";
            }

            if (currentObject instanceof Collection) {
                return "array";
            }

            if (currentObject instanceof Number) {
                return "number";
            }

            if (currentObject instanceof Boolean) {
                return "boolean";
            }

            if (currentObject instanceof String
                    || currentObject instanceof UUID
                    || currentObject instanceof Enum) {
                return "string";
            }

            return "object";
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    static class FloorSegment implements Segment {
        public static final FloorSegment instance = new FloorSegment();
        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            if (currentObject instanceof JSONArray) {
                JSONArray array = ((JSONArray)  currentObject);
                for (int i = 0; i < array.size(); i++) {
                    Object item = array.get(i);
                    Object newItem = floor(item);
                    if (newItem != item) {
                        array.set(i , newItem);
                    }
                }
                return array;
            }

            return floor(currentObject);
        }

        private static Object floor(Object item) {
            if (item == null) {
                return null;
            }

            if (item instanceof Float) {
                return Math.floor((Float) item);
            }

            if (item instanceof Double) {
                return Math.floor((Double) item);
            }

            if (item instanceof BigDecimal) {
                BigDecimal decimal = (BigDecimal) item;
                return decimal.setScale(0, RoundingMode.FLOOR);
            }

            if (item instanceof Byte
                    || item instanceof Short
                    || item instanceof Integer
                    || item instanceof Long
                    || item instanceof BigInteger) {
                return item;
            }

            throw new UnsupportedOperationException();
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    static class MaxSegment implements Segment {

        public static final MaxSegment instance = new MaxSegment();

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            Object max = null;
            if (currentObject instanceof Collection) {
                Iterator<?> iterator = ((Collection) currentObject).iterator();
                while (iterator.hasNext()) {
                    Object next = iterator.next();
                    if (next == null) {
                        continue;
                    }

                    if (max == null) {
                        max = next;
                    }
                }
            } else {
                throw new UnsupportedOperationException();
            }

            return max;
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    static class MinSegment implements Segment {
        public static final MinSegment instance = new MinSegment();

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            Object min = null;
            if (currentObject instanceof Collection) {
                Iterator<?> iterator = ((Collection) currentObject).iterator();
                while (iterator.hasNext()) {
                    Object next = iterator.next();
                    if (next == null) {
                        continue;
                    }

                    if (min == null) {
                        min = next;
                    }
                }
            } else {
                throw new UnsupportedOperationException();
            }

            return min;
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    static int compare(Object a, Object b) {
        if (a.getClass() == b.getClass()) {
            return ((Comparable) a).compareTo(b);
        }

        Class<?> typeA = a.getClass();
        Class<?> typeB = b.getClass();

        if (typeA == BigDecimal.class) {
            b = extracted34(b, typeB);
        } else if (typeA == Long.class) {
            a = extracted37(a, typeB);
        } else if (typeA == Integer.class) {
            a = extracted35(a, typeB);
        }

        return ((Comparable) a).compareTo(b);
    }


    private static Object extracted37(Object a, Class<?> typeB) {
        if (typeB == BigDecimal.class) {
            a = new BigDecimal((Long) a);
        }
        return a;
    }

    private static Object extracted35(Object a, Class<?> typeB) {
        if (typeB == BigDecimal.class) {
            a = new BigDecimal((Integer) a);
        }
        return a;
    }

    private static Object extracted34(Object b, Class<?> typeB) {
        if (typeB == Integer.class) {
            b = new BigDecimal((Integer) b);
        } else if (typeB == Long.class) {
            b = new BigDecimal((Long) b);
        } else if (typeB == Float.class) {
            b = BigDecimal.valueOf((Float) b);
        } else if (typeB == Double.class) {
            b = BigDecimal.valueOf((Double) b);
        }
        return b;
    }

    static class KeySetSegment implements Segment {

        public static final KeySetSegment instance = new KeySetSegment();

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            return path.evalKeySet(currentObject);
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    static class PropertySegment implements Segment {

        private final String  propertyName;
        private final long    propertyNameHash;
        private final boolean deep;

        public PropertySegment(String propertyName, boolean deep){
            this.propertyName = propertyName;
            this.propertyNameHash = TypeUtils.fnv1a64(propertyName);
            this.deep = deep;
        }

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            if (deep) {
                List<Object> results = new ArrayList<>();
                path.deepScan(currentObject, propertyName, results);
                return results;
            } else {

                return path.getPropertyValue(currentObject, propertyName, propertyNameHash);
            }
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            JSONLexerBase lexer = (JSONLexerBase) parser.lexer;

            if (deep && context.object == null) {
                context.object = new JSONArray();
            }

            if (lexer.token() == JSONToken.LBRACKET) {
                if ("*".equals(propertyName)) {
                    return;
                }

                lexer.nextToken();
                JSONArray array;

                array = extracted(context);
                for (;;) {
                    switch (lexer.token()) {
                        case JSONToken.LBRACE: {

                            break;
                        }
                        case JSONToken.LBRACKET:
                            extracted17(path, parser, context, lexer);
                            break;
                        case JSONToken.LITERAL_STRING:
                        case JSONToken.LITERAL_INT:
                        case JSONToken.LITERAL_FLOAT:
                        case JSONToken.LITERAL_ISO8601_DATE:
                        case JSONToken.TRUE:
                        case JSONToken.FALSE:
                        case JSONToken.NULL:
                            lexer.nextToken();
                            break;
                        default:
                            break;
                    }

                    if (lexer.token() == JSONToken.RBRACKET) {
                        lexer.nextToken();
                        break;
                    } else if (lexer.token() == JSONToken.COMMA) {
                        lexer.nextToken();
                    }
                }

                extracted16(context, array);
                return;
            }

            if (!deep) {
                int matchStat = lexer.seekObjectToField(propertyNameHash, deep);
                extracted14(parser, context, lexer, matchStat);
                return;
            }

            // deep
            extracted10(path, parser, context, lexer);
        }

        private void extracted17(JSONPath path, DefaultJSONParser parser, Context context, JSONLexerBase lexer) {
            if (deep) {
                extract(path, parser, context);
            } else {
                lexer.skipObject(false);
            }
        }

        private void extracted16(Context context, JSONArray array) {
            if (!deep) {
                extracted15(context, array);
            }
        }

        private void extracted15(Context context, JSONArray array) {
            if(!array.isEmpty()) {
                context.object = array;
            }
        }

        private void extracted14(DefaultJSONParser parser, Context context, JSONLexerBase lexer, int matchStat) {
            if (matchStat == JSONLexer.VALUE) {
                extracted13(parser, context, lexer);
            }
        }

        private void extracted13(DefaultJSONParser parser, Context context, JSONLexerBase lexer) {
            if (context.eval) {
                Object value;
                value = extracted7(parser, lexer);

                extracted11(context, value);
            }
        }


        private void extracted11(Context context, Object value) {
            if (context.eval) {
                context.object = value;
            }
        }

        private void extracted10(JSONPath path, DefaultJSONParser parser, Context context, JSONLexerBase lexer) {
            for (;;) {
                int matchStat = lexer.seekObjectToField(propertyNameHash, deep);
                if (matchStat == JSONLexer.NOT_MATCH) {
                    break;
                }

                extracted9(path, parser, context, lexer, matchStat);
            }
        }

        private void extracted9(JSONPath path, DefaultJSONParser parser, Context context, JSONLexerBase lexer,
                                int matchStat) {
            if (matchStat == JSONLexer.VALUE) {
                extracted8(parser, context, lexer);
            } else if (matchStat == JSONLexer.OBJECT || matchStat == JSONLexer.ARRAY) {
                extract(path, parser, context);
            }
        }

        private void extracted8(DefaultJSONParser parser, Context context, JSONLexerBase lexer) {
            if (context.eval) {
                Object value;
                value = extracted7(parser, lexer);

                extracted6(context, value);
            }
        }

        private Object extracted7(DefaultJSONParser parser, JSONLexerBase lexer) {
            Object value;
            switch (lexer.token()) {
                case JSONToken.LITERAL_INT:
                    value = lexer.integerValue();
                    lexer.nextToken(JSONToken.COMMA);
                    break;
                case JSONToken.LITERAL_FLOAT:
                    value = lexer.decimalValue();
                    lexer.nextToken(JSONToken.COMMA);
                    break;
                case JSONToken.LITERAL_STRING:
                    value = lexer.stringVal();
                    lexer.nextToken(JSONToken.COMMA);
                    break;
                default:
                    value = parser.parse();
                    break;
            }
            return value;
        }

        private void extracted6(Context context, Object value) {
            if (context.eval) {
                extracted5(context, value);
            }
        }

        private void extracted5(Context context, Object value) {
            if (context.object instanceof List) {
                List<Object> list = (List) context.object;
                extracted4(context, value, list);
            } else {
                context.object = value;
            }
        }

        private void extracted4(Context context, Object value, List<Object> list) {
            if (list.isEmpty() && value instanceof List) {
                context.object = value;
            } else {
                list.add(value);
            }
        }



        private JSONArray extracted(Context context) {
            JSONArray array;
            if (deep) {
                array =(JSONArray) context.object;
            } else {
                array = new JSONArray();
            }
            return array;
        }

        public void setValue(JSONPath path, Object parent, Object value) {
            if (deep) {
                path.deepSet(parent, propertyName, propertyNameHash, value);
            } else {
                path.setPropertyValue(parent, propertyName, propertyNameHash, value);
            }
        }

        public boolean remove(JSONPath path, Object parent) {
            return path.removePropertyValue(parent, propertyName, deep);
        }
    }

    static class MultiPropertySegment implements Segment {

        private final String[] propertyNames;
        private final long[]   propertyNamesHash;

        public MultiPropertySegment(String[] propertyNames){
            this.propertyNames = propertyNames;
            this.propertyNamesHash = new long[propertyNames.length];
            for (int i = 0; i < propertyNamesHash.length; i++) {
                propertyNamesHash[i] = TypeUtils.fnv1a64(propertyNames[i]);
            }
        }

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            List<Object> fieldValues = new ArrayList<>(propertyNames.length);

            for (int i = 0; i < propertyNames.length; i++) {
                Object fieldValue = path.getPropertyValue(currentObject, propertyNames[i], propertyNamesHash[i]);
                fieldValues.add(fieldValue);
            }

            return fieldValues;
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            JSONLexerBase lexer = (JSONLexerBase) parser.lexer;

            JSONArray array;
            if (context.object == null) {
                context.object = array = new JSONArray();
            } else {
                array = (JSONArray) context.object;
            }
            for (int i = array.size(); i < propertyNamesHash.length; ++i) {
                array.add(null);
            }




            for (;;) {
                int index = lexer.seekObjectToField(propertyNamesHash);
                int matchStat = lexer.matchStat;
                if (matchStat == JSONLexer.VALUE) {
                    Object value;
                    switch (lexer.token()) {
                        case JSONToken.LITERAL_INT:
                            value = lexer.integerValue();
                            lexer.nextToken(JSONToken.COMMA);
                            break;
                        case JSONToken.LITERAL_FLOAT:
                            value = lexer.decimalValue();
                            lexer.nextToken(JSONToken.COMMA);
                            break;
                        case JSONToken.LITERAL_STRING:
                            value = lexer.stringVal();
                            lexer.nextToken(JSONToken.COMMA);
                            break;
                        default:
                            value = parser.parse();
                            break;
                    }

                    array.set(index, value);


                }

                break;
            }
        }
    }

    static class WildCardSegment implements Segment {
        private boolean deep;
        private boolean objectOnly;

        private WildCardSegment(boolean deep, boolean objectOnly) {
            this.deep = deep;
            this.objectOnly = objectOnly;
        }

        public static final WildCardSegment instance = new WildCardSegment(false, false);
        public static final WildCardSegment instance_deep = new WildCardSegment(true, false);
        public static final WildCardSegment instance_deep_objectOnly = new WildCardSegment(true, true);

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            if (!deep) {
                return path.getPropertyValues(currentObject);
            }

            List<Object> values = new ArrayList<>();
            path.deepGetPropertyValues(currentObject, values);
            return values;
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            if (context.eval) {
                Object object = parser.parse();
                if (deep) {
                    List<Object> values = new ArrayList<>();
                    if (objectOnly) {
                        path.deepGetObjects(object, values);
                    } else {
                        path.deepGetPropertyValues(object, values);
                    }
                    context.object = values;
                    return;
                }

                if (object instanceof JSONObject) {
                    Collection<Object> values = ((JSONObject) object).values();
                    JSONArray array = new JSONArray(values.size());
                    array.addAll(values);
                    context.object = array;
                    return;
                } else if (object instanceof JSONArray) {
                    context.object = object;
                    return;
                }
            }

            throw new JSONException("TODO");
        }
    }

    static class ArrayAccessSegment implements Segment {

        private final int index;

        public ArrayAccessSegment(int index){
            this.index = index;
        }

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            return path.getArrayItem(currentObject, index);
        }

        public boolean setValue(JSONPath path, Object currentObject, Object value) {
            return path.setArrayItem(currentObject, index, value);
        }

        public boolean remove(JSONPath path, Object currentObject) {
            return path.removeArrayItem(currentObject, index);
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            JSONLexerBase lexer = (JSONLexerBase) parser.lexer;
            if (lexer.seekArrayToItem(index)
                    && context.eval)
            {
                context.object = parser.parse();
            }
        }
    }

    static class MultiIndexSegment implements Segment {

        private final int[] indexes;

        public MultiIndexSegment(int[] indexes){
            this.indexes = indexes;
        }

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            List<Object> items = new JSONArray(indexes.length);
            for (int i = 0; i < indexes.length; ++i) {
                Object item = path.getArrayItem(currentObject, indexes[i]);
                items.add(item);
            }
            return items;
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            if (context.eval) {
                Object object = parser.parse();
                if (object instanceof List) {
                    int[] indexes1 = new int[this.indexes.length];
                    System.arraycopy(this.indexes, 0, indexes1, 0, indexes1.length);
                    boolean noneNegative = indexes1[0] >= 0;

                    List<?> list = (List) object;
                    if (noneNegative) {
                        for (int i = list.size() - 1; i >= 0; i--) {
                            if (Arrays.binarySearch(indexes1, i) < 0) {
                                list.remove(i);
                            }
                        }
                        context.object = list;
                        return;
                    }
                }
            }
            throw new UnsupportedOperationException();
        }
    }

    static class RangeSegment implements Segment {
        private final int start;
        private final int end;
        private final int step;

        public RangeSegment(int start, int end, int step){
            this.start = start;
            this.end = end;
            this.step = step;
        }

        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            int size = SizeSegment.instance.eval(path, rootObject, currentObject);
            int start1 = this.start >= 0 ? this.start : this.start + size;
            int end1 = this.end >= 0 ? this.end : this.end + size;

            int arraySize = (end1 - start1) / step + 1;
            if (arraySize == -1) {
                return null;
            }

            List<Object> items = new ArrayList<>(arraySize);
            for (int i = start1; i <= end1 && i < size; i += step) {
                Object item = path.getArrayItem(currentObject, i);
                items.add(item);
            }
            return items;
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            throw new UnsupportedOperationException();
        }
    }

    static class NotNullSegement extends PropertyFilter {
        public NotNullSegement(String propertyName, boolean function){
            super(propertyName, function);
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            return path.getPropertyValue(item, propertyName, propertyNameHash) != null;
        }
    }

    static class NullSegement extends PropertyFilter {
        public NullSegement(String propertyName, boolean function){
            super(propertyName, function);
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            return propertyValue == null;
        }
    }

    static class ValueSegment extends PropertyFilter {
        private final Object value;
        private boolean eq = true;

        public ValueSegment(String propertyName,boolean function, Object value, boolean eq){
            super(propertyName, function);

            if (value == null) {
                throw new IllegalArgumentException("value is null");
            }
            this.value = value;
            this.eq = eq;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);
            boolean result = value.equals(propertyValue);
            if (!eq) {
                result = !result;
            }
            return result;
        }
    }

    static class IntInSegement extends PropertyFilter {
        private final long[]  values;
        private final boolean not;

        public IntInSegement(String propertyName, boolean function, long[] values, boolean not){
            super(propertyName, function);
            this.values = values;
            this.not = not;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (propertyValue == null) {
                return false;
            }

            if (propertyValue instanceof Number) {
                long longPropertyValue = TypeUtils.longExtractValue((Number) propertyValue);
                for (long value : values) {
                    if (value == longPropertyValue) {
                        return !not;
                    }
                }
            }

            return not;
        }
    }

    static class IntBetweenSegement extends PropertyFilter {
        private final long    startValue;
        private final long    endValue;
        private final boolean not;

        public IntBetweenSegement(String propertyName, boolean function, long startValue, long endValue, boolean not){
            super(propertyName, function);
            this.startValue = startValue;
            this.endValue = endValue;
            this.not = not;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (propertyValue == null) {
                return false;
            }

            if (propertyValue instanceof Number) {
                long longPropertyValue = TypeUtils.longExtractValue((Number) propertyValue);
                if (longPropertyValue >= startValue && longPropertyValue <= endValue) {
                    return !not;
                }
            }

            return not;
        }
    }

    static class IntObjInSegement extends PropertyFilter {
        private final Long[]  values;
        private final boolean not;

        public IntObjInSegement(String propertyName, boolean function, Long[] values, boolean not){
            super(propertyName, function);
            this.values = values;
            this.not = not;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (propertyValue == null) {
                for (Long value : values) {
                    if (value == null) {
                        return !not;
                    }
                }

                return not;
            }

            if (propertyValue instanceof Number) {
                long longPropertyValue = TypeUtils.longExtractValue((Number) propertyValue);
                for (Long value : values) {
                    if (value == null) {
                        continue;
                    }

                    if (value.longValue() == longPropertyValue) {
                        return !not;
                    }
                }
            }

            return not;
        }
    }

    static class StringInSegement extends PropertyFilter {
        private final String[] values;
        private final boolean  not;

        public StringInSegement(String propertyName, boolean function, String[] values, boolean not){
            super(propertyName, function);
            this.values = values;
            this.not = not;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            for (String value : values) {
                if (value == propertyValue) {
                    return !not;
                }
            }

            return not;
        }
    }

    static class IntOpSegement extends PropertyFilter {
        private final long     value;
        private final Operator op;

        private BigDecimal     valueDecimal;
        private Float          valueFloat;
        private Double         valueDouble;

        public IntOpSegement(String propertyName, boolean function, long value, Operator op){
            super(propertyName, function);
            this.value = value;
            this.op = op;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (propertyValue == null) {
                return false;
            }

            if (!(propertyValue instanceof Number)) {
                return false;
            }

            if (propertyValue instanceof BigDecimal) {
                extracted3();

                int result = valueDecimal.compareTo((BigDecimal) propertyValue);
                switch (op) {
                    case EQ:
                        return result == 0;
                    case NE:
                        return result != 0;
                    case GE:
                        return 0 >= result;
                    case GT:
                        return 0 > result;
                    case LE:
                        return 0 <= result;
                    case LT:
                        return 0 < result;
                    default:
                        break;
                }

                return false;
            }

            if (propertyValue instanceof Float) {
                extracted();

                int result = valueFloat.compareTo((Float) propertyValue);
                switch (op) {
                    case EQ:
                        return result == 0;
                    case NE:
                        return result != 0;
                    case GE:
                        return 0 >= result;
                    case GT:
                        return 0 > result;
                    case LE:
                        return 0 <= result;
                    case LT:
                        return 0 < result;
                    default:
                        break;
                }

                return false;
            }

            if (propertyValue instanceof Double) {
                extracted2();

                int result = valueDouble.compareTo((Double) propertyValue);
                switch (op) {
                    case EQ:
                        return result == 0;
                    case NE:
                        return result != 0;
                    case GE:
                        return 0 >= result;
                    case GT:
                        return 0 > result;
                    case LE:
                        return 0 <= result;
                    case LT:
                        return 0 < result;
                    default:
                        break;
                }

                return false;
            }

            long longValue = TypeUtils.longExtractValue((Number) propertyValue);

            switch (op) {
                case EQ:
                    return longValue == value;
                case NE:
                    return longValue != value;
                case GE:
                    return longValue >= value;
                case GT:
                    return longValue > value;
                case LE:
                    return longValue <= value;
                case LT:
                    return longValue < value;
                default:
                    break;
            }

            return false;
        }

        private void extracted3() {
            if (valueDecimal == null) {
                valueDecimal = BigDecimal.valueOf(value);
            }
        }

        private void extracted2() {
            if (valueDouble == null) {
                valueDouble = Double.valueOf(value);
            }
        }

        private void extracted() {
            if (valueFloat == null) {
                valueFloat = Float.valueOf(value);
            }
        }
    }

    abstract static class PropertyFilter implements Filter {
        static long type1 = TypeUtils.fnv1a64("type");

        protected final String  propertyName;
        protected final long    propertyNameHash;
        protected final boolean function;
        protected Segment functionExpr;

        protected PropertyFilter(String propertyName, boolean function) {
            this.propertyName = propertyName;
            this.propertyNameHash = TypeUtils.fnv1a64(propertyName);
            this.function = function;

            if (function) {
                if (propertyNameHash == type1) {
                    functionExpr = TypeSegment.instance;
                } else if (propertyNameHash == SIZE) {
                    functionExpr = SizeSegment.instance;
                } else {
                    throw new JSONPathException("unsupported funciton : " + propertyName);
                }
            }
        }

        protected Object get(JSONPath path, Object rootObject, Object currentObject) {
            if (functionExpr != null) {
                return functionExpr.eval(path, rootObject, currentObject);
            }
            return path.getPropertyValue(currentObject, propertyName, propertyNameHash);
        }
    }

    static class DoubleOpSegement extends PropertyFilter {
        private final double   value;
        private final Operator op;

        public DoubleOpSegement(String propertyName, boolean function, double value, Operator op){
            super(propertyName, function);
            this.value = value;
            this.op = op;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (propertyValue == null) {
                return false;
            }

            if (!(propertyValue instanceof Number)) {
                return false;
            }

            double doubleValue = ((Number) propertyValue).doubleValue();

            switch (op) {
                case EQ:
                    return doubleValue == value;
                case NE:
                    return doubleValue != value;
                case GE:
                    return doubleValue >= value;
                case GT:
                    return doubleValue > value;
                case LE:
                    return doubleValue <= value;
                case LT:
                    return doubleValue < value;
                default:
                    break;
            }

            return false;
        }
    }

    static class RefOpSegement extends PropertyFilter {
        private final Segment  refSgement;
        private final Operator  op;

        public RefOpSegement(String propertyName, boolean function, Segment refSgement, Operator op){
            super(propertyName, function);
            this.refSgement = refSgement;
            this.op = op;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (propertyValue == null) {
                return false;
            }

            if (!(propertyValue instanceof Number)) {
                return false;
            }

            Object refValue = refSgement.eval(path, rootObject, rootObject);

            if (refValue instanceof Integer || refValue instanceof Long || refValue instanceof Short || refValue instanceof Byte) {
                long value = TypeUtils.longExtractValue((Number) refValue);

                if (propertyValue instanceof Integer || propertyValue instanceof Long || propertyValue instanceof Short || propertyValue instanceof Byte) {
                    long longValue = TypeUtils.longExtractValue((Number) propertyValue);

                    switch (op) {
                        case EQ:
                            return longValue == value;
                        case NE:
                            return longValue != value;
                        case GE:
                            return longValue >= value;
                        case GT:
                            return longValue > value;
                        case LE:
                            return longValue <= value;
                        case LT:
                            return longValue < value;
                        default:
                            break;
                    }
                } else if (propertyValue instanceof BigDecimal) {
                    BigDecimal valueDecimal = BigDecimal.valueOf(value);

                    int result = valueDecimal.compareTo((BigDecimal) propertyValue);
                    switch (op) {
                        case EQ:
                            return result == 0;
                        case NE:
                            return result != 0;
                        case GE:
                            return 0 >= result;
                        case GT:
                            return 0 > result;
                        case LE:
                            return 0 <= result;
                        case LT:
                            return 0 < result;
                        default:
                            break;
                    }

                    return false;
                }
            }

            throw new UnsupportedOperationException();
        }
    }

    static class MatchSegement extends PropertyFilter {
        private final String   startsWithValue;
        private final String   endsWithValue;
        private final String[] containsValues;
        private final int      minLength;
        private final boolean  not;

        public MatchSegement(
                String propertyName,
                boolean function,
                String startsWithValue,
                String endsWithValue,
                String[] containsValues,
                boolean not)
        {
            super(propertyName, function);
            this.startsWithValue = startsWithValue;
            this.endsWithValue = endsWithValue;
            this.containsValues = containsValues;
            this.not = not;

            int len = 0;
            if (startsWithValue != null) {
                len += startsWithValue.length();
            }

            if (endsWithValue != null) {
                len += endsWithValue.length();
            }

            if (containsValues != null) {
                for (String item : containsValues) {
                    len += item.length();
                }
            }

            this.minLength = len;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (propertyValue == null) {
                return false;
            }

            final String strPropertyValue = propertyValue.toString();

            if (strPropertyValue.length() < minLength) {
                return not;
            }

            int start = 0;
            if (startsWithValue != null) {
                if (!strPropertyValue.startsWith(startsWithValue)) {
                    return not;
                }
                start += startsWithValue.length();
            }

            if (containsValues != null) {
                for (String containsValue : containsValues) {
                    int index = strPropertyValue.indexOf(containsValue, start);
                    if (index == -1) {
                        return not;
                    }
                    start = index + containsValue.length();
                }
            }

            if (endsWithValue != null && !strPropertyValue.endsWith(endsWithValue)) {
                return not;
            }

            return !not;
        }
    }

    static class RlikeSegement extends PropertyFilter {
        private final Pattern pattern;
        private final boolean not;

        public RlikeSegement(String propertyName, boolean function, String pattern, boolean not){
            super(propertyName, function);
            this.pattern = Pattern.compile(pattern);
            this.not = not;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (propertyValue == null) {
                return false;
            }

            String strPropertyValue = propertyValue.toString();
            Matcher m = pattern.matcher(strPropertyValue);
            boolean match = m.matches();

            if (not) {
                match = !match;
            }

            return match;
        }
    }

    static class StringOpSegement extends PropertyFilter {
        private final String   value;
        private final Operator op;

        public StringOpSegement(String propertyName, boolean function, String value, Operator op){
            super(propertyName, function);
            this.value = value;
            this.op = op;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);

            if (op == Operator.EQ) {
                return value.equals(propertyValue);
            } else if (op == Operator.NE) {
                return !value.equals(propertyValue);
            }

            if (propertyValue == null) {
                return false;
            }

            int compareResult = value.compareTo(propertyValue.toString());
            if (op == Operator.GE) {
                return compareResult <= 0;
            } else if (op == Operator.GT) {
                return compareResult < 0;
            } else if (op == Operator.LE) {
                return compareResult >= 0;
            } else if (op == Operator.LT) {
                return compareResult > 0;
            }

            return false;
        }
    }

    static class RegMatchSegement extends PropertyFilter {
        private final Pattern  pattern;

        public RegMatchSegement(String propertyName, boolean function, Pattern pattern){
            super(propertyName, function);
            this.pattern = pattern;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            Object propertyValue = get(path, rootObject, item);
            if (propertyValue == null) {
                return false;
            }

            String str = propertyValue.toString();

            Matcher m = pattern.matcher(str);
            return m.matches();
        }
    }

    enum Operator {
        EQ, NE, GT, GE, LT, LE, LIKE, NOT_LIKE, RLIKE, NOT_RLIKE, IN, NOT_IN, BETWEEN, NOT_BETWEEN, AND, OR, REG_MATCH
    }

    public static class FilterSegment implements Segment {

        private final Filter filter;

        public FilterSegment(Filter filter){
            super();
            this.filter = filter;
        }

        @SuppressWarnings("rawtypes")
        public Object eval(JSONPath path, Object rootObject, Object currentObject) {
            if (currentObject == null) {
                return null;
            }

            List<Object> items = new JSONArray();

            if (currentObject instanceof Iterable) {
                Iterator it = ((Iterable) currentObject).iterator();
                while (it.hasNext()) {
                    Object item = it.next();

                    if (filter.apply(path, rootObject, currentObject, item)) {
                        items.add(item);
                    }
                }

                return items;
            }

            if (filter.apply(path, rootObject, currentObject, currentObject)) {
                return currentObject;
            }

            return null;
        }

        public void extract(JSONPath path, DefaultJSONParser parser, Context context) {
            Object object = parser.parse();
            context.object = eval(path, object, object);
        }

        public boolean remove(JSONPath path, Object rootObject, Object currentObject) {
            if (currentObject == null) {
                return false;
            }

            if (currentObject instanceof Iterable) {
                Iterator<?> it = ((Iterable) currentObject).iterator();
                while (it.hasNext()) {
                    Object item = it.next();

                    if (filter.apply(path, rootObject, currentObject, item)) {
                        it.remove();
                    }
                }

                return true;
            }

            return false;
        }
    }

    interface Filter {
        boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item);
    }

    static class FilterGroup implements Filter {
        private boolean and;
        private List<Filter> fitlers;

        public FilterGroup(Filter left, Filter right, boolean and) {
            fitlers = new ArrayList<>(2);
            fitlers.add(left);
            fitlers.add(right);
            this.and = and;
        }

        public boolean apply(JSONPath path, Object rootObject, Object currentObject, Object item) {
            if (and) {
                for (Filter fitler : this.fitlers) {
                    if (!fitler.apply(path, rootObject, currentObject, item)) {
                        return false;
                    }
                }
                return true;
            } else {
                for (Filter fitler : this.fitlers) {
                    if (fitler.apply(path, rootObject, currentObject, item)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    @SuppressWarnings("rawtypes")
    protected Object getArrayItem(final Object currentObject, int index) {
        if (currentObject == null) {
            return null;
        }

        if (currentObject instanceof List) {
            List list = (List) currentObject;

            return extracted31(index, list);
        }

        if (currentObject.getClass().isArray()) {
            int arrayLenth = Array.getLength(currentObject);

            return extracted32(currentObject, index, arrayLenth);
        }

        if (currentObject instanceof Map) {
            Map map = (Map) currentObject;
            Object value = map.get(index);
            value = extracted33(index, map, value);
            return value;
        }

        if (currentObject instanceof Collection) {
            Collection collection = (Collection) currentObject;
            int i = 0;
            for (Object item : collection) {
                if (i == index) {
                    return item;
                }
                i++;
            }
            return null;
        }

        if (index == 0) {
            return currentObject;
        }

        throw new UnsupportedOperationException();
    }

    private Object extracted33(int index, Map<Object, Object> map, Object value) {
        if (value == null) {
            value = map.get(Integer.toString(index));
        }
        return value;
    }

    private Object extracted32(final Object currentObject, int index, int arrayLenth) {
        if (index >= 0) {
            if (index < arrayLenth) {
                return Array.get(currentObject, index);
            }
            return null;
        } else {
            if (Math.abs(index) <= arrayLenth) {
                return Array.get(currentObject, arrayLenth + index);
            }
            return null;
        }
    }

    private Object extracted31(int index, List<Object> list) {
        if (index >= 0) {
            if (index < list.size()) {
                return list.get(index);
            }
            return null;
        } else {
            if (Math.abs(index) <= list.size()) {
                return list.get(list.size() + index);
            }
            return null;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean setArrayItem(Object currentObject, int index, Object value) {
        if (currentObject instanceof List) {
            List list = (List) currentObject;
            if (index >= 0) {
                list.set(index, value);
                return true;
            } else {
                list.set(list.size() + index, value);
                return false;
            }

        }

        Class<?> clazz = currentObject.getClass();
        if (clazz.isArray()) {
            int arrayLenth = Array.getLength(currentObject);

            if (index >= 0) {
                if (index < arrayLenth) {
                    Array.set(currentObject, index, value);
                }
            } else {
                if (Math.abs(index) <= arrayLenth) {
                    Array.set(currentObject, arrayLenth + index, value);
                }
            }

            return true;
        }

        throw new JSONPathException("unsupported set operation." + clazz);
    }

    @SuppressWarnings("rawtypes")
    public boolean removeArrayItem(Object currentObject, int index) {
        if (currentObject instanceof List) {
            List list = (List) currentObject;
            if (index >= 0) {
                if (index >= list.size()) {
                    return false;
                }
                list.remove(index);
            } else {
                int newIndex = list.size() + index;

                if (newIndex < 0) {
                    return false;
                }

                list.remove(newIndex);
            }
            return true;
        }

        Class<?> clazz = currentObject.getClass();
        throw new JSONPathException("unsupported set operation." + clazz);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Collection<Object> getPropertyValues(final Object currentObject) {
        if (currentObject == null) {
            return Collections.emptyList();
        }

        final Class<?> currentClass = currentObject.getClass();

        JavaBeanSerializer beanSerializer = getJavaBeanSerializer(currentClass);

        if (beanSerializer != null) {
            try {
                return beanSerializer.getFieldValues(currentObject);
            } catch (Exception e) {
                throw new JSONPathException(JSON_PATH_ERROR + path, e);
            }
        }

        if (currentObject instanceof Map) {
            Map map = (Map) currentObject;
            return map.values();
        }

        if (currentObject instanceof Collection) {
            return (Collection) currentObject;
        }

        throw new UnsupportedOperationException();
    }

    protected void deepGetObjects(final Object currentObject, List<Object> outValues) {
        final Class<?> currentClass = currentObject.getClass();

        JavaBeanSerializer beanSerializer = getJavaBeanSerializer(currentClass);

        Collection<?> collection = null;
        if (beanSerializer != null) {
            try {
                collection = beanSerializer.getFieldValues(currentObject);
                outValues.add(currentObject);
            } catch (Exception e) {
                throw new JSONPathException(JSON_PATH_ERROR + path, e);
            }
        } else if (currentObject instanceof Map) {
            outValues.add(currentObject);
            Map map = (Map) currentObject;
            collection = map.values();
        } else if (currentObject instanceof Collection) {
            collection = (Collection) currentObject;
        }

        if (collection != null) {
            for (Object fieldValue : collection) {
                if (fieldValue == null || ParserConfig.isPrimitive2(fieldValue.getClass())) {
                    // skip
                } else {
                    deepGetObjects(fieldValue, outValues);
                }
            }
            return;
        }

        throw new UnsupportedOperationException(currentClass.getName());
    }

    protected void deepGetPropertyValues(final Object currentObject, List<Object> outValues) {
        final Class<?> currentClass = currentObject.getClass();

        JavaBeanSerializer beanSerializer = getJavaBeanSerializer(currentClass);

        Collection<?> collection = null;
        if (beanSerializer != null) {
            try {
                collection = beanSerializer.getFieldValues(currentObject);
            } catch (Exception e) {
                throw new JSONPathException(JSON_PATH_ERROR + path, e);
            }
        } else if (currentObject instanceof Map) {
            Map map = (Map) currentObject;
            collection = map.values();
        } else if (currentObject instanceof Collection) {
            collection = (Collection) currentObject;
        }

        if (collection != null) {
            for (Object fieldValue : collection) {
                if (fieldValue == null || ParserConfig.isPrimitive2(fieldValue.getClass())) {
                    outValues.add(fieldValue);
                } else {
                    deepGetPropertyValues(fieldValue, outValues);
                }
            }
            return;
        }

        throw new UnsupportedOperationException(currentClass.getName());
    }

    static boolean eq(Object a, Object b) {
        if (a == b) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        if (a.getClass() == b.getClass()) {
            return a.equals(b);
        }

        if (a instanceof Number) {
            if (b instanceof Number) {
                return eqNotNull((Number) a, (Number) b);
            }

            return false;
        }

        return a.equals(b);
    }

    @SuppressWarnings("rawtypes")
    static boolean eqNotNull(Number a, Number b) {
        Class clazzA = a.getClass();
        boolean isIntA = isInt(clazzA);

        Class clazzB = b.getClass();
        boolean isIntB = isInt(clazzB);

        if (a instanceof BigDecimal) {
            BigDecimal decimalA = (BigDecimal) a;

            if (isIntB) {
                return decimalA.equals(BigDecimal.valueOf(TypeUtils.longExtractValue(b)));
            }
        }


        if (isIntA && isIntB) {
            return a.longValue() == b.longValue();
        }

        if (b instanceof BigInteger) {
            BigInteger bigIntB = BigInteger.valueOf((long) a);
            BigInteger bigIntA = BigInteger.valueOf(a.longValue());

            return bigIntA.equals(bigIntB);
        }


        if (isIntB && (a instanceof BigInteger)) {
            BigInteger bigIntA = (BigInteger) a;
            BigInteger bigIntB = BigInteger.valueOf(TypeUtils.longExtractValue(b));

            return bigIntA.equals(bigIntB);
        }


        boolean isDoubleA = isDouble(clazzA);
        boolean isDoubleB = isDouble(clazzB);

        if ((isDoubleA && isDoubleB) || (isDoubleA && isIntB) || (isDoubleB && isIntA)) {
            return a.doubleValue() == b.doubleValue();
        }


        return false;
    }

    protected static boolean isDouble(Class<?> clazzA) {
        return clazzA == Float.class || clazzA == Double.class;
    }

    protected static boolean isInt(Class<?> clazzA) {
        return clazzA == Byte.class || clazzA == Short.class || clazzA == Integer.class || clazzA == Long.class;
    }

    static final long SIZE = 0x4dea9618e618ae3cL;
    static final long LENGTH = 0xea11573f1af59eb5L;

    protected Object getPropertyValue(Object currentObject, String propertyName, long propertyNameHash) {
        if (currentObject == null) {
            return null;
        }

        currentObject = extracted29(currentObject);

        if (currentObject instanceof Map) {
            Map map = (Map) currentObject;
            Object val = map.get(propertyName);

            val = extracted21(propertyNameHash, map, val);

            return val;
        }

        final Class<?> currentClass = currentObject.getClass();

        JavaBeanSerializer beanSerializer = getJavaBeanSerializer(currentClass);
        if (beanSerializer != null) {
            return extracted22(currentObject, propertyName, propertyNameHash, beanSerializer);
        }

        if (currentObject instanceof List) {
            List list = (List) currentObject;

            if (extracted30(propertyNameHash)) {
                return list.size();
            }

            List<Object> fieldValues = null;

            fieldValues = extracted25(propertyName, propertyNameHash, list, fieldValues);

            fieldValues = extracted26(fieldValues);

            return fieldValues;
        }

        if (currentObject instanceof Object[]) {
            Object[] array = (Object[]) currentObject;

            if (extracted30(propertyNameHash)) {
                return array.length;
            }

            List<Object> fieldValues = new JSONArray(array.length);

            extracted28(propertyName, propertyNameHash, array, fieldValues);

            return fieldValues;
        }

        if (currentObject instanceof Enum) {
            final long NAME = 0xc4bcadba8e631b86L;
            final long ORDINAL = 0xf1ebc7c20322fc22L;

            Enum<?> e = (Enum) currentObject;
            if (NAME == propertyNameHash) {
                return e.name();
            }

            if (ORDINAL == propertyNameHash) {
                return e.ordinal();
            }
        }



        return null;

    }


    private boolean extracted30(long propertyNameHash) {
        return SIZE == propertyNameHash || LENGTH == propertyNameHash;
    }

    private Object extracted29(Object currentObject) {
        if (currentObject instanceof String) {
            currentObject = extracted20(currentObject);
        }
        return currentObject;
    }

    private void extracted28(String propertyName, long propertyNameHash, Object[] array, List<Object> fieldValues) {
        for (int i = 0; i < array.length; ++i) {
            Object obj = array[i];

            //
            if (obj == array) {
                fieldValues.add(obj);
                continue;
            }

            Object itemValue = getPropertyValue(obj, propertyName, propertyNameHash);
            extracted27(fieldValues, itemValue);
        }
    }

    private void extracted27(List<Object> fieldValues, Object itemValue) {
        if (itemValue instanceof Collection) {
            Collection<?> collection = (Collection) itemValue;
            fieldValues.addAll(collection);
        } else if (itemValue != null || !ignoreNullValue) {
            fieldValues.add(itemValue);
        }
    }

    private List<Object> extracted26(List<Object> fieldValues) {
        if (fieldValues == null) {
            fieldValues = Collections.emptyList();
        }
        return fieldValues;
    }

    private List<Object> extracted25(String propertyName, long propertyNameHash, List<Object> list, List<Object> fieldValues) {
        for (int i = 0; i < list.size(); ++i) {
            Object obj = list.get(i);

            //
            if (obj == list) {
                fieldValues = extracted23(list, fieldValues);
                fieldValues.add(obj);
                continue;
            }

            Object itemValue = getPropertyValue(obj, propertyName, propertyNameHash);
            fieldValues = extracted24(list, fieldValues, itemValue);
        }
        return fieldValues;
    }

    private List<Object> extracted24(List<Object> list, List<Object> fieldValues, Object itemValue) {
        if (itemValue instanceof Collection) {
            Collection<?> collection = (Collection) itemValue;
            fieldValues = extracted23(list, fieldValues);
            fieldValues.addAll(collection);
        } else if (itemValue != null || !ignoreNullValue) {
            fieldValues = extracted23(list, fieldValues);
            fieldValues.add(itemValue);
        }
        return fieldValues;
    }

    private List<Object> extracted23(List<Object> list, List<Object> fieldValues) {
        if (fieldValues == null) {
            fieldValues = new JSONArray(list.size());
        }
        return fieldValues;
    }

    private Object extracted22(Object currentObject, String propertyName, long propertyNameHash,
                               JavaBeanSerializer beanSerializer) throws JSONPathException {
        try {
            return beanSerializer.getFieldValue(currentObject, propertyName, propertyNameHash, false);
        } catch (Exception e) {
            throw new JSONPathException(JSON_PATH_ERROR + path + SEGEMENT + propertyName, e);
        }
    }

    private Object extracted21(long propertyNameHash, Map<Object, Object> map, Object val) {
        if (val == null && extracted30(propertyNameHash)) {
            val = map.size();
        }
        return val;
    }

    private Object extracted20(Object currentObject) {
        try {
            JSONObject object = (JSONObject) JSON.parse((String) currentObject, parserConfig);
            currentObject = object;
        } catch (Exception ex) {
            // skip
        }
        return currentObject;
    }

    @SuppressWarnings("rawtypes")
    protected void deepScan(final Object currentObject, final String propertyName, List<Object> results) {
        if (currentObject == null) {
            return;
        }

        if (currentObject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) currentObject;

            extracted16(propertyName, results, map);

            return;
        }

        if (currentObject instanceof Collection) {
            Iterator iterator = ((Collection) currentObject).iterator();
            extracted17(propertyName, results, iterator);
            return;
        }

        final Class<?> currentClass = currentObject.getClass();

        JavaBeanSerializer beanSerializer = getJavaBeanSerializer(currentClass);
        if (beanSerializer != null) {
            extracted19(currentObject, propertyName, results, beanSerializer);
        }

        if (currentObject instanceof List) {
            List list = (List) currentObject;

            for (int i = 0; i < list.size(); ++i) {
                Object val = list.get(i);
                deepScan(val, propertyName, results);
            }
        }
    }

    private void extracted19(final Object currentObject, final String propertyName, List<Object> results,
                             JavaBeanSerializer beanSerializer){
        try {
            FieldSerializer fieldDeser = beanSerializer.getFieldSerializer(propertyName);
            if (fieldDeser != null) {
                extracted18(currentObject, propertyName, results, fieldDeser);
                return;
            }
            List<Object> fieldValues = beanSerializer.getFieldValues(currentObject);
            for (Object val : fieldValues) {
                deepScan(val, propertyName, results);
            }
        } catch (Exception e) {
            throw new JSONPathException(JSON_PATH_ERROR + path + SEGEMENT + propertyName, e);
        }
    }

    private void extracted18(final Object currentObject, final String propertyName, List<Object> results,
                             FieldSerializer fieldDeser) {
        try {
            Object val = fieldDeser.getPropertyValueDirect(currentObject);
            results.add(val);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new JSONException("getFieldValue error." + propertyName, ex);
        }
    }

    private void extracted17(final String propertyName, List<Object> results, Iterator<Object> iterator) {
        while (iterator.hasNext()) {
            Object next = iterator.next();
            if (ParserConfig.isPrimitive2(next.getClass())) {
                continue;
            }
            deepScan(next, propertyName, results);
        }
    }

    private void extracted16(final String propertyName, List<Object> results, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object val = entry.getValue();

            if (propertyName.equals(entry.getKey())) {
                extracted15(results, val);
            }

            if (val == null || ParserConfig.isPrimitive2(val.getClass())) {
                continue;
            }

            deepScan(val, propertyName, results);
        }
    }

    private void extracted15(List<Object> results, Object val) {
        if (val instanceof Collection) {
            results.addAll((Collection) val);
        } else {
            results.add(val);
        }
    }

    protected void deepSet(final Object currentObject, final String propertyName, long propertyNameHash, Object value) {
        if (currentObject == null) {
            return;
        }

        if (currentObject instanceof Map) {
            Map map = (Map) currentObject;

            if (map.containsKey(propertyName)) {
                map.put(propertyName, value);
                return;
            }

            extracted2(propertyName, propertyNameHash, value, map);
            return;
        }

        final Class<?> currentClass = currentObject.getClass();

        JavaBeanDeserializer beanDeserializer = getJavaBeanDeserializer(currentClass);
        if (beanDeserializer != null) {
            try {
                FieldDeserializer fieldDeser = beanDeserializer.getFieldDeserializer(propertyName);
                if (fieldDeser != null) {
                    fieldDeser.setValue(currentObject, value);
                    return;
                }

                JavaBeanSerializer beanSerializer = getJavaBeanSerializer(currentClass);
                List<Object> fieldValues = beanSerializer.getObjectFieldValues(currentObject);
                for (Object val : fieldValues) {
                    deepSet(val, propertyName, propertyNameHash, value);
                }
                return;
            } catch (Exception e) {
                throw new JSONPathException(JSON_PATH_ERROR + path + SEGEMENT + propertyName, e);
            }
        }

        if (currentObject instanceof List) {
            List<?> list = (List) currentObject;

            for (int i = 0; i < list.size(); ++i) {
                Object val = list.get(i);
                deepSet(val, propertyName, propertyNameHash, value);
            }
        }
    }

    private void extracted2(final String propertyName, long propertyNameHash, Object value, Map<Object,Object> map) {
        for (Object val : map.values()) {
            deepSet(val, propertyName, propertyNameHash, value);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected boolean setPropertyValue(Object parent, String name, long propertyNameHash, Object value) {
        if (parent instanceof Map) {
            ((Map) parent).put(name, value);
            return true;
        }

        if (parent instanceof List) {
            for (Object element : (List) parent) {
                if (element == null) {
                    continue;
                }
                setPropertyValue(element, name, propertyNameHash, value);
            }
            return true;
        }

        ObjectDeserializer deserializer = parserConfig.getDeserializer(parent.getClass());

        JavaBeanDeserializer beanDeserializer = null;
        if (deserializer instanceof JavaBeanDeserializer) {
            beanDeserializer = (JavaBeanDeserializer) deserializer;
        }

        if (beanDeserializer != null) {
            FieldDeserializer fieldDeserializer = beanDeserializer.getFieldDeserializer(propertyNameHash);
            if (fieldDeserializer == null) {
                return false;
            }

            if (value != null && value.getClass() != fieldDeserializer.fieldInfo.fieldClass) {
                value = TypeUtils.cast(value, fieldDeserializer.fieldInfo.fieldType, parserConfig);
            }

            fieldDeserializer.setValue(parent, value);
            return true;
        }

        throw new UnsupportedOperationException();
    }

    @SuppressWarnings({"rawtypes" })
    protected boolean removePropertyValue(Object parent, String name, boolean deep) {
        if (parent instanceof Map) {
            Object origin = ((Map) parent).remove(name);
            boolean found = origin != null;

            extracted6(parent, name, deep);

            return found;
        }

        ObjectDeserializer deserializer = parserConfig.getDeserializer(parent.getClass());

        JavaBeanDeserializer beanDeserializer = null;
        if (deserializer instanceof JavaBeanDeserializer) {
            beanDeserializer = (JavaBeanDeserializer) deserializer;
        }

        if (beanDeserializer != null) {
            FieldDeserializer fieldDeserializer = beanDeserializer.getFieldDeserializer(name);

            boolean found = false;
            found = extracted7(parent, fieldDeserializer, found);

            if (deep) {
                Collection<Object> propertyValues = this.getPropertyValues(parent);
                for (Object item : propertyValues) {
                    if (item == null) {
                        continue;
                    }
                    removePropertyValue(item, name, deep);
                }
            }

            return found;
        }

        if (deep) {
            return false;
        }

        throw new UnsupportedOperationException();
    }

    private boolean extracted7(Object parent, FieldDeserializer fieldDeserializer, boolean found) {
        if (fieldDeserializer != null) {
            fieldDeserializer.setValue(parent, null);
            found = true;
        }
        return found;
    }

    private void extracted6(Object parent, String name, boolean deep) {
        if (deep) {
            extracted5(parent, name, deep);
        }
    }

    private void extracted5(Object parent, String name, boolean deep) {
        for (Object item : ((Map) parent).values()) {
            removePropertyValue(item, name, deep);
        }
    }

    protected JavaBeanSerializer getJavaBeanSerializer(final Class<?> currentClass) {
        JavaBeanSerializer beanSerializer = null;
        beanSerializer = extracted38(currentClass, beanSerializer);
        return beanSerializer;
    }

    private JavaBeanSerializer extracted38(final Class<?> currentClass, JavaBeanSerializer beanSerializer) {
        ObjectSerializer serializer = serializeConfig.getObjectWriter(currentClass);
        if (serializer instanceof JavaBeanSerializer) {
            beanSerializer = (JavaBeanSerializer) serializer;
        }
        return beanSerializer;
    }

    protected JavaBeanDeserializer getJavaBeanDeserializer(final Class<?> currentClass) {
        JavaBeanDeserializer beanDeserializer = null;
        beanDeserializer = extracted36(currentClass, beanDeserializer);
        return beanDeserializer;
    }

    private JavaBeanDeserializer extracted36(final Class<?> currentClass, JavaBeanDeserializer beanDeserializer) {
        ObjectDeserializer deserializer = parserConfig.getDeserializer(currentClass);
        if (deserializer instanceof JavaBeanDeserializer) {
            beanDeserializer = (JavaBeanDeserializer) deserializer;
        }
        return beanDeserializer;
    }

    @SuppressWarnings("rawtypes")
    int evalSize(Object currentObject) {
        if (currentObject == null) {
            return -1;
        }

        if (currentObject instanceof Collection) {
            return ((Collection) currentObject).size();
        }

        if (currentObject instanceof Object[]) {
            return ((Object[]) currentObject).length;
        }

        if (currentObject.getClass().isArray()) {
            return Array.getLength(currentObject);
        }

        if (currentObject instanceof Map) {
            int count = 0;

            for (Object value : ((Map) currentObject).values()) {
                if (value != null) {
                    count++;
                }
            }
            return count;
        }

        JavaBeanSerializer beanSerializer = getJavaBeanSerializer(currentObject.getClass());

        if (beanSerializer == null) {
            return -1;
        }

        try {
            return beanSerializer.getSize(currentObject);
        } catch (Exception e) {
            throw new JSONPathException("evalSize error : " + path, e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    Set<String> evalKeySet(Object currentObject) {
        if (currentObject == null) {
            return Collections.emptySet();
        }

        if (currentObject instanceof Map) {
            // For performance reasons return keySet directly, without filtering null-value key.
            return ((Map)currentObject).keySet();
        }

        if (currentObject instanceof Collection || currentObject instanceof Object[]
                || currentObject.getClass().isArray()) {
            return Collections.emptySet();
        }

        JavaBeanSerializer beanSerializer = getJavaBeanSerializer(currentObject.getClass());
        if (beanSerializer == null) {
            return Collections.emptySet();
        }

        try {
            return beanSerializer.getFieldNames(currentObject);
        } catch (Exception e) {
            throw new JSONPathException("evalKeySet error : " + path, e);
        }
    }

    public String toJSONString() {
        return JSON.toJSONString(path);
    }

    public static Object reserveToArray(Object object, String... paths) {
        JSONArray reserved = new JSONArray();

        if (paths == null || paths.length == 0) {
            return reserved;
        }

        for (String item : paths) {
            JSONPath path = JSONPath.compile(item);
            path.init();

            Object value = path.eval(object);
            reserved.add(value);
        }

        return reserved;
    }

    public static Object reserveToObject(Object object, String... paths) {
        if (paths == null || paths.length == 0) {
            return object;
        }

        JSONObject reserved = new JSONObject(true);
        for (String item : paths) {
            JSONPath path = JSONPath.compile(item);
            path.init();
            Segment lastSegement = path.segments[path.segments.length - 1];
            if (lastSegement instanceof PropertySegment) {
                Object value = path.eval(object);
                if (value == null) {
                    continue;
                }
                path.set(reserved, value);
            } else {
                // skip
            }
        }

        return reserved;
    }
}
