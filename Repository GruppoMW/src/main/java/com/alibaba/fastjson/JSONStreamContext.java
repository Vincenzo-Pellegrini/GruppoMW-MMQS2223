package com.alibaba.fastjson;

class JSONStreamContext {

    static final int                  START_OBJECT   = 1001;
    static final int                  PROPERTY_KEY   = 1002;
    static final int                  PROPERTY_VALUE = 1003;
    static final int                  START_ARRAY    = 1004;
    static final int                  ARRAY_VALUE    = 1005;

    protected final JSONStreamContext parent;

    protected int                     state;

    public JSONStreamContext(JSONStreamContext parent, int state){
        this.parent = parent;
        this.state = state;
    }
}
