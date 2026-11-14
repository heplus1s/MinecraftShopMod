package dev.jdguo.shop.price;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shop money is kept at four decimal places. Comparison and posting use that scale.
 * A whole number may be shown without a decimal tail, and zero is shown as {@code 0}.
 */
public final class MoneyScale {
    public static final int PLACES = 4;
    public static final RoundingMode ROUND = RoundingMode.HALF_UP;

    private MoneyScale() {}

    public static BigDecimal quantize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(PLACES, ROUND);
    }

    /** Value stored after a price step. Still equal to the four-decimal figure. */
    public static BigDecimal normalize(BigDecimal value) {
        BigDecimal scaled = quantize(value);
        if (scaled == null) {
            return null;
        }
        if (scaled.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal stripped = scaled.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return new BigDecimal(stripped.toPlainString());
    }

    public static String display(BigDecimal value) {
        if (value == null || quantize(value).signum() == 0) {
            return "0";
        }
        return normalize(value).toPlainString();
    }

    public static boolean positive(BigDecimal value) {
        return value != null && quantize(value).signum() > 0;
    }
}
