// Copyright 2018 Ulf Adams
//


package com.alibaba.fastjson.util;

/**
 * An implementation of Ryu for float.
 */
public final class RyuFloat {
    private RyuFloat() {
        throw new IllegalStateException("Utility class");
    }
    private static final int[][] POW5_SPLIT = {
            {536870912, 0},
            {671088640, 0},
            {838860800, 0},
            {1048576000, 0},
            {655360000, 0},
            {819200000, 0},
            {1024000000, 0},
            {640000000, 0},
            {800000000, 0},
            {1000000000, 0},
            {625000000, 0},
            {781250000, 0},
            {976562500, 0},
            {610351562, 1073741824},
            {762939453, 268435456},
            {953674316, 872415232},
            {596046447, 1619001344},
            {745058059, 1486880768},
            {931322574, 1321730048},
            {582076609, 289210368},
            {727595761, 898383872},
            {909494701, 1659850752},
            {568434188, 1305842176},
            {710542735, 1632302720},
            {888178419, 1503507488},
            {555111512, 671256724},
            {693889390, 839070905},
            {867361737, 2122580455},
            {542101086, 521306416},
            {677626357, 1725374844},
            {847032947, 546105819},
            {1058791184, 145761362},
            {661744490, 91100851},
            {827180612, 1187617888},
            {1033975765, 1484522360},
            {646234853, 1196261931},
            {807793566, 2032198326},
            {1009741958, 1466506084},
            {631088724, 379695390},
            {788860905, 474619238},
            {986076131, 1130144959},
            {616297582, 437905143},
            {770371977, 1621123253},
            {962964972, 415791331},
            {601853107, 1333611405},
            {752316384, 1130143345},
            {940395480, 1412679181},
    };

    private static final int[][] POW5_INV_SPLIT = {
            {268435456, 1},
            {214748364, 1717986919},
            {171798691, 1803886265},
            {137438953, 1013612282},
            {219902325, 1192282922},
            {175921860, 953826338},
            {140737488, 763061070},
            {225179981, 791400982},
            {180143985, 203624056},
            {144115188, 162899245},
            {230584300, 1978625710},
            {184467440, 1582900568},
            {147573952, 1266320455},
            {236118324, 308125809},
            {188894659, 675997377},
            {151115727, 970294631},
            {241785163, 1981968139},
            {193428131, 297084323},
            {154742504, 1955654377},
            {247588007, 1840556814},
            {198070406, 613451992},
            {158456325, 61264864},
            {253530120, 98023782},
            {202824096, 78419026},
            {162259276, 1780722139},
            {259614842, 1990161963},
            {207691874, 733136111},
            {166153499, 1016005619},
            {265845599, 337118801},
            {212676479, 699191770},
            {170141183, 988850146},
    };

    public static String toString(float value) {
        char[] result = new char[15];
        int len = toString(value, result, 0);
        return new String(result, 0, len);
    }

