package com.alibaba.fastjson.serializer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ContextObjectDeserializer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

public class CalendarCodec extends ContextObjectDeserializer implements ObjectSerializer, ObjectDeserializer, ContextObjectSerializer {

    public static final CalendarCodec instance = new CalendarCodec();

    private DatatypeFactory dateFactory;

    public void write(JSONSerializer serializer, Object object, BeanContext context) throws IOException {
        SerializeWriter out = serializer.out;
        String format = context.getFormat();
        Calendar calendar = (Calendar) object;

        if (format.equals("unixtime")) {
            long seconds = calendar.getTimeInMillis() / 1000L;
            out.writeInt((int) seconds);
            return;
        }

        DateFormat dateFormat = new SimpleDateFormat(format);



        dateFormat.setTimeZone(serializer.timeZone);
        String text = dateFormat.format(calendar.getTime());
        out.writeString(text);
    }


    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;
        if (object == null) {
            out.writeNull();
            return;
        }
        Calendar calendar = object instanceof XMLGregorianCalendar ? ((XMLGregorianCalendar) object).toGregorianCalendar() : (Calendar) object;

        if (out.isEnabled(SerializerFeature.USE_ISO8601_DATE_FORMAT)) {
            final char quote = out.isEnabled(SerializerFeature.USE_SINGLE_QUOTES) ? '\'' : '\"';
            out.append(quote);

            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            int second = calendar.get(Calendar.SECOND);
            int millis = calendar.get(Calendar.MILLISECOND);

            String format = "";
            if (millis != 0) {
                format = "%04d-%02d-%02dT%02d:%02d:%02d.%03d";
            } else if (second == 0 && minute == 0 && hour == 0) {
                format = "%04d-%02d-%02d";
            } else {
                format = "%04d-%02d-%02dT%02d:%02d:%02d";
            }

            char[] buf = String.format(format, year, month, day, hour, minute, second, millis).toCharArray();
            out.write(buf);

            int timeZone = calendar.getTimeZone().getOffset(calendar.getTimeInMillis()) / (3600 * 1000);
            timezoneFunction(timeZone,calendar,out);

            out.append(quote);
        } else {
            Date date = calendar.getTime();
            serializer.write(date);
        }
    }

    protected void timezoneFunction(int timeZone, Calendar calendar, SerializeWriter out){
        if (timeZone == 0) {
            out.write('Z');
        } else {
            out.write(timeZone > 0 ? '+' : '-');
            timeZone = Math.abs(timeZone);
            out.append(String.format("%02d", timeZone));
            out.write(':');
            int offsetInMinutes = (Math.abs(calendar.getTimeZone().getOffset(calendar.getTimeInMillis())) / (60 * 1000)) % 60;
            out.append(String.format("%02d", offsetInMinutes));
        }
    }
    @Override
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        return deserialze(parser, clazz, fieldName, null, 0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName, String format, int features) {
        Object value = DateCodec.instance.deserialze(parser, type, fieldName, format, features);

        if (value instanceof Calendar) {
            return (T) value;
        }

        Date date = (Date) value;
        if (date == null) {
            return null;
        }

        JSONLexer lexer = parser.lexer;
        Calendar calendar = Calendar.getInstance(lexer.getTimeZone(), lexer.getLocale());
        calendar.setTime(date);

        if (type == XMLGregorianCalendar.class) {
            return (T) createXMLGregorianCalendar(calendar);
        }

        return (T) calendar;
    }

    public XMLGregorianCalendar createXMLGregorianCalendar(Calendar calendar) {
        if (dateFactory == null) {
            try {
                dateFactory = DatatypeFactory.newInstance();
            } catch (DatatypeConfigurationException e) {
                throw new IllegalStateException("Could not obtain an instance of DatatypeFactory.", e);
            }
        }
        return dateFactory.newXMLGregorianCalendar((GregorianCalendar) calendar);
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_INT;
    }
}
