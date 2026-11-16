package dev.jdguo.shop.price;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketInfluenceTest {
    @Test
    void marketInfluenceUsesOneFifthOfTheGapAndCapsExtremeValues() {
        BigDecimal base = BigDecimal.TEN;
        assertEquals(0, new BigDecimal("10.2").compareTo(MarketInfluence.blend(base, new BigDecimal("11"))));
        assertEquals(0, new BigDecimal("10.2").compareTo(MarketInfluence.blend(base, new BigDecimal("30"))));
        assertEquals(0, new BigDecimal("9.8").compareTo(MarketInfluence.blend(base, BigDecimal.ONE)));
    }
}
