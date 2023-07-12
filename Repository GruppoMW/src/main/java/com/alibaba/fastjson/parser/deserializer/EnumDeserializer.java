package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.util.TypeUtils;

import static com.alibaba.fastjson.util.TypeUtils.FNV1A_64_MAGIC_HASHCODE;
import static com.alibaba.fastjson.util.TypeUtils.FNV1A_64_MAGIC_PRIME;


@SuppressWarnings("rawtypes")
public class EnumDeserializer implements ObjectDeserializer {

    protected final Class<?> enumClass;
    protected final Enum[]   enums;
    protected final Enum[]   ordinalEnums;
    protected       long[]   enumNameHashCodes;

    public EnumDeserializer(Class<?> enumClass){
        this.enumClass = enumClass;

        ordinalEnums = (Enum[]) enumClass.getEnumConstants();

        Map<Long, Enum> enumMap = new HashMap<>();
        for (int i = 0; i < ordinalEnums.length; ++i) {
            Enum e = ordinalEnums[i];
            String name = e.name();

            JSONField jsonField = null;
            try {
                Field field = enumClass.getField(name);
                jsonField = TypeUtils.getAnnotation(field, JSONField.class);
                name = extracted13(name, jsonField);
            } catch (Exception ex) {
                // skip
            }

            long hash = FNV1A_64_MAGIC_HASHCODE;
            long hashLower = FNV1A_64_MAGIC_HASHCODE;
            for (int j = 0; j < name.length(); ++j) {
                char ch = name.charAt(j);

                hash ^= ch;
                hashLower ^= ((ch >= 'A' && ch <= 'Z') ? (ch + 32) : ch);

                hash *= FNV1A_64_MAGIC_PRIME;
                hashLower *= FNV1A_64_MAGIC_PRIME;
            }

            enumMap.put(hash, e);
            extracted11(enumMap, e, hash, hashLower);

            extracted10(enumMap, e, jsonField, hash, hashLower);
        }

        extracted5(enumMap);

        this.enums = new Enum[enumNameHashCodes.length];
        extracted4(enumMap);
    }

    private String extracted13(String name, JSONField jsonField) {
        if (jsonField != null) {
            String jsonFieldName = jsonField.name();
            name = extracted12(name, jsonFieldName);
        }
        return name;
    }

    private String extracted12(String name, String jsonFieldName) {
        if (jsonFieldName != null && jsonFieldName.length() > 0) {
            name = jsonFieldName;
        }
        return name;
    }

    private void extracted11(Map<Long, Enum> enumMap, Enum e, long hash, long hashLower) {
        if (hash != hashLower) {
            enumMap.put(hashLower, e);
        }
    }

    private void extracted10(Map<Long, Enum> enumMap, Enum e, JSONField jsonField, long hash, long hashLower) {
        if (jsonField != null) {
            extracted9(enumMap, e, jsonField, hash, hashLower);
        }
    }

    private void extracted9(Map<Long, Enum> enumMap, Enum e, JSONField jsonField, long hash, long hashLower) {
        for (String alterName : jsonField.alternateNames()) {
            long alterNameHash = FNV1A_64_MAGIC_HASHCODE;
            alterNameHash = extracted6(alterName, alterNameHash);
            extracted8(enumMap, e, hash, hashLower, alterNameHash);
        }
    }

    private void extracted8(Map<Long, Enum> enumMap, Enum e, long hash, long hashLower, long alterNameHash) {
        if (extracted7(hash, hashLower, alterNameHash)) {
            enumMap.put(alterNameHash, e);
        }
    }

    private boolean extracted7(long hash, long hashLower, long alterNameHash) {
        return alterNameHash != hash && alterNameHash != hashLower;
    }

    private long extracted6(String alterName, long alterNameHash) {
        for (int j = 0; j < alterName.length(); ++j) {
            char ch = alterName.charAt(j);
            alterNameHash ^= ch;
            alterNameHash *= FNV1A_64_MAGIC_PRIME;
        }
        return alterNameHash;
    }

    private void extracted5(Map<Long, Enum> enumMap) {
        this.enumNameHashCodes = new long[enumMap.size()];
        
        extracted14(enumMap);
        
    }

    private void extracted14(Map<Long, Enum> enumMap) {
        int i = 0;
        for (Long h : enumMap.keySet()) {
            enumNameHashCodes[i++] = h;
        }
        Arrays.sort(this.enumNameHashCodes);
    }

    private void extracted4(Map<Long, Enum> enumMap) {
        for (int i = 0; i < this.enumNameHashCodes.length; ++i) {
            long hash = enumNameHashCodes[i];
            Enum e = enumMap.get(hash);
            this.enums[i] = e;
        }
    }

    public Enum getEnumByHashCode(long hashCode) {
        if (enums == null) {
            return null;
        }

        int enumIndex = Arrays.binarySearch(this.enumNameHashCodes, hashCode);

        if (enumIndex < 0) {
            return null;
        }

        return enums[enumIndex];
    }

    public Enum<?> valueOf(int ordinal) {
        return ordinalEnums[ordinal];
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        try {
            Object value;
            final JSONLexer lexer = parser.lexer;
            final int token = lexer.token();
            if (token == JSONToken.LITERAL_INT) {
                int intValue = lexer.intValue();
                lexer.nextToken(JSONToken.COMMA);

                extracted(intValue);

                return (T) ordinalEnums[intValue];
            } else if (token == JSONToken.LITERAL_STRING) {
                String name = lexer.stringVal();
                lexer.nextToken(JSONToken.COMMA);

                if (name.length() == 0) {
                    return null;
                }

                long hash = FNV1A_64_MAGIC_HASHCODE;
                long hashLower = FNV1A_64_MAGIC_HASHCODE;
                for (int j = 0; j < name.length(); ++j) {
                    char ch = name.charAt(j);

                    hash ^= ch;
                    hashLower ^= ((ch >= 'A' && ch <= 'Z') ? (ch + 32) : ch);

                    hash *= FNV1A_64_MAGIC_PRIME;
                    hashLower *= FNV1A_64_MAGIC_PRIME;
                }

                Enum e = getEnumByHashCode(hash);
                e = extracted2(hash, hashLower, e);

                extracted3(lexer, name, e);
                return (T) e;
            } else if (token == JSONToken.NULL) {

                lexer.nextToken(JSONToken.COMMA);

                return null;
            } else {
                value = parser.parse();
            }

            throw new JSONException("parse enum " + enumClass.getName() + " error, value : " + value);
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            throw new JSONException(e.getMessage(), e);
        }
    }

    private void extracted3(final JSONLexer lexer, String name, Enum e) {
        if (e == null && lexer.isEnabled(Feature.ERROR_ON_ENUM_NOT_MATCH)) {
            throw new JSONException("not match enum value, " + enumClass.getName() + " : " + name);
        }
    }

    private Enum extracted2(long hash, long hashLower, Enum e) {
        if (e == null && hashLower != hash) {
            e = getEnumByHashCode(hashLower);
        }
        return e;
    }

    private void extracted(int intValue) {
        if (intValue < 0 || intValue >= ordinalEnums.length) {
            throw new JSONException("parse enum " + enumClass.getName() + " error, value : " + intValue);
        }
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_INT;
    }
}
