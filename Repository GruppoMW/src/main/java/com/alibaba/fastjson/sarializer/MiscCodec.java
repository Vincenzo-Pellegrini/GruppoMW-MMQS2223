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

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;


import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;

import com.alibaba.fastjson.util.TypeUtils;
import org.w3c.dom.Node;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class MiscCodec implements ObjectSerializer, ObjectDeserializer {
    public static final MiscCodec instance                   = new MiscCodec();
    private       Method    methodPathsGet;
    private       boolean methodPathsGetError = false;
    private static final String PATH_DESERIALIZE_ERROR = "Path deserialize erorr";


    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType,
                      int features) throws IOException {
        SerializeWriter out = serializer.out;

        if (object == null) {
            out.writeNull();
            return;
        }

        Class<?> objClass = object.getClass();

        String strVal = returnObjClassVoid(serializer,object,fieldType,out,objClass);

        out.writeString(strVal);
    }

    public <T> String returnObjClassVoid(JSONSerializer serializer, Object object,Type fieldType,SerializeWriter out,Class<?> objClass){
        String strVal = null;
        if (objClass == SimpleDateFormat.class) {
            String pattern = ((SimpleDateFormat) object).toPattern();

            if (out.isEnabled(SerializerFeature.WRITE_CLASS_NAME) && object.getClass() != fieldType) {
                out.write('{');
                out.writeFieldName(JSON.DEFAULT_TYPE_KEY);
                serializer.write(object.getClass().getName());
                out.writeFieldValue(',', "val", pattern);
                out.write('}');
            }

            strVal = pattern;
        } else if (objClass == Class.class) {
            Class<?> clazz = (Class<?>) object;
            strVal = clazz.getName();
        } else if (objClass == InetSocketAddress.class) {
            InetSocketAddress address = (InetSocketAddress) object;

            InetAddress inetAddress = address.getAddress();

            out.write('{');
            if (inetAddress != null) {
                out.writeFieldName("address");
                serializer.write(inetAddress);
                out.write(',');
            }
            out.writeFieldName("port");
            out.writeInt(address.getPort());
            out.write('}');
        } else if (object instanceof Map.Entry) {
            Map.Entry<T,T> entry = (Map.Entry<T,T>) object;
            Object objKey = entry.getKey();
            Object objVal = entry.getValue();

            ifBlock(objKey,objVal,out,serializer);
            out.write('}');
        } else if (object.getClass().isInstance("net.sf.json.JSONNull")) {
            out.writeNull();
        } else if (object instanceof org.w3c.dom.Node) {
            strVal = toString((Node) object);
        } else {
            throw new JSONException("not support class : " + objClass);
        }
        return strVal;
    }

    public void ifBlock(Object objKey,Object objVal,SerializeWriter out,JSONSerializer serializer){
        if (objKey instanceof String) {
            String key = (String) objKey;

            if (objVal instanceof String) {
                String value = (String) objVal;
                out.writeFieldValueStringWithDoubleQuoteCheck('{', key, value);
            } else {
                out.write('{');
                out.writeFieldName(key);
                serializer.write(objVal);
            }
        } else {
            out.write('{');
            serializer.write(objKey);
            out.write(':');
            serializer.write(objVal);
        }
    }

    private static String toString(org.w3c.dom.Node node) {
        try {
            TransformerFactory transFactory = TransformerFactory.newInstance();
            transFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transFactory.newTransformer();
            DOMSource domSource = new DOMSource(node);

            StringWriter out = new StringWriter();
            transformer.transform(domSource, new StreamResult(out));
            return out.toString();
        } catch (TransformerException e) {
            throw new JSONException("xml node to string error", e);
        }
    }

    protected void writeIterator(JSONSerializer serializer, SerializeWriter out, Iterator<?> it) {
        int i = 0;
        out.write('[');
        while (it.hasNext()) {
            if (i != 0) {
                out.write(',');
            }
            Object item = it.next();
            serializer.write(item);
            ++i;
        }
        out.write(']');
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        JSONLexer lexer = parser.lexer;

        if(lexerNextToken(parser,clazz,lexer)!=null){
            return lexerNextToken(parser,clazz,lexer);
        }

        Object objVal;

        objVal = parserResolveStatus(parser,lexer);

        String strVal = null;

        if(returnBlock(objVal,clazz)!=null){
            return returnBlock(objVal,clazz);
        }

        if(returnBlock2(clazz,parser)!=null){
            return returnBlock2(clazz,parser);
        }



        if (clazz instanceof Class) {
            String className = ((Class) clazz).getName();

            if(classNameEquals(className,strVal)!=null){
                return classNameEquals(className,strVal);
            }

            throw new JSONException("MiscCodec not support " + className);
        }

        throw new JSONException("MiscCodec not support " + clazz.toString());
    }

    public <T> T classNameEquals(String className,String strVal){
        if (className.equals("java.nio.file.Path")) {
            try {
                if (methodPathsGet == null && !methodPathsGetError) {
                    Class<?> paths = TypeUtils.loadClass("java.nio.file.Paths");
                    methodPathsGet = paths.getMethod("get", String.class, String[].class);
                }
                if (methodPathsGet != null) {
                    return (T) methodPathsGet.invoke(null, strVal, new String[0]);
                }

                throw new JSONException(PATH_DESERIALIZE_ERROR);
            } catch (NoSuchMethodException ex) {
                methodPathsGetError = true;
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new JSONException(PATH_DESERIALIZE_ERROR, ex);
            }
        }
        return null;
    }

    public <T> T lexerNextToken(DefaultJSONParser parser, Type clazz,JSONLexer lexer){
        if (clazz == InetSocketAddress.class) {
            if (lexer.token() == JSONToken.NULL) {
                lexer.nextToken();
                return null;
            }

            parser.accept(JSONToken.LBRACE);

            InetAddress address = null;
            int port = 0;
            for (;;) {
                String key = lexer.stringVal();
                lexer.nextToken(JSONToken.COLON);

                if (key.equals("address")) {
                    parser.accept(JSONToken.COLON);
                    address = parser.parseObject(InetAddress.class);
                } else if (key.equals("port")) {
                    parser.accept(JSONToken.COLON);
                    if (lexer.token() != JSONToken.LITERAL_INT) {
                        throw new JSONException("port is not int");
                    }
                    port = lexer.intValue();
                    lexer.nextToken();
                } else {
                    parser.accept(JSONToken.COLON);
                    parser.parse();
                }

                break;
            }

            parser.accept(JSONToken.RBRACE);

            return (T) new InetSocketAddress(address, port);
        }
        return null;
    }

    public <T> T instanceOfJSONObject(Type clazz,Object objVal){
        if (objVal instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) objVal;

            if (clazz == Currency.class) {
                String currency = jsonObject.getString("currency");
                if (currency != null) {
                    return (T) Currency.getInstance(currency);
                }

                String symbol = jsonObject.getString("currencyCode");
                if (symbol != null) {
                    return (T) Currency.getInstance(symbol);
                }
            }

            if (clazz == Map.Entry.class) {
                return (T) jsonObject.entrySet().iterator().next();
            }

            return jsonObject.toJavaObject(clazz);
        }
        return null;
    }

    public Object parserResolveStatus(DefaultJSONParser parser,JSONLexer lexer){
        Object objVal = null;
        if (parser.resolveStatus == DefaultJSONParser.TYPE_NAME_REDIRECT) {
            parser.resolveStatus = DefaultJSONParser.NONE;
            parser.accept(JSONToken.COMMA);

            if (lexer.token() == JSONToken.LITERAL_STRING) {
                if (!"val".equals(lexer.stringVal())) {
                    throw new JSONException("syntax error");
                }
                lexer.nextToken();
            } else {
                throw new JSONException("syntax error");
            }

            parser.accept(JSONToken.COLON);

            objVal = parser.parse();

            parser.accept(JSONToken.RBRACE);
        } else {
            objVal = parser.parse();
        }
        return objVal;
    }

    public <T> T returnBlock(Object objVal,Type clazz){
        String strVal = null;
        if (objVal instanceof String) {
            strVal = (String) objVal;
        } else {
            if(instanceOfJSONObject(clazz,objVal)!=null){
                return instanceOfJSONObject(clazz,objVal);
            }
        }

        if (strVal == null || strVal.length() == 0) {
            return null;
        }

        if (clazz == UUID.class) {
            return (T) UUID.fromString(strVal);
        }

        if (clazz == URI.class) {
            return (T) URI.create(strVal);
        }

        if (clazz == URL.class) {
            try {
                return (T) new URL(strVal);
            } catch (MalformedURLException e) {
                throw new JSONException("create url error", e);
            }
        }

        if (clazz == Pattern.class) {
            return (T) Pattern.compile(strVal);
        }

        if (clazz == Locale.class) {
            return (T) TypeUtils.toLocale(strVal);
        }
        return null;
    }

    public <T> T returnBlock2(Type clazz,DefaultJSONParser parser){
        String strVal = null;
        if (clazz == File.class) {
            return (T) new File(strVal);
        }

        if (clazz == TimeZone.class) {
            return (T) TimeZone.getTimeZone(strVal);
        }

        if (clazz instanceof ParameterizedType) {
            ParameterizedType parmeterizedType = (ParameterizedType) clazz;
            clazz = parmeterizedType.getRawType();
        }

        if (clazz == Class.class) {
            return (T) TypeUtils.loadClass(strVal, parser.getConfig().getDefaultClassLoader(), false);
        }

        if (clazz == Charset.class) {
            return (T) Charset.forName(strVal);
        }

        if (clazz == Currency.class) {
            return (T) Currency.getInstance(strVal);
        }

        if (clazz == JSONPath.class) {
            return (T) new JSONPath(strVal);
        }
        return null;
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_STRING;
    }
}
