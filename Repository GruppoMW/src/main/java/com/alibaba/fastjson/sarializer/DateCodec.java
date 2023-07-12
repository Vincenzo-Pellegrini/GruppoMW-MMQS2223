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

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.text.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONScanner;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.AbstractDateDeserializer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.util.IOUtils;
import com.alibaba.fastjson.util.TypeUtils;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class DateCodec extends AbstractDateDeserializer implements ObjectSerializer, ObjectDeserializer {

    public static final DateCodec instance = new DateCodec();

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        if (object == null) {
            out.writeNull();
            return;
        }

        Class<?> clazz = object.getClass();

        millisEquals(clazz,serializer,object,features,out);

        int nanos = 0;
        if (clazz == java.sql.Timestamp.class) {
            java.sql.Timestamp ts = (java.sql.Timestamp) object;
            nanos = ts.getNanos();
        }

        Date date = returnDate(object);

        returnMillis(serializer,object,out);

        writeOut(clazz,serializer,object,out,fieldType);

        long time = date.getTime();
        if (out.isEnabled(SerializerFeature.USE_ISO8601_DATE_FORMAT)) {

            retunOutValue(serializer,out,time,nanos);

        }
    }

    public Date returnDate(Object object){
        Date datep;
        if (object instanceof Date) {
            datep = (Date) object;
        } else {
            datep = TypeUtils.castToDate(object);
        }
        return datep;
    }

    public void millisEquals(Class<?> clazz,JSONSerializer serializer, Object object, int features,SerializeWriter out){
        if (clazz == java.sql.Date.class && !out.isEnabled(SerializerFeature.WRITE_DATE_USE_DATE_FORMAT)) {
            long millis = ((java.sql.Date) object).getTime();
            TimeZone timeZone = serializer.timeZone;
            int offset = timeZone.getOffset(millis);
            //
            if ((millis + offset) % (24 * 1000 * 3600) == 0
                    && !SerializerFeature.isEnabled(out.features, features, SerializerFeature.WRITE_CLASS_NAME)) {
                out.writeString(object.toString());
            }
        }

        if (clazz == java.sql.Time.class) {
            long millis = ((java.sql.Time) object).getTime();
            if ("unixtime".equals(serializer.getDateFormatPattern())) {
                long seconds = millis / 1000;
                out.writeLong(seconds);
            }

            if ("millis".equals(serializer.getDateFormatPattern())) {
                out.writeLong(millis);
            }

            if (millis < 24L * 60L * 60L * 1000L) {
                out.writeString(object.toString());
            }
        }
    }

    public void returnMillis(JSONSerializer serializer, Object object,SerializeWriter out){
        Date datep;
        if (object instanceof Date) {
            datep = (Date) object;
        } else {
            datep = TypeUtils.castToDate(object);
        }

        if ("unixtime".equals(serializer.getDateFormatPattern())) {
            long seconds = datep.getTime() / 1000;
            out.writeLong(seconds);
        }

        if ("millis".equals(serializer.getDateFormatPattern())) {
            long millis = datep.getTime();
            out.writeLong(millis);
        }

        if (out.isEnabled(SerializerFeature.WRITE_DATE_USE_DATE_FORMAT)) {
            DateFormat format = serializer.getDateFormat();
            if (format == null) {
                // 如果是通过FastJsonConfig进行设置，优先从FastJsonConfig获取
                String dateFormatPattern = serializer.getFastJsonConfigDateFormatPattern();
                if(dateFormatPattern == null) {
                    dateFormatPattern = JSON.DEFAULT_DATE_FORMAT;
                }

                format = new SimpleDateFormat(dateFormatPattern, serializer.locale);
                format.setTimeZone(serializer.timeZone);
            }
            String text = format.format(datep);
            out.writeString(text);
        }
    }

    public void writeOut(Class<?> clazz,JSONSerializer serializer, Object object,SerializeWriter out,Type fieldType){
        if (out.isEnabled(SerializerFeature.WRITE_CLASS_NAME) && clazz != fieldType) {
            if (clazz == Date.class) {
                out.write("new Date(");
                out.writeLong(((Date) object).getTime());
                out.write(')');
            } else {
                out.write('{');
                out.writeFieldName(JSON.DEFAULT_TYPE_KEY);
                serializer.write(clazz.getName());
                out.writeFieldValue(',', "val", ((Date) object).getTime());
                out.write('}');
            }
        }
    }

    public void retunOutValue(JSONSerializer serializer,SerializeWriter out,long time,int nanos){
        char quote = out.isEnabled(SerializerFeature.USE_SINGLE_QUOTES) ? '\'' : '\"';
        out.write(quote);

        Calendar calendar = Calendar.getInstance(serializer.timeZone, serializer.locale);
        calendar.setTimeInMillis(time);

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int millis = calendar.get(Calendar.MILLISECOND);

        char[] buf;
        if (nanos > 0) {
            buf = "0000-00-00 00:00:00.000000000".toCharArray();
            IOUtils.getChars(nanos, 29, buf);
            IOUtils.getChars(second, 19, buf);
            IOUtils.getChars(minute, 16, buf);
            IOUtils.getChars(hour, 13, buf);
            IOUtils.getChars(day, 10, buf);
            IOUtils.getChars(month, 7, buf);
            IOUtils.getChars(year, 4, buf);
        } else if (millis != 0) {
            buf = "0000-00-00T00:00:00.000".toCharArray();
            IOUtils.getChars(millis, 23, buf);
            IOUtils.getChars(second, 19, buf);
            IOUtils.getChars(minute, 16, buf);
            IOUtils.getChars(hour, 13, buf);
            IOUtils.getChars(day, 10, buf);
            IOUtils.getChars(month, 7, buf);
            IOUtils.getChars(year, 4, buf);

        } else {
            if (second == 0 && minute == 0 && hour == 0) {
                buf = "0000-00-00".toCharArray();
                IOUtils.getChars(day, 10, buf);
                IOUtils.getChars(month, 7, buf);
                IOUtils.getChars(year, 4, buf);
            } else {
                buf = "0000-00-00T00:00:00".toCharArray();
                IOUtils.getChars(second, 19, buf);
                IOUtils.getChars(minute, 16, buf);
                IOUtils.getChars(hour, 13, buf);
                IOUtils.getChars(day, 10, buf);
                IOUtils.getChars(month, 7, buf);
                IOUtils.getChars(year, 4, buf);
            }
        }


        if (nanos > 0) { // java.sql.Timestamp
            int i = 0;
            for (; i < 9; ++i) {
                int off = buf.length - i - 1;
                if (buf[off] != '0') {
                    break;
                }
            }
            out.write(buf, 0, buf.length - i);
            out.write(quote);
        }
    }

    public void outAppend(SerializeWriter out,int timeZone){
        if (timeZone > 9) {
            out.write('+');
            out.writeInt(timeZone);
        } else if (timeZone > 0) {
            out.write('+');
            out.write('0');
            out.writeInt(timeZone);
        } else if (timeZone < -9) {
            out.write('-');
            out.writeInt(-timeZone);
        } else if (timeZone < 0) {
            out.write('-');
            out.write('0');
            out.writeInt(-timeZone);
        }
        out.write(':');
    }

    @SuppressWarnings("unchecked")
    public <T> T cast(DefaultJSONParser parser, Type clazz, Object fieldName, Object val) {

        if (val == null) {
            return null;
        }

        if (val instanceof java.util.Date) {
            return (T) val;
        } else if (val instanceof BigDecimal) {
            return (T) new java.util.Date(TypeUtils.longValue((BigDecimal) val));
        } else if (val instanceof Number) {
            return (T) new java.util.Date(((Number) val).longValue());
        } else if (val instanceof String) {
            String strVal = (String) val;

            if (strVal.startsWith("/Date(") && strVal.endsWith(")/")) {
                String dotnetDateStr = strVal.substring(6, strVal.length() - 2);
                strVal = dotnetDateStr;
            }

            if ("0000-00-00".equals(strVal)
                    || "0000-00-00T00:00:00".equalsIgnoreCase(strVal)
                    || "0001-01-01T00:00:00+08:00".equalsIgnoreCase(strVal)) {
                return null;
            }

            // 2017-08-14 19:05:30.000|America/Los_Angeles
//            
            long longVal = Long.parseLong(strVal);
            return (T) new java.util.Date(longVal);
        }

        throw new JSONException("parse error");
    }

    public <T> T returnValue(Type clazz,String strValp){
        if (strValp.length() == 0) {
            return null;
        }

        if (strValp.length() == 23 && strValp.endsWith(" 000")) {
            strValp = strValp.substring(0, 19);
        }

        try ( JSONScanner dateLexer = new JSONScanner(strValp) ) {
            if (dateLexer.scanISO8601DateIfMatch(false)) {
                Calendar calendar = dateLexer.getCalendar();

                if (clazz == Calendar.class) {
                    return (T) calendar;
                }

                return (T) calendar.getTime();
            }
        }
        return null;
    }

    public <T> T returnType(String strValp,int index,Type clazz){
        if (index > 20) {
            String tzStr = strValp.substring(index + 1);
            TimeZone timeZone = TimeZone.getTimeZone(tzStr);
            if (!"GMT".equals(timeZone.getID())) {
                String subStr = strValp.substring(0, index);
                try ( JSONScanner dateLexer = new JSONScanner(subStr) ) {
                    if (dateLexer.scanISO8601DateIfMatch(false)) {
                        Calendar calendar = dateLexer.getCalendar();

                        calendar.setTimeZone(timeZone);

                        if (clazz == Calendar.class) {
                            return (T) calendar;
                        }

                        return (T) calendar.getTime();
                    }
                }
            }
        }
        return null;
    }

    public boolean strValBool(String strVal, String dateFomartPattern){
        return strVal.length() == dateFomartPattern.length()
                || (strVal.length() == 22 && dateFomartPattern.equals("yyyyMMddHHmmssSSSZ"))
                || (strVal.indexOf('T') != -1 && dateFomartPattern.contains("'T'") && strVal.length() + 2 == dateFomartPattern.length())
                ;
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_INT;
    }

}
