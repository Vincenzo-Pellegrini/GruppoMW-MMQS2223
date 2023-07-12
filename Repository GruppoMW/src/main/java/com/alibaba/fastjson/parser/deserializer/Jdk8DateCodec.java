package com.alibaba.fastjson.parser.deserializer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONScanner;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.serializer.*;
import com.alibaba.fastjson.util.TypeUtils;

public class Jdk8DateCodec extends ContextObjectDeserializer implements ObjectSerializer, ContextObjectSerializer, ObjectDeserializer {

    public static final Jdk8DateCodec      instance            = new Jdk8DateCodec();

    private static final String            DEFAULT_PATTERN     = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter defaultFormatter    = DateTimeFormatter.ofPattern(DEFAULT_PATTERN);
    private static final DateTimeFormatter defaultFormatter_23 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter formatter_dt19_tw   = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_cn   = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_cn_1 = DateTimeFormatter.ofPattern("yyyy年M月d日 H时m分s秒");
    private static final DateTimeFormatter formatter_dt19_kr   = DateTimeFormatter.ofPattern("yyyy년M월d일 HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_us   = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_eur  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_de   = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter formatter_dt19_in   = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private static final DateTimeFormatter formatter_d8        = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter formatter_d10_tw    = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter formatter_d10_cn    = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter formatter_d10_kr    = DateTimeFormatter.ofPattern("yyyy년M월d일");
    private static final DateTimeFormatter formatter_d10_us    = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter formatter_d10_eur   = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter formatter_d10_de    = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter formatter_d10_in    = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final String FORMATTER_ISO8601_PATTERN        = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String FORMATTER_ISO8601_PATTERN_23     = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private static final String FORMATTER_ISO8601_PATTERN_29     = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS";
    private static final DateTimeFormatter formatter_iso8601     = DateTimeFormatter.ofPattern(FORMATTER_ISO8601_PATTERN);

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName, String format, int feature) {
        JSONLexer lexer = parser.lexer;

        if (lexer.token() == JSONToken.LBRACE) {
            JSONObject object = parser.parseObject();

            if (type == Instant.class) {
                Object epochSecond = object.get("epochSecond");
                Object nano = object.get("nano");
                if (epochSecond instanceof Number && nano instanceof Number) {
                    return (T) Instant.ofEpochSecond(
                            TypeUtils.longExtractValue((Number) epochSecond)
                            , TypeUtils.longExtractValue((Number) nano));
                }

                if (epochSecond instanceof Number) {
                    return (T) Instant.ofEpochSecond(
                            TypeUtils.longExtractValue((Number) epochSecond));
                }
            }
        } else {
            throw new UnsupportedOperationException();
        }
        return null;
    }

    private boolean extracted40(String text, boolean digit) {
        for (int i = 0; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if (extracted31(ch)) {
                digit = false;
                break;
            }
        }
        return digit;
    }

