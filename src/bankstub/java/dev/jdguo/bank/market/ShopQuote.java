package dev.jdguo.bank.market;

import java.math.BigDecimal;

/**
 * Compile-only copy of the public bank hook. Not packaged in the shop jar.
 * The running server uses the class from bankmod.
 */
public interface ShopQuote {
    BigDecimal diamondsEach(String itemId);

    static ShopQuote installed() {
        return Holder.quote;
    }

    static void install(ShopQuote quote) {
        Holder.quote = quote;
    }

    final class Holder {
        private static ShopQuote quote;

        private Holder() {}
    }
}
