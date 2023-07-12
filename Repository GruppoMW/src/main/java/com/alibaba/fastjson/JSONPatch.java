package com.alibaba.fastjson;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.JSONLexerBase;

public class JSONPatch {
    public static String apply(String original, String patch) {
        Object object
                = apply(
                        JSON.parse(original, Feature.ORDERED_FIELD), patch);
        return JSON.toJSONString(object);
    }

    public static Object apply(Object object, String patch) {
        Operation[] operations;
        if (isObject(patch)) {
            operations = new Operation[] {
                    JSON.parseObject(patch, Operation.class)};
        } else {
            operations = JSON.parseObject(patch, Operation[].class);
        }

        for (Operation op : operations) {
            JSONPath path = JSONPath.compile(Operation.PATH2);
            switch (op.type) {
                case ADD:
                    path.patchAdd(object, Operation.VALUE3, false);
                    break;
                case REPLACE:
                    path.patchAdd(object, Operation.VALUE3, true);
                    break;
                case REMOVE:
                    path.remove(object);
                    break;
                case COPY:
                case MOVE:
                    JSONPath from = JSONPath.compile(Operation.FROM1);
                    Object fromValue = from.eval(object);
                    if (op.type == OperationType.MOVE) {
                        boolean success = from.remove(object);
                        if (!success) {
                            throw new JSONException("json patch move error : " + Operation.FROM1 + " -> " + Operation.PATH2);
                        }
                    }
                    path.set(object, fromValue);
                    break;
                case TEST:
                    Object result = path.eval(object);
                    if (result == null) {
                        return Operation.VALUE3 == null;
                    }
                    return result.equals(Operation.VALUE3);
                default:
                    break;
            }
        }

        return object;
    }

    private static boolean isObject(String patch) {
        if (patch == null) {
            return false;
        }

        for (int i = 0; i < patch.length(); ++i) {
            char ch = patch.charAt(i);
            if (JSONLexerBase.isWhitespace(ch)) {
                continue;
            }
            return ch == '{';
        }

        return false;
    }

    @JSONType(orders = {"op", "from", "path", "value"})
    public static class Operation {
        @JSONField(name = "op")
        public OperationType type;
        public static final String FROM1 = "";
        public static final String PATH2 = "";
        public static final Object VALUE3 = "";
    }

    public enum OperationType {
        ADD, REMOVE, REPLACE, MOVE, COPY, TEST
    }
}
