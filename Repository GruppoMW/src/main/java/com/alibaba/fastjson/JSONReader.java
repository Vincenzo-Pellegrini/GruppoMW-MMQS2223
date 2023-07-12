package com.alibaba.fastjson;

import static com.alibaba.fastjson.JSONStreamContext.ARRAY_VALUE;
import static com.alibaba.fastjson.JSONStreamContext.PROPERTY_KEY;
import static com.alibaba.fastjson.JSONStreamContext.PROPERTY_VALUE;
import static com.alibaba.fastjson.JSONStreamContext.START_ARRAY;
import static com.alibaba.fastjson.JSONStreamContext.START_OBJECT;

import java.io.Closeable;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONReaderScanner;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.util.TypeUtils;

public class JSONReader implements Closeable {

    private static final String ILLEGAL_STATE = "illegal state : ";
    private final DefaultJSONParser parser;
    private JSONStreamContext       context;
    private JSONStreamContext lastContext;

    public JSONReader(Reader reader){
        this(reader, new Feature[0]);
    }
    
    public JSONReader(Reader reader, Feature... features){
        this(new JSONReaderScanner(reader));
        for (Feature feature : features) {
            this.config(feature, true);
        }
    }

    public JSONReader(JSONLexer lexer){
        this(new DefaultJSONParser(lexer));
    }

    public JSONReader(DefaultJSONParser parser){
        this.parser = parser;
    }
    
    public void setTimzeZone(TimeZone timezone) {
        this.parser.lexer.setTimeZone(timezone);
    }
    
    public void setLocale(Locale locale) {
        this.parser.lexer.setLocale(locale);
    }

    public void config(Feature feature, boolean state) {
        this.parser.config(feature, state);
    }
    
    public Locale getLocal() {
        return this.parser.lexer.getLocale();
    }
    
    public TimeZone getTimzeZone() {
        return this.parser.lexer.getTimeZone();
    }

    public void startObject() {
        if (context == null) {
            context = new JSONStreamContext(null, JSONStreamContext.START_OBJECT);
        } else {
            startStructure();
            if (lastContext != null
                    && lastContext.parent == context) {
                context = lastContext;
                if (context.state != JSONStreamContext.START_OBJECT) {
                    context.state = JSONStreamContext.START_OBJECT;
                }
            } else {
                context = new JSONStreamContext(context, JSONStreamContext.START_OBJECT);
            }
        }

        this.parser.accept(JSONToken.LBRACE, JSONToken.IDENTIFIER);
    }

    public void endObject() {
        this.parser.accept(JSONToken.RBRACE);
        endStructure();
    }

    public void startArray() {
        if (context == null) {
            context = new JSONStreamContext(null, START_ARRAY);
        } else {
            startStructure();

            context = new JSONStreamContext(context, START_ARRAY);
        }
        this.parser.accept(JSONToken.LBRACKET);
    }

    public void endArray() {
        this.parser.accept(JSONToken.RBRACKET);
        endStructure();
    }

    private void startStructure() {
        final int state = context.state;
        switch (state) {
            case PROPERTY_KEY:
                parser.accept(JSONToken.COLON);
                break;
            case PROPERTY_VALUE:
            case ARRAY_VALUE:
                parser.accept(JSONToken.COMMA);
                break;
            case START_ARRAY:
            case START_OBJECT:
                break;
            default:
                throw new JSONException(ILLEGAL_STATE + context.state);
        }
    }

    private void endStructure() {
        lastContext = context;
        context = context.parent;

        if (context == null) {
            return;
        }
        
        final int state = context.state;
        int newState = -1;
        switch (state) {
            case PROPERTY_KEY:
                newState = PROPERTY_VALUE;
                break;
            case START_ARRAY:
                newState = ARRAY_VALUE;
                break;
            case PROPERTY_VALUE:
            case START_OBJECT:
                newState = PROPERTY_KEY;
                break;
            default:
                break;
        }
        if (newState != -1) {
            context.state = newState;
        }
    }

