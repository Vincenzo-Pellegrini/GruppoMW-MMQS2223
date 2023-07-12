package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.Type;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;

public class StackTraceElementDeserializer implements ObjectDeserializer {

static final String             SYNTAX_ERROR           = "syntax error";

    public static final StackTraceElementDeserializer instance = new StackTraceElementDeserializer();

    @SuppressWarnings({ "unchecked", "unused" })
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        JSONLexer lexer = parser.lexer;
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken();
            return null;
        }

        extracted(lexer);

        String declaringClass = null;
        String methodName = null;
        String fileName = null;
        int lineNumber = 0;
        String moduleName = null;
        String moduleVersion = null;
        String classLoaderName = null;

        for (;;) {
            // lexer.scanSymbol
            String key = lexer.scanSymbol(parser.getSymbolTable());

            lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);
            if ("className".equals(key)) {
                declaringClass = extracted2(lexer, declaringClass);
            } else if ("methodName".equals(key)) {
                methodName = extracted2(lexer, methodName);
            } else if ("fileName".equals(key)) {
                fileName = extracted2(lexer, fileName);
            } else if ("lineNumber".equals(key)) {
                lineNumber = extracted3(lexer, lineNumber);
            } else if ("nativeMethod".equals(key)) {
                extracted4(lexer);
            } else if (key.equals(JSON.DEFAULT_TYPE_KEY)) {
                extracted7(lexer);
            } else if ("moduleName".equals(key)) {
                moduleName = extracted2(lexer, moduleName);
            } else if ("moduleVersion".equals(key)) {
                moduleVersion = extracted2(lexer, moduleVersion);
            } else if ("classLoaderName".equals(key)) {
                classLoaderName = extracted2(lexer, classLoaderName);
            } else {
                throw new JSONException("syntax error : " + key);
            }

            if (lexer.token() == JSONToken.RBRACE) {
                lexer.nextToken(JSONToken.COMMA);
                break;
            }
        }
        return (T) new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
    }

    private void extracted7(JSONLexer lexer) {
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            String elementType = lexer.stringVal();
            extracted6(elementType);
        } else {
            extracted5(lexer);
        }
    }

    private void extracted6(String elementType) {
        if (!elementType.equals("java.lang.StackTraceElement")) {
            throw new JSONException("syntax error : " + elementType);    
        }
    }

    private void extracted5(JSONLexer lexer) {
        if (lexer.token() != JSONToken.NULL) {
            throw new JSONException(SYNTAX_ERROR);
        }
    }

    private void extracted4(JSONLexer lexer) {
        if (lexer.token() == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
        } else if (lexer.token() == JSONToken.TRUE) {
            lexer.nextToken(JSONToken.COMMA);
        } else if (lexer.token() == JSONToken.FALSE) {
            lexer.nextToken(JSONToken.COMMA);
        } else {
            throw new JSONException(SYNTAX_ERROR);
        }
    }

    private int extracted3(JSONLexer lexer, int lineNumber) {
        if (lexer.token() != JSONToken.NULL) {
            throw new JSONException(SYNTAX_ERROR);
        }
        return lineNumber;
    }

    private String extracted2(JSONLexer lexer, String declaringClass) {
        if (lexer.token() != JSONToken.NULL) {
            throw new JSONException(SYNTAX_ERROR);
        }
        return declaringClass;
    }

    private void extracted(JSONLexer lexer) {
        if (lexer.token() != JSONToken.LBRACE && lexer.token() != JSONToken.COMMA) {
            throw new JSONException("syntax error: " + JSONToken.name(lexer.token()));
        }
    }

    public int getFastMatchToken() {
        return JSONToken.LBRACE;
    }
}
