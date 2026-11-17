package dev.jdguo.shop.bank;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/** The bank mod is installed, but this shop build could not bind its public types. */
public final class UnreachableBank implements ShopBank {
    @Override public boolean modLoaded() { return true; }
    @Override public boolean ledgerReady() { return false; }
    @Override public boolean operatingHookInstalled() { return false; }
    @Override public boolean operatingAccountReady() { return false; }
    @Override public String currencyId() { return "note"; }
    @Override public String currencyName() { return "央行券"; }
    @Override public BigDecimal unitsPerDiamond(PayChoice choice) { return null; }
    @Override public BigInteger copperPerDiamond() { return null; }
    @Override public BigDecimal annualInterestFraction() { return null; }
    @Override public BigInteger savingsPool() { return null; }
    @Override public BigDecimal playerBalance(UUID player, PayChoice choice) { return null; }
    @Override public BigDecimal operatingBalance(PayChoice choice) { return null; }
    @Override public PersonalNoteRef findPersonal(String name) { return null; }
    @Override public List<String> personalNames() { return List.of(); }

    @Override
    public PostResult post(UUID player, PayChoice choice, BigDecimal amount, boolean playerPaysShop) {
        return PostResult.fail("bank_not_ready");
    }
}
