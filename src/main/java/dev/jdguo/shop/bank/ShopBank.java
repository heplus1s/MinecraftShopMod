package dev.jdguo.shop.bank;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/**
 * What the shop needs from the central bank on the server thread.
 * Amounts posted and compared here are four-decimal figures in one selected currency.
 */
public interface ShopBank {
    boolean modLoaded();

    boolean ledgerReady();

    /** The bank jar contains the operating-account class, whether or not a world is loaded. */
    boolean operatingHookInstalled();

    boolean operatingAccountReady();

    String currencyId();

    String currencyName();

    /**
     * Units of {@code choice} for one diamond. Null when that currency cannot be quoted.
     * Hard currencies are not quoted.
     */
    BigDecimal unitsPerDiamond(PayChoice choice);

    BigInteger copperPerDiamond();

    /** Annual demand-deposit rate. Informational; the shop does not pay interest itself. */
    BigDecimal annualInterestFraction();

    /** Aggregate savings pool for the central note, when the bank can report it. */
    BigInteger savingsPool();

    /** Balance of {@code choice} at four decimal places. Null when it cannot be read. */
    BigDecimal playerBalance(UUID player, PayChoice choice);

    BigDecimal operatingBalance(PayChoice choice);

    /**
     * Moves {@code amount} of one currency. {@code amount} is already at four decimal places.
     * The whole amount posts, or nothing does.
     */
    PostResult post(UUID player, PayChoice choice, BigDecimal amount, boolean playerPaysShop);

    /** Must mean reference-idempotent, durably committed before success; never silently fall back. */
    default boolean supportsReferencedSettlement() { return false; }
    default PostResult postReferenced(UUID player, PayChoice choice, BigDecimal amount,
                                      boolean playerPaysShop, String reference) {
        return PostResult.fail("bank_hook_missing");
    }
    /** Exact inverse of this reference, not a new opposite-direction trade. */
    default PostResult rollback(String reference) { return PostResult.fail("bank_hook_missing"); }
    /** Release pending settlement locks only after inventory delivery is durably proved. */
    default PostResult completePayment(String reference) { return PostResult.fail("bank_hook_missing"); }
    default String receiptStatus(String reference) { return "unknown"; }


    /** Null when no personal note has that name. */
    PersonalNoteRef findPersonal(String name);

    List<String> personalNames();

    record PersonalNoteRef(UUID id, String name, boolean priced) {}

    record PostResult(boolean success, String code, BigDecimal playerBalance, BigDecimal operatingBalance) {
        public static PostResult ok(BigDecimal playerBalance, BigDecimal operatingBalance) {
            return new PostResult(true, "ok", playerBalance, operatingBalance);
        }

        public static PostResult fail(String code) {
            return new PostResult(false, code, null, null);
        }

        public static PostResult fail(String code, BigDecimal playerBalance, BigDecimal operatingBalance) {
            return new PostResult(false, code, playerBalance, operatingBalance);
        }
    }

    static ShopBank absent() {
        return AbsentBank.INSTANCE;
    }
}
