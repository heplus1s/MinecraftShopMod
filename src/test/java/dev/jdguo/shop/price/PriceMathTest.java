package dev.jdguo.shop.price;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceMathTest {
    private static final BigDecimal STEP = new BigDecimal("0.002");
    private static final BigDecimal FLOOR = new BigDecimal("0.000000001");

    @Test
    void eachBuyStepIsAFractionOfTheCurrentPrice() {
        PricePath path = PriceMath.buy(new BigDecimal("1"), new BigDecimal("0.1"), FLOOR, 3);
        assertEquals(0, path.total().compareTo(new BigDecimal("3.31")));
        assertEquals(0, path.end().compareTo(new BigDecimal("1.331")));
    }

    @Test
    void sellsStepDownAndStopAtTheFloor() {
        PricePath path = PriceMath.sell(new BigDecimal("0.000000002"), new BigDecimal("0.5"), FLOOR, 5);
        assertEquals(0, path.end().compareTo(FLOOR));
        assertTrue(path.total().compareTo(FLOOR) > 0);
    }

    @Test
    void longRunBuysLeaveTheAnchorFarBehind() {
        BigDecimal price = new BigDecimal("1");
        for (int i = 0; i < 2000; i++) {
            price = PriceMath.buy(price, STEP, FLOOR, 1).end();
        }
        assertTrue(price.compareTo(new BigDecimal("20")) > 0, price.toPlainString());
        BigDecimal afterSell = PriceMath.sell(price, STEP, FLOOR, 1).end();
        assertTrue(afterSell.compareTo(new BigDecimal("20")) > 0);
        assertTrue(afterSell.compareTo(BigDecimal.ONE) > 0);
    }

    @Test
    void anEqualBuyAndSellDoesNotReturnToTheStart() {
        BigDecimal start = new BigDecimal("5");
        BigDecimal bought = PriceMath.buy(start, STEP, FLOOR, 1).end();
        BigDecimal sold = PriceMath.sell(bought, STEP, FLOOR, 1).end();
        assertTrue(sold.compareTo(start) != 0);
        assertTrue(sold.subtract(start).abs().compareTo(new BigDecimal("0.01")) < 0);
    }

    @Test
    void notesBelowOneUnitAreNotRoundedUpToAUnit() {
        BigDecimal exact = PriceMath.notesExact(new BigDecimal("0.06"), BigDecimal.ONE);
        assertNull(PriceMath.notesDue(exact));
        assertEquals(1, PriceMath.notesDue(new BigDecimal("1")).intValue());
        assertEquals(2, PriceMath.notesDue(new BigDecimal("1.001")).intValue());
    }

    @Test
    void minimumCountIsTheFirstOrderThatReachesOneUnit() {
        int count = PriceMath.minimumCount(new BigDecimal("0.06"), STEP, FLOOR, 64, BigDecimal.ONE, true);
        assertTrue(count > 1);
        assertNull(PriceMath.notesDue(PriceMath.notesExact(
                PriceMath.buy(new BigDecimal("0.06"), STEP, FLOOR, count - 1).total(), BigDecimal.ONE)));
        assertEquals(count, PriceMath.minimumCount(new BigDecimal("0.06"), STEP, FLOOR, 64, BigDecimal.ONE, true));
        assertTrue(PriceMath.notesExact(
                PriceMath.buy(new BigDecimal("0.06"), STEP, FLOOR, count).total(), BigDecimal.ONE)
                .compareTo(BigDecimal.ONE) >= 0);
    }
}
