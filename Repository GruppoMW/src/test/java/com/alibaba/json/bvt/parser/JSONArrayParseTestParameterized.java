package com.alibaba.json.bvt.parser;


import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.parser.Feature;
import com.sun.jdi.Type;
import org.junit.Assert;
import junit.framework.TestCase;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


@RunWith(Parameterized.class)
public class JSONArrayParseTestParameterized extends TestCase {

    /* public void test_array() throws Exception {
        String text = "[{id:123}]";
        List<Map<String, Integer>> array = JSON.parseObject(text, new TypeReference<List<Map<String, Integer>>>() {});
        Assert.assertEquals(1, array.size());
        Map<String, Integer> map  = array.get(0);
        Assert.assertEquals(123, map.get("id").intValue());
    }*/


    private String text;
    private TypeReference<List<Map<String, Integer>>> typeReference;
    private Feature feature;

    public JSONArrayParseTestParameterized(String inputString, TypeReference<List<Map<String, Integer>>> typeReference
    , Feature feature) {
        configure(inputString,typeReference, feature);
    }

    private void configure(String inputString,  TypeReference<List<Map<String, Integer>>> typeReference, Feature feature) {
       this.text = inputString;
       this.typeReference = typeReference;
       this.feature = feature;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
                // inputString		//TypeReference
                {"[{id:123}, {}]", new TypeReference<List<Map<String, Integer>>>() {}, Feature.ORDERED_FIELD},

                {null, new TypeReference<List<Map<String, Integer>>>() {}, Feature.ORDERED_FIELD},
                {"", new TypeReference<List<Map<String, Integer>>>() {}, Feature.ORDERED_FIELD}
        });
    }

    @Test
    public void test_array() {
        List<Map<String, Integer>> array = JSON.parseObject(text, typeReference, feature);

        if( this.text == null || this.text.isEmpty()) Assert.assertNull(array);
        else {
            String[] tokens = text.split("}");
            String[] tokens2 = tokens[0].split(":");

            Assert.assertEquals(tokens.length - 1, array.size());
            Map<String, Integer> map = array.get(0);
            Assert.assertEquals(Integer.parseInt(tokens2[tokens2.length - 1]), map.get("id").intValue());
        }
    }
}
