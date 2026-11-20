package dev.jdguo.shop.price;

import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.catalog.CatalogSupply;

import java.math.BigDecimal;

/** Monotone inventory curve. Buy and sell traverse the same unit edges. */
public final class StockPricing {
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal NORMAL_DEFICIT = new BigDecimal("0.05");
    private static final BigDecimal MAX_GLUT = new BigDecimal("0.03");
    private static final BigDecimal LOW_STOCK_EXTRA = new BigDecimal("0.13");
    private static final BigDecimal MAX_FLOW_MOVE = new BigDecimal("0.04");
    private static final BigDecimal DEFAULT_STEP = new BigDecimal("0.002");
    private static final long SLOT_MS = 300_000L; // 5 minutes; a half-life is two slots.
    public static long slot(long timeMs) { return Math.floorDiv(Math.max(0, timeMs), SLOT_MS); }

    public static BigDecimal decay(BigDecimal recent, long fromSlot, long nowSlot) {
        long elapsed = Math.max(0, nowSlot - fromSlot);
        if (elapsed == 0 || recent.signum() == 0) return recent;
        if (elapsed >= 96) return BigDecimal.ZERO;
        return recent.multiply(BigDecimal.valueOf(StrictMath.pow(0.5, elapsed / 2.0)), PriceMath.MC)
                .setScale(9, java.math.RoundingMode.HALF_EVEN).stripTrailingZeros();
    }

    private StockPricing() {}

    /** Baseline from typical world supply; add up to one baseline of proven demand. */
    public static int target(String itemId, BigDecimal anchor, long recentUnits) {
        int base = CatalogSupply.baseStock(itemId, anchor);
        return base + (int) Math.min(base, Math.max(0, recentUnits) / 4);
    }

    /** Level is the number present before one item is sold by the shop. */
    public static BigDecimal midpoint(BigDecimal base, int target, long level, ShopConfig config) {
        return midpoint(base, target, level, config, BigDecimal.ZERO);
    }

    public static BigDecimal midpoint(BigDecimal base, int target, long level, ShopConfig config, BigDecimal recent) {
        if (level < 0 || target < 1) throw new IllegalArgumentException("stock");
        BigDecimal desired = BigDecimal.valueOf(target);
        BigDecimal actual = BigDecimal.valueOf(level);
        BigDecimal sensitivity = config.stepFraction().divide(DEFAULT_STEP, PriceMath.MC).min(BigDecimal.ONE);
        BigDecimal impact = level <= target
                ? desired.subtract(actual).divide(desired, PriceMath.MC).multiply(NORMAL_DEFICIT)
                : desired.subtract(actual).divide(desired.add(actual), PriceMath.MC).multiply(MAX_GLUT);
        long low = Math.max(2L, (target + 9L) / 10L);
        if (level < low) {
            BigDecimal shortage = BigDecimal.valueOf(low - level).divide(BigDecimal.valueOf(low), PriceMath.MC);
            impact = impact.add(shortage.multiply(shortage, PriceMath.MC).multiply(LOW_STOCK_EXTRA));
        }
        // Thin inventory dampens the discount from recent selling. Outflow remains upward pressure.
        BigDecimal flowWeight = recent.signum() > 0 && level < low
                ? actual.divide(BigDecimal.valueOf(low), PriceMath.MC) : BigDecimal.ONE;
        BigDecimal rush = recent.divide(recent.abs().add(desired), PriceMath.MC).multiply(MAX_FLOW_MOVE).multiply(flowWeight);
        return base.multiply(BigDecimal.ONE.add(impact.subtract(rush).multiply(sensitivity)), PriceMath.MC)
                .max(config.minDiamondPrice());
    }

    public static PriceMath.Execution execution(BigDecimal base, int target, long stock, int count,
                                                boolean buying, ShopConfig config, BigDecimal marketInDiamonds,
                                                BigDecimal paymentRate) {
        return execution(base, target, stock, count, buying, config, marketInDiamonds, paymentRate, BigDecimal.ZERO);
    }

    public static PriceMath.Execution execution(BigDecimal base, int target, long stock, int count,
                                                boolean buying, ShopConfig config, BigDecimal marketInDiamonds,
                                                BigDecimal paymentRate, BigDecimal recent) {
        if (count < 1 || stock < 0 || (buying && stock < count)) throw new IllegalArgumentException("stock/count");
        long endStock = Math.addExact(stock, buying ? -(long) count : count);
        BigDecimal spread = config.spreadFraction();
        BigDecimal half = spread.divide(TWO);
        BigDecimal side = buying ? BigDecimal.ONE.add(half) : BigDecimal.ONE.subtract(half);
        BigDecimal first = null;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = paymentRate == null || paymentRate.signum() <= 0 ? null : BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            long level = buying ? stock - i : stock + i + 1;
            BigDecimal edgeFlow = recent.add(BigDecimal.valueOf(buying ? -(long)i : i + 1L));
            BigDecimal center = midpoint(base, target, level, config, edgeFlow);
            if (marketInDiamonds != null) center = MarketInfluence.blend(center, marketInDiamonds, spread);
            BigDecimal unit = center.multiply(side, PriceMath.MC);
            if (first == null) first = unit;
            total = total.add(unit);
            if (paid != null) paid = paid.add(PriceMath.charge(unit, paymentRate, buying));
        }
        BigDecimal endFlow = recent.add(BigDecimal.valueOf(buying ? -(long) count : count));
        BigDecimal end = midpoint(base, target, endStock, config, endFlow);
        if (marketInDiamonds != null) end = MarketInfluence.blend(end, marketInDiamonds, spread);
        return new PriceMath.Execution(new PricePath(total, end), paid, first);
    }
}
