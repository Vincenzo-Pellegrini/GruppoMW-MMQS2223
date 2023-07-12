package com.alibaba.fastjson.serializer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSON;

public abstract class SerializeFilterable {

    protected List<BeforeFilter>       beforeFilters       = null;
    protected List<AfterFilter>        afterFilters        = null;
    protected List<PropertyFilter>     propertyFilters     = null;
    protected List<ValueFilter>        valueFilters        = null;
    protected List<NameFilter>         nameFilters         = null;
    protected List<PropertyPreFilter>  propertyPreFilters  = null;
    protected List<LabelFilter>        labelFilters        = null;
    protected List<ContextValueFilter> contextValueFilters = null;

    protected boolean                  writeDirect         = true;

    public List<BeforeFilter> getBeforeFilters() {
        if (beforeFilters == null) {
            beforeFilters = new ArrayList<>();
            writeDirect = false;
        }

        return beforeFilters;
    }

    public List<AfterFilter> getAfterFilters() {
        if (afterFilters == null) {
            afterFilters = new ArrayList<>();
            writeDirect = false;
        }

        return afterFilters;
    }

    public List<NameFilter> getNameFilters() {
        if (nameFilters == null) {
            nameFilters = new ArrayList<>();
            writeDirect = false;
        }

        return nameFilters;
    }

    public List<PropertyPreFilter> getPropertyPreFilters() {
        if (propertyPreFilters == null) {
            propertyPreFilters = new ArrayList<>();
            writeDirect = false;
        }

        return propertyPreFilters;
    }

    public List<LabelFilter> getLabelFilters() {
        if (labelFilters == null) {
            labelFilters = new ArrayList<>();
            writeDirect = false;
        }

        return labelFilters;
    }

    public List<PropertyFilter> getPropertyFilters() {
        if (propertyFilters == null) {
            propertyFilters = new ArrayList<>();
            writeDirect = false;
        }

        return propertyFilters;
    }

    public List<ContextValueFilter> getContextValueFilters() {
        if (contextValueFilters == null) {
            contextValueFilters = new ArrayList<>();
            writeDirect = false;
        }

        return contextValueFilters;
    }

    public List<ValueFilter> getValueFilters() {
        if (valueFilters == null) {
            valueFilters = new ArrayList<>();
            writeDirect = false;
        }

        return valueFilters;
    }

    public void addFilter(SerializeFilter filter) {
        if (filter == null) {
            return;
        }

        if (filter instanceof PropertyPreFilter) {
            this.getPropertyPreFilters().add((PropertyPreFilter) filter);
        }

        if (filter instanceof NameFilter) {
            this.getNameFilters().add((NameFilter) filter);
        }

        if (filter instanceof ValueFilter) {
            this.getValueFilters().add((ValueFilter) filter);
        }

        if (filter instanceof ContextValueFilter) {
            this.getContextValueFilters().add((ContextValueFilter) filter);
        }

        if (filter instanceof PropertyFilter) {
            this.getPropertyFilters().add((PropertyFilter) filter);
        }

        if (filter instanceof BeforeFilter) {
            this.getBeforeFilters().add((BeforeFilter) filter);
        }

        if (filter instanceof AfterFilter) {
            this.getAfterFilters().add((AfterFilter) filter);
        }

        if (filter instanceof LabelFilter) {
            this.getLabelFilters().add((LabelFilter) filter);
        }
    }

    public boolean applyName(JSONSerializer jsonBeanDeser, //
                             Object object, String key) {

        if (jsonBeanDeser.propertyPreFilters != null) {
            for (PropertyPreFilter filter : jsonBeanDeser.propertyPreFilters) {
                if (!filter.apply(jsonBeanDeser, object, key)) {
                    return false;
                }
            }
        }
        
        if (this.propertyPreFilters != null) {
            for (PropertyPreFilter filter : this.propertyPreFilters) {
                if (!filter.apply(jsonBeanDeser, object, key)) {
                    return false;
                }
            }
        }

        return true;
    }
    
    public boolean apply(JSONSerializer jsonBeanDeser, //
                         Object object, //
                         String key, Object propertyValue) {
        
        if (jsonBeanDeser.propertyFilters != null) {
            for (PropertyFilter propertyFilter : jsonBeanDeser.propertyFilters) {
                if (!propertyFilter.apply(object, key, propertyValue)) {
                    return false;
                }
            }
        }
        
        if (this.propertyFilters != null) {
            for (PropertyFilter propertyFilter : this.propertyFilters) {
                if (!propertyFilter.apply(object, key, propertyValue)) {
                    return false;
                }
            }
        }

        return true;
    }
    
