package com.alibaba.fastjson;

/**
 * @since 1.2.15
 */
public enum PropertyNamingStrategy {
                                    CAMEL_CASE, // camelCase
                                    PASCAL_CASE, // PascalCase
                                    SNAKE_CASE, // snake_case
                                    KEBAB_CASE, // kebab-case
                                    NO_CHANGE,  //
                                    NEVER_USE_THIS_VALUE_EXCEPT_DEFAULT_VALUE;

    public String translate(String propertyName) {
        switch (this) {
            case SNAKE_CASE: {
                StringBuilder buf = new StringBuilder();
                extracted2(propertyName, buf);
                return buf.toString();
            }
            case KEBAB_CASE: {
                StringBuilder buf = new StringBuilder();
                extracted4(propertyName, buf);
                return buf.toString();
            }
            case PASCAL_CASE: {
                char ch = propertyName.charAt(0);
                if (ch >= 'a' && ch <= 'z') {
                    char[] chars = propertyName.toCharArray();
                    chars[0] -= 32;
                    return new String(chars);
                }

                return propertyName;
            }
            case CAMEL_CASE: {
                char ch = propertyName.charAt(0);
                if (ch >= 'A' && ch <= 'Z') {
                    char[] chars = propertyName.toCharArray();
                    chars[0] += 32;
                    return new String(chars);
                }

                return propertyName;
            }
            case NO_CHANGE:
            case NEVER_USE_THIS_VALUE_EXCEPT_DEFAULT_VALUE:
            default:
                return propertyName;
        }
    }

    private void extracted4(String propertyName, StringBuilder buf) {
        for (int i = 0; i < propertyName.length(); ++i) {
            char ch = propertyName.charAt(i);
            extracted3(buf, i, ch);
        }
    }

    private void extracted3(StringBuilder buf, int i, char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            char chUcase = (char) (ch + 32);
            extracted(buf, i);
            buf.append(chUcase);
        } else {
            buf.append(ch);
        }
    }

    private void extracted2(String propertyName, StringBuilder buf) {
        for (int i = 0; i < propertyName.length(); ++i) {
            char ch = propertyName.charAt(i);
            extracted1(buf, i, ch);
        }
    }

    private void extracted1(StringBuilder buf, int i, char ch) {
        extracted3(buf, i, ch);
    }

    private void extracted(StringBuilder buf, int i) {
        if (i > 0) {
            buf.append('_');
        }
    }
}
