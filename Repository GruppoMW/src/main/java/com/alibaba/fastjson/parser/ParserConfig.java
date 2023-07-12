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

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.asm.ClassReader;
import com.alibaba.fastjson.asm.TypeCollector;
import com.alibaba.fastjson.parser.deserializer.*;
import com.alibaba.fastjson.serializer.*;
import com.alibaba.fastjson.spi.Module;
import com.alibaba.fastjson.support.moneta.MonetaCodec;
import com.alibaba.fastjson.util.IdentityHashMap;
import com.alibaba.fastjson.util.ServiceLoader;
import com.alibaba.fastjson.util.*;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;

import static com.alibaba.fastjson.util.TypeUtils.FNV1A_64_MAGIC_HASHCODE;
import static com.alibaba.fastjson.util.TypeUtils.FNV1A_64_MAGIC_PRIME;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class ParserConfig {

    public static final String    DENY_PROPERTY             = "fastjson.parser.deny";
    public static final String    AUTOTYPE_ACCEPT           = "fastjson.parser.autoTypeAccept";
    public static final String    AUTOTYPE_SUPPORT_PROPERTY = "fastjson.parser.autoTypeSupport";
    public static final String    SAFE_MODE_PROPERTY        = "fastjson.parser.safeMode";
    static final String           TYPE_NOT_MATCH            = "type not match. ";
    static final String           AUTOTYPE_IS_NOT_SUPPORT   = "autoType is not support. ";

    protected String[] denysInternal;
    protected String[] denys;
    private static final String[] AUTO_TYPE_ACCEPT_LIST;
    public static  final boolean  AUTO_SUPPORT;
    public static  final boolean  SAFE_MODE;
    private static final long[]   INTERNAL_WHITELIST_HASHCODES;

    static  {
        String property = null;
        property = IOUtils.getStringProperty(AUTOTYPE_SUPPORT_PROPERTY);
        AUTO_SUPPORT = "true".equals(property);

        property = null;
        property = IOUtils.getStringProperty(SAFE_MODE_PROPERTY);
        SAFE_MODE = "true".equals(property);

        property = null;
        property = IOUtils.getStringProperty(AUTOTYPE_ACCEPT);
        String[] items = splitItemsFormProperty(property);
        if (items == null) {
            items = new String[0];
        }
        AUTO_TYPE_ACCEPT_LIST = items;

        INTERNAL_WHITELIST_HASHCODES = new long[] {
                0x82E8E13016B73F9EL,
                0x863D2DD1E82B9ED9L,
                0x8B2081CB3A50BD44L,
                0x90003416F28ACD89L,
                0x92F252C398C02946L,
                0x9E404E583F254FD4L,
                0x9F2E20FB6049A371L,
                0xA8AAA929446FFCE4L,
                0xAB9B8D073948CA9DL,
                0xAFCB539973CEA3F7L,
                0xB5114C70135C4538L,
                0xC0FE32B8DC897DE9L,
                0xC59AA84D9A94C640L,
                0xC92D8F9129AF339BL,
                0xCC720543DC5E7090L,
                0xD0E71A6E155603C1L,
                0xD11D2A941337A7BCL,
                0xDB7BFFC197369352L,
                0xDC9583F0087CC2C7L,
                0xDDAAA11FECA77B5EL,
                0xE08EE874A26F5EAFL,
                0xE794F5F7DCD3AC85L,
                0xEB7D4786C473368DL,
                0xF4AA683928027CDAL,
                0xF8C7EF9B13231FB6L,
                0xD45D6F8C9017FAL,
                0x6B949CE6C2FE009L,
                0x76566C052E83815L,
                0x9DF9341F0C76702L,
                0xB81BA299273D4E6L,
                0xD4788669A13AE74L,
                0x111D12921C5466DAL,
                0x178B0E2DC3AE9FE5L,
                0x19DCAF4ADC37D6D4L,
                0x1F10A70EE4065963L,
                0x21082DFBF63FBCC1L,
                0x24AE2D07FB5D7497L,
                0x26C5D923AF21E2E1L,
                0x34CC8E52316FA0CBL,
                0x3F64BC3933A6A2DFL,
                0x42646E60EC7E5189L,
                0x44D57A1B1EF53451L,
                0x4A39C6C7ACB6AA18L,
                0x4BB3C59964A2FC50L,
                0x4F0C3688E8A18F9FL,
                0x5449EC9B0280B9EFL,
                0x54DC66A59269BAE1L,
                0x552D9FB02FFC9DEFL,
                0x557F642131553498L,
                0x604D6657082C1EE9L,
                0x61D10AF54471E5DEL,
                0x64DC636F343516DCL,
                0x73A0BE903F2BCBF4L,
                0x73FBA1E41C4C3553L,
                0x7B606F16A261E1E6L,
                0x7F36112F218143B6L,
                0x7FE2B8E675DA0CEFL
        };
    }

    public static ParserConfig getGlobalInstance() {
        return global;
    }
    public static final ParserConfig global = new ParserConfig();

    private final IdentityHashMap<Type, ObjectDeserializer> deserializers         = new IdentityHashMap<>();
    private final IdentityHashMap<Type, IdentityHashMap<Type, ObjectDeserializer>> mixInDeserializers = new IdentityHashMap<>(16);
    private final ConcurrentMap<String,Class<?>>            typeMapping           = new ConcurrentHashMap<>(16, 0.75f, 1);

    private boolean                                         asmEnable             = !ASMUtils.IS_ANDROID;

    public final SymbolTable                                symbolTable           = new SymbolTable(4096);

    public PropertyNamingStrategy                           propertyNamingStrategy;

    protected ClassLoader                                   defaultClassLoader;

    protected ASMDeserializerFactory                        asmFactory;

    private boolean                                         awtError              = false;
    private boolean                                         jdk8Error             = false;
    private boolean                                         jodaError             = false;
    private  boolean                                  guavaError            = false;

    private boolean                                         autoTypeSupport       = AUTO_SUPPORT;
    private long[]                                          internalDenyHashCodes;
    private List<Long>                                          denyHashCodes = new ArrayList<>();

    private long[]                                          acceptHashCodes;


    public final boolean                                    fieldBased;
    private boolean                                         jacksonCompatible     = false;

    private List<Module>                                    modules                = new ArrayList<>();
    private List<AutoTypeCheckHandler>             autoTypeCheckHandlers;
    private boolean                                         safeMode               = SAFE_MODE;

    public ParserConfig(){
        this(false);
    }

    public ParserConfig(boolean fieldBase){
        this(null, null, fieldBase);
    }

    public ParserConfig(ClassLoader parentClassLoader){
        this(null, parentClassLoader, false);
    }

    public ParserConfig(ASMDeserializerFactory asmFactory){
        this(asmFactory, null, false);
    }

    private ParserConfig(ASMDeserializerFactory asmFactory, ClassLoader parentClassLoader, boolean fieldBased){
        this.fieldBased = fieldBased;
        if (asmFactory == null && !ASMUtils.IS_ANDROID) {
            try {
                if (parentClassLoader == null) {
                    asmFactory = new ASMDeserializerFactory(new ASMClassLoader());
                } else {
                    asmFactory = new ASMDeserializerFactory(parentClassLoader);
                }
            } catch (ExceptionInInitializerError  | NoClassDefFoundError error) {
                // skip
            }
        }

        long[] hashCodes = new long[AUTO_TYPE_ACCEPT_LIST.length];
        for (int i = 0; i < AUTO_TYPE_ACCEPT_LIST.length; i++) {
            hashCodes[i] = TypeUtils.fnv1a64(AUTO_TYPE_ACCEPT_LIST[i]);
        }

        Arrays.sort(hashCodes);
        acceptHashCodes = hashCodes;

        this.asmFactory = asmFactory;

        if (asmFactory == null) {
            asmEnable = false;
        }

        denyHashCodes.add(0x80D0C70BCC2FEA02L);
        denyHashCodes.add(0x86FC2BF9BEAF7AEFL);
        denyHashCodes.add(0x87F52A1B07EA33A6L);
        denyHashCodes.add(0x8EADD40CB2A94443L);
        denyHashCodes.add(0x8F75F9FA0DF03F80L);
        denyHashCodes.add(0x9172A53F157930AFL);
        denyHashCodes.add(0x92122D710E364FB8L);
        denyHashCodes.add(0x941866E73BEFF4C9L);
        denyHashCodes.add(0x94305C26580F73C5L);
        denyHashCodes.add(0x9437792831DF7D3FL);
        denyHashCodes.add(0xA123A62F93178B20L);
        denyHashCodes.add(0xA85882CE1044C450L);
        denyHashCodes.add(0xAA3DAFFDB10C4937L);
        denyHashCodes.add(0xAAA9E6B7C1E1C6A7L);
        denyHashCodes.add(0xAAAA0826487A3737L);
        denyHashCodes.add(0xAC6262F52C98AA39L);
        denyHashCodes.add(0xAD937A449831E8A0L);
        denyHashCodes.add(0xAE50DA1FAD60A096L);
        denyHashCodes.add(0xAFF6FF23388E225AL);
        denyHashCodes.add(0xAFFF4C95B99A334DL);
        denyHashCodes.add(0xB40F341C746EC94FL);
        denyHashCodes.add(0xB7E8ED757F5D13A2L);
        denyHashCodes.add(0xB98B6B5396932FE9L);
        denyHashCodes.add(0xBCDD9DC12766F0CEL);
        denyHashCodes.add(0xBCE0DEE34E726499L);
        denyHashCodes.add(0xBEBA72FB1CCBA426L);
        denyHashCodes.add(0xC00BE1DEBAF2808BL);
        denyHashCodes.add(0xC1086AFAE32E6258L);
        denyHashCodes.add(0xC2664D0958ECFE4CL);
        denyHashCodes.add(0xC41FF7C9C87C7C05L);
        denyHashCodes.add(0xC664B363BACA050AL);
        denyHashCodes.add(0xC7599EBFE3E72406L);
        denyHashCodes.add(0xC8D49E5601E661A9L);
        denyHashCodes.add(0xC8F04B3A28909935L);
        denyHashCodes.add(0xC963695082FD728EL);
        denyHashCodes.add(0xD1EFCDF4B3316D34L);
        denyHashCodes.add(0xD54B91CC77B239EDL);
        denyHashCodes.add(0xD59EE91F0B09EA01L);
        denyHashCodes.add(0xD66F68AB92E7FEF5L);
        denyHashCodes.add(0xD8CA3D595E982BACL);
        denyHashCodes.add(0xDCD8D615A6449E3EL);
        denyHashCodes.add(0xDE23A0809A8B9BD6L);
        denyHashCodes.add(0xDEFC208F237D4104L);
        denyHashCodes.add(0xDF2DDFF310CDB375L);
        denyHashCodes.add(0xE09AE4604842582FL);
        denyHashCodes.add(0xE1919804D5BF468FL);
        denyHashCodes.add(0xE2EB3AC7E56C467EL);
        denyHashCodes.add(0xE603D6A51FAD692BL);
        denyHashCodes.add(0xE9184BE55B1D962AL);
        denyHashCodes.add(0xE9F20BAD25F60807L);
        denyHashCodes.add(0xF2983D099D29B477L);
        denyHashCodes.add(0xF3702A4A5490B8E8L);
        denyHashCodes.add(0xF474E44518F26736L);
        denyHashCodes.add(0xF5D77DCF8E4D71E6L);
        denyHashCodes.add(0xF6C0340E73A36A69L);
        denyHashCodes.add(0xF7E96E74DFA58DBCL);
        denyHashCodes.add(0xFC773AE20C827691L);
        denyHashCodes.add(0xFCF3E78644B98BD8L);
        denyHashCodes.add(0xFD5BFC610056D720L);
        denyHashCodes.add(0xFFA15BF021F1E37CL);
        denyHashCodes.add(0xFFDD1A80F1ED3405L);
        denyHashCodes.add(0x10E067CD55C5E5L);
        denyHashCodes.add(0x761619136CC13EL);
        denyHashCodes.add(0x22BAA234C5BFB8AL);
        denyHashCodes.add(0x3085068CB7201B8L);
        denyHashCodes.add(0x45B11BC78A3ABA3L);
        denyHashCodes.add(0x55CFCA0F2281C07L);
        denyHashCodes.add(0xA555C74FE3A5155L);
        denyHashCodes.add(0xB6E292FA5955ADEL);
        denyHashCodes.add(0xEE6511B66FD5EF0L);
        denyHashCodes.add(0x100150A253996624L);
        denyHashCodes.add(0x10B2BDCA849D9B3EL);
        denyHashCodes.add(0x10DBC48446E0DAE5L);
        denyHashCodes.add(0x144277B467723158L);
        denyHashCodes.add(0x14DB2E6FEAD04AF0L);
        denyHashCodes.add(0x154B6CB22D294CFAL);
        denyHashCodes.add(0x17924CCA5227622AL);
        denyHashCodes.add(0x193B2697EAAED41AL);
        denyHashCodes.add(0x1CD6F11C6A358BB7L);
        denyHashCodes.add(0x1E0A8C3358FF3DAEL);
        denyHashCodes.add(0x24652CE717E713BBL);
        denyHashCodes.add(0x24D2F6048FEF4E49L);
        denyHashCodes.add(0x24EC99D5E7DC5571L);
        denyHashCodes.add(0x25E962F1C28F71A2L);
        denyHashCodes.add(0x275D0732B877AF29L);
        denyHashCodes.add(0x28AC82E44E933606L);
        denyHashCodes.add(0x2AD1CE3A112F015DL);
        denyHashCodes.add(0x2ADFEFBBFE29D931L);
        denyHashCodes.add(0x2B3A37467A344CDFL);
        denyHashCodes.add(0x2B6DD8B3229D6837L);
        denyHashCodes.add(0x2D308DBBC851B0D8L);
        denyHashCodes.add(0x2FE950D3EA52AE0DL);
        denyHashCodes.add(0x313BB4ABD8D4554CL);
        denyHashCodes.add(0x327C8ED7C8706905L);
        denyHashCodes.add(0x332F0B5369A18310L);
        denyHashCodes.add(0x339A3E0B6BEEBEE9L);
        denyHashCodes.add(0x34A81EE78429FDF1L);
        denyHashCodes.add(0x378307CB0111E878L);
        denyHashCodes.add(0x3826F4B2380C8B9BL);
        denyHashCodes.add(0x398F942E01920CF0L);
        denyHashCodes.add(0x3A31412DBB05C7FFL);
        denyHashCodes.add(0x3ADBA40367F73264L);
        denyHashCodes.add(0x3B0B51ECBF6DB221L);
        denyHashCodes.add(0x42D11A560FC9FBA9L);
        denyHashCodes.add(0x43320DC9D2AE0892L);
        denyHashCodes.add(0x440E89208F445FB9L);
        denyHashCodes.add(0x46C808A4B5841F57L);
        denyHashCodes.add(0x49312BDAFB0077D9L);
        denyHashCodes.add(0x4A3797B30328202CL);
        denyHashCodes.add(0x4BA3E254E758D70DL);
        denyHashCodes.add(0x4BF881E49D37F530L);
        denyHashCodes.add(0x4CF54EEC05E3E818L);
        denyHashCodes.add(0x4DA972745FEB30C1L);
        denyHashCodes.add(0x4EF08C90FF16C675L);
        denyHashCodes.add(0x4FD10DDC6D13821FL);
        denyHashCodes.add(0x527DB6B46CE3BCBCL);
        denyHashCodes.add(0x535E552D6F9700C1L);
        denyHashCodes.add(0x5728504A6D454FFCL);
        denyHashCodes.add(0x599B5C1213A099ACL);
        denyHashCodes.add(0x5A5BD85C072E5EFEL);
        denyHashCodes.add(0x5AB0CB3071AB40D1L);
        denyHashCodes.add(0x5B6149820275EA42L);
        denyHashCodes.add(0x5D74D3E5B9370476L);
        denyHashCodes.add(0x5D92E6DDDE40ED84L);
        denyHashCodes.add(0x5E61093EF8CDDDBBL);
        denyHashCodes.add(0x5F215622FB630753L);
        denyHashCodes.add(0x61C5BDD721385107L);
        denyHashCodes.add(0x62DB241274397C34L);
        denyHashCodes.add(0x63A220E60A17C7B9L);
        denyHashCodes.add(0x647AB0224E149EBEL);
        denyHashCodes.add(0x65F81B84C1D920CDL);
        denyHashCodes.add(0x665C53C311193973L);
        denyHashCodes.add(0x6749835432E0F0D2L);
        denyHashCodes.add(0x69B6E0175084B377L);
        denyHashCodes.add(0x6A47501EBB2AFDB2L);
        denyHashCodes.add(0x6FCABF6FA54CAFFFL);
        denyHashCodes.add(0x6FE92D83FC0A4628L);
        denyHashCodes.add(0x746BD4A53EC195FBL);
        denyHashCodes.add(0x74B50BB9260E31FFL);
        denyHashCodes.add(0x75CC60F5871D0FD3L);
        denyHashCodes.add(0x7AA7EE3627A19CF3L);
        denyHashCodes.add(0x7ED9311D28BF1A65L);
        denyHashCodes.add(0x7ED9481D28BF417AL);
        denyHashCodes.add(0xC664B363BACA050AL);
        denyHashCodes.add(0xC7599EBFE3E72406L);
        denyHashCodes.add(0xC8D49E5601E661A9L);
        denyHashCodes.add(0xC8F04B3A28909935L);
        denyHashCodes.add(0xC963695082FD728EL);
        denyHashCodes.add(0xD1EFCDF4B3316D34L);
        denyHashCodes.add(0xD54B91CC77B239EDL);
        denyHashCodes.add(0xD59EE91F0B09EA01L);
        denyHashCodes.add(0xD66F68AB92E7FEF5L);
        denyHashCodes.add(0xD8CA3D595E982BACL);
        denyHashCodes.add(0xDCD8D615A6449E3EL);
        denyHashCodes.add(0xDE23A0809A8B9BD6L);
        denyHashCodes.add(0xDEFC208F237D4104L);
        denyHashCodes.add(0xDF2DDFF310CDB375L);
        denyHashCodes.add(0xE09AE4604842582FL);
        denyHashCodes.add(0xE1919804D5BF468FL);
        denyHashCodes.add(0xE2EB3AC7E56C467EL);
        denyHashCodes.add(0xE603D6A51FAD692BL);
        denyHashCodes.add(0xE9184BE55B1D962AL);
        denyHashCodes.add(0xE9F20BAD25F60807L);
        denyHashCodes.add(0xF2983D099D29B477L);
        denyHashCodes.add(0xF3702A4A5490B8E8L);
        denyHashCodes.add(0xF474E44518F26736L);
        denyHashCodes.add(0xF5D77DCF8E4D71E6L);
        denyHashCodes.add(0xF6C0340E73A36A69L);
        denyHashCodes.add(0xF7E96E74DFA58DBCL);
        denyHashCodes.add(0xFC773AE20C827691L);
        denyHashCodes.add(0xFCF3E78644B98BD8L);
        denyHashCodes.add(0xFD5BFC610056D720L);
        denyHashCodes.add(0xFFA15BF021F1E37CL);
        denyHashCodes.add(0xFFDD1A80F1ED3405L);
        denyHashCodes.add(0x10E067CD55C5E5L);
        denyHashCodes.add(0x761619136CC13EL);
        denyHashCodes.add(0x22BAA234C5BFB8AL);
        denyHashCodes.add(0x3085068CB7201B8L);
        denyHashCodes.add(0x45B11BC78A3ABA3L);
        denyHashCodes.add(0x55CFCA0F2281C07L);
        denyHashCodes.add(0xA555C74FE3A5155L);
        denyHashCodes.add(0xB6E292FA5955ADEL);
        denyHashCodes.add(0xEE6511B66FD5EF0L);
        denyHashCodes.add(0x100150A253996624L);
        denyHashCodes.add(0x10B2BDCA849D9B3EL);
        denyHashCodes.add(0x10DBC48446E0DAE5L);
        denyHashCodes.add(0x144277B467723158L);
        denyHashCodes.add(0x14DB2E6FEAD04AF0L);
        denyHashCodes.add(0x154B6CB22D294CFAL);
        denyHashCodes.add(0x17924CCA5227622AL);
        denyHashCodes.add(0x193B2697EAAED41AL);
        denyHashCodes.add(0x1CD6F11C6A358BB7L);
        denyHashCodes.add(0x1E0A8C3358FF3DAEL);
        denyHashCodes.add(0x24652CE717E713BBL);
        denyHashCodes.add(0x24D2F6048FEF4E49L);
        denyHashCodes.add(0x24EC99D5E7DC5571L);
        denyHashCodes.add(0x25E962F1C28F71A2L);
        denyHashCodes.add(0x275D0732B877AF29L);
        denyHashCodes.add(0x28AC82E44E933606L);
        denyHashCodes.add(0x2AD1CE3A112F015DL);
        denyHashCodes.add(0x2ADFEFBBFE29D931L);
        denyHashCodes.add(0x2B3A37467A344CDFL);
        denyHashCodes.add(0x2B6DD8B3229D6837L);
        denyHashCodes.add(0x2D308DBBC851B0D8L);
        denyHashCodes.add(0x2FE950D3EA52AE0DL);
        denyHashCodes.add(0x313BB4ABD8D4554CL);
        denyHashCodes.add(0x327C8ED7C8706905L);
        denyHashCodes.add(0x332F0B5369A18310L);
        denyHashCodes.add(0x339A3E0B6BEEBEE9L);
        denyHashCodes.add(0x33C64B921F523F2FL);
        denyHashCodes.add(0x34A81EE78429FDF1L);
        denyHashCodes.add(0x378307CB0111E878L);
        denyHashCodes.add(0x3826F4B2380C8B9BL);
        denyHashCodes.add(0x398F942E01920CF0L);
        denyHashCodes.add(0x3A31412DBB05C7FFL);
        denyHashCodes.add(0x3ADBA40367F73264L);
        denyHashCodes.add(0x3B0B51ECBF6DB221L);
        denyHashCodes.add(0x42D11A560FC9FBA9L);
        denyHashCodes.add(0x43320DC9D2AE0892L);
        denyHashCodes.add(0x440E89208F445FB9L);
        denyHashCodes.add(0x46C808A4B5841F57L);
        denyHashCodes.add(0x49312BDAFB0077D9L);
        denyHashCodes.add(0x4A3797B30328202CL);
        denyHashCodes.add(0x4BA3E254E758D70DL);
        denyHashCodes.add(0x4BF881E49D37F530L);
        denyHashCodes.add(0x4CF54EEC05E3E818L);
        denyHashCodes.add(0x4DA972745FEB30C1L);
        denyHashCodes.add(0x4EF08C90FF16C675L);
        denyHashCodes.add(0x4FD10DDC6D13821FL);
        denyHashCodes.add(0x527DB6B46CE3BCBCL);
        denyHashCodes.add(0x535E552D6F9700C1L);
        denyHashCodes.add(0x5728504A6D454FFCL);
        denyHashCodes.add(0x599B5C1213A099ACL);
        denyHashCodes.add(0x5A5BD85C072E5EFEL);
        denyHashCodes.add(0x5AB0CB3071AB40D1L);
        denyHashCodes.add(0x5B6149820275EA42L);
        denyHashCodes.add(0x5D74D3E5B9370476L);
        denyHashCodes.add(0x5D92E6DDDE40ED84L);
        denyHashCodes.add(0x5E61093EF8CDDDBBL);
        denyHashCodes.add(0x5F215622FB630753L);
        denyHashCodes.add(0x61C5BDD721385107L);
        denyHashCodes.add(0x62DB241274397C34L);
        denyHashCodes.add(0x63A220E60A17C7B9L);
        denyHashCodes.add(0x647AB0224E149EBEL);
        denyHashCodes.add(0x65F81B84C1D920CDL);
        denyHashCodes.add(0x665C53C311193973L);
        denyHashCodes.add(0x6749835432E0F0D2L);
        denyHashCodes.add(0x69B6E0175084B377L);
        denyHashCodes.add(0x6A47501EBB2AFDB2L);
        denyHashCodes.add(0x6FCABF6FA54CAFFFL);
        denyHashCodes.add(0x6FE92D83FC0A4628L);
        denyHashCodes.add(0x746BD4A53EC195FBL);
        denyHashCodes.add(0x74B50BB9260E31FFL);
        denyHashCodes.add(0x75CC60F5871D0FD3L);
        denyHashCodes.add(0x767A586A5107FEEFL);
        denyHashCodes.add(0x7AA7EE3627A19CF3L);
        denyHashCodes.add(0x7ED9311D28BF1A65L);
        denyHashCodes.add(0x7ED9481D28BF417AL);

        initDeserializers();

        addItemsToDeny(denys);
        addItemsToDeny0(denysInternal);
        addItemsToAccept(AUTO_TYPE_ACCEPT_LIST);

    }

    private final Callable<Void> initDeserializersWithJavaSql = new Callable<Void>() {
        public Void call() {
            deserializers.put(java.sql.Timestamp.class, SqlDateDeserializer.instance_timestamp);
            deserializers.put(java.sql.Date.class, SqlDateDeserializer.instance);
            deserializers.put(java.sql.Time.class, TimeDeserializer.instance);
            deserializers.put(java.util.Date.class, DateCodec.instance);
            return null;
        }
    };

    private void initDeserializers() {
        deserializers.put(SimpleDateFormat.class, MiscCodec.instance);
        deserializers.put(Calendar.class, CalendarCodec.instance);
        deserializers.put(XMLGregorianCalendar.class, CalendarCodec.instance);

        deserializers.put(JSONObject.class, MapDeserializer.instance);
        deserializers.put(JSONArray.class, CollectionCodec.instance);

        deserializers.put(Map.class, MapDeserializer.instance);
        deserializers.put(HashMap.class, MapDeserializer.instance);
        deserializers.put(LinkedHashMap.class, MapDeserializer.instance);
        deserializers.put(TreeMap.class, MapDeserializer.instance);
        deserializers.put(ConcurrentMap.class, MapDeserializer.instance);
        deserializers.put(ConcurrentHashMap.class, MapDeserializer.instance);

        deserializers.put(Collection.class, CollectionCodec.instance);
        deserializers.put(List.class, CollectionCodec.instance);
        deserializers.put(ArrayList.class, CollectionCodec.instance);

        deserializers.put(Object.class, JavaObjectDeserializer.instance);
        deserializers.put(String.class, StringCodec.instance);
        deserializers.put(StringBuffer.class, StringCodec.instance);
        deserializers.put(StringBuilder.class, StringCodec.instance);
        deserializers.put(char.class, CharacterCodec.instance);
        deserializers.put(Character.class, CharacterCodec.instance);
        deserializers.put(byte.class, NumberDeserializer.instance);
        deserializers.put(Byte.class, NumberDeserializer.instance);
        deserializers.put(short.class, NumberDeserializer.instance);
        deserializers.put(Short.class, NumberDeserializer.instance);
        deserializers.put(int.class, IntegerCodec.instance);
        deserializers.put(Integer.class, IntegerCodec.instance);
        deserializers.put(long.class, LongCodec.instance);
        deserializers.put(Long.class, LongCodec.instance);
        deserializers.put(BigInteger.class, BigIntegerCodec.instance);
        deserializers.put(BigDecimal.class, BigDecimalCodec.instance);
        deserializers.put(float.class, FloatCodec.instance);
        deserializers.put(Float.class, FloatCodec.instance);
        deserializers.put(double.class, NumberDeserializer.instance);
        deserializers.put(Double.class, NumberDeserializer.instance);
        deserializers.put(boolean.class, BooleanCodec.instance);
        deserializers.put(Boolean.class, BooleanCodec.instance);
        deserializers.put(Class.class, MiscCodec.instance);
        deserializers.put(char[].class, new CharArrayCodec());

        deserializers.put(AtomicBoolean.class, BooleanCodec.instance);
        deserializers.put(AtomicInteger.class, IntegerCodec.instance);
        deserializers.put(AtomicLong.class, LongCodec.instance);
        deserializers.put(AtomicReference.class, ReferenceCodec.instance);

        deserializers.put(WeakReference.class, ReferenceCodec.instance);
        deserializers.put(SoftReference.class, ReferenceCodec.instance);

        deserializers.put(UUID.class, MiscCodec.instance);
        deserializers.put(TimeZone.class, MiscCodec.instance);
        deserializers.put(Locale.class, MiscCodec.instance);
        deserializers.put(Currency.class, MiscCodec.instance);

        deserializers.put(Inet4Address.class, MiscCodec.instance);
        deserializers.put(Inet6Address.class, MiscCodec.instance);
        deserializers.put(InetSocketAddress.class, MiscCodec.instance);
        deserializers.put(File.class, MiscCodec.instance);
        deserializers.put(URI.class, MiscCodec.instance);
        deserializers.put(URL.class, MiscCodec.instance);
        deserializers.put(Pattern.class, MiscCodec.instance);
        deserializers.put(Charset.class, MiscCodec.instance);
        deserializers.put(JSONPath.class, MiscCodec.instance);
        deserializers.put(Number.class, NumberDeserializer.instance);
        deserializers.put(AtomicIntegerArray.class, AtomicCodec.instance);
        deserializers.put(AtomicLongArray.class, AtomicCodec.instance);
        deserializers.put(StackTraceElement.class, StackTraceElementDeserializer.instance);

        deserializers.put(Serializable.class, JavaObjectDeserializer.instance);
        deserializers.put(Cloneable.class, JavaObjectDeserializer.instance);
        deserializers.put(Comparable.class, JavaObjectDeserializer.instance);
        deserializers.put(Closeable.class, JavaObjectDeserializer.instance);

        deserializers.put(JSONPObject.class, new JSONPDeserializer());
        ModuleUtil.callWhenHasJavaSql(initDeserializersWithJavaSql);
    }

    private static String[] splitItemsFormProperty(final String property ){
        if (property != null && property.length() > 0) {
            return property.split(",");
        }
        return new String[0];
    }

    public void configFromPropety(Properties properties) {
        addItemsToDenyFunction(properties);
        addItemsToAcceptFunction(properties);
        setAutoSupport(properties);
    }

    private void setAutoSupport(Properties properties) {
        String property = properties.getProperty(AUTOTYPE_SUPPORT_PROPERTY);
        if ("true".equals(property)) {
            this.autoTypeSupport = true;
        } else if ("false".equals(property)) {
            this.autoTypeSupport = false;
        }
    }

    private void addItemsToAcceptFunction(Properties properties) {
        String property = properties.getProperty(AUTOTYPE_ACCEPT);
        String[] items = splitItemsFormProperty(property);
        addItemsToAccept(items);
    }

    private void addItemsToDenyFunction(Properties properties) {
        String property = properties.getProperty(DENY_PROPERTY);
        String[] items = splitItemsFormProperty(property);
        addItemsToDeny(items);
    }

    private void addItemsToDeny0(final String[] items){
        if (items == null){
            return;
        }

        for (int i = 0; i < items.length; ++i) {
            String item = items[i];
            this.addDenyInternal(item);
        }
    }

    private void addItemsToDeny(final String[] items){
        if (items == null){
            return;
        }

        for (int i = 0; i < items.length; ++i) {
            String item = items[i];
            this.addDeny(item);
        }
    }

    private void addItemsToAccept(final String[] items){
        if (items == null){
            return;
        }

        for (int i = 0; i < items.length; ++i) {
            String item = items[i];
            this.addAccept(item);
        }
    }

    /**
     * @since 1.2.68
     */
    public boolean isSafeMode() {
        return safeMode;
    }

    /**
     * @since 1.2.68
     */
    public void setSafeMode(boolean safeMode) {
        this.safeMode = safeMode;
    }

    public boolean isAutoTypeSupport() {
        return autoTypeSupport;
    }

    public void setAutoTypeSupport(boolean autoTypeSupport) {
        this.autoTypeSupport = autoTypeSupport;
    }

    public boolean isAsmEnable() {
        return asmEnable;
    }

    public void setAsmEnable(boolean asmEnable) {
        this.asmEnable = asmEnable;
    }

    public IdentityHashMap<Type, ObjectDeserializer> getDeserializers() {
        return deserializers;
    }

    public  ObjectDeserializer getDeserializer(Type type) {
        ObjectDeserializer deserializer = get(type);
        if (deserializer != null) {
            return deserializer;
        }

        if (type instanceof Class<?>) {
            return getDeserializer((Class<?>) type, type);
        }

        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return getDeserializer((Class<?>) rawType, type);
            } else {
                return getDeserializer(rawType);
            }
        }

        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length == 1) {
                Type upperBoundType = upperBounds[0];
                return getDeserializer(upperBoundType);
            }
        }

        return JavaObjectDeserializer.instance;
    }

    public <T> ObjectDeserializer getDeserializer(Class<T> clazz, Type type) {
        ObjectDeserializer deserializer = get(type);
        if (deserializer == null && type instanceof ParameterizedTypeImpl) {
            Type innerType = TypeReference.intern((ParameterizedTypeImpl) type);
            deserializer = get(innerType);
        }

        if (deserializer != null) {
            return deserializer;
        }

        if (type == null) {
            type = clazz;
        }

        deserializer = get(type);
        if (deserializer != null) {
            return deserializer;
        }

        JSONType annotation = TypeUtils.getAnnotation(clazz,JSONType.class);
        if (annotation != null) {
            Class<?> mappingTo = annotation.mappingTo();
            if (mappingTo != Void.class) {
                return getDeserializer(mappingTo, mappingTo);
            }
        }

        if (type instanceof WildcardType || type instanceof TypeVariable || type instanceof ParameterizedType) {
            deserializer = get(clazz);
        }

        if (deserializer != null) {
            return deserializer;
        }

        for (Module module : modules) {
            deserializer = module.createDeserializer(this, clazz);
            if (deserializer != null) {
                putDeserializer(type, deserializer);
                return deserializer;
            }
        }

        String className = clazz.getName();
        className = className.replace('$', '.');

        if (className.startsWith("java.awt.") //
                && AwtCodec.support(clazz) && !awtError) {
            String[] names = new String[]{
                    "java.awt.Point",
                    "java.awt.Font",
                    "java.awt.Rectangle",
                    "java.awt.Color"
            };

            try {
                for (String name : names) {
                    if (name.equals(className)) {
                        deserializer = AwtCodec.instance;
                        putDeserializer(Class.forName(name), deserializer);
                        return deserializer;
                    }
                }
            } catch (ClassNotFoundException e) {
                // skip
                awtError = true;
            }

            deserializer = AwtCodec.instance;
        }

        if (!jdk8Error) {
            try {
                if (className.startsWith("java.time.")) {
                    String[] names = new String[]{
                            "java.time.LocalDateTime",
                            "java.time.LocalDate",
                            "java.time.LocalTime",
                            "java.time.ZonedDateTime",
                            "java.time.OffsetDateTime",
                            "java.time.OffsetTime",
                            "java.time.ZoneOffset",
                            "java.time.ZoneRegion",
                            "java.time.ZoneId",
                            "java.time.Period",
                            "java.time.Duration",
                            "java.time.Instant"
                    };

                    for (String name : names) {
                        if (name.equals(className)) {
                            deserializer = Jdk8DateCodec.instance;
                            putDeserializer(Class.forName(name), deserializer);
                            return deserializer;
                        }
                    }
                } else if (className.startsWith("java.util.Optional")) {
                    String[] names = new String[]{
                            "java.util.Optional",
                            "java.util.OptionalDouble",
                            "java.util.OptionalInt",
                            "java.util.OptionalLong"
                    };
                    for (String name : names) {
                        if (name.equals(className)) {
                            deserializer = OptionalCodec.instance;
                            putDeserializer(Class.forName(name), deserializer);
                            return deserializer;
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // skip
                jdk8Error = true;
            }
        }

        if (!jodaError) {
            try {
                if (className.startsWith("org.joda.time.")) {
                    String[] names = new String[]{
                            "org.joda.time.DateTime",
                            "org.joda.time.LocalDate",
                            "org.joda.time.LocalDateTime",
                            "org.joda.time.LocalTime",
                            "org.joda.time.Instant",
                            "org.joda.time.Period",
                            "org.joda.time.Duration",
                            "org.joda.time.DateTimeZone",
                            "org.joda.time.format.DateTimeFormatter"
                    };

                    for (String name : names) {
                        if (name.equals(className)) {
                            deserializer = JodaCodec.instance;
                            putDeserializer(Class.forName(name), deserializer);
                            return deserializer;
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // skip
                jodaError = true;
            }
        }

        if ((!guavaError) //
                && className.startsWith("com.google.common.collect.")) {
            try {
                String[] names = new String[] {
                        "com.google.common.collect.HashMultimap",
                        "com.google.common.collect.LinkedListMultimap",
                        "com.google.common.collect.LinkedHashMultimap",
                        "com.google.common.collect.ArrayListMultimap",
                        "com.google.common.collect.TreeMultimap"
                };

                for (String name : names) {
                    if (name.equals(className)) {
                        deserializer = GuavaCodec.instance;
                        putDeserializer(Class.forName(name), deserializer);
                        return deserializer;
                    }
                }
            } catch (ClassNotFoundException e) {
                // skip
                guavaError = true;
            }
        }

        if (className.equals("java.nio.ByteBuffer")) {
            deserializer = ByteBufferCodec.instance;
            putDeserializer(clazz, deserializer);
        }

        if (className.equals("java.nio.file.Path")) {
            deserializer = MiscCodec.instance;
            putDeserializer(clazz, deserializer);
        }

        if (clazz == Map.Entry.class) {
            deserializer = MiscCodec.instance;
            putDeserializer(clazz, deserializer);
        }

        if (className.equals("org.javamoney.moneta.Money")) {
            deserializer = MonetaCodec.instance;
            putDeserializer(clazz, deserializer);
        }

        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            for (AutowiredObjectDeserializer autowired : ServiceLoader.load(AutowiredObjectDeserializer.class,
                    classLoader)) {
                for (Type forType : autowired.getAutowiredFor()) {
                    putDeserializer(forType, autowired);
                }
            }
        } catch (Exception ex) {
            // skip
        }

        if (deserializer == null) {
            deserializer = get(type);
        }

        if (deserializer != null) {
            return deserializer;
        }

        if (clazz.isEnum()) {
            if (jacksonCompatible) {
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (TypeUtils.isJacksonCreator(method)) {
                        deserializer = createJavaBeanDeserializer(clazz, type);
                        putDeserializer(type, deserializer);
                        return deserializer;
                    }
                }
            }

            Class<T> mixInType = (Class<T>) JSON.getMixInAnnotations(clazz);

            JSONType jsonType = TypeUtils.getAnnotation(mixInType != null ? mixInType : clazz, JSONType.class);

            if (jsonType != null) {
                //Skip
                putDeserializer(clazz, deserializer);
                return deserializer;
            }

            Method jsonCreatorMethod = null;
            if (mixInType != null) {
                Method mixedCreator = getEnumCreator(mixInType, clazz);
                if (mixedCreator != null) {
                    try {
                        jsonCreatorMethod = clazz.getMethod(mixedCreator.getName(), mixedCreator.getParameterTypes());
                    } catch (Exception e) {
                        // skip
                    }
                }
            } else {
                jsonCreatorMethod = getEnumCreator(clazz, clazz);
            }

            if (jsonCreatorMethod != null) {
                deserializer = new EnumCreatorDeserializer(jsonCreatorMethod);
                putDeserializer(clazz, deserializer);
                return deserializer;
            }

            deserializer = getEnumDeserializer(clazz);
        } else if (clazz.isArray()) {
            deserializer = ObjectArrayCodec.instance;
        } else if (clazz == Set.class || clazz == HashSet.class || clazz == Collection.class || clazz == List.class
                || clazz == ArrayList.class) {
            deserializer = CollectionCodec.instance;
        } else if (Collection.class.isAssignableFrom(clazz)) {
            deserializer = CollectionCodec.instance;
        } else if (Map.class.isAssignableFrom(clazz)) {
            deserializer = MapDeserializer.instance;
        } else if (Throwable.class.isAssignableFrom(clazz)) {
            deserializer = new ThrowableDeserializer(this, clazz);
        } else if (PropertyProcessable.class.isAssignableFrom(clazz)) {
            deserializer = new PropertyProcessableDeserializer((Class<PropertyProcessable>) clazz);
        } else if (clazz == InetAddress.class) {
            deserializer = MiscCodec.instance;
        } else {
            deserializer = createJavaBeanDeserializer(clazz, type);
        }

        putDeserializer(type, deserializer);

        return deserializer;
    }

    private static <T> Method getEnumCreator(Class<T> clazz, Class<T> enumClass) {
        Method[] methods = clazz.getMethods();
        Method jsonCreatorMethod = null;
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == enumClass
                    && method.getParameterTypes().length == 1
            ) {
                JSONCreator jsonCreator = method.getAnnotation(JSONCreator.class);
                if (jsonCreator != null) {
                    jsonCreatorMethod = method;
                    break;
                }
            }
        }

        return jsonCreatorMethod;
    }

    /**
     * 可以通过重写这个方法，定义自己的枚举反序列化实现
     * @param clazz 转换的类型
     * @return 返回一个枚举的反序列化实现
     * @author zhu.xiaojie
     * @time 2020-4-5
     */
    protected ObjectDeserializer getEnumDeserializer(Class<?> clazz){
        return new EnumDeserializer(clazz);
    }

    /**
     *
     * @since 1.2.25
     */
    public void initJavaBeanDeserializers(Class<?>... classes) {
        if (classes == null) {
            return;
        }

        for (Class<?> type : classes) {
            if (type == null) {
                continue;
            }
            ObjectDeserializer deserializer = createJavaBeanDeserializer(type, type);
            putDeserializer(type, deserializer);
        }
    }

    public ObjectDeserializer createJavaBeanDeserializer(Class<?> clazz, Type type) {
        boolean asmEnable2 = this.asmEnable && !this.fieldBased;
        asmEnable2 = extracted11(clazz, asmEnable2);

        asmEnable2 = extracted12(clazz, asmEnable2);

        asmEnable2 = extracted13(clazz, asmEnable2);

        asmEnable2 = extracted14(clazz, asmEnable2);

        asmEnable2 = extracted20(clazz, type, asmEnable2);

        asmEnable2 = extracted22(clazz, asmEnable2);

        asmEnable2 = extracted23(clazz, asmEnable2);

        if (!asmEnable2) {
            return new JavaBeanDeserializer(this, clazz, type);
        }

        JavaBeanInfo beanInfo = JavaBeanInfo.build(clazz, type, propertyNamingStrategy);
        return extracted24(clazz, type, beanInfo);
    }

    private ObjectDeserializer extracted24(Class<?> clazz, Type type, JavaBeanInfo beanInfo) {
        try {
            return asmFactory.createJavaBeanDeserializer(this, beanInfo);
        } catch (NoSuchMethodException ex) {
            return new JavaBeanDeserializer(this, clazz, type);
        } catch (JSONException asmError) {
            return new JavaBeanDeserializer(this, beanInfo);
        } catch (Exception e) {
            throw new JSONException("create asm deserializer error, " + clazz.getName(), e);
        }
    }

    private boolean extracted23(Class<?> clazz, boolean asmEnable) {
        if (asmEnable && TypeUtils.isXmlField(clazz)) {
            asmEnable = false;
        }
        return asmEnable;
    }

    private boolean extracted22(Class<?> clazz, boolean asmEnable) {
        if (asmEnable) {
            asmEnable = extracted21(clazz, asmEnable);
        }
        return asmEnable;
    }

    private boolean extracted21(Class<?> clazz, boolean asmEnable) {
        if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            asmEnable = false;
        }
        return asmEnable;
    }

    private boolean extracted20(Class<?> clazz, Type type, boolean asmEnable) {
        if (asmEnable) {
            asmEnable = extracted15(clazz, asmEnable);
            JavaBeanInfo beanInfo = JavaBeanInfo.build(clazz
                    , type
                    , propertyNamingStrategy
                    ,false
                    , jacksonCompatible
            );

            asmEnable = extracted16(asmEnable, beanInfo);

            Constructor<?> defaultConstructor = beanInfo.defaultConstructor;
            asmEnable = extracted17(clazz, asmEnable, defaultConstructor);

            asmEnable = extracted19(asmEnable, beanInfo);
        }
        return asmEnable;
    }

    private boolean extracted19(boolean asmEnable, JavaBeanInfo beanInfo) {
        for (FieldInfo fieldInfo : beanInfo.fields) {
            if (fieldInfo.getOnly) {
                asmEnable = false;
                return asmEnable;
            }

            Class<?> fieldClass = fieldInfo.fieldClass;
            if (!Modifier.isPublic(fieldClass.getModifiers())) {
                return asmEnable;
            }

            if (extracted25(fieldClass)) {
                asmEnable = false;
                return asmEnable;
            }

            if (extracted26(fieldInfo)) {
                asmEnable = false;
                return asmEnable;
            }

            JSONField annotation = fieldInfo.getAnnotation();
            if (extracted18(fieldInfo, annotation)) {
                asmEnable = false;
                return asmEnable;
            }

            if (fieldClass.isEnum()) { // EnumDeserializer
                return asmEnable;
            }
        }
        return asmEnable;
    }

    private boolean extracted26(FieldInfo fieldInfo) {
        return fieldInfo.getMember() != null //
                && !ASMUtils.checkName(fieldInfo.getMember().getName());
    }

    private boolean extracted25(Class<?> fieldClass) {
        return fieldClass.isMemberClass() && !Modifier.isStatic(fieldClass.getModifiers());
    }

    private boolean extracted18(FieldInfo fieldInfo, JSONField annotation) {
        return annotation != null //
                && ((!ASMUtils.checkName(annotation.name())) //
                || annotation.format().length() != 0 //
                || annotation.deserializeUsing() != Void.class //
                || annotation.parseFeatures().length != 0 //
                || annotation.unwrapped())
                || (fieldInfo.method != null && fieldInfo.method.getParameterTypes().length > 1);
    }

    private boolean extracted17(Class<?> clazz, boolean asmEnable, Constructor<?> defaultConstructor) {
        if (asmEnable && defaultConstructor == null && !clazz.isInterface()) {
            asmEnable = false;
        }
        return asmEnable;
    }

    private boolean extracted16(boolean asmEnable, JavaBeanInfo beanInfo) {
        if (asmEnable && beanInfo.fields.length > 200) {
            asmEnable = false;
        }
        return asmEnable;
    }

    private boolean extracted15(Class<?> clazz, boolean asmEnable) {
        if (clazz.isInterface()) {
            asmEnable = false;
        }
        return asmEnable;
    }

    private boolean extracted14(Class<?> clazz, boolean asmEnable) {
        if (asmEnable) {
            asmEnable = ASMUtils.checkName(clazz.getSimpleName());
        }
        return asmEnable;
    }

    private boolean extracted13(Class<?> clazz, boolean asmEnable) {
        if (asmEnable && asmFactory != null && asmFactory.classLoader.isExternalClass(clazz)) {
            asmEnable = false;
        }
        return asmEnable;
    }

    private boolean extracted12(Class<?> clazz, boolean asmEnable) {
        if (clazz.getTypeParameters().length != 0) {
            asmEnable = false;
        }
        return asmEnable;
    }

    private boolean extracted11(Class<?> clazz, boolean asmEnable) {
        if (asmEnable) {
            JSONType jsonType = TypeUtils.getAnnotation(clazz,JSONType.class);

            asmEnable = extracted10(clazz, asmEnable, jsonType);
        }
        return asmEnable;
    }

    private boolean extracted10(Class<?> clazz, boolean asmEnable, JSONType jsonType) {
        if (asmEnable) {
            Class<?> superClass = JavaBeanInfo.getBuilderClass(clazz, jsonType);
            if (superClass == null) {
                superClass = clazz;
            }

            asmEnable = extracted9(asmEnable, superClass);
        }
        return asmEnable;
    }

    private boolean extracted9(boolean asmEnable, Class<?> superClass) {
        for (;;) {
            if (!Modifier.isPublic(superClass.getModifiers())) {
                asmEnable = false;
                return asmEnable;
            }

            superClass = superClass.getSuperclass();
            if (superClass == Object.class || superClass == null) {
                break;
            }
        }
        return asmEnable;
    }

    public FieldDeserializer createFieldDeserializer(ParserConfig mapping, //
                                                     JavaBeanInfo beanInfo, //
                                                     FieldInfo fieldInfo) {
        Class<?> clazz = beanInfo.clazz;
        Class<?> fieldClass = fieldInfo.fieldClass;

        Class<?> deserializeUsing = null;
        JSONField annotation = fieldInfo.getAnnotation();
        if (annotation != null) {
            deserializeUsing = annotation.deserializeUsing();
            if (deserializeUsing == Void.class) {
                deserializeUsing = null;
            }
        }

        if (deserializeUsing == null && (fieldClass == List.class || fieldClass == ArrayList.class)) {
            return new ArrayListTypeFieldDeserializer(clazz, fieldInfo);
        }

        return new DefaultFieldDeserializer(clazz, fieldInfo);
    }

    public void putDeserializer(Type type, ObjectDeserializer deserializer) {
        Type mixin = JSON.getMixInAnnotations(type);
        if (mixin != null) {
            IdentityHashMap<Type, ObjectDeserializer> mixInClasses = this.mixInDeserializers.get(type);
            if (mixInClasses == null) {
                //多线程下可能会重复创建，但不影响正确性
                mixInClasses = new IdentityHashMap<>(4);
                this.mixInDeserializers.put(type, mixInClasses);
            }
            mixInClasses.put(mixin, deserializer);
        } else {
            this.deserializers.put(type, deserializer);
        }
    }

    public ObjectDeserializer get(Type type) {
        Type mixin = JSON.getMixInAnnotations(type);
        if (null == mixin) {
            return this.deserializers.get(type);
        }
        IdentityHashMap<Type, ObjectDeserializer> mixInClasses = this.mixInDeserializers.get(type);
        if (mixInClasses == null) {
            return null;
        }
        return mixInClasses.get(mixin);
    }

    public ObjectDeserializer getDeserializer(FieldInfo fieldInfo) {
        return getDeserializer(fieldInfo.fieldClass, fieldInfo.fieldType);
    }

    public boolean isPrimitive(Class<?> clazz) {
        return isPrimitive2(clazz);
    }

    private static Function<Class<?>, Boolean> isPrimitiveFuncation = new Function<Class<?>, Boolean>() {
        public Boolean apply(Class<?> clazz) {
            return clazz == java.sql.Date.class //
                    || clazz == java.sql.Time.class //
                    || clazz == java.sql.Timestamp.class;
        }
    };

    public static boolean isPrimitive2(final Class<?> clazz) {
        Boolean primitive = clazz.isPrimitive() //
                || clazz == Boolean.class //
                || clazz == Character.class //
                || clazz == Byte.class //
                || clazz == Short.class //
                || clazz == Integer.class //
                || clazz == Long.class //
                || clazz == Float.class //
                || clazz == Double.class //
                || clazz == BigInteger.class //
                || clazz == BigDecimal.class //
                || clazz == String.class //
                || clazz == java.util.Date.class //
                || clazz.isEnum() //
                ;
        if (Boolean.FALSE.equals(primitive)) {
            primitive = ModuleUtil.callWhenHasJavaSql(isPrimitiveFuncation, clazz);
        }
        return primitive != null && primitive;
    }

    /**
     * fieldName,field ，先生成fieldName的快照，减少之后的findField的轮询
     *
     * @param clazz
     * @param fieldCacheMap :map&lt;fieldName ,Field&gt;
     */
    public static void  parserAllFieldToCache(Class<?> clazz,Map</**fieldName*/String , Field> fieldCacheMap){
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            if (!fieldCacheMap.containsKey(fieldName)) {
                fieldCacheMap.put(fieldName, field);
            }
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            parserAllFieldToCache(clazz.getSuperclass(), fieldCacheMap);
        }
    }

    public static Field getFieldFromCache(String fieldName, Map<String, Field> fieldCacheMap) {
        Field field = fieldCacheMap.get(fieldName);

        field = extracted7(fieldName, fieldCacheMap, field);

        field = extracted6(fieldName, fieldCacheMap, field);

        field = extracted5(fieldName, fieldCacheMap, field);

        return field;
    }

    private static Field extracted7(String fieldName, Map<String, Field> fieldCacheMap, Field field) {
        if (field == null) {
            field = fieldCacheMap.get("_" + fieldName);
        }
        return field;
    }

    private static Field extracted6(String fieldName, Map<String, Field> fieldCacheMap, Field field) {
        if (field == null) {
            field = fieldCacheMap.get("m_" + fieldName);
        }
        return field;
    }

    private static Field extracted5(String fieldName, Map<String, Field> fieldCacheMap, Field field) {
        if (field == null) {
            char c0 = fieldName.charAt(0);
            field = extracted(fieldName, fieldCacheMap, field, c0);

            field = extracted4(fieldName, fieldCacheMap, field, c0);
        }
        return field;
    }

    private static Field extracted4(String fieldName, Map<String, Field> fieldCacheMap, Field field, char c0) {
        if (fieldName.length() > 2) {
            char c1 = fieldName.charAt(1);
            field = extracted3(fieldName, fieldCacheMap, field, c0, c1);
        }
        return field;
    }

    private static Field extracted3(String fieldName, Map<String, Field> fieldCacheMap, Field field, char c0, char c1) {
        if (c0 >= 'a' && c0 <= 'z'
                && c1 >= 'A' && c1 <= 'Z') {
            field = extracted2(fieldName, fieldCacheMap, field);
        }
        return field;
    }

    private static Field extracted2(String fieldName, Map<String, Field> fieldCacheMap, Field field) {
        for (Map.Entry<String, Field> entry : fieldCacheMap.entrySet()) {
            if (fieldName.equalsIgnoreCase(entry.getKey())) {
                field = entry.getValue();
                break;
            }
        }
        return field;
    }

    private static Field extracted(String fieldName, Map<String, Field> fieldCacheMap, Field field, char c0) {
        if (c0 >= 'a' && c0 <= 'z') {
            char[] chars = fieldName.toCharArray();
            chars[0] -= 32; // lower
            String fieldNameX = new String(chars);
            field = fieldCacheMap.get(fieldNameX);
        }
        return field;
    }

    public ClassLoader getDefaultClassLoader() {
        return defaultClassLoader;
    }

    public void setDefaultClassLoader(ClassLoader defaultClassLoader) {
        this.defaultClassLoader = defaultClassLoader;
    }

    public void addDenyInternal(String name) {
        if (name == null || name.length() == 0) {
            return;
        }

        long hash = TypeUtils.fnv1a64(name);
        if (internalDenyHashCodes == null) {
            this.internalDenyHashCodes = new long[] {hash};
            return;
        }

        if (Arrays.binarySearch(this.internalDenyHashCodes, hash) >= 0) {
            return;
        }

        long[] hashCodes = new long[this.internalDenyHashCodes.length + 1];
        hashCodes[hashCodes.length - 1] = hash;
        System.arraycopy(this.internalDenyHashCodes, 0, hashCodes, 0, this.internalDenyHashCodes.length);
        Arrays.sort(hashCodes);
        this.internalDenyHashCodes = hashCodes;
    }

    public void addDeny(String name) {
        if (name == null || name.length() == 0) {
            return;
        }

        long hash = TypeUtils.fnv1a64(name);
        if (Arrays.binarySearch(new List[]{this.denyHashCodes}, hash) >= 0) {
            return;
        }

        List<Long> hashCodes = new ArrayList<>();
        hashCodes.set(hashCodes.size() - 1, hash);
        System.arraycopy(this.denyHashCodes, 0, hashCodes, 0, this.denyHashCodes.size());
        this.denyHashCodes = hashCodes;
    }

    public void addAccept(String name) {
        if (name == null || name.length() == 0) {
            return;
        }

        long hash = TypeUtils.fnv1a64(name);
        if (Arrays.binarySearch(this.acceptHashCodes, hash) >= 0) {
            return;
        }

        long[] hashCodes = new long[this.acceptHashCodes.length + 1];
        hashCodes[hashCodes.length - 1] = hash;
        System.arraycopy(this.acceptHashCodes, 0, hashCodes, 0, this.acceptHashCodes.length);
        Arrays.sort(hashCodes);
        this.acceptHashCodes = hashCodes;
    }

    public <T> Class<T> checkAutoType(Class<T> type) {
        if (get(type) != null) {
            return type;
        }

        return (Class<T>) checkAutoType(type.getName(), null, JSON.DEFAULT_PARSER_FEATURE);
    }

    public Class<?> checkAutoType(String typeName, Class<?> expectClass) {
        return checkAutoType(typeName, expectClass, JSON.DEFAULT_PARSER_FEATURE);
    }

    public Class<?> checkAutoType(String typeName, Class<?> expectClass, int features) {
        if (typeName == null) {
            return null;
        }

        final int safeModeMask = Feature.SAFE_MODE.mask;
        boolean safeMode2 = extracted8(features, safeModeMask);
        extracted27(typeName, safeMode2);

        extracted28(typeName);

        final boolean expectClassFlag = extracted31(expectClass);

        String className = typeName.replace('$', '.');
        Class<?> clazz;

        final long h1 = (FNV1A_64_MAGIC_HASHCODE ^ className.charAt(0)) * FNV1A_64_MAGIC_PRIME;
        extracted32(typeName, h1);

        extracted33(typeName, className, h1);

        final long h3 = (((((FNV1A_64_MAGIC_HASHCODE ^ className.charAt(0))
                * FNV1A_64_MAGIC_PRIME)
                ^ className.charAt(1))
                * FNV1A_64_MAGIC_PRIME)
                ^ className.charAt(2))
                * FNV1A_64_MAGIC_PRIME;

        long fullHash = TypeUtils.fnv1a64(className);
        boolean internalWhite = Arrays.binarySearch(INTERNAL_WHITELIST_HASHCODES,  fullHash) >= 0;

        extracted35(typeName, className, h3);

        clazz = TypeUtils.getClassFromMapping(typeName);

        clazz = extracted37(typeName, clazz);

        clazz = extracted38(typeName, clazz);

        clazz = extracted39(typeName, clazz, internalWhite);

        if (clazz != null) {
            extracted41(typeName, expectClass, clazz);

            return clazz;
        }

        boolean jsonType = extracted46(typeName);

        final int mask = Feature.SUPPORT_AUTO_TYPE.mask;
        boolean autoTypeSupporto = extracted47(features, mask);

        clazz = extracted48(typeName, expectClassFlag, clazz, jsonType, autoTypeSupporto);

        if (extracted56(clazz, jsonType)) {
            TypeUtils.addMapping(typeName, clazz);

            extracted50(typeName, clazz);

            if (expectClass != null) {
                return extracted51(typeName, expectClass, clazz);
            }

            extracted52(typeName, clazz, autoTypeSupport);
        }

        extracted53(typeName, autoTypeSupport);

        extracted54(typeName, clazz);

        return clazz;
    }

    private boolean extracted56(Class<?> clazz, boolean jsonType) {
        return clazz != null && jsonType;
    }

    private void extracted54(String typeName, Class<?> clazz) {
        if (clazz != null) {
            TypeUtils.addMapping(typeName, clazz);
        }
    }

    private void extracted53(String typeName, boolean autoTypeSupport) {
        if (!autoTypeSupport) {
            throw new JSONException(AUTOTYPE_IS_NOT_SUPPORT + typeName);
        }
    }

    private void extracted52(String typeName, Class<?> clazz, boolean autoTypeSupport) {
        JavaBeanInfo beanInfo = JavaBeanInfo.build(clazz, clazz, propertyNamingStrategy);
        if (beanInfo.creatorConstructor != null && autoTypeSupport) {
            throw new JSONException(AUTOTYPE_IS_NOT_SUPPORT + typeName);
        }
    }

    private Class<?> extracted51(String typeName, Class<?> expectClass, Class<?> clazz) {
        if (expectClass.isAssignableFrom(clazz)) {
            TypeUtils.addMapping(typeName, clazz);
            return clazz;
        } else {
            throw new JSONException(TYPE_NOT_MATCH + typeName + " -> " + expectClass.getName());
        }
    }

    private void extracted50(String typeName, Class<?> clazz) {
        if (extracted49(clazz)
        ) {
            throw new JSONException(AUTOTYPE_IS_NOT_SUPPORT + typeName);
        }
    }

    private boolean extracted49(Class<?> clazz) {
        return ClassLoader.class.isAssignableFrom(clazz) // classloader is danger
                || javax.sql.DataSource.class.isAssignableFrom(clazz) // dataSource can load jdbc driver
                || javax.sql.RowSet.class.isAssignableFrom(clazz) //
                ;
    }

    private Class<?> extracted48(String typeName, final boolean expectClassFlag, Class<?> clazz, boolean jsonType,
                                 boolean autoTypeSupport) {
        if (autoTypeSupport || jsonType || expectClassFlag) {
            boolean cacheClass = autoTypeSupport || jsonType;
            clazz = TypeUtils.loadClass(typeName, defaultClassLoader, cacheClass);
        }
        return clazz;
    }

    private boolean extracted47(int features, final int mask) {
        return this.autoTypeSupport
                || (features & mask) != 0
                || (JSON.DEFAULT_PARSER_FEATURE & mask) != 0;
    }

    private boolean extracted46(String typeName) {
        boolean jsonType = false;
        InputStream is = null;
        try {
            is = extracted44(typeName);
            jsonType = extracted45(jsonType, is);
        } catch (Exception e) {
            // skip
        } finally {
            IOUtils.close(is);
        }
        return jsonType;
    }

    private boolean extracted45(boolean jsonType, InputStream is) throws IOException {
        if (is != null) {
            ClassReader classReader = new ClassReader(is, true);
            TypeCollector visitor = new TypeCollector("<clinit>", new Class[0]);
            classReader.accept(visitor);
            jsonType = visitor.hasJsonType();
        }
        return jsonType;
    }

    private InputStream extracted44(String typeName) {
        InputStream is;
        String resource = typeName.replace('.', '/') + ".class";
        if (defaultClassLoader != null) {
            is = defaultClassLoader.getResourceAsStream(resource);
        } else {
            is = ParserConfig.class.getClassLoader().getResourceAsStream(resource);
        }
        return is;
    }

    private void extracted41(String typeName, Class<?> expectClass, Class<?> clazz) {
        if (extracted40(expectClass, clazz)) {
            throw new JSONException(TYPE_NOT_MATCH + typeName + " -> " + expectClass.getName());
        }
    }

    private boolean extracted40(Class<?> expectClass, Class<?> clazz) {
        return expectClass != null
                && clazz != java.util.HashMap.class
                && clazz != java.util.LinkedHashMap.class
                && !expectClass.isAssignableFrom(clazz);
    }

    private Class<?> extracted39(String typeName, Class<?> clazz, boolean internalWhite) {
        if (internalWhite) {
            clazz = TypeUtils.loadClass(typeName, defaultClassLoader, true);
        }
        return clazz;
    }

    private Class<?> extracted38(String typeName, Class<?> clazz) {
        if (clazz == null) {
            clazz = typeMapping.get(typeName);
        }
        return clazz;
    }

    private Class<?> extracted37(String typeName, Class<?> clazz) {
        if (clazz == null) {
            clazz = deserializers.findClass(typeName);
        }
        return clazz;
    }

    private void extracted35(String typeName, String className, final long h3) {
        if (internalDenyHashCodes != null) {
            long hash = h3;
            extracted34(typeName, className, hash);
        }
    }

    private void extracted34(String typeName, String className, long hash) {
        for (int i = 3; i < className.length(); ++i) {
            hash ^= className.charAt(i);
            hash *= FNV1A_64_MAGIC_PRIME;
            if (Arrays.binarySearch(internalDenyHashCodes, hash) >= 0) {
                throw new JSONException(AUTOTYPE_IS_NOT_SUPPORT + typeName);
            }
        }
    }

    private void extracted33(String typeName, String className, final long h1) {
        if ((h1 ^ className.charAt(className.length() - 1)) * FNV1A_64_MAGIC_PRIME == 0x9198507b5af98f0L) {
            throw new JSONException(AUTOTYPE_IS_NOT_SUPPORT + typeName);
        }
    }

    private void extracted32(String typeName, final long h1) {
        if (h1 == 0xaf64164c86024f1aL) { // [
            throw new JSONException(AUTOTYPE_IS_NOT_SUPPORT + typeName);
        }
    }

    private boolean extracted31(Class<?> expectClass) {
        final boolean expectClassFlag;
        if (expectClass == null) {
            expectClassFlag = false;
        } else {
            expectClassFlag = extracted30(expectClass);
        }
        return expectClassFlag;
    }

    private boolean extracted30(Class<?> expectClass) {
        final boolean expectClassFlag;
        long expectHash = TypeUtils.fnv1a64(expectClass.getName());
        if (extracted29(expectHash)) {
            expectClassFlag = false;
        } else {
            expectClassFlag = true;
        }
        return expectClassFlag;
    }

    private boolean extracted29(long expectHash) {
        return expectHash == 0x90a25f5baa21529eL
                || expectHash == 0x2d10a5801b9d6136L
                || expectHash == 0xaf586a571e302c6bL
                || expectHash == 0xed007300a7b227c6L
                || expectHash == 0x295c4605fd1eaa95L
                || expectHash == 0x47ef269aadc650b4L
                || expectHash == 0x6439c4dff712ae8bL
                || expectHash == 0xe3dd9875a2dc5283L
                || expectHash == 0xe2a8ddba03e69e0dL
                || expectHash == 0xd734ceb4c3e9d1daL;
    }

    private void extracted28(String typeName) {
        if (typeName.length() >= 192 || typeName.length() < 3) {
            throw new JSONException(AUTOTYPE_IS_NOT_SUPPORT + typeName);
        }
    }

    private void extracted27(String typeName, boolean safeMode) {
        if (safeMode) {
            throw new JSONException("safeMode not support autoType : " + typeName);
        }
    }

    private boolean extracted8(int features, final int safeModeMask) {
        return this.safeMode
                || (features & safeModeMask) != 0
                || (JSON.DEFAULT_PARSER_FEATURE & safeModeMask) != 0;
    }

    public void clearDeserializers() {
        this.deserializers.clear();
        this.initDeserializers();
    }

    public boolean isJacksonCompatible() {
        return jacksonCompatible;
    }

    public void setJacksonCompatible(boolean jacksonCompatible) {
        this.jacksonCompatible = jacksonCompatible;
    }

    public <T> void register(String typeName, Class<T> type) {
        typeMapping.putIfAbsent(typeName, type);
    }

    public void register(Module module) {
        this.modules.add(module);
    }

    public void addAutoTypeCheckHandler(AutoTypeCheckHandler h) {
        List<AutoTypeCheckHandler> autoTypeCheckHandlers2 = this.autoTypeCheckHandlers;
        if (autoTypeCheckHandlers2 == null) {
            this.autoTypeCheckHandlers
                    = autoTypeCheckHandlers2
                    = new CopyOnWriteArrayList<>();
        }

        autoTypeCheckHandlers2.add(h);
    }

    /**
     * @since 1.2.68
     */
    public interface AutoTypeCheckHandler {
        Class<?> handler(String typeName, Class<?> expectClass, int features);
    }
}