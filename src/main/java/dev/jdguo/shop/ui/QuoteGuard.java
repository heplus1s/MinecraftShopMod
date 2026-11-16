package dev.jdguo.shop.ui;
import java.math.BigDecimal;
/** A changed quote always requires a fresh explicit confirmation. */
public final class QuoteGuard {
    private QuoteGuard() { }
    public static boolean same(BigDecimal displayed,BigDecimal current) {
        return displayed!=null && current!=null && displayed.signum()>0 && displayed.compareTo(current)==0;
    }
}