    public static int toString(float value, char[] result, int off) {
        final int FLOAT_MANTISSA_MASK = 8388607;
        final int FLOAT_EXPONENT_MASK = 255;
        final int FLOAT_EXPONENT_BIAS = 127;
        final long LOG10_2_NUMERATOR = 3010299;
        final long LOG10_5_DENOMINATOR = 10000000L;
        final long LOG10_5_NUMERATOR = 6989700L;

        int bits = Float.floatToIntBits(value);

        if(isaBoolean(value, result, off)){
            return getIndex(value, result, off);
        }
        int index = 0;
        // Otherwise extract the mantissa and exponent bits and run the full algorithm.
        int ieeeExponent = (bits >> 23) & FLOAT_EXPONENT_MASK;
        int ieeeMantissa = bits & FLOAT_MANTISSA_MASK;
        // By default, the correct mantissa starts with a 1, except for denormal numbers.
        int e2;
        int m2;
        if (isaBoolean(ieeeExponent)) {
            e2 = 1 - FLOAT_EXPONENT_BIAS - 23;
            m2 = ieeeMantissa;
        } else {
            e2 = ieeeExponent - FLOAT_EXPONENT_BIAS - 23;
            m2 = ieeeMantissa | (1 << 23);
        }

        boolean sign = bits < 0;

        // Step 2: Determine the interval of legal decimal representations.
        boolean even = isaBoolean((m2 & 1));
        int mv = 4 * m2;
        int mp = 4 * m2 + 2;
        int mm = 4 * m2 - getAnInt(ieeeExponent, m2);
        e2 -= 2;

        // Step 3: Convert to a decimal power base using 128-bit arithmetic.
        // -151 = 1 - 127 - 23 - 2 <= e_2 - 2 <= 254 - 127 - 23 - 2 = 102
        int dp;
        int dv;
        int dm;
        int e10;
        boolean dpIsTrailingZeros;
        boolean dvIsTrailingZeros;
        boolean dmIsTrailingZeros;
        int lastRemovedDigit = 0;
        if (e2 >= 0) {
            // Compute m * 2^e_2 / 10^q = m * 2^(e_2 - q) / 5^q
            int q = (int) (e2 * LOG10_2_NUMERATOR / 10000000L);
            int k = 59 + (isaBoolean(q) ? 1 : (int) ((q * 23219280L + 10000000L - 1) / 10000000L)) - 1;
            int i = -e2 + q + k;
            long pis0 = POW5_INV_SPLIT[q][0];
            long pis1 = POW5_INV_SPLIT[q][1];
            dv = (int) ((mv * pis0 + ((mv * pis1) >> 31)) >> (i - 31));
            dp = (int) ((mp * pis0 + ((mp * pis1) >> 31)) >> (i - 31));
            dm = (int) ((mm * pis0 + ((mm * pis1) >> 31)) >> (i - 31));
            lastRemovedDigit = getRemovedDigit(e2, mv, dp, dm, lastRemovedDigit, q);
            e10 = q;

            int pow5FactorMp = getPow5FactorMp(mp);

            int pow5FactorMv = getPow5FactorMp(mv);

            int pow5FactorMm = getPow5FactorMp(mm);

            dpIsTrailingZeros = pow5FactorMp >= q;
            dvIsTrailingZeros = pow5FactorMv >= q;
            dmIsTrailingZeros = pow5FactorMm >= q;
        } else {
            // Compute m * 5^(-e_2) / 10^q = m * 5^(-e_2 - q) / 2^q
            int q = (int) (-e2 * LOG10_5_NUMERATOR / LOG10_5_DENOMINATOR);
            int i = -e2 - q;
            int k = (isaBoolean(i) ? 1 : (int) ((i * 23219280L + 10000000L - 1) / 10000000L)) - 61;
            int j = q - k;

            long ps0 = POW5_SPLIT[i][0];
            long ps1 = POW5_SPLIT[i][1];
            int j31 = j - 31;
            dv = (int) ((mv * ps0 + ((mv * ps1) >> 31)) >> j31);
            dp = (int) ((mp * ps0 + ((mp * ps1) >> 31)) >> j31);
            dm = (int) ((mm * ps0 + ((mm * ps1) >> 31)) >> j31);

            lastRemovedDigit = getLastRemovedDigit(mv, dp, dm, lastRemovedDigit, q, i);
            e10 = q + e2; // Note: e2 and e10 are both negative here.

            dpIsTrailingZeros = 1 >= q;
            dvIsTrailingZeros = (q < 23) && (mv & ((1 << (q - 1)) - 1)) == 0;
            dmIsTrailingZeros = (mm % 2 == 1 ? 0 : 1) >= q;
        }

        // Step 4: Find the shortest decimal representation in the interval of legal representations.
        //
        // We do some extra work here in order to follow Float/Double.toString semantics. In particular,
        // that requires printing in scientific format if and only if the exponent is between -3 and 7,
        // and it requires printing at least two decimal digits.
        //
        // Above, we moved the decimal dot all the way to the right, so now we need to count digits to
        // figure out the correct exponent for scientific notation.

        int dplength = 10;
        int exp = e10 + dplength - 1;

        // Float.toString semantics requires using scientific notation if and only if outside this range.
        boolean scientificNotation = !((exp >= -3) && (exp < 7));

        int removed = 0;
        dp = getDp(even, dp, dpIsTrailingZeros);

        lastRemoveDigit1(dp, dv, dm, dmIsTrailingZeros, scientificNotation, removed);
        lastRemoveDigit2(even, dp, dv, dm, dmIsTrailingZeros, scientificNotation, removed);

        lastRemovedDigit = getLastRemovedDigit(dv, dvIsTrailingZeros, lastRemovedDigit);
        int output = dv +
                getAnInt(even, dv, dm, dmIsTrailingZeros, lastRemovedDigit);
        int olength = dplength - removed;

        // Step 5: Print the decimal representation.
        // We follow Float.toString semantics here.
        index = getIndex(result, index, sign);

        index = getIndex(result, index, exp, scientificNotation, output, olength);
        return index - off;
    }

    private static int getAnInt(int ieeeExponent, int m2) {
        return (m2 != (1L << 23)) || (ieeeExponent <= 1) ? 2 : 1;
    }

