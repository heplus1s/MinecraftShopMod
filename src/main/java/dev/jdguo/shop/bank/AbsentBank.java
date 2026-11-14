package dev.jdguo.shop.bank;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/** Used when bankmod is not loaded. The shop still starts. */
public final class AbsentBank implements ShopBank {
    static final AbsentBank INSTANCE = new AbsentBank();

    private AbsentBank() {}

    @Override public boolean modLoaded() { return false; }
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
        return PostResult.fail("bank_absent");
    }
}
