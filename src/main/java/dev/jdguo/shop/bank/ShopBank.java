package dev.jdguo.shop.bank;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/**
 * What the shop needs from the central bank on the server thread.
 * Exchange-rate reads can succeed before the operating account exists.
 * {@link #post} never succeeds unless that account is actually there.
 */
public interface ShopBank {
    boolean modLoaded();

    boolean ledgerReady();

    /** The bank jar contains the operating-account class, whether or not a world is loaded. */
    boolean operatingHookInstalled();

    boolean operatingAccountReady();

    String currencyId();

    String currencyName();

    /** Settlement-currency units for one diamond. Null when the rate cannot be quoted. */
    BigDecimal unitsPerDiamond();

    BigInteger copperPerDiamond();

    /** Annual demand-deposit rate. Informational; the shop does not pay interest itself. */
    BigDecimal annualInterestFraction();

    /** Aggregate savings pool for the settlement currency, when the bank can report it. */
    BigInteger savingsPool();

    BigInteger playerBalance(UUID player);

    BigInteger operatingBalance();

    PostResult post(UUID player, BigInteger amount, boolean playerPaysShop);

    record PostResult(boolean success, String code, BigInteger playerBalance, BigInteger operatingBalance) {
        public static PostResult ok(BigInteger playerBalance, BigInteger operatingBalance) {
            return new PostResult(true, "ok", playerBalance, operatingBalance);
        }

        public static PostResult fail(String code) {
            return new PostResult(false, code, null, null);
        }

        public static PostResult fail(String code, BigInteger playerBalance, BigInteger operatingBalance) {
            return new PostResult(false, code, playerBalance, operatingBalance);
        }
    }

    static ShopBank absent() {
        return AbsentBank.INSTANCE;
    }
}
