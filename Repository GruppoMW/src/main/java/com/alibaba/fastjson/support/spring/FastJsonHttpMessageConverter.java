package com.alibaba.fastjson.support.spring;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONPObject;
import com.alibaba.fastjson.serializer.SerializeFilter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fastjson for Spring MVC Converter.
 * <p>
 * Compatible Spring MVC version 3.2+
 *
 * @author VictorZeng
 * @see AbstractHttpMessageConverter
 * @see GenericHttpMessageConverter
 * @since 1.2.10
 * <p>
 * <p>
 * <p>
 * Supported return type:
 * </p>
 * Simple object: Object
 * <p>
 * <p>
 * With property filter :FastJsonContainer[Object]
 * </p>
 * <p>
 * Jsonp :MappingFastJsonValue[Object]
 * </p>
 * Jsonp with property filter: MappingFastJsonValue[FastJsonContainer[Object]]
 */

public class FastJsonHttpMessageConverter extends AbstractHttpMessageConverter<Object>//
        implements GenericHttpMessageConverter<Object> {

    public static final MediaType APPLICATION_JAVASCRIPT = new MediaType("application", "javascript");

    

    protected SerializeFilter[] filters = new SerializeFilter[0];

    protected String dateFormat;

    private boolean setLengthError = false;

    /**
     * with fastJson config
     */
    private FastJsonConfig fastJsonConfig = new FastJsonConfig();

    /**
     * @return the fastJsonConfig.
     * @since 1.2.11
     */
    public FastJsonConfig getFastJsonConfig() {
        return fastJsonConfig;
    }

    /**
     * @param fastJsonConfig the fastJsonConfig to set.
     * @since 1.2.11
     */
    public void setFastJsonConfig(FastJsonConfig fastJsonConfig) {
        this.fastJsonConfig = fastJsonConfig;
    }

    /**
     * Can serialize/deserialize all types.
     */
    public FastJsonHttpMessageConverter() {

        super(MediaType.ALL);
    }

    public Charset getCharset() {
        return this.fastJsonConfig.getCharset();
    }

    public void setCharset(Charset charset) {
        this.fastJsonConfig.setCharset(charset);
    }

    public String getDateFormat() {
        return this.fastJsonConfig.getDateFormat();
    }

    public void setDateFormat(String dateFormat) {
        this.fastJsonConfig.setDateFormat(dateFormat);
    }

    public SerializerFeature[] getFeatures() {
        return this.fastJsonConfig.getSerializerFeatures();
    }
    

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }


    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        return super.canRead(contextClass, mediaType);
    }


    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return super.canWrite(clazz, mediaType);
    }

    /*
     * @see org.springframework.http.converter.GenericHttpMessageConverter#read(java.lang.reflect.Type, java.lang.Class, org.springframework.http.HttpInputMessage)
     */
    public Object read(Type type, //
                       Class<?> contextClass, //
                       HttpInputMessage inputMessage //
    ) throws IOException, HttpMessageNotReadableException {
        return readType(getType(type, contextClass), inputMessage);
    }

    /*
     * @see org.springframework.http.converter.GenericHttpMessageConverter.write
     */
    public void write(Object o, Type type, MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        super.write(o, contentType, outputMessage);// support StreamingHttpOutputMessage in spring4.0+
        
    }


    /*
     * @see org.springframework.http.converter.AbstractHttpMessageConverter#readInternal(java.lang.Class, org.springframework.http.HttpInputMessage)
     */
    @Override
    protected Object readInternal(Class<?> clazz, //
                                  HttpInputMessage inputMessage //
    ) throws IOException, HttpMessageNotReadableException {
        return readType(getType(clazz, null), inputMessage);
    }

    private Object readType(Type type, HttpInputMessage inputMessage) {

        try {
            InputStream in = inputMessage.getBody();
            return JSON.parseObject(in,
                    fastJsonConfig.getCharset(),
                    type,
                    fastJsonConfig.getParserConfig(),
                    fastJsonConfig.getParseProcess(),
                    JSON.DEFAULT_PARSER_FEATURE,
                    fastJsonConfig.getFeatures());
        } catch (JSONException ex) {
            throw new HttpMessageNotReadableException("JSON parse error: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new HttpMessageNotReadableException("I/O error while reading input message", ex);
        }
    }

    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {

        try ( ByteArrayOutputStream outnew = new ByteArrayOutputStream() ){
            HttpHeaders headers = outputMessage.getHeaders();

            //获取全局配置的filter
            SerializeFilter[] globalFilters = fastJsonConfig.getSerializeFilters();
            List<SerializeFilter> allFilters = new ArrayList<>(Arrays.asList(globalFilters));

            boolean isJsonp = false;

            //不知道为什么会有这行代码， 但是为了保持和原来的行为一致，还是保留下来
            Object value = strangeCodeForJackson(object);

            if (value instanceof FastJsonContainer) {
                FastJsonContainer fastJsonContainer = (FastJsonContainer) value;
                PropertyPreFilters filters1 = fastJsonContainer.getFilters();
                allFilters.addAll(filters1.getFilters());
                value = fastJsonContainer.getValue();
            }

            //revise 2017-10-23 ,
            // 保持原有的MappingFastJsonValue对象的contentType不做修改 保持旧版兼容。
            // 但是新的JSONPObject将返回标准的contentType：application/javascript ，不对是否有function进行判断
            if (value instanceof MappingFastJsonValue) {
                if (!StringUtils.isEmpty(((MappingFastJsonValue) value).getJsonpFunction())) {
                    isJsonp = true;
                }
            } else if (value instanceof JSONPObject) {
                isJsonp = true;
            }


            int len = JSON.writeJSONStringWithFastJsonConfig(outnew, //
                    fastJsonConfig.getCharset(), //
                    value, //
                    fastJsonConfig.getSerializeConfig(), //
                    //fastJsonConfig.getSerializeFilters(), //
                    allFilters.toArray(new SerializeFilter[allFilters.size()]),
                    fastJsonConfig.getDateFormat(), //
                    JSON.DEFAULT_GENERATE_FEATURE, //
                    fastJsonConfig.getSerializerFeatures());

            if (isJsonp) {
                headers.setContentType(APPLICATION_JAVASCRIPT);
            }

            if (fastJsonConfig.isWriteContentLength() && !setLengthError) {
                
                headers.setContentLength(len);
                
            }

            outnew.writeTo(outputMessage.getBody());

        } catch (JSONException ex) {
            throw new HttpMessageNotWritableException("Could not write JSON: " + ex.getMessage(), ex);
        } 
    }

    private Object strangeCodeForJackson(Object obj) {
        if (obj != null) {
            String className = obj.getClass().getName();
            if ("com.fasterxml.jackson.databind.node.ObjectNode".equals(className)) {
                return obj.toString();
            }
        }
        return obj;
    }

    protected Type getType(Type type, Class<?> contextClass) {
        if (Spring4TypeResolvableHelper.isSupport()) {
            return Spring4TypeResolvableHelper.getType(type, contextClass);
        }

        return type;
    }


    private static class Spring4TypeResolvableHelper {
        private static boolean hasClazzResolvableType;

        static {
            try {
                Class.forName("org.springframework.core.ResolvableType");
                hasClazzResolvableType = true;
            } catch (ClassNotFoundException e) {
                hasClazzResolvableType = false;
            }
        }

        private static boolean isSupport() {
            return hasClazzResolvableType;
        }


        private static Type getType(Type type, Class<?> contextClass) {
            if (contextClass != null) {
                ResolvableType resolvedType = ResolvableType.forType(type);
                if (type instanceof TypeVariable) {
                    ResolvableType resolvedTypeVariable = resolveVariable((TypeVariable) type, ResolvableType.forClass(contextClass));
                    if (resolvedTypeVariable != ResolvableType.NONE) {
                        return resolvedTypeVariable.resolve();
                    }
                } else if (type instanceof ParameterizedType && resolvedType.hasUnresolvableGenerics()) {
                    ParameterizedType parameterizedType = (ParameterizedType) type;
                    Class<?>[] generics = new Class[parameterizedType.getActualTypeArguments().length];
                    Type[] typeArguments = parameterizedType.getActualTypeArguments();

                    for (int i = 0; i < typeArguments.length; ++i) {
                        Type typeArgument = typeArguments[i];
                        generics[i] = genericsRefactor(typeArgument,contextClass);
                    }

                    return ResolvableType.forClassWithGenerics(resolvedType.getRawClass(), generics).getType();
                }
            }

            return type;
        }

        protected static Class<?> genericsRefactor(Type typeArgument , Class<?> contextClass){
            Class<?> genericsP;
            if (typeArgument instanceof TypeVariable) {
                ResolvableType resolvedTypeArgument = resolveVariable((TypeVariable) typeArgument, ResolvableType.forClass(contextClass));
                if (resolvedTypeArgument != ResolvableType.NONE) {
                    genericsP = resolvedTypeArgument.resolve();
                } else {
                    genericsP = ResolvableType.forType(typeArgument).resolve();
                }
            } else {
                genericsP = ResolvableType.forType(typeArgument).resolve();
            }
            return genericsP;
        }

        private static ResolvableType resolveVariable(TypeVariable<?> typeVariable, ResolvableType contextType) {
            ResolvableType resolvedType;
            if (contextType.hasGenerics()) {
                resolvedType = ResolvableType.forType(typeVariable, contextType);
                if (resolvedType.resolve() != null) {
                    return resolvedType;
                }
            }

            ResolvableType superType = contextType.getSuperType();
            if (superType != ResolvableType.NONE) {
                resolvedType = resolveVariable(typeVariable, superType);
                if (resolvedType.resolve() != null) {
                    return resolvedType;
                }
            }
            for (ResolvableType ifc : contextType.getInterfaces()) {
                resolvedType = resolveVariable(typeVariable, ifc);
                if (resolvedType.resolve() != null) {
                    return resolvedType;
                }
            }
            return ResolvableType.NONE;
        }
    }


}
