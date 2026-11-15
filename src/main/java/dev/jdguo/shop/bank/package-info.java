/**
 * Connection to the central bank.
 *
 * <p>Two bank hooks already exist and are used when {@code bankmod} is loaded:
 * {@code ShopQuote.install} for a live diamond quote, and {@code BankBooks.unitsPer}
 * plus {@code copperPerDiamond} for the exchange rate. Personal demand-deposit balances
 * and the savings-pool total are read the same way.
 *
 * <p>{@link dev.jdguo.shop.bank.ShopOperatingLedger} is the hook the bank does not have yet.
 * Purchases and sales need a shop operating account inside the savings pool, a book transfer
 * that stays inside that pool, and the same demand-deposit interest as a personal balance.
 * The expected bank class is {@code dev.jdguo.bank.market.ShopOperatingAccount}. Until it
 * exists, a bank-backed action tells the player in chat and does not invent another currency.
 * See {@code docs/bank-hooks.md}.
 */
package dev.jdguo.shop.bank;
