package dev.jdguo.shop.price;

import java.math.BigDecimal;

/** Diamond-equivalents paid across a trade, and the unit price after the last step. */
public record PricePath(BigDecimal total, BigDecimal end) {
}