    protected LocalDateTime parseDateTime(String text, DateTimeFormatter formatter) {
        if (formatter == null) {
            formatter = extracted35(text, formatter);

            formatter = extracted30(text, formatter);
        }

        if (formatter == null) {
            JSONScanner dateScanner = new JSONScanner(text);
            dateScanner.close();
            if (dateScanner.scanISO8601DateIfMatch(false)) {
                Instant instant = dateScanner.getCalendar().toInstant();
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            }

            boolean digit = true;
            digit = extracted40(text, digit);
            if (extracted33(text, digit)) {
                long epochMillis = Long.parseLong(text);
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), JSON.defaultTimeZone.toZoneId());
            }
        }

        return formatter == null ? //
            LocalDateTime.parse(text) //
            : LocalDateTime.parse(text, formatter);
    }

    private DateTimeFormatter extracted35(String text, DateTimeFormatter formatter) {
        if (text.length() == 19) {
            formatter = extracted26(text, formatter);
        } else if (text.length() == 23) {
            char c4 = text.charAt(4);
            char c7 = text.charAt(7);
            char c10 = text.charAt(10);
            char c13 = text.charAt(13);
            char c16 = text.charAt(16);
            char c19 = text.charAt(19);

            formatter = extracted34(formatter, c4, c7, c10, c13, c16, c19);
        }
        return formatter;
    }

    private DateTimeFormatter extracted34(DateTimeFormatter formatter, char c4, char c7, char c10, char c13, char c16,
            char c19) {
        if (extracted32(c4, c7, c10, c13, c16, c19)
        ) {
            formatter = defaultFormatter_23;
        }
        return formatter;
    }

    private boolean extracted32(char c4, char c7, char c10, char c13, char c16, char c19) {
        return c13 == ':'
                && c16 == ':'
                && c4 == '-'
                && c7 == '-'
                && c10 == ' '
                && c19 == '.';
    }

    protected LocalDate parseLocalDate(String text,DateTimeFormatter formatter) {
        if (formatter == null) {
            formatter = extracted11(text, formatter);

            formatter = extracted16(text, formatter);

            formatter = extracted18(text, formatter);

            boolean digit = extracted19(text);
            if (extracted33(text, digit)) {
                long epochMillis = Long.parseLong(text);
                return LocalDateTime
                        .ofInstant(
                                Instant.ofEpochMilli(epochMillis),
                                JSON.defaultTimeZone.toZoneId())
                        .toLocalDate();
            }
        }

        return extracted20(text, formatter);
    }

    private LocalDate extracted20(String text, DateTimeFormatter formatter) {
        return formatter == null ? //
            LocalDate.parse(text) //
            : LocalDate.parse(text, formatter);
    }

    private boolean extracted19(String text) {
        boolean digit = true;
        digit = extracted40(text, digit);
        return digit;
    }

    private DateTimeFormatter extracted18(String text, DateTimeFormatter formatter) {
        if (text.length() >= 9) {
            char c4 = text.charAt(4);
            formatter = extracted17(formatter, c4);
        }
        return formatter;
    }

    private DateTimeFormatter extracted17(DateTimeFormatter formatter, char c4) {
        if (c4 == '年') {
            formatter = formatter_d10_cn;
        } else if (c4 == '년') {
            formatter = formatter_d10_kr;
        }
        return formatter;
    }

    private DateTimeFormatter extracted16(String text, DateTimeFormatter formatter) {
        if (text.length() == 10) {
            char c4 = text.charAt(4);
            char c7 = text.charAt(7);
            formatter = extracted12(formatter, c4, c7);

            char c0 = text.charAt(0);
            char c1 = text.charAt(1);
            char c2 = text.charAt(2);
            char c3 = text.charAt(3);
            char c5 = text.charAt(5);
            formatter = extracted15(formatter, c4, c0, c1, c2, c3, c5);
        }
        return formatter;
    }

    private DateTimeFormatter extracted15(DateTimeFormatter formatter, char c4, char c0, char c1, char c2, char c3,
            char c5) {
        if (c2 == '/' && c5 == '/') { // mm/dd/yyyy or mm/dd/yyyy
            int v0 = (c0 - '0') * 10 + (c1 - '0');
            int v1 = (c3 - '0') * 10 + (c4 - '0');
            formatter = extracted14(formatter, v0, v1);
        } else if (c2 == '.' && c5 == '.') { // dd.mm.yyyy
            formatter = formatter_d10_de;
        } else if (c2 == '-' && c5 == '-') { // dd-mm-yyyy
            formatter = formatter_d10_in;
        }
        return formatter;
    }

    private DateTimeFormatter extracted14(DateTimeFormatter formatter, int v0, int v1) {
        if (v0 > 12) {
            formatter = formatter_d10_eur;
        } else if (v1 > 12) {
            formatter = formatter_d10_us;
        } else {
            formatter = extracted13(formatter);
        }
        return formatter;
    }

    private DateTimeFormatter extracted13(DateTimeFormatter formatter) {
        String country = Locale.getDefault().getCountry();

        if (country.equals("US")) {
            formatter = formatter_d10_us;
        } else if (country.equals("BR") //
                   || country.equals("AU")) {
            formatter = formatter_d10_eur;
        }
        return formatter;
    }

    private DateTimeFormatter extracted12(DateTimeFormatter formatter, char c4, char c7) {
        if (c4 == '/' && c7 == '/') { // tw yyyy/mm/dd
            formatter = formatter_d10_tw;
        }
        return formatter;
    }

    private DateTimeFormatter extracted11(String text, DateTimeFormatter formatter) {
        if (text.length() == 8) {
            formatter = formatter_d8;
        }
        return formatter;
    }

    protected ZonedDateTime parseZonedDateTime(String text, DateTimeFormatter formatter) {
        if (formatter == null) {
            formatter = extracted27(text, formatter);

            formatter = extracted30(text, formatter);

            boolean digit = extracted19(text);
            if (extracted33(text, digit)) {
                long epochMillis = Long.parseLong(text);
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), JSON.defaultTimeZone.toZoneId());
            }
        }

        return formatter == null ? //
                ZonedDateTime.parse(text) //
                : ZonedDateTime.parse(text, formatter);
    }

    private boolean extracted33(String text, boolean digit) {
        return digit && text.length() > 8 && text.length() < 19;
    }

    private boolean extracted31(char ch) {
        return ch < '0' || ch > '9';
    }

    private DateTimeFormatter extracted30(String text, DateTimeFormatter formatter) {
        if (text.length() >= 17) {
            formatter = extracted29(text, formatter);
        }
        return formatter;
    }

    private DateTimeFormatter extracted29(String text, DateTimeFormatter formatter) {
        char c4 = text.charAt(4);
        if (c4 == '年') {
            formatter = extracted28(text);
        } else if (c4 == '년') {
            formatter = formatter_dt19_kr;
        }
        return formatter;
    }

    private DateTimeFormatter extracted28(String text) {
        DateTimeFormatter formatter;
        if (text.charAt(text.length() - 1) == '秒') {
            formatter = formatter_dt19_cn_1;
        } else {
            formatter = formatter_dt19_cn;
        }
        return formatter;
    }

    private DateTimeFormatter extracted27(String text, DateTimeFormatter formatter) {
        if (text.length() == 19) {
            formatter = extracted26(text, formatter);
        }
        return formatter;
    }

    private DateTimeFormatter extracted26(String text, DateTimeFormatter formatter) {
        char c4 = text.charAt(4);
        char c7 = text.charAt(7);
        char c10 = text.charAt(10);
        char c13 = text.charAt(13);
        char c16 = text.charAt(16);
        if (c13 == ':' && c16 == ':') {
            formatter = extracted25(text, formatter, c4, c7, c10);
        }
        return formatter;
    }

    private DateTimeFormatter extracted25(String text, DateTimeFormatter formatter, char c4, char c7, char c10) {
        if (c4 == '-' && c7 == '-') {
            formatter = extracted21(formatter, c10);
        } else if (c4 == '/' && c7 == '/') { // tw yyyy/mm/dd
            formatter = formatter_dt19_tw;
        } else {
            formatter = extracted24(text, formatter, c4);
        }
        return formatter;
    }

    private DateTimeFormatter extracted24(String text, DateTimeFormatter formatter, char c4) {
        char c0 = text.charAt(0);
        char c1 = text.charAt(1);
        char c2 = text.charAt(2);
        char c3 = text.charAt(3);
        char c5 = text.charAt(5);
        if (c2 == '/' && c5 == '/') { // mm/dd/yyyy or mm/dd/yyyy
            formatter = extracted23(formatter, c4, c0, c1, c3);
        } else if (c2 == '.' && c5 == '.') { // dd.mm.yyyy
            formatter = formatter_dt19_de;
        } else if (c2 == '-' && c5 == '-') { // dd-mm-yyyy
            formatter = formatter_dt19_in;
        }
        return formatter;
    }

    private DateTimeFormatter extracted23(DateTimeFormatter formatter, char c4, char c0, char c1, char c3) {
        int v0 = (c0 - '0') * 10 + (c1 - '0');
        int v1 = (c3 - '0') * 10 + (c4 - '0');
        if (v0 > 12) {
            formatter = formatter_dt19_eur;
        } else if (v1 > 12) {
            formatter = formatter_dt19_us;
        } else {
            formatter = extracted22(formatter);
        }
        return formatter;
    }

    private DateTimeFormatter extracted22(DateTimeFormatter formatter) {
        String country = Locale.getDefault().getCountry();

        if (country.equals("US")) {
            formatter = formatter_dt19_us;
        } else if (country.equals("BR") //
                || country.equals("AU")) {
            formatter = formatter_dt19_eur;
        }
        return formatter;
    }

    private DateTimeFormatter extracted21(DateTimeFormatter formatter, char c10) {
        if (c10 == 'T') {
            formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        } else if (c10 == ' ') {
            formatter = defaultFormatter;
        }
        return formatter;
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_STRING;
    }

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType,
                      int features) throws IOException {
        SerializeWriter out = serializer.out;
        extracted10(serializer, object, fieldType, features, out);
    }

    private void extracted10(JSONSerializer serializer, Object object, Type fieldType, int features,
            SerializeWriter out) {
        if (object == null) {
            out.writeNull();
        } else {
            fieldType = extracted(object, fieldType);

            extracted9(serializer, object, fieldType, features, out);
        }
    }

    private void extracted9(JSONSerializer serializer, Object object, Type fieldType, int features,
            SerializeWriter out) {
        if (fieldType == LocalDateTime.class) {
            final int mask = SerializerFeature.USE_ISO8601_DATE_FORMAT.getMask();
            LocalDateTime dateTime = (LocalDateTime) object;
            String format = serializer.getDateFormatPattern();

            format = extracted7(serializer, features, mask, dateTime, format);

            extracted8(out, dateTime, format);
        } else {
            out.writeString(object.toString());
        }
    }

    private void extracted8(SerializeWriter out, LocalDateTime dateTime, String format) {
        if (format != null) {
            write(out, dateTime, format);
        } else {
            out.writeLong(dateTime.atZone(JSON.defaultTimeZone.toZoneId()).toInstant().toEpochMilli());
        }
    }

    private String extracted7(JSONSerializer serializer, int features, final int mask, LocalDateTime dateTime,
            String format) {
        if (format == null) {
            format = extracted6(serializer, features, mask, dateTime);
        }
        return format;
    }

    private String extracted6(JSONSerializer serializer, int features, final int mask, LocalDateTime dateTime) {
        String format;
        if (extracted2(serializer, features, mask)) {
            format = FORMATTER_ISO8601_PATTERN;
        } else if (serializer.isEnabled(SerializerFeature.WRITE_DATE_USE_DATE_FORMAT)) {
            format = extracted4(serializer);
        } else {
            format = extracted5(dateTime);
        }
        return format;
    }

    private String extracted5(LocalDateTime dateTime) {
        String format;
        int nano = dateTime.getNano();
        if (nano == 0) {
            format = FORMATTER_ISO8601_PATTERN;
        } else if (nano % 1000000 == 0) {
            format = FORMATTER_ISO8601_PATTERN_23;
        } else {
            format = FORMATTER_ISO8601_PATTERN_29;
        }
        return format;
    }

    private String extracted4(JSONSerializer serializer) {
        String format;
        if (extracted3(serializer)){
            format = serializer.getFastJsonConfigDateFormatPattern();
        }else{
            format = JSON.DEFAULT_DATE_FORMAT; 
        }
        return format;
    }

    private boolean extracted3(JSONSerializer serializer) {
        return serializer.getFastJsonConfigDateFormatPattern() != null && 
                serializer.getFastJsonConfigDateFormatPattern().length() > 0;
    }

    private boolean extracted2(JSONSerializer serializer, int features, final int mask) {
        return (features & mask) != 0 || serializer.isEnabled(SerializerFeature.USE_ISO8601_DATE_FORMAT);
    }

    private Type extracted(Object object, Type fieldType) {
        if (fieldType == null) {
            fieldType = object.getClass();
        }
        return fieldType;
    }

    public void write(JSONSerializer serializer, Object object, BeanContext context) throws IOException {
        SerializeWriter out = serializer.out;
        String format = context.getFormat();
        write(out, (TemporalAccessor) object, format);
    }

    private void write(SerializeWriter out, TemporalAccessor object, String format) {
        DateTimeFormatter formatter;
        if ("unixtime".equals(format)) {
            if (object instanceof ChronoZonedDateTime) {
                long seconds = ((ChronoZonedDateTime) object).toEpochSecond();
                out.writeInt((int) seconds);
                return;
            }

            if (object instanceof LocalDateTime) {
                long seconds = ((LocalDateTime) object).atZone(JSON.defaultTimeZone.toZoneId()).toEpochSecond();
                out.writeInt((int) seconds);
                return;
            }
        }

        if ("millis".equals(format)) {
            Instant instant = null;
            if (object instanceof ChronoZonedDateTime) {
                instant = ((ChronoZonedDateTime) object).toInstant();
            } else if (object instanceof LocalDateTime) {
                instant = ((LocalDateTime) object).atZone(JSON.defaultTimeZone.toZoneId()).toInstant();
            }
            if (instant != null) {
                long millis = instant.toEpochMilli();
                out.writeLong(millis);
                return;
            }
        }

        if (format.equals(FORMATTER_ISO8601_PATTERN)) {
            formatter = formatter_iso8601;
        } else {
            formatter = DateTimeFormatter.ofPattern(format);
        }

        String text = formatter.format(object);
        out.writeString(text);
    }

    public static Object castToLocalDateTime(Object value, String format) {
        if (value == null) {
            return null;
        }

        if (format == null) {
            format = DEFAULT_PATTERN;
        }

        DateTimeFormatter df = DateTimeFormatter.ofPattern(format);
        return LocalDateTime.parse(value.toString(), df);
    }
}
