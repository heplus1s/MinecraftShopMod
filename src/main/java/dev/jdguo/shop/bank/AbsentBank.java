package dev.jdguo.shop.bank;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
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
        return PostResult.fail("bank_absent");
    }
}
