package dev.jdguo.shop.price;

import java.math.BigDecimal;
import java.math.BigInteger;
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

    public static BigDecimal notesExact(BigDecimal diamondTotal, BigDecimal notesPerDiamond) {
        return diamondTotal.multiply(notesPerDiamond, MC);
    }

    /** Whole settlement units. Null when the exact amount is still below one unit. */
    public static BigInteger notesDue(BigDecimal exact) {
        if (exact == null || exact.compareTo(BigDecimal.ONE) < 0) {
            return null;
        }
        return exact.setScale(0, RoundingMode.CEILING).toBigInteger();
    }

    /**
     * Smallest count whose diamond total converts to at least one settlement unit.
     * Returns {@code -1} when even {@code maxCount} stays below one unit.
     */
    public static int minimumCount(BigDecimal start, BigDecimal step, BigDecimal floor, int maxCount,
                                    BigDecimal notesPerDiamond, boolean buying) {
        if (maxCount < 1 || notesPerDiamond == null || notesPerDiamond.signum() <= 0) {
            return -1;
        }
        if (covers(start, step, floor, 1, notesPerDiamond, buying)) {
            return 1;
        }
        if (!covers(start, step, floor, maxCount, notesPerDiamond, buying)) {
            return -1;
        }
        int low = 2;
        int high = maxCount;
        int answer = maxCount;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (covers(start, step, floor, mid, notesPerDiamond, buying)) {
                answer = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return answer;
    }

    public static BigDecimal normalize(BigDecimal value) {
        BigDecimal scaled = value.setScale(12, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.scale() < 0) {
            scaled = scaled.setScale(0);
        }
        return new BigDecimal(scaled.toPlainString());
    }

    private static boolean covers(BigDecimal start, BigDecimal step, BigDecimal floor, int count,
                                   BigDecimal notesPerDiamond, boolean buying) {
        PricePath path = buying ? buy(start, step, floor, count) : sell(start, step, floor, count);
        return notesExact(path.total(), notesPerDiamond).compareTo(BigDecimal.ONE) >= 0;
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
        BigDecimal price = start == null || start.compareTo(floor) < 0 ? floor : start;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal factor = buying ? BigDecimal.ONE.add(step, MC) : BigDecimal.ONE.subtract(step, MC);
        for (int i = 0; i < count; i++) {
            total = total.add(price, MC);
            BigDecimal next = price.multiply(factor, MC);
            if (next.compareTo(floor) < 0) {
                next = floor;
            }
            price = next;
        }
        return new PricePath(total, normalize(price));
    }
}
