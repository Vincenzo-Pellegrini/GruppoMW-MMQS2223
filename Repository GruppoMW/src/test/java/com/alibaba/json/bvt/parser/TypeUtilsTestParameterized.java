package com.alibaba.json.bvt.parser;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.TypeUtils;
import junit.framework.TestCase;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TypeUtilsTestParameterized extends TestCase {

    // Test test_castJavaBean_with_Map
    private Map<?,?> map;
    private Object mapClass ;

    //Test test_castJavaBean_with_JsonObj
    private JSONObject JsonObj;

    //Test test_castJavaBean_with_User
    private JSONObject JsonObjUser;
    private Object userClass ;
    private String userKey1;
    private String userKey2;

    //Test test_casts:
     /*test_cast_Integer(), test_cast_Integer_2(), test_cast_to_long(), test_cast_to_Long(), test_cast_to_short()
        test_cast_to_Short(), test_cast_to_byte(), test_cast_to_Byte(), test_cast_to_BigInteger(), test_cast_to_BigDecimal()
        test_cast_to_boolean(), test_cast_to_Boolean() test_cast_to_String(), test_cast_null(), test_cast_to_Date(), test_cast_to_SqlDate(),
        test_cast_to_SqlDate_string(), test_cast_to_SqlDate_null(), test_cast_to_SqlDate_util_Date(), test_cast_to_SqlDate_sql_Date,
        test_cast_to_Timestamp(), test_cast_to_Timestamp_string, test_cast_to_Timestamp_number(), test_cast_to_Timestamp_null(), test_cast_to_Timestamp_util_Date()
        test_cast_to_Timestamp_sql_Date(), test_cast_ab(), test_cast_ab_1()*/
    private JSONObject JsonObject;
    private String key;
    private Object classType ;
    private Object expectedValue;

    //Test test_cast_to_SqlDate_sql_Date2
    private Object expectedValueSqlDate;
    private Object inputValueSqlDate;

    //Test test_cast_to_error:  //test_cast_ab_error(), test_cast_to_SqlDate_error()
    private JSONObject jsonCastToError;
    private String CastToErrorKey;
    private Object castToErrorClass;

   //Test test_cast_to_BigDecimal_same
    private Object expectedCastToBigDecimal;
    private Object inputCastToBigDecimal;

    //Test test_cast_to_BigInteger_same
    private Object expectedCastToBigInteger;
    private Object inputCastToBigInteger;


    //Test test_cast_to_Timestamp_1970_01_01_00_00_00
    private Timestamp expectedCastTo1970;
    private String inputCastTo1970;


    //Test test_error
    private JSONObject jsonError;
    private Object errorClass;

    private ParserConfig parserConfig;


    //Test test_error_2
    private JSONObject jsonError_2;
    private Object errorClass_2;
    private String inputError_2;
    private ParserConfig parserConfig_2;

    //Test test_cast_to_Timestamp_not_error
    private JSONObject jsonCastNoError;
    private Object castNoErrorClass;
    private String castNoErrorKey;
    private Object castNoErrorValue;
    private Timestamp expectedTimestamp;

    //Test test_cast_to_Timestamp_sql_Timestamp
    private Object expectedCastToTimestamp;
    private Object inputCastToTimestamp;

    //Test test_cast_Array
    private List<?> castToArrayList;
    private Object castToArrayClass;
    private Object expectedCastToArray;
    private ParserConfig castToArrayParser;




    public TypeUtilsTestParameterized(Object[] test, Object[] error) {

        configure_test_castJavaBean_with_Map(new HashMap<>(),Map.class);

        configure_test_castJavaBean_with_JsonObj(new JSONObject(),Map.class);

        configure_test_castJavaBean_with_User(new JSONObject(),User.class, "id", 1L, "name","panlei");

        configure_test_casts(new JSONObject(),(Class<?>) test[0], (String) test[1],test[2],test[3]);

        configure_test_cast_to_SqlDate_sql_Date2(new java.sql.Date(System.currentTimeMillis()));

        configure_test_cast_to_error(new JSONObject(),error[0],(String) error[1],error[2]);

        configure_test_cast_to_BigDecimal_same("123",true);

        configure_test_cast_to_BigInteger_same("123",true);

        configure_test_cast_to_Timestamp_1970_01_01_00_00_00("Asia/Shanghai","1970-01-01 08:00:00", 0L);

        configure_test_error(new JSONObject(),"id", 1, C.class, ParserConfig.getGlobalInstance());

        configure_test_error_2(new JSONObject(),"id", 1, "f", List.class, ParserConfig.getGlobalInstance());

        configure_test_cast_to_Timestamp_not_error (new JSONObject(),java.sql.Timestamp.class, "date" ,-1, -1L);

        configure_test_cast_to_Timestamp_sql_Timestamp (System.currentTimeMillis());

        configure_test_cast_Array(Integer[].class, new ArrayList<>(),null, Integer[].class);



    }


    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
       long millis = System.currentTimeMillis();

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        B b;


        return Arrays.asList(new Object[][] {

                {new Object[] {int.class,"id",null, 0}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[] {long.class, "id",null,0L}, new Object[] {B.class,"value", new A()}},
                {new Object[]{short.class,"id",null, Short.valueOf("0")}, new Object[] {B.class,"value", new A()}},
                {new Object[]{byte.class,"id",null, Byte.valueOf("0")}, new Object[] {B.class,"value", new A()}},
                {new Object[]{boolean.class,"id",null, Boolean.FALSE}, new Object[] {B.class,"value", new A()}},
                {new Object[]{float.class,"id",null, (float) 0}, new Object[] {B.class,"value", new A()}},
                {new Object[]{double.class,"id",null, (double) 0}, new Object[] {B.class,"value", new A()}},

                { new Object[] {int.class,"id",1L, 1}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[] {Integer.class,"id",1L,1}, new Object[] {B.class,"value", new A()}},
                {new Object[] {long.class, "id",1,1L}, new Object[] {B.class,"value", new A()}},
                { new Object[] {Long.class, "id",1,1L}, new Object[] {B.class,"value", new A()}},
                { new Object[]{short.class,"id",1, Short.valueOf("1")}, new Object[] {B.class,"value", new A()}},
                { new Object[]{Short.class,"id",1, Short.valueOf("1")}, new Object[] {B.class,"value", new A()}},
                { new Object[]{byte.class,"id",1, Byte.valueOf("1")}, new Object[] {B.class,"value", new A()}},
                {new Object[]{Byte.class,"id",1,Byte.valueOf("1")}, new Object[] {B.class,"value", new A()}},
                { new Object[]{BigDecimal.class,"id",1, new BigDecimal("1")}, new Object[] {B.class,"value", new A()}},
                {new Object[]{BigInteger.class,"id",1, new BigInteger("1")}, new Object[] {B.class,"value", new A()}},
                {new Object[]{boolean.class,"id",1, Boolean.TRUE}, new Object[] {B.class,"value", new A()}},
                {new Object[]{Boolean.class,"id",1,  Boolean.TRUE}, new Object[] {B.class,"value", new A()}},
                {new Object[]{Boolean.class,"id",null, null}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{String.class,"id",1, "1"}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{Date.class,"date",millis, new Date(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Date.class,"date",Long.toString(millis), new java.sql.Date(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Date.class,"date",null, null}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Date.class,"date",new Date(millis), new java.sql.Date(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Date.class,"date",new java.sql.Date(millis), new java.sql.Date(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Date.class,"date",calendar, new java.sql.Date(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Timestamp.class,"date",millis, new java.sql.Timestamp(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Timestamp.class,"date",Long.toString(millis), new java.sql.Timestamp(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Timestamp.class,"date",new BigDecimal(Long.toString(millis)), new java.sql.Timestamp(millis)} , new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Timestamp.class,"date",new Date(millis), new java.sql.Timestamp(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Timestamp.class,"date",new java.sql.Date(millis), new java.sql.Timestamp(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{java.sql.Timestamp.class,"date",calendar, new java.sql.Timestamp(millis)}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{A.class,"value",b= new B(),b}, new Object[] {java.sql.Date.class,"date",0}},
                {new Object[]{IA.class,"value",b= new B(),b}, new Object[] {java.sql.Date.class,"date",0}},

        });
    }

    private void configure_test_cast_to_Timestamp_sql_Timestamp(long currentTimeMillis) {
        java.sql.Timestamp date = new java.sql.Timestamp(currentTimeMillis);

        inputCastToTimestamp = date;
        expectedCastToTimestamp = date;

    }

    
    private void configure_test_cast_Array (Class<?> classType, List<?> list, ParserConfig config, Object expectedValue ){

        castToArrayList = list;
        castToArrayClass = classType;
        castToArrayParser = config;

        expectedCastToArray = expectedValue;

    }

    private void configure_test_cast_to_Timestamp_not_error(JSONObject jsonObj,Class<?> timestampClass, String key, Object value, long expectedValue) {

        jsonCastNoError = jsonObj;
        castNoErrorKey = key;
        castNoErrorValue = value;
        castNoErrorClass = timestampClass;

        jsonCastNoError.put(key, value);

        expectedTimestamp = new Timestamp( expectedValue);

    }

    private void configure_test_error(JSONObject jsonObj,String key, int value, Class<C> cClass, ParserConfig globalInstance) {

        jsonError = jsonObj;

        errorClass = cClass;
        parserConfig = globalInstance;

        jsonError.put(key,value);


    }

    private void configure_test_error_2(JSONObject jsonObj,String key, int value, String f, Class<List> listClass, ParserConfig globalInstance) {

        jsonError_2 = jsonObj;

        errorClass_2 = listClass;
        parserConfig_2 = globalInstance;

        inputError_2 = f;

        jsonError_2.put(key,value);


    }

    public void configure_test_cast_to_Timestamp_1970_01_01_00_00_00(String timeZone, String time, long expectedTime){

        JSON.defaultTimeZone = TimeZone.getTimeZone(timeZone);

        this.inputCastTo1970 = time;

        this.expectedCastTo1970 = new Timestamp(expectedTime);

    }

    public void configure_test_casts(JSONObject jsonObj,Class<?> classType, String key, Object value, Object expectedValue){

            JsonObject = jsonObj;
            this.classType = classType;

            this.key = key;

            JsonObject.put(key,value);

            this.expectedValue = expectedValue;

    }

    private void configure_test_cast_to_error(JSONObject jsonObj,Object classType, String key, Object value){
        this.jsonCastToError = jsonObj;

        this.CastToErrorKey = key;
        this.castToErrorClass = classType;

        jsonCastToError.put(this.CastToErrorKey, value);
    }


    private void configure_test_cast_to_SqlDate_sql_Date2(Object input){
        this.inputValueSqlDate = input;
        this.expectedValueSqlDate = input;

    }

    private void configure_test_castJavaBean_with_Map(HashMap<?,?> map,Class<?> test){
        this.mapClass = test;
        this.map = map;
    }

    private void configure_test_castJavaBean_with_JsonObj(JSONObject jsonObj,Class<?> mapClass) {
        this.mapClass = mapClass;
        this.JsonObj =jsonObj;

    }

    private void configure_test_castJavaBean_with_User(JSONObject jsonObj,Class<?> userClass, String key1, Object value1, String  key2, Object value2){
        this.JsonObjUser = jsonObj;
        this.userClass = userClass;

        this.userKey1 = String.valueOf(key1);
        this.userKey2 = String.valueOf(key2);

        JsonObjUser.put(userKey1, value1);
        JsonObjUser.put(userKey2, value2);

    }

    private void configure_test_cast_to_BigDecimal_same(String input, Object expectedValue){
        this.inputCastToBigDecimal = new BigDecimal(input);
        this.expectedCastToBigDecimal = expectedValue;

    }

    private void configure_test_cast_to_BigInteger_same(String input, Object expectedValue){
        this.inputCastToBigInteger = new BigInteger(input);
        this.expectedCastToBigInteger = expectedValue;

    }


    @Test
    public void test_castJavaBean_with_Map() {
        Assert.assertSame(map, TypeUtils.castToJavaBean(map,(Class<?>) mapClass));
    }

    @Test
    public void test_castJavaBean_with_JsonObj() {
        Assert.assertSame(JsonObj, TypeUtils.castToJavaBean(JsonObj,(Class<?>) mapClass));
    }

    @Test
    public void test_castJavaBean_with_User() {
        User user = (User) TypeUtils.castToJavaBean(JsonObjUser, (Class<?>) userClass);
        Assert.assertEquals(JsonObjUser.get(userKey1), user.getId());
        Assert.assertEquals(JsonObjUser.get(userKey2), user.getName());
    }

    @Test
    public void test_3() {
        User user = (User) JSON.toJavaObject(JsonObjUser,(Class<?>) userClass);
        Assert.assertEquals(JsonObjUser.getLongValue(userKey1), user.getId());
        Assert.assertEquals(JsonObjUser.getString(userKey2), user.getName());
    }

    @Test
    public void test_cast_all_Types() {
        Assert.assertEquals(expectedValue, JsonObject.getObject(key, (Class<?>) classType));
    }

    @Test
    public void test_cast_to_SqlDate_null2() {
        Assert.assertNull(TypeUtils.castToSqlDate(null));
    }

    @Test
    public void test_cast_to_SqlDate_sql_Date2() {
        Assert.assertEquals(expectedValueSqlDate, TypeUtils.castToSqlDate(inputValueSqlDate));
    }


    @Test
    public void test_cast_to_error() {

        JSONException error = null;
        try {
            jsonCastToError.getObject(CastToErrorKey, (Class<?>) castToErrorClass);
        } catch (JSONException e) {
            error = e;
        }
        Assert.assertNotNull(error);
    }


    @Test
    public void test_cast_to_Timestamp_null2() {
        Assert.assertNull(TypeUtils.castToTimestamp(null));
    }

    @Test
    public void test_cast_to_Timestamp_1970_01_01_00_00_00() {

        Assert.assertEquals(expectedCastTo1970, TypeUtils.castToTimestamp(inputCastTo1970));
    }

    @Test
    public void test_cast_to_BigDecimal_same() {
        Assert.assertEquals(expectedCastToBigDecimal, inputCastToBigDecimal == TypeUtils.castToBigDecimal(inputCastToBigDecimal));
    }

    @Test
    public void test_cast_to_BigInteger_same() {
        Assert.assertEquals(expectedCastToBigInteger, inputCastToBigInteger == TypeUtils.castToBigInteger(inputCastToBigInteger));
    }

    @Test
    public void test_cast_to_Timestamp_sql_Timestamp() {
        Assert.assertEquals(expectedCastToTimestamp, TypeUtils.castToTimestamp(inputCastToTimestamp));
    }

    
    @Test
    public void test_cast_Array() {
        Assert.assertEquals(expectedCastToArray, TypeUtils.cast(castToArrayList, (Class<?>) castToArrayClass, castToArrayParser).getClass());
    }

    @Test
    public void test_cast_to_Timestamp_not_error() {

        JSONException error = null;
        try {
            jsonCastNoError.getObject(castNoErrorKey, (Class<?>) castNoErrorClass);
        } catch (JSONException e) {
            error = e;
        }
        Assert.assertNull(error);
        Assert.assertEquals(expectedTimestamp, (java.sql.Timestamp) jsonCastNoError.getObject(castNoErrorKey,(Class<?>) castNoErrorClass));
    }

    @Test
    public void test_error() {

        JSONException error = null;
        try {
            TypeUtils.castToJavaBean(jsonError, (Class<?>) errorClass, parserConfig);
        } catch (JSONException e) {
            error = e;
        }
        Assert.assertNotNull(error);
    }


    @Test
    public void test_error_2() throws Exception {


        Method method = TypeUtilsTestParameterized.class.getMethod(inputError_2, (Class<?>) errorClass_2);

        Throwable error = null;
        try {
            TypeUtils.cast(jsonError_2, method.getGenericParameterTypes()[0], parserConfig_2);
        } catch (JSONException ex) {
            error = ex;
        }
        assertNotNull(error);
    }
    

    public static class User {

        private long id;
        private String name;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class A implements IA {

    }

    public interface IA {

    }

    public static class B extends A {

    }

    public static class C extends B {

        public int getId() {
            throw new UnsupportedOperationException();
        }

        public void setId(int id) {
            throw new UnsupportedOperationException();
        }
    }

    public static void f(List<?> list) {

    }
}