    private static int getAnInt(boolean even, int dv, int dm, boolean dmIsTrailingZeros, int lastRemovedDigit) {
        return (dv == dm && !(dmIsTrailingZeros && even)) || (lastRemovedDigit >= 5) ? 1 : 0;
    }

    private static boolean isaBoolean(float value, char[] result, int off) {
        return getIndex(value, result, off) != off;
    }

    private static boolean isaBoolean(int ieeeExponent) {
        return ieeeExponent == 0;
    }

    private static int getRemovedDigit(int e2, int mv, int dp, int dm, int lastRemovedDigit, int q) {
        if (q != 0 && ((dp - 1) / 10 <= dm / 10)) {
            // We need to know one removed digit even if we are not going to loop below. We could use
            // q = X - 1 above, except that would require 33 bits for the result, and we've found that
            // 32-bit arithmetic is faster even on 64-bit machines.
            int e = q - 1;
            int l = 59 + (isaBoolean(e) ? 1 : (int) ((e * 23219280L + 10000000L - 1) / 10000000L)) - 1;
            int qx = q - 1;
            int ii = -e2 + q - 1 + l;
            long mulPow5InvDivPow2 =  (mv * (long) POW5_INV_SPLIT[qx][0] + ((mv * (long) POW5_INV_SPLIT[qx][1]) >> 31)) >> (ii - 31);
            lastRemovedDigit = (int) (mulPow5InvDivPow2 % 10);
        }
        return lastRemovedDigit;
    }

    private static int getPow5FactorMp(int mp) {
        int pow5factorMpVariabile = 0;
        pow5factorMpVariabile = getPow5FactorMv(mp, pow5factorMpVariabile);
        return pow5factorMpVariabile;
    }

    private static int getLastRemovedDigit(int dv, boolean dvIsTrailingZeros, int lastRemovedDigit) {
        if (dvIsTrailingZeros && (lastRemovedDigit == 5) && isaBoolean(dv % 2)) {
            // Round down not up if the number ends in X50000 and the number is even.
            lastRemovedDigit = 4;
        }
        return lastRemovedDigit;
    }

    private static int getIndex(char[] result, int index, int exp, boolean scientificNotation, int output, int olength) {
        if (scientificNotation) {
            index = getIndex1(result, index, exp, output, olength);
        } else {
            index = getAnInt(result, index, exp, output, olength);
        }
        return index;
    }

    private static int getIndex1(char[] result, int index, int exp, int output, int olength) {
        // Print in the format x.xxxxxE-yy.
        output = getOutput(result, index, output, olength);
        result[index] = (char) ('0' + output % 10);
        result[index + 1] = '.';
        index += olength + 1;
        index = getIndex(result, index, olength);

        // Print 'E', the exponent sign, and the exponent, which has at most two digits.
        result[index++] = 'E';
        exp =  getE(exp);
        index = getAnInt(result, index, exp);
        result[index++] = (char) ('0' + exp % 10);
        return index;
    }

    private static int getAnInt(char[] result, int index, int exp, int output, int olength) {
        // Otherwise follow the Java spec for values in the interval [1E-3, 1E7).
        if (exp < 0) {
            // Decimal dot is before any of the digits.
            result[index++] = '0';
            result[index++] = '.';
            index = getIndex1(result, index, exp);
            int current = index;
            index = getIndex(result, index, output, olength, current);
        } else if (exp + 1 >= olength) {
            // Decimal dot is after any of the digits.
            getResultIndex(result, index, output, olength);
            index += olength;
            index = getIndex(result, index, exp, olength);
            result[index++] = '.';
            result[index++] = '0';
        } else {
            // Decimal dot is somewhere between the digits.
            int current = index + 1;
            oLength(result, exp, output, olength, current);
            index += olength + 1;
        }
        return index;
    }

    private static int getIndex(char[] result, int index, int exp, int olength) {
        for (int i = olength; i < exp + 1; i++) {
            result[index++] = '0';
        }
        return index;
    }

    private static void getResultIndex(char[] result, int index, int output, int olength) {
        for (int i = 0; i < olength; i++) {
            result[index + olength - i - 1] = (char) ('0' + output % 10);
            output /= 10;
        }
    }

    private static int getIndex1(char[] result, int index, int exp) {
        for (int i = -1; i > exp; i--) {
            result[index++] = '0';
        }
        return index;
    }

    private static void oLength(char[] result, int exp, int output, int olength, int current) {
        for (int i = 0; i < olength; i++) {
            if (olength - i - 1 == exp) {
                result[current + olength - i - 1] = '.';
                current--;
            }
            result[current + olength - i - 1] = (char) ('0' + output % 10);
            output /= 10;
        }
    }