    public boolean hasNext() {
        if (context == null) {
            throw new JSONException("context is null");
        }

        final int token = parser.lexer.token();
        final int state = context.state;
        switch (state) {
            case START_ARRAY:
            case ARRAY_VALUE:
                return token != JSONToken.RBRACKET;
            case START_OBJECT:
            case PROPERTY_VALUE:
                return token != JSONToken.RBRACE;
            default:
                throw new JSONException(ILLEGAL_STATE + state);
        }
    }

    public int peek() {
        return parser.lexer.token();
    }

    public void close() {
        parser.close();
    }

    public Integer readInteger() {
        Object object;
        if (context == null) {
            object = parser.parse();
        } else {
            readBefore();
            object = parser.parse();
            readAfter();
        }

        return TypeUtils.castToInt(object);
    }

    public Long readLong() {
        Object object;
        if (context == null) {
            object = parser.parse();
        } else {
            readBefore();
            object = parser.parse();
            readAfter();
        }

        return TypeUtils.castToLong(object);
    }

    public String readString() {
        Object object;
        if (context == null) {
            object = parser.parse();
        } else {
            readBefore();
            JSONLexer lexer = parser.lexer;
            if (context.state == JSONStreamContext.START_OBJECT && lexer.token() == JSONToken.IDENTIFIER) {
                object = lexer.stringVal();
                lexer.nextToken();
            } else {
                object = parser.parse();
            }
            readAfter();
        }

        return TypeUtils.castToString(object);
    }
    
    public <T> T readObject(TypeReference<T> typeRef) {
        return readObject(typeRef.getType());
    }

    public <T> T readObject(Type type) {
        if (context == null) {
            return parser.parseObject(type);
        }

        readBefore();
        T object = parser.parseObject(type);
        readAfter();
        return object;
    }

    public <T> T readObject(Class<T> type) {
        if (context == null) {
            return parser.parseObject(type);
        }

        readBefore();
        T object = parser.parseObject(type);
        parser.handleResovleTask(object);
        readAfter();
        return object;
    }

    public void readObject(Object object) {
        if (context == null) {
            parser.parseObject(object);
            return;
        }

        readBefore();
        parser.parseObject(object);
        readAfter();
    }

    public Object readObject() {
        if (context == null) {
            return parser.parse();
        }

        readBefore();
        Object object;
        switch (context.state) {
            case START_OBJECT:
            case PROPERTY_VALUE:
                object = parser.parseKey();
                break;
            default:
                object = parser.parse();
                break;
        }

        readAfter();
        return object;
    }

    @SuppressWarnings("rawtypes")
    public Object readObject(Map object) {
        if (context == null) {
            return parser.parseObject(object);
        }

        readBefore();
        Object value = parser.parseObject(object);
        readAfter();
        return value;
    }

    private void readBefore() {
        int state = context.state;
        // before
        switch (state) {
            case PROPERTY_KEY:
                parser.accept(JSONToken.COLON);
                break;
            case PROPERTY_VALUE:
                parser.accept(JSONToken.COMMA, JSONToken.IDENTIFIER);
                break;
            case ARRAY_VALUE:
                parser.accept(JSONToken.COMMA);
                break;
            case START_OBJECT:
                break;
            case START_ARRAY:
                break;
            default:
                throw new JSONException(ILLEGAL_STATE + state);
        }
    }

    private void readAfter() {
        int state = context.state;
        int newStat = -1;
        switch (state) {
            case START_OBJECT:
                newStat = PROPERTY_KEY;
                break;
            case PROPERTY_KEY:
                newStat = PROPERTY_VALUE;
                break;
            case PROPERTY_VALUE:
                newStat = PROPERTY_KEY;
                break;
            case ARRAY_VALUE:
                break;
            case START_ARRAY:
                newStat = ARRAY_VALUE;
                break;
            default:
                throw new JSONException(ILLEGAL_STATE + state);
        }
        if (newStat != -1) {
            context.state = newStat;
        }
    }

}
