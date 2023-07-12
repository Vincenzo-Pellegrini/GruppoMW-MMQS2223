package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.Type;
import java.math.BigDecimal;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONScanner;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.util.TypeUtils;

public class TimeDeserializer implements ObjectDeserializer {

    static final String             SYNTAX_ERROR           = "syntax error";

    public static final TimeDeserializer instance = new TimeDeserializer();

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        JSONLexer lexer = parser.lexer;
        
        if (lexer.token() == JSONToken.COMMA) {
            lexer.nextToken(JSONToken.LITERAL_STRING);
            
            extracted(lexer);
            
            lexer.nextTokenWithColon(JSONToken.LITERAL_INT);
            
            extracted2(lexer);
            
            long time = lexer.longValue();
            lexer.nextToken(JSONToken.RBRACE);
            extracted3(lexer);
            lexer.nextToken(JSONToken.COMMA);
            
            return (T) new java.sql.Time(time);
        }
        
        Object val = parser.parse();

        if (val == null) {
            return null;
        }

        if (val instanceof java.sql.Time) {
            return (T) val;
        } else if (val instanceof BigDecimal) {
            return (T) new java.sql.Time(TypeUtils.longValue((BigDecimal) val));
        } else if (val instanceof Number) {
            return (T) new java.sql.Time(((Number) val).longValue());
        } else if (val instanceof String) {
            String strVal = (String) val;
            if (strVal.length() == 0) {
                return null;
            }
            
            long longVal;
            JSONScanner dateLexer = new JSONScanner(strVal);
            if (dateLexer.scanISO8601DateIfMatch()) {
                longVal = dateLexer.getCalendar().getTimeInMillis();
            } else {
                boolean isDigit = true;
                isDigit = extracted4(strVal, isDigit);
                if (!isDigit) {
                    dateLexer.close();
                    return (T) java.sql.Time.valueOf(strVal);    
                }
                
                longVal = Long.parseLong(strVal);
            }
            dateLexer.close();
            return (T) new java.sql.Time(longVal);
        }
        
        throw new JSONException("parse error");
    }

    private boolean extracted4(String strVal, boolean isDigit) {
        for (int i = 0; i< strVal.length(); ++i) {
            char ch = strVal.charAt(i);
            if (ch < '0' || ch > '9') {
                isDigit = false;
                break;
            }
        }
        return isDigit;
    }

    private void extracted3(JSONLexer lexer) {
        if (lexer.token() != JSONToken.RBRACE) {
            throw new JSONException(SYNTAX_ERROR);
        }
    }

    private void extracted2(JSONLexer lexer) {
        if (lexer.token() != JSONToken.LITERAL_INT) {
            throw new JSONException(SYNTAX_ERROR);
        }
    }

    private void extracted(JSONLexer lexer) {
        if (lexer.token() != JSONToken.LITERAL_STRING) {
            throw new JSONException(SYNTAX_ERROR);
        }
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_INT;
    }
}
