package dev.jdguo.shop.bank;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Book-entry shop account inside the central bank's savings pool (储蓄总池).
 *
 * <p>The bank does not ship this yet. When it does, {@code dev.jdguo.bank.market.ShopOperatingAccount}
 * should behave as this contract, and the shop will call that class. Do not implement the account
 * with issuance reserves, a personal issuance vault, paper currency, or the land-grant account
 * (土地出让账户). A transfer moves the same amount between the player's demand deposit and this
 * operating account, so the pool total does not change. Interest is a separate daily pass and
 * uses the same rule as personal demand deposits.
 */
public interface ShopOperatingLedger {
    /** Central virtual currency, normally {@code note}. */
    String currencyId();

    /** Operating-account balance, in whole units of {@link #currencyId()}. */
    BigInteger balance();

    /** Player demand-deposit balance in that same currency. */
    BigInteger playerBalance(UUID player);

    /**
     * Moves whole units inside the savings pool.
     *
     * @param playerPaysShop {@code true} when the player buys from the server
     * @return an ASCII failure code, or {@code null} when both legs are posted
     */
    String transfer(UUID player, BigInteger amount, boolean playerPaysShop);
}