    protected String processKey(JSONSerializer jsonBeanDeser, //
                             Object object, //
                             String key, //
                             Object propertyValue) {

        if (jsonBeanDeser.nameFilters != null) {
            for (NameFilter nameFilter : jsonBeanDeser.nameFilters) {
                key = nameFilter.process(object, key, propertyValue);
            }
        }
        
        if (this.nameFilters != null) {
            for (NameFilter nameFilter : this.nameFilters) {
                key = nameFilter.process(object, key, propertyValue);
            }
        }

        return key;
    }

    protected Object processValue(JSONSerializer jsonBeanDeser, //
            BeanContext beanContext,
            Object object, //
            String key, //
            Object propertyValue) {
        return processValue(jsonBeanDeser, beanContext, object, key, propertyValue, 0);
    }
    
    protected Object processValue(JSONSerializer jsonBeanDeser, //
                               BeanContext beanContext,
                               Object object, //
                               String key, //
                               Object propertyValue, //
                               int features) {

        propertyValue = returnPropertyValueifItIsNotNull(propertyValue,jsonBeanDeser,beanContext,features);
        
        propertyValue = returnPropertyValue(propertyValue,jsonBeanDeser,object,key,beanContext);

        return propertyValue;
    }

    public Object returnPropertyValue (Object propertyValuep ,JSONSerializer jsonBeanDeser,Object object,String key,BeanContext beanContext){
        if (jsonBeanDeser.valueFilters != null) {
            for (ValueFilter valueFilter : jsonBeanDeser.valueFilters) {
                propertyValuep = valueFilter.process(object, key, propertyValuep);
            }
        }

        List<ValueFilter> valueFilters1 = this.valueFilters;
        if (valueFilters1 != null) {
            for (ValueFilter valueFilter : valueFilters1) {
                propertyValuep = valueFilter.process(object, key, propertyValuep);
            }
        }

        if (jsonBeanDeser.contextValueFilters != null) {
            for (ContextValueFilter valueFilter : jsonBeanDeser.contextValueFilters) {
                propertyValuep = valueFilter.process(beanContext, object, key, propertyValuep);
            }
        }

        if (this.contextValueFilters != null) {
            for (ContextValueFilter valueFilter : this.contextValueFilters) {
                propertyValuep = valueFilter.process(beanContext, object, key, propertyValuep);
            }
        }
        return propertyValuep;
    }

    public Object returnPropertyValueifItIsNotNull(Object propertyValuep ,JSONSerializer jsonBeanDeser,BeanContext beanContext,int features){
        if (propertyValuep != null) {
            if (serializeFeatureBoolean(propertyValuep,jsonBeanDeser,beanContext,features)) {
                String format = null;
                if (propertyValuep instanceof Number
                        && beanContext != null) {
                    format = beanContext.getFormat();
                }

                if (format != null) {
                    propertyValuep = new DecimalFormat(format).format(propertyValuep);
                } else {
                    propertyValuep = propertyValuep.toString();
                }
            } else if (beanContext != null && beanContext.isJsonDirect()) {
                String jsonStr = (String) propertyValuep;
                propertyValuep = JSON.parse(jsonStr);
            }
        }
        return propertyValuep;
    }

    public boolean serializeFeatureBoolean(Object propertyValuep ,JSONSerializer jsonBeanDeser,BeanContext beanContext,int features){
        return (SerializerFeature.isEnabled(jsonBeanDeser.out.features, features, SerializerFeature.WRITE_NON_STRING_VALUE_AS_STRING)  //
                    || (beanContext != null && (beanContext.getFeatures() & SerializerFeature.WRITE_NON_STRING_VALUE_AS_STRING.mask) != 0))
                    && (propertyValuep instanceof Number || propertyValuep instanceof Boolean);
    }
    
    /**
     * only invoke by asm byte
     * 
     * @return
     */
    protected boolean writeDirectMethod(JSONSerializer jsonBeanDeser) {
        return jsonBeanDeser.out.writeDirect //
               && this.writeDirect //
               && jsonBeanDeser.writeDirect;
    }
}