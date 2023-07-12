package com.alibaba.fastjson.serializer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.TimeZone;

import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import org.joda.time.*;
import org.joda.time.format.*;

public class JodaCodec implements ObjectSerializer, ContextObjectSerializer, ObjectDeserializer {
    public static final JodaCodec instance = new JodaCodec();

    private static final String            DEFAULT_PATTERN     = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter defaultFormatter    = DateTimeFormat.forPattern(DEFAULT_PATTERN);
    private static final DateTimeFormatter defaultFormatter_23 = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter formatter_dt19_tw   = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_cn   = DateTimeFormat.forPattern("yyyy年M月d日 HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_cn_1 = DateTimeFormat.forPattern("yyyy年M月d日 H时m分s秒");
    private static final DateTimeFormatter formatter_dt19_us   = DateTimeFormat.forPattern("MM/dd/yyyy HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_eur  = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter formatter_d8        = DateTimeFormat.forPattern("yyyyMMdd");
    private static final DateTimeFormatter formatter_d10_tw    = DateTimeFormat.forPattern("yyyy/MM/dd");
    private static final DateTimeFormatter formatter_d10_cn    = DateTimeFormat.forPattern("yyyy年M月d日");
    private static final DateTimeFormatter formatter_d10_kr    = DateTimeFormat.forPattern("yyyy년M월d일");
    private static final DateTimeFormatter formatter_d10_us    = DateTimeFormat.forPattern("MM/dd/yyyy");
    private static final DateTimeFormatter formatter_d10_eur   = DateTimeFormat.forPattern("dd/MM/yyyy");
    private static final DateTimeFormatter formatter_d10_de    = DateTimeFormat.forPattern("dd.MM.yyyy");
    private static final DateTimeFormatter formatter_d10_in    = DateTimeFormat.forPattern("dd-MM-yyyy");

    private static final DateTimeFormatter ISO_FIXED_FORMAT =
            DateTimeFormat.forPattern(DEFAULT_PATTERN).withZone(DateTimeZone.getDefault());

    private static final  String FORMATTER_ISO8601_PATTERN     = "yyyy-MM-dd'T'HH:mm:ss";
    private static final  String FORMATTER_ISO8601_PATTERN_23     = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private static final  String FORMATTER_ISO8601_PATTERN_29     = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS";
    private static final  DateTimeFormatter formatter_iso8601  = DateTimeFormat.forPattern(FORMATTER_ISO8601_PATTERN);


    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        return deserialze(parser, type);
    }

    public <T> T deserialze(DefaultJSONParser parser, Type type) {
        JSONLexer lexer = parser.lexer;
        if (lexer.token() == JSONToken.NULL){
            lexer.nextToken();
            return null;
        }

        if (lexer.token() == JSONToken.LITERAL_STRING) {
            if(returnValue2(lexer, type)!=null){
                return returnValue2(lexer, type);
            }
        } else if (lexer.token() == JSONToken.LITERAL_INT) {
            long millis = lexer.longValue();
            lexer.nextToken();

            if(returnValue(type,millis)!=null){
                return returnValue(type,millis);
            }

            throw new UnsupportedOperationException();
        }
        return null;
    }

    public <T> T returnValue(Type type,Object millis){
        TimeZone timeZone = JSON.defaultTimeZone;
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }

        if (type == DateTime.class) {
            return (T) new DateTime(millis, DateTimeZone.forTimeZone(timeZone));
        }

        LocalDateTime localDateTime = new LocalDateTime(millis, DateTimeZone.forTimeZone(timeZone));
        if (type == LocalDateTime.class) {
            return (T) localDateTime;
        }

        if (type == LocalDate.class) {
            return (T) localDateTime.toLocalDate();
        }

        if (type == LocalTime.class) {
            return (T) localDateTime.toLocalTime();
        }

        if (type == Instant.class) {
            Instant instant = new Instant(millis);

            return (T) instant;
        }
        return null;
    }

    public <T> T returnValue2(JSONLexer lexer, Type type){
        String text = lexer.stringVal();
        lexer.nextToken();

        DateTimeFormatter formatter = null;

        if (type == LocalDateTime.class) {
            LocalDateTime localDateTime;
            if (text.length() == 10 || text.length() == 8) {
                LocalDate localDate = parseLocalDate(text, formatter);
                localDateTime = localDate.toLocalDateTime(LocalTime.MIDNIGHT);
            } else {
                localDateTime = parseDateTime(text, formatter);
            }
            return (T) localDateTime;
        } else if (type == LocalTime.class) {
            LocalTime localDate;
            if (text.length() == 23) {
                LocalDateTime localDateTime = LocalDateTime.parse(text);
                localDate = localDateTime.toLocalTime();
            } else {
                localDate = LocalTime.parse(text);
            }
            return (T) localDate;
        } else if (type == DateTime.class) {
            if (formatter == defaultFormatter) {
                formatter = ISO_FIXED_FORMAT;
            }

            DateTime zonedDateTime = parseZonedDateTime(text, formatter);

            return (T) zonedDateTime;
        }
        return null;
    }

