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

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public enum SerializerFeature {
    QUOTE_FIELD_NAMES,
    /**
     * 
     */
    USE_SINGLE_QUOTES,
    /**
     * 
     */
    WRITE_MAP_NULL_VALUE,
    /**
     * 用枚举toString()值输出
     */
    WRITE_ENUM_USING_TO_STRING,
    /**
     * 用枚举name()输出
     */
    WRITE_ENUM_USING_NAME,
    /**
     * 
     */
    USE_ISO8601_DATE_FORMAT,
    /**
     * @since 1.1
     */
    WRITE_NULL_LIST_AS_EMPTY,
    /**
     * @since 1.1
     */
    WRITE_NULL_STRING_AS_EMPTY,
    /**
     * @since 1.1
     */
    WRITE_NULL_NUMBER_AS_ZERO,
    /**
     * @since 1.1
     */
    WRITE_NULL_BOOLEAN_AS_FALSE,
    /**
     * @since 1.1
     */
    SKIP_TRANSIENT_FIELD,
    /**
     * @since 1.1
     */
    SORT_FIELD,
    /**
     * @since 1.1.1
     */
    @Deprecated
    WRITE_TAB_AS_SPECIAL,
    /**
     * @since 1.1.2
     */
    PRETTY_FORMAT,
    /**
     * @since 1.1.2
     */
    WRITE_CLASS_NAME,

    /**
     * @since 1.1.6
     */
    DISABLE_CIRCULAR_REFERENCE_DETECT, // 32768

    /**
     * @since 1.1.9
     */
    WRITE_SLASH_AS_SPECIAL,

    /**
     * @since 1.1.10
     */
    BROWSER_COMPATIBLE,

    /**
     * @since 1.1.14
     */
    WRITE_DATE_USE_DATE_FORMAT,

    /**
     * @since 1.1.15
     */
    NOT_WRITE_ROOT_CLASS_NAME,

    /**
     * @since 1.1.19
     * @deprecated
     */
    DISABLE_CHECK_SPECIAL_CHAR,

    /**
     * @since 1.1.35
     */
    BEAN_TO_ARRAY,

    /**
     * @since 1.1.37
     */
    WRITE_NON_STRING_KEY_AS_STRING,
    
    /**
     * @since 1.1.42
     */
    NOT_WRITE_DEFAULT_VALUE,
    
    /**
     * @since 1.2.6
     */
    BROWSER_SECURE,
    
    /**
     * @since 1.2.7
     */
    IGNORE_NON_FIELD_GETTER,
    
    /**
     * @since 1.2.9
     */
    WRITE_NON_STRING_VALUE_AS_STRING,
    
    /**
     * @since 1.2.11
     */
    IGNORE_ERROR_GETTER,

    /**
     * @since 1.2.16
     */
    WRITE_BIG_DECIMAL_AS_PLAIN,

    /**
     * @since 1.2.27
     */
    MAP_SORT_FIELD;

    SerializerFeature(){
        mask = (1 << ordinal());
    }

    public final int mask;

    public final int getMask() {
        return mask;
    }

    public static boolean isEnabled(int features, SerializerFeature feature) {
        return (features & feature.mask) != 0;
    }
    
    public static boolean isEnabled(int features, int featuresB, SerializerFeature feature) {
        int mask = feature.mask;
        
        return (features & mask) != 0 || (featuresB & mask) != 0;
    }

    public static int config(int features, SerializerFeature feature, boolean state) {
        if (state) {
            features |= feature.mask;
        } else {
            features &= ~feature.mask;
        }

        return features;
    }
    
    public static int of(SerializerFeature[] features) {
        if (features == null) {
            return 0;
        }
        
        int value = 0;
        
        for (SerializerFeature feature: features) {
            value |= feature.mask;
        }
        
        return value;
    }
    
    public static final SerializerFeature[] EMPTY = new SerializerFeature[0];

    public static final int WRITE_MAP_NULL_FEATURES
            = WRITE_MAP_NULL_VALUE.getMask()
            | WRITE_NULL_BOOLEAN_AS_FALSE.getMask()
            | WRITE_NULL_LIST_AS_EMPTY.getMask()
            | WRITE_NULL_NUMBER_AS_ZERO.getMask()
            | WRITE_NULL_STRING_AS_EMPTY.getMask()
            ;
}
