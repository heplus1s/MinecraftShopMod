package dev.jdguo.shop.price;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceMathTest {
    private static final BigDecimal STEP = new BigDecimal("0.002");
    private static final BigDecimal FLOOR = new BigDecimal("0.000000001");

    @Test
    void eachBuyStepIsRoundedToFourDecimals() {
        PricePath path = PriceMath.buy(new BigDecimal("1"), new BigDecimal("0.1"), FLOOR, 3);
        assertEquals(0, path.total().compareTo(new BigDecimal("3.3100")));
        assertEquals(0, path.end().compareTo(new BigDecimal("1.331")));
    }

    @Test
    void aSmallOrderStaysBelowOneUnit() {
        BigDecimal charge = PriceMath.charge(new BigDecimal("0.0600"), BigDecimal.ONE);
        assertEquals(0, charge.compareTo(new BigDecimal("0.0600")));
        assertTrue(charge.compareTo(BigDecimal.ONE) < 0);
    }

    @Test
    void chargeDoesNotRoundUpToAWholeUnit() {
        assertEquals(0, PriceMath.charge(new BigDecimal("1.0001"), BigDecimal.ONE).compareTo(new BigDecimal("1.0001")));
        assertEquals("0", MoneyScale.display(BigDecimal.ZERO));
        assertEquals("1", MoneyScale.display(new BigDecimal("1.0000")));
        assertEquals("0.06", MoneyScale.display(new BigDecimal("0.0600")));
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
    }

    @Test
    void aFarPriceIsNotPulledBackToTheAnchor() {
        BigDecimal sold = PriceMath.sell(new BigDecimal("10"), STEP, FLOOR, 1).end();
        assertEquals(0, sold.compareTo(new BigDecimal("9.9800")));
    }
}
