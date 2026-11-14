package dev.jdguo.shop.support;

import dev.jdguo.shop.bank.ShopBank;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/** In-memory savings pool used by shop tests. Not a second currency. */
public final class MemoryBank implements ShopBank {
    public BigInteger player = new BigInteger("100");
    public BigInteger shop = BigInteger.ZERO;
    public BigDecimal fx = BigDecimal.ONE;
    public boolean loaded = true;
    public boolean ready = true;
    public boolean hook = true;
    public BigInteger savings = new BigInteger("100");
    public BigDecimal interest = new BigDecimal("0.02");
    public boolean hideShopBalance;

    @Override public boolean modLoaded() { return loaded; }
    @Override public boolean ledgerReady() { return loaded && ready; }
    @Override public boolean operatingHookInstalled() { return loaded && hook; }
    @Override public boolean operatingAccountReady() { return loaded && ready && hook; }
    @Override public String currencyId() { return "note"; }
    @Override public String currencyName() { return "银行券"; }
    @Override public BigDecimal unitsPerDiamond() { return loaded && ready ? fx : null; }
    @Override public BigInteger copperPerDiamond() { return new BigInteger("1000"); }
    @Override public BigDecimal annualInterestFraction() { return interest; }
    @Override public BigInteger savingsPool() { return savings; }

    @Override
    public BigInteger playerBalance(UUID player) {
        return operatingAccountReady() ? this.player : null;
    }

    @Override
    public BigInteger operatingBalance() {
        if (!operatingAccountReady() || hideShopBalance) {
            return null;
        }
        return shop;
    }

    @Override
    public PostResult post(UUID player, BigInteger amount, boolean playerPaysShop) {
        if (!loaded) {
            return PostResult.fail("bank_absent");
        }
        if (!ready) {
            return PostResult.fail("bank_not_ready");
        }
        if (!hook) {
            return PostResult.fail("bank_hook_missing");
        }
        if (amount == null || amount.signum() <= 0) {
            return PostResult.fail("bank_rejected");
        }
        if (playerPaysShop) {
            if (this.player.compareTo(amount) < 0) {
                return PostResult.fail("insufficient_funds", this.player, shop);
            }
            this.player = this.player.subtract(amount);
            shop = shop.add(amount);
        } else {
            if (shop.compareTo(amount) < 0) {
                return PostResult.fail("insufficient_shop", this.player, shop);
            }
            shop = shop.subtract(amount);
            this.player = this.player.add(amount);
        }
        savings = this.player.add(shop);
        return PostResult.ok(this.player, shop);
    }
}
