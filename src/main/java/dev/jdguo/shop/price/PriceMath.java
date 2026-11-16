package dev.jdguo.shop.price;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.UnaryOperator;

/**
 * Quotes edges of one price curve: buy at P then move to P*(1+step); sell first
 * moves to P/(1+step), then quotes that edge. Thus the reverse trade traverses
 * the same prices, rather than paying the new, higher price back to a buyer.
 * Curve state has 34 significant digits; only payable amounts use four decimals.
 */
public final class PriceMath {
    public static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private PriceMath() {}

    public static PricePath buy(BigDecimal start, BigDecimal step, BigDecimal floor, int count) {
        return curve(start, step, floor, count, true, BigDecimal.ZERO, UnaryOperator.identity(), null).path();
    }
    public static PricePath sell(BigDecimal start, BigDecimal step, BigDecimal floor, int count) {
        return curve(start, step, floor, count, false, BigDecimal.ZERO, UnaryOperator.identity(), null).path();
    }

    /** Spread is (ask-bid)/center at the same execution edge. Conversion and rounding
     * are per item, so splitting a batch cannot improve rounding. */
    public static Execution execution(BigDecimal start, BigDecimal step, BigDecimal floor, int count,
                                      boolean buying, BigDecimal spread, UnaryOperator<BigDecimal> center,
                                      BigDecimal unitsPerDiamond) {
        if (spread == null || spread.signum() <= 0 || spread.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("spread");
        return curve(start, step, floor, count, buying, spread, center, unitsPerDiamond);
    }

    private static Execution curve(BigDecimal start, BigDecimal step, BigDecimal floor, int count,
                                   boolean buying, BigDecimal spread, UnaryOperator<BigDecimal> center,
                                   BigDecimal rate) {
        if (count < 1) throw new IllegalArgumentException("count");
        if (step == null || step.signum() <= 0 || step.compareTo(BigDecimal.ONE) >= 0)
            throw new IllegalArgumentException("step");
        if (floor == null || floor.signum() <= 0) throw new IllegalArgumentException("floor");
        BigDecimal price = start == null ? floor : start.max(floor);
        BigDecimal factor = BigDecimal.ONE.add(step);
        BigDecimal half = spread.divide(new BigDecimal("2"));
        BigDecimal side = buying ? BigDecimal.ONE.add(half) : BigDecimal.ONE.subtract(half);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = rate == null || rate.signum() <= 0 ? null : BigDecimal.ZERO;
        BigDecimal first = null;
        for (int i = 0; i < count; i++) {
            if (!buying) price = price.divide(factor, MC).max(floor);
            BigDecimal quoted = center.apply(price).multiply(side, MC);
            if (first == null) first = quoted;
            total = total.add(quoted);
            if (paid != null) paid = paid.add(charge(quoted, rate, buying));
            if (buying) price = price.multiply(factor, MC);
        }
        return new Execution(new PricePath(total, normalize(price)), paid, first);
    }

    /** Informational midpoint conversion only. Executions use the directional overload. */
    public static BigDecimal charge(BigDecimal diamondTotal, BigDecimal unitsPerDiamond) {
        if (diamondTotal == null || unitsPerDiamond == null || unitsPerDiamond.signum() <= 0) return null;
        return MoneyScale.quantize(diamondTotal.max(BigDecimal.ZERO).multiply(unitsPerDiamond, MC));
    }
    public static BigDecimal charge(BigDecimal diamondTotal, BigDecimal unitsPerDiamond, boolean buying) {
        if (diamondTotal == null || unitsPerDiamond == null || unitsPerDiamond.signum() <= 0) return null;
        return diamondTotal.max(BigDecimal.ZERO).multiply(unitsPerDiamond, MC)
                .setScale(MoneyScale.PLACES, buying ? RoundingMode.CEILING : RoundingMode.FLOOR);
    }
    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.round(MC).stripTrailingZeros();
    }
    public record Execution(PricePath path, BigDecimal charge, BigDecimal first) {}
}
