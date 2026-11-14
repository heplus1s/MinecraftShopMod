package dev.jdguo.shop.bank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Book entry for one shop payment currency.
 *
 * <p>The bank does not ship this yet. When it does, {@code dev.jdguo.bank.market.ShopOperatingAccount}
 * should accept four-decimal amounts and a single currency key: {@code note} for the central
 * bank note, or {@code personal:<uuid>} for one personal note. Copper and diamond are not keys.
 * One call moves only that currency. It does not fill part of an order and does not round the
 * amount up to a whole unit.
 *
 * <p>A central-note balance stays a sub-ledger of 储蓄总池. A personal-note balance is a holding
 * of that note alone. Neither leg touches issuance reserves, a personal issuance vault, paper,
 * or the land-grant account (土地出让账户).
 */
public interface ShopOperatingLedger {
    /** Central virtual currency, normally {@code note}. */
    String currencyId();

    BigDecimal balance(String currencyKey);

    BigDecimal playerBalance(UUID player, String currencyKey);

    /**
     * @param playerPaysShop {@code true} when the player buys from the server
     * @return an ASCII failure code, or {@code null} when the whole amount is posted
     */
    String transfer(UUID player, String currencyKey, BigDecimal amount, boolean playerPaysShop);
}
