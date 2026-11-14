package dev.jdguo.shop.price;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Full digits only. Chat never shortens a number with a suffix. */
public final class PlainNumbers {
    private PlainNumbers() {}

    public static String amount(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal scaled = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.scale() < 0) {
            scaled = scaled.setScale(0);
        }
        return scaled.toPlainString();
    }

    public static String whole(BigInteger value) {
        return value == null ? "0" : value.toString();
    }
}
