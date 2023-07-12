/*
 * Copyright 1999-2017 Alibaba Group.
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
package com.alibaba.fastjson.parser;


/**
 * @author wenshao[szujobs@hotmail.com]
 */
public enum Feature {
    /**
	 * 
	 */
    AUTO_CLOSE_SOURCE,
    /**
	 * 
	 */
    ALLOW_COMMENT,
    /**
	 * 
	 */
    ALLOW_UN_QUOTED_FIELD_NAMES,
    /**
	 * 
	 */
    ALLOW_SINGLE_QUOTES,
    /**
	 * 
	 */
    INTERN_FIELD_NAMES,
    /**
	 * 
	 */
    ALLOW_ISO8601_DATE_FORMAT,

    /**
     * {"a":1,,,"b":2}
     */
    ALLOW_ARBITRARY_COMMAS,

    /**
     * 
     */
    USE_BIG_DECIMAL,
    
    /**
     * @since 1.1.2
     */
    IGNORE_NOT_MATCH,

    /**
     * @since 1.1.3
     */
    SORT_FEID_FAST_MATCH,
    
    /**
     * @since 1.1.3
     */
    DISABLE_ASM,
    
    /**
     * @since 1.1.7
     */
    DISABLE_CIRCULAR_REFERENCE_DETECT,
    
    /**
     * @since 1.1.10
     */
    INIT_STRING_FIELD_AS_EMPTY,
    
    /**
     * @since 1.1.35
     * 
     */
    SUPPORT_ARRAY_TO_BEAN,
    
    /**
     * @since 1.2.3
     * 
     */
    ORDERED_FIELD,
    
    /**
     * @since 1.2.5
     * 
     */
    DISABLE_SPECIAL_KEY_DETECT,
    
    /**
     * @since 1.2.9
     */
    USE_OBJECT_ARRAY,

    /**
     * @since 1.2.22, 1.1.54.android
     */
    SUPPORT_NON_PUBLIC_FIELD,

    /**
     * @since 1.2.29
     *
     * disable autotype key '@type'
     */
    IGNORE_AUTO_TYPE,

    /**
     * @since 1.2.30
     *
     * disable field smart match, improve performance in some scenarios.
     */
    DISABLE_FIELD_SMART_MATCH,

    /**
     * @since 1.2.41, backport to 1.1.66.android
     */
    SUPPORT_AUTO_TYPE,

    /**
     * @since 1.2.42
     */
    NON_STRING_KEY_AS_STRING,

    /**
     * @since 1.2.45
     */
    CUSTOM_MAP_DESERIALIZER,

    /**
     * @since 1.2.55
     */
    ERROR_ON_ENUM_NOT_MATCH,

    /**
     * @since 1.2.68
     */
    SAFE_MODE,

    /**
     * @since 1.2.72
     */
    TRIM_STRING_FIELD_VALUE,

    /**
     * @since 1.2.77
     * use HashMap instead of JSONObject, ArrayList instead of JSONArray
     */
    USE_NATIVE_JAVA_OBJECT
    ;

    Feature(){
        mask = (1 << ordinal());
    }

    public final int mask;

    public final int getMask() {
        return mask;
    }

    public static boolean isEnabled(int features, Feature feature) {
        return (features & feature.mask) != 0;
    }

    public static int config(int features, Feature feature, boolean state) {
        if (state) {
            features |= feature.mask;
        } else {
            features &= ~feature.mask;
        }

        return features;
    }
    
    public static int of(Feature[] features) {
        if (features == null) {
            return 0;
        }
        
        int value = 0;
        
        for (Feature feature: features) {
            value |= feature.mask;
        }
        
        return value;
    }
}