    private static int getIndex(char[] result, int index, int output, int olength, int current) {
        for (int i = 0; i < olength; i++) {
            result[current + olength - i - 1] = (char) ('0' + output % 10);
            output /= 10;
            index++;
        }
        return index;
    }

    private static int getE(int exp) {
        if (exp < 0) {
            exp = -exp;
        }
        return exp;
    }

    private static int getAnInt(char[] result, int index, int exp) {
        if (exp >= 10) {
            result[index++] = (char) ('0' + exp / 10);
        }
        return index;
    }

    private static int getOutput(char[] result, int index, int output, int olength) {
        for (int i = 0; i < olength - 1; i++) {
            int c = output % 10;
            output /= 10;
            result[index + olength - i] = (char) ('0' + c);
        }
        return output;
    }

    private static int getIndex(char[] result, int index, int olength) {
        if (olength == 1) {
            result[index++] = '0';
        }
        return index;
    }

    private static int getIndex(char[] result, int index, boolean sign) {
        if (sign) {
            result[index++] = '-';
        }
        return index;
    }

    private static int getDp(boolean even, int dp, boolean dpIsTrailingZeros) {
        if (dpIsTrailingZeros && !even) {
            dp--;
        }
        return dp;
    }

    private static void lastRemoveDigit2(boolean even, int dp, int dv, int dm, boolean dmIsTrailingZeros, boolean scientificNotation, int removed) {
        if (dmIsTrailingZeros && even) {
            while (isaBoolean(dm % 10)) {
                if ((dp < 100) && scientificNotation) {
                    // We print at least two digits, so we might as well stop now.
                    break;
                }
                dp /= 10;
                dv /= 10;
                dm /= 10;
                removed++;
            }
        }
    }

    private static void lastRemoveDigit1(int dp, int dv, int dm, boolean dmIsTrailingZeros, boolean scientificNotation, int removed) {
        while (dp / 10 > dm / 10) {
            if ((dp < 100) && scientificNotation) {
                // We print at least two digits, so we might as well stop now.
                break;
            }
            dmIsTrailingZeros &= isaBoolean(dm % 10);
            dp /= 10;
            dv /= 10;
            dm /= 10;
            removed++;
        }
    }

    private static int getLastRemovedDigit(int mv, int dp, int dm, int lastRemovedDigit, int q, int i) {
        int j;
        if (q != 0 && ((dp - 1) / 10 <= dm / 10)) {
            int e = i + 1;
            j = q - 1 - ((isaBoolean(e) ? 1 : (int) ((e * 23219280L + 10000000L - 1) / 10000000L)) - 61);
            int ix = i + 1;
            long mulPow5divPow2 = (mv * (long) POW5_SPLIT[ix][0] + ((mv * (long) POW5_SPLIT[ix][1]) >> 31)) >> (j - 31);
            lastRemovedDigit = (int) (mulPow5divPow2 % 10);
        }
        return lastRemovedDigit;
    }

    private static int getPow5FactorMv(int mv, int pow5FactorMv) {
        int v = mv;
        while (v > 0) {
            if (v % 5 != 0) {
                break;
            }
            v /= 5;
            pow5FactorMv++;
        }
        return pow5FactorMv;
    }

    private static int getIndex(float value, char[] result, int off) {
        // Step 1: Decode the floating point number, and unify normalized and subnormal cases.
        // First, handle all the trivial cases.
        int index = off;
        if (Float.isNaN(value)) {
            result[index++] = 'N';
            result[index++] = 'a';
            result[index++] = 'N';
            return index - off;
        }

        if (value == Float.POSITIVE_INFINITY) {
            result[index++] = 'I';
            result[index++] = 'n';
            result[index++] = 'f';
            result[index++] = 'i';
            result[index++] = 'n';
            result[index++] = 'i';
            result[index++] = 't';
            result[index++] = 'y';
            return index - off;
        }

        if (value == Float.NEGATIVE_INFINITY) {
            result[index++] = '-';
            result[index++] = 'I';
            result[index++] = 'n';
            result[index++] = 'f';
            result[index++] = 'i';
            result[index++] = 'n';
            result[index++] = 'i';
            result[index++] = 't';
            result[index++] = 'y';
            return index - off;
        }

        int bits = Float.floatToIntBits(value);
        if (isaBoolean(bits)) {
            result[index++] = '0';
            result[index++] = '.';
            result[index++] = '0';
            return index - off;
        }
        if (bits == 0x80000000) {
            result[index++] = '-';
            result[index++] = '0';
            result[index++] = '.';
            result[index++] = '0';
            return index - off;
        }
        return index;
    }

}