    protected LocalDateTime parseDateTime(String text, DateTimeFormatter formatter) {
        if (formatter == null) {

            formatter = returnFormatterMultipleChoice(text);

            boolean digit = true;
            for (int i = 0; i < text.length(); ++i) {
                char ch = text.charAt(i);
                if (ch < '0' || ch > '9') {
                    digit = false;
                    break;
                }
            }
            if (digit && text.length() > 8 && text.length() < 19) {
                long epochMillis = Long.parseLong(text);
                return new LocalDateTime(epochMillis, DateTimeZone.forTimeZone(JSON.defaultTimeZone));
            }
        }

        return formatter == null ? //
                LocalDateTime.parse(text) //
                : LocalDateTime.parse(text, formatter);
    }

    public DateTimeFormatter cValues(String text,char c4,char c7,char c10){
        DateTimeFormatter formatterp = null;
        if (c4 == '-' && c7 == '-') { // yyyy-MM-dd  or  yyyy-MM-dd'T'
            if (c10 == 'T') {
                formatterp = formatter_iso8601;
            } else if (c10 == ' ') {
                formatterp = defaultFormatter;
            }
        } else if (c4 == '/' && c7 == '/') { // tw yyyy/mm/dd
            formatterp = formatter_dt19_tw;
        } else {
            char c0 = text.charAt(0);
            char c1 = text.charAt(1);
            char c2 = text.charAt(2);
            char c5 = text.charAt(5);
            if (c2 == '/' && c5 == '/') { // mm/dd/yyyy or mm/dd/yyyy
                int v0 = (c0 - '0') * 10 + (c1 - '0');
                if (v0 > 12) {
                    formatterp = formatter_dt19_eur;
                }
            }
        }
        return formatterp;
    }

    public DateTimeFormatter returnFormatterMultipleChoice(String text){
        DateTimeFormatter formatterq = null;
        if (text.length() == 19) {
            char c4 = text.charAt(4);
            char c7 = text.charAt(7);
            char c10 = text.charAt(10);
            char c13 = text.charAt(13);
            char c16 = text.charAt(16);
            if (c13 == ':' && c16 == ':') {
                formatterq = cValues(text,c4,c7,c10);
            }
        } else if (text.length() == 23) {
            char c4 = text.charAt(4);
            char c7 = text.charAt(7);
            char c10 = text.charAt(10);
            char c13 = text.charAt(13);
            char c16 = text.charAt(16);
            char c19 = text.charAt(19);

            if (c13 == ':'
                    && c16 == ':'
                    && c4 == '-'
                    && c7 == '-'
                    && c10 == ' '
                    && c19 == '.'
            ) {
                formatterq = defaultFormatter_23;
            }
        }

        if (text.length() >= 17) {
            char c4 = text.charAt(4);
            if (c4 == '年') {
                if (text.charAt(text.length() - 1) == '秒') {
                    formatterq = formatter_dt19_cn_1;
                } else {
                    formatterq = formatter_dt19_cn;
                }
            }
        }
        return formatterq;
    }

    protected LocalDate parseLocalDate(String text, DateTimeFormatter formatter) {
        if (formatter == null) {
            if (text.length() == 8) {
                formatter = formatter_d8;
            }

            formatter = ifTextLenghtIs10(text,formatter);

            if (text.length() >= 9) {
                char c4 = text.charAt(4);
                if (c4 == '年') {
                    formatter = formatter_d10_cn;
                } else if (c4 == '년') {
                    formatter = formatter_d10_kr;
                }
            }
        }

        return formatter == null ? //
                LocalDate.parse(text) //
                : LocalDate.parse(text, formatter);
    }

    public DateTimeFormatter ifTextLenghtIs10(String text, DateTimeFormatter formatterp){
        if (text.length() == 10) {
            char c4 = text.charAt(4);
            char c7 = text.charAt(7);
            if (c4 == '/' && c7 == '/') { // tw yyyy/mm/dd
                formatterp = formatter_d10_tw;
            }
        }
        return formatterp;
    }

    public DateTimeFormatter charVoid(String text,DateTimeFormatter formatterq){
        char c0 = text.charAt(0);
        char c1 = text.charAt(1);
        char c2 = text.charAt(2);
        char c3 = text.charAt(3);
        char c4 = text.charAt(4);
        char c5 = text.charAt(5);
        if (c2 == '/' && c5 == '/') { // mm/dd/yyyy or mm/dd/yyyy
            int v0 = (c0 - '0') * 10 + (c1 - '0');
            int v1 = (c3 - '0') * 10 + (c4 - '0');
            if (v0 > 12) {
                formatterq = formatter_d10_eur;
            } else if (v1 > 12) {
                formatterq = formatter_d10_us;
            } else {
                String country = Locale.getDefault().getCountry();

                if (country.equals("US")) {
                    formatterq = formatter_d10_us;
                } else if (country.equals("BR") //
                        || country.equals("AU")) {
                    formatterq = formatter_d10_eur;
                }
            }
        } else if (c2 == '.' && c5 == '.') { // dd.mm.yyyy
            formatterq = formatter_d10_de;
        } else if (c2 == '-' && c5 == '-') { // dd-mm-yyyy
            formatterq = formatter_d10_in;
        }
        return formatterq;
    }

