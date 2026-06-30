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

    /** Decodes a Base58 string back to bytes. Throws on an invalid character. */
    public static byte[] decode(String input) {
        BigInteger num = BigInteger.ZERO;
        for (int i = 0; i < input.length(); i++) {
            int digit = indexOf(input.charAt(i));
            if (digit < 0)
                throw new IllegalArgumentException("Invalid Base58 char: " + input.charAt(i));
            num = num.multiply(BASE).add(BigInteger.valueOf(digit));
        }
        byte[] bytes = num.toByteArray();
        // Skip every leading 0x00 byte from the BigInteger encoding: the sign
        // byte for values whose MSB is set, and the lone 0x00 that toByteArray()
        // produces for zero. Leading zero *value* bytes are never in the numeric
        // portion — they are carried by the leading '1's below.
        int start = 0;
        while (start < bytes.length && bytes[start] == 0) start++;
        // Restore leading-zero bytes that Base58 encodes as leading '1's.
        int leadingZeros = 0;
        for (int i = 0; i < input.length() && input.charAt(i) == ALPHABET[0]; i++) leadingZeros++;
        byte[] out = new byte[leadingZeros + (bytes.length - start)];
        System.arraycopy(bytes, start, out, leadingZeros, bytes.length - start);
        return out;
    }

    private static int indexOf(char c) {
        for (int i = 0; i < ALPHABET.length; i++) if (ALPHABET[i] == c) return i;
        return -1;
    }
}
