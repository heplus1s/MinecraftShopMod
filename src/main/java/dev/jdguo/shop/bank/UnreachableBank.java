package dev.jdguo.shop.bank;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/** The bank mod is installed, but this shop build could not bind its public types. */
public final class UnreachableBank implements ShopBank {
    @Override public boolean modLoaded() { return true; }
    @Override public boolean ledgerReady() { return false; }
    @Override public boolean operatingHookInstalled() { return false; }
    @Override public boolean operatingAccountReady() { return false; }
    @Override public String currencyId() { return "note"; }
    @Override public String currencyName() { return "银行券"; }
    @Override public BigDecimal unitsPerDiamond() { return null; }
    @Override public BigInteger copperPerDiamond() { return null; }
    @Override public BigDecimal annualInterestFraction() { return null; }
    @Override public BigInteger savingsPool() { return null; }
    @Override public BigInteger playerBalance(UUID player) { return null; }
    @Override public BigInteger operatingBalance() { return null; }

    @Override
    public PostResult post(UUID player, BigInteger amount, boolean playerPaysShop) {
        return PostResult.fail("bank_not_ready");
    }
}
