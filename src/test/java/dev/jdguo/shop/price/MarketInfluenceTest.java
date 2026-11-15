package dev.jdguo.shop.price;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketInfluenceTest {
    @Test
    void marketInfluenceUsesOneFifthOfTheGapAndCapsExtremeValues() {
        BigDecimal base = BigDecimal.TEN;
        assertEquals(new BigDecimal("10.2000"), MarketInfluence.blend(base, new BigDecimal("11")));
        assertEquals(new BigDecimal("10.5000"), MarketInfluence.blend(base, new BigDecimal("30")));
        assertEquals(new BigDecimal("9.5000"), MarketInfluence.blend(base, BigDecimal.ONE));
    }
}
