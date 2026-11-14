package dev.jdguo.shop.price;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Each traded item takes one small step off the current diamond-equivalent price.
 * Buying multiplies by {@code 1 + step}. Selling multiplies by {@code 1 - step}.
 * Nothing pulls the price back to the anchor. The floor only stops a price at zero.
 */
public final class PriceMath {
    public static final MathContext MC = new MathContext(34, RoundingMode.HALF_UP);

    private PriceMath() {}

    public static PricePath buy(BigDecimal start, BigDecimal step, BigDecimal floor, int count) {
        return walk(start, step, floor, count, true);
    }

    public static PricePath sell(BigDecimal start, BigDecimal step, BigDecimal floor, int count) {
        return walk(start, step, floor, count, false);
    }

    /**
     * Currency amount for a diamond total, at four decimal places.
     * Does not round up to a whole unit. Returns null when the rate is missing,
     * and zero when the four-decimal result is zero.
     */
    public static BigDecimal charge(BigDecimal diamondTotal, BigDecimal unitsPerDiamond) {
        if (diamondTotal == null || unitsPerDiamond == null || unitsPerDiamond.signum() <= 0) {
            return null;
        }
        if (diamondTotal.signum() <= 0) {
            return MoneyScale.quantize(BigDecimal.ZERO);
        }
        return MoneyScale.quantize(diamondTotal.multiply(unitsPerDiamond, MC));
    }

    public static BigDecimal normalize(BigDecimal value) {
        return MoneyScale.normalize(value);
    }

    private static PricePath walk(BigDecimal start, BigDecimal step, BigDecimal floor, int count, boolean buying) {
        if (count < 1) {
            throw new IllegalArgumentException("count");
        }
        if (step == null || step.signum() <= 0 || step.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("step");
        }
        if (floor == null || floor.signum() <= 0) {
            throw new IllegalArgumentException("floor");
        }
        BigDecimal price = MoneyScale.quantize(start);
        BigDecimal floorQ = MoneyScale.quantize(floor);
        if (price == null || (floorQ != null && price.compareTo(floorQ) < 0)) {
            price = floorQ == null ? MoneyScale.quantize(BigDecimal.ZERO) : floorQ;
        }
        if (!MoneyScale.positive(price)) {
            BigDecimal zero = MoneyScale.quantize(BigDecimal.ZERO);
            return new PricePath(zero, zero);
        }
        BigDecimal total = MoneyScale.quantize(BigDecimal.ZERO);
        BigDecimal factor = buying ? BigDecimal.ONE.add(step, MC) : BigDecimal.ONE.subtract(step, MC);
        for (int i = 0; i < count; i++) {
            total = MoneyScale.quantize(total.add(price, MC));
            BigDecimal raw = price.multiply(factor, MC);
            if (floor != null && raw.compareTo(floor) < 0) {
                raw = floor;
            }
            BigDecimal next = MoneyScale.quantize(raw);
            if (!MoneyScale.positive(next)) {
                next = price;
            }
            price = next;
        }
        return new PricePath(total, normalize(price));
    }
}
