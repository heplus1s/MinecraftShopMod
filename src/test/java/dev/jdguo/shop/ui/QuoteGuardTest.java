package dev.jdguo.shop.ui;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class QuoteGuardTest {
    @Test void exactMoneyNotScaleMustMatch() {
        assertTrue(QuoteGuard.same(new BigDecimal("1.5"),new BigDecimal("1.5000")));
        assertFalse(QuoteGuard.same(new BigDecimal("1.5"),new BigDecimal("1.5001")));
        assertFalse(QuoteGuard.same(new BigDecimal("1.5"),new BigDecimal("1.4999")));
    }
    @Test void unavailableOrZeroQuoteCannotConfirm() {
        assertFalse(QuoteGuard.same(null,BigDecimal.ONE));
        assertFalse(QuoteGuard.same(BigDecimal.ONE,null));
        assertFalse(QuoteGuard.same(BigDecimal.ZERO,BigDecimal.ZERO));
    }
}
