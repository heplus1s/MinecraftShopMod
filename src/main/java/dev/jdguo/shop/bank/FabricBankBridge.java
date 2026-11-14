package dev.jdguo.shop.bank;

import dev.jdguo.bank.currency.CurrencyDef;
import dev.jdguo.bank.ledger.BankBooks;
import dev.jdguo.bank.market.ShopQuote;
import dev.jdguo.bank.persistence.BankRuntime;
import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.price.PriceBook;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/**
 * Live bank connection. This class is loaded only when {@code bankmod} is present, so a server
 * without the bank can still start the shop. Settlement goes through
 * {@code dev.jdguo.bank.market.ShopOperatingAccount} when that class exists.
 */
public final class FabricBankBridge implements ShopBank {
    private static final String ACCOUNT = "dev.jdguo.bank.market.ShopOperatingAccount";
    private static volatile Boolean hookClassPresent;
    private static boolean loggedFx;

    private FabricBankBridge() {}

    public static ShopBank open() {
        installQuote();
        return new FabricBankBridge();
    }

    public static void installQuote() {
        ShopQuote.install(itemId -> {
            try {
                if (itemId == null || ShopRuntime.config == null) {
                    return null;
                }
                PriceBook book = ShopRuntime.state == null ? null : ShopRuntime.state.book();
                if (book == null) {
                    return null;
                }
                PriceBook.Lookup lookup = book.lookup(ShopRuntime.config, itemId);
                if (lookup.corrupt() || lookup.entry() == null) {
                    return null;
                }
                return lookup.diamondEach();
            } catch (RuntimeException error) {
                ShopRuntime.LOGGER.warn("商店报价没有读出 {}", itemId, error);
                return null;
            }
        });
    }

    @Override
    public boolean modLoaded() {
        return true;
    }

    @Override
    public boolean ledgerReady() {
        return books() != null;
    }

    @Override
    public boolean operatingHookInstalled() {
        return hookClassPresent();
    }

    @Override
    public boolean operatingAccountReady() {
        return account() != null;
    }

    @Override
    public String currencyId() {
        CurrencyDef note = note();
        return note == null || note.id() == null ? "note" : note.id();
    }

    @Override
    public String currencyName() {
        CurrencyDef note = note();
        if (note == null || note.name() == null || note.name().isBlank()) {
            return "银行券";
        }
        return note.name();
    }

    @Override
    public BigDecimal unitsPerDiamond() {
        try {
            BankBooks books = books();
            CurrencyDef note = books == null ? null : books.primaryVirtual();
            if (note == null || note.id() == null) {
                return null;
            }
            return books.unitsPer(note.id(), "diamond");
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    @Override
    public BigInteger copperPerDiamond() {
        try {
            BankBooks books = books();
            return books == null ? null : books.copperPerDiamond();
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    @Override
    public BigDecimal annualInterestFraction() {
        try {
            BankBooks books = books();
            return books == null ? null : books.interestFraction();
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    @Override
    public BigInteger savingsPool() {
        try {
            BankBooks books = books();
            if (books == null) {
                return null;
            }
            return books.savingsPool(currencyId());
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    @Override
    public BigInteger playerBalance(UUID player) {
        Object account = account();
        if (account != null) {
            BigInteger fromHook = invoke(account, "playerBalance", new Class<?>[] {UUID.class}, player);
            if (fromHook != null) {
                return fromHook;
            }
        }
        try {
            BankBooks books = books();
            return books == null ? null : books.balance(player, currencyId());
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    @Override
    public BigInteger operatingBalance() {
        Object account = account();
        if (account == null) {
            return null;
        }
        return invoke(account, "balance", new Class<?>[0]);
    }

    @Override
    public PostResult post(UUID player, BigInteger amount, boolean playerPaysShop) {
        if (!ledgerReady()) {
            return PostResult.fail("bank_not_ready");
        }
        if (!hookClassPresent()) {
            return PostResult.fail("bank_hook_missing");
        }
        Object account = account();
        if (account == null) {
            return PostResult.fail("bank_not_ready");
        }
        if (amount == null || amount.signum() <= 0) {
            return PostResult.fail("bank_rejected");
        }
        try {
            Object result = account.getClass()
                    .getMethod("transfer", UUID.class, BigInteger.class, boolean.class)
                    .invoke(account, player, amount, playerPaysShop);
            String code = failureCode(result);
            if (code != null) {
                ShopRuntime.LOGGER.info("商店结算被拒绝：{}", code);
                return PostResult.fail(normalize(code), playerBalance(player), operatingBalance());
            }
            try {
                BankRuntime.save();
            } catch (Throwable error) {
                ShopRuntime.LOGGER.warn("商店结算已经入账，但中央银行存档没有立即写下", error);
            }
            return PostResult.ok(playerBalance(player), operatingBalance());
        } catch (Throwable error) {
            ShopRuntime.LOGGER.error("商店结算调用失败。按约定，失败时中央银行不应改动余额。", error);
            return PostResult.fail("bank_rejected");
        }
    }

    private static BankBooks books() {
        try {
            if (BankRuntime.state == null) {
                return null;
            }
            return BankRuntime.books();
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    private static CurrencyDef note() {
        try {
            BankBooks books = books();
            return books == null ? null : books.primaryVirtual();
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    private static boolean hookClassPresent() {
        Boolean cached = hookClassPresent;
        if (cached != null) {
            return cached;
        }
        try {
            Class.forName(ACCOUNT);
            hookClassPresent = Boolean.TRUE;
            return true;
        } catch (ClassNotFoundException error) {
            hookClassPresent = Boolean.FALSE;
            ShopRuntime.LOGGER.info("中央银行尚未提供商店经营账户 {}", ACCOUNT);
            return false;
        } catch (Throwable error) {
            hookClassPresent = Boolean.FALSE;
            ShopRuntime.LOGGER.error("商店经营账户接口无法加载", error);
            return false;
        }
    }

    private static Object account() {
        if (!hookClassPresent()) {
            return null;
        }
        try {
            Class<?> type = Class.forName(ACCOUNT);
            Method get = type.getMethod("get");
            return get.invoke(null);
        } catch (Throwable error) {
            ShopRuntime.LOGGER.warn("商店经营账户暂时取不到", error);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Object value = target.getClass().getMethod(name, types).invoke(target, args);
            return (T) value;
        } catch (Throwable error) {
            ShopRuntime.LOGGER.warn("读取商店经营账户的 {} 失败", name, error);
            return null;
        }
    }

    private static String failureCode(Object result) throws ReflectiveOperationException {
        if (result == null) {
            return "bank_rejected";
        }
        if (result instanceof String code) {
            return code.isBlank() ? null : code;
        }
        Method success = result.getClass().getMethod("success");
        if (Boolean.TRUE.equals(success.invoke(result))) {
            return null;
        }
        try {
            Object code = result.getClass().getMethod("code").invoke(result);
            return code == null ? "bank_rejected" : code.toString();
        } catch (NoSuchMethodException error) {
            return "bank_rejected";
        }
    }

    private static String normalize(String code) {
        if ("insufficient_funds".equals(code) || "insufficient_shop".equals(code)
                || "bank_absent".equals(code) || "bank_not_ready".equals(code)
                || "bank_hook_missing".equals(code) || "bank_rejected".equals(code)) {
            return code;
        }
        if ("insufficient_operating".equals(code)) {
            return "insufficient_shop";
        }
        return "bank_rejected";
    }

    private static void logFx(Throwable error) {
        if (loggedFx) {
            return;
        }
        loggedFx = true;
        ShopRuntime.LOGGER.warn("读取中央银行汇率或余额失败", error);
    }
}