    public LocalDate digitBoolean(String text){
        boolean digit = true;
        for (int i = 0; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                digit = false;
            }
        }
        if (digit && text.length() > 8 && text.length() < 19) {
            long epochMillis = Long.parseLong(text);
            return new LocalDateTime(epochMillis, DateTimeZone.forTimeZone(JSON.defaultTimeZone))
                    .toLocalDate();
        }
        return null;
    }

    protected DateTime parseZonedDateTime(String text, DateTimeFormatter formatter) {
        if (formatter == null && text.length() == 19) {
            char c4 = text.charAt(4);
            char c7 = text.charAt(7);
            char c10 = text.charAt(10);
            char c13 = text.charAt(13);
            char c16 = text.charAt(16);
            try {
                formatter = returnFormatter(text, c4, c7, c10, c13, c16);
            } catch (NullPointerException e) {
                //Skip
            }
        }

        return formatter == null ? //
                DateTime.parse(text) //
                : DateTime.parse(text, formatter);
    }

    public DateTimeFormatter returnFormatter(String text, char c4, char c7, char c10, char c13, char c16){
        DateTimeFormatter formatterd =  null;
        if (c13 == ':' && c16 == ':') {
            formatterd = c4Equals(text,c4,c7,c10);
        }
        return formatterd;
    }

    public DateTimeFormatter c4Equals(String text, char c4, char c7, char c10){
        DateTimeFormatter formattert =  null;
        if (c4 == '-' && c7 == '-') { // yyyy-MM-dd  or  yyyy-MM-dd'T'
            if (c10 == 'T') {
                formattert = formatter_iso8601;
            } else if (c10 == ' ') {
                formattert = defaultFormatter;
            }
        } else if (c4 == '/' && c7 == '/') { // tw yyyy/mm/dd
            formattert = formatter_dt19_tw;
        } else {
            char c0 = text.charAt(0);
            char c1 = text.charAt(1);
            char c2 = text.charAt(2);
            char c3 = text.charAt(3);
            char c5 = text.charAt(5);
            if (c2 == '/' && c5 == '/') { // mm/dd/yyyy or mm/dd/yyyy
                int v0 = (c0 - '0') * 10 + (c1 - '0');
                int v1 = (c3 - '0') * 10 + (c4 - '0');
                if (v0 > 12) {
                    formattert = formatter_dt19_eur;
                } else if (v1 > 12) {
                    formattert = formatter_dt19_us;
                }
            }
        }
        return formattert;
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_STRING;
    }

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType,
                      int features) throws IOException {
        SerializeWriter out = serializer.out;
        if (object == null) {
            out.writeNull();
        } else {
            if (fieldType == null) {
                fieldType = object.getClass();
            }

            if (fieldType == LocalDateTime.class) {
                final int mask = SerializerFeature.USE_ISO8601_DATE_FORMAT.getMask();
                LocalDateTime dateTime = (LocalDateTime) object;
                String format = serializer.getDateFormatPattern();

                format = returnFormat(format,serializer,features,mask,dateTime);

                if (format != null) {
                    write(out, dateTime, format);
                } else {
                    out.writeLong(dateTime.toDateTime(DateTimeZone.forTimeZone(JSON.defaultTimeZone)).toInstant().getMillis());
                }
            } else {
                out.writeString(object.toString());
            }
        }
    }

    public String returnFormat(String formatp , JSONSerializer serializer,int features,final int mask,LocalDateTime dateTime){
        if (formatp == null) {
            if ((features & mask) != 0 || serializer.isEnabled(SerializerFeature.USE_ISO8601_DATE_FORMAT)) {
                formatp = FORMATTER_ISO8601_PATTERN;
            } else if (serializer.isEnabled(SerializerFeature.WRITE_DATE_USE_DATE_FORMAT)) {
                formatp = JSON.DEFAULT_DATE_FORMAT;
            } else {
                int millis = dateTime.getMillisOfSecond();
                if (millis == 0) {
                    formatp = FORMATTER_ISO8601_PATTERN_23;
                } else {
                    formatp = FORMATTER_ISO8601_PATTERN_29;
                }
            }
        }
        return formatp;
    }

    public void write(JSONSerializer serializer, Object object, BeanContext context) throws IOException {
        SerializeWriter out = serializer.out;
        String format = context.getFormat();
        write(out, (ReadablePartial) object, format);
    }

    private void write(SerializeWriter out, ReadablePartial object, String format) {
        DateTimeFormatter formatter;
        if (format.equals(FORMATTER_ISO8601_PATTERN)) {
            formatter = formatter_iso8601;
        } else {
            formatter = DateTimeFormat.forPattern(format);
        }

        String text = formatter.print(object);
        out.writeString(text);
    }
}
