package com.nanu.aitradingbot;

import java.math.BigInteger;
import java.util.Arrays;

public class Base58 {
    private static final char[] ALPHABET =
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final BigInteger BASE = BigInteger.valueOf(58);

    public static String encode(byte[] input) {
        BigInteger num = new BigInteger(1, input);
        StringBuilder sb = new StringBuilder();
        while (num.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divRem = num.divideAndRemainder(BASE);
            sb.append(ALPHABET[divRem[1].intValue()]);
            num = divRem[0];
        }
        for (byte b : input) {
            if (b == 0) sb.append(ALPHABET[0]); else break;
        }
        return sb.reverse().toString();
    }
}
