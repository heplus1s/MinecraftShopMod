package dev.jdguo.shop.bank;

import dev.jdguo.bank.currency.CurrencyDef;
import dev.jdguo.bank.ledger.BankBooks;
import dev.jdguo.bank.market.PersonalNote;
import dev.jdguo.bank.market.ShopQuote;
import dev.jdguo.bank.persistence.BankRuntime;
import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.price.MoneyScale;
import dev.jdguo.shop.price.PriceBook;
import dev.jdguo.shop.price.PriceMath;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Live bank connection. This class is loaded only when {@code bankmod} is present, so a server
 * without the bank can still start the shop. Settlement goes through
 * {@code dev.jdguo.bank.market.ShopOperatingAccount} when that class exists.
 */
public final class FabricBankBridge implements ShopBank {
    private static final String ACCOUNT = "dev.jdguo.bank.market.ShopOperatingAccount";
    private static volatile Boolean hookClassPresent;
    private static volatile Boolean decimalTransfer;
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
        return decimalTransferPresent();
    }

    @Override
    public boolean operatingAccountReady() {
        return account() != null && supportsReferencedSettlement();
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
            return "央行券";
        }
        return note.name();
    }

    @Override
    public BigDecimal unitsPerDiamond(PayChoice choice) {
        if (choice == null || choice.hard()) {
            return null;
        }
        try {
            BankBooks books = books();
            if (books == null) {
                return null;
            }
            if (!choice.personal()) {
                CurrencyDef note = books.primaryVirtual();
                if (note == null || note.id() == null) {
                    return null;
                }
                return books.unitsPer(note.id(), "diamond");
            }
            PersonalNote series = books.personalNoteById(choice.personalId());
            if (series == null || series.bankrupt()) {
                return null;
            }
            BigDecimal diamondsEach = books.personalDiamondPrice(series);
            if (diamondsEach == null || diamondsEach.signum() <= 0) {
                return null;
            }
            return BigDecimal.ONE.divide(diamondsEach, PriceMath.MC);
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
    public BigDecimal playerBalance(UUID player, PayChoice choice) {
        if (choice == null || choice.hard()) {
            return null;
        }
        Object account = account();
        if (account != null && decimalTransferPresent()) {
            BigDecimal fromHook = invoke(account, "playerBalance",
                    new Class<?>[] {UUID.class, String.class}, player, choice.key());
            if (fromHook != null) {
                return MoneyScale.quantize(fromHook);
            }
        }
        try {
            BankBooks books = books();
            if (books == null) {
                return null;
            }
            BigInteger whole = choice.personal()
                    ? books.personalHolding(choice.personalId(), player)
                    : books.balance(player, currencyId());
            return whole == null ? null : MoneyScale.quantize(new BigDecimal(whole));
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    @Override
    public BigDecimal operatingBalance(PayChoice choice) {
        if (choice == null || choice.hard()) {
            return null;
        }
        Object account = account();
        if (account == null || !decimalTransferPresent()) {
            return null;
        }
        BigDecimal balance = invoke(account, "balance", new Class<?>[] {String.class}, choice.key());
        return balance == null ? null : MoneyScale.quantize(balance);
    }

    @Override
    public PostResult post(UUID player, PayChoice choice, BigDecimal amount, boolean playerPaysShop) {
        // Compatibility entrypoint still uses the durable implementation, never the legacy transfer.
        return postReferenced(player, choice, amount, playerPaysShop, "shop_direct_" + UUID.randomUUID());
    }

    @Override
    public boolean supportsReferencedSettlement() {
        Object account = account();
        if (account == null) return false;
        try {
            account.getClass().getMethod("transferReferenced", UUID.class, String.class,
                    BigDecimal.class, boolean.class, String.class);
            account.getClass().getMethod("rollback", String.class);
            account.getClass().getMethod("receiptStatus", String.class);
            account.getClass().getMethod("completePayment", String.class);
            return Boolean.TRUE.equals(account.getClass().getMethod("durablePayments").invoke(account));
        } catch (ReflectiveOperationException error) { return false; }
    }

    @Override
    public PostResult postReferenced(UUID player, PayChoice choice, BigDecimal amount,
                                      boolean playerPaysShop, String reference) {
        if (choice == null || choice.hard()) return PostResult.fail("hard_currency");
        Object account = account();
        if (account == null || !supportsReferencedSettlement()) return PostResult.fail("bank_hook_missing");
        try {
            Object result = account.getClass().getMethod("transferReferenced", UUID.class, String.class,
                    BigDecimal.class, boolean.class, String.class)
                    .invoke(account, player, choice.key(), MoneyScale.quantize(amount), playerPaysShop, reference);
            String code = failureCode(result);
            return code == null ? PostResult.ok(playerBalance(player, choice), operatingBalance(choice))
                    : PostResult.fail(code, playerBalance(player, choice), operatingBalance(choice));
        } catch (Throwable error) {
            ShopRuntime.LOGGER.error("商店引用结算结果不明，禁止自动重发或反向退款：{}", reference, error);
            return PostResult.fail("settlement_unknown");
        }
    }

    @Override
    public PostResult rollback(String reference) {
        Object account = account();
        if (account == null || !supportsReferencedSettlement()) return PostResult.fail("bank_hook_missing");
        try {
            Object result = account.getClass().getMethod("rollback", String.class).invoke(account, reference);
            String code = failureCode(result);
            return code == null ? PostResult.ok(null, null) : PostResult.fail(code);
        } catch (Throwable error) {
            ShopRuntime.LOGGER.error("商店结算回滚结果不明：{}", reference, error);
            return PostResult.fail("settlement_unknown");
        }
    }

    @Override
    public PostResult completePayment(String reference) {
        Object account = account();
        if (account == null || !supportsReferencedSettlement()) return PostResult.fail("bank_hook_missing");
        try {
            Object result = account.getClass().getMethod("completePayment", String.class).invoke(account, reference);
            String code = failureCode(result);
            return code == null ? PostResult.ok(null, null) : PostResult.fail(code);
        } catch (Throwable error) {
            ShopRuntime.LOGGER.error("商店交付确认结果不明：{}", reference, error);
            return PostResult.fail("settlement_unknown");
        }
    }

    @Override
    public String receiptStatus(String reference) {
        Object account = account();
        String status = account == null ? null : invoke(account, "receiptStatus", new Class<?>[]{String.class}, reference);
        return status == null ? "unknown" : status;
    }

    @Override
    public com.google.gson.JsonObject quoteIssuerPayout(UUID coin, BigDecimal budget) {
        Object account = account();
        if (account == null) return null;
        try {
            Object result = account.getClass().getMethod("quoteIssuerPayout", UUID.class, BigDecimal.class)
                    .invoke(account, coin, budget);
            return result instanceof String json ? com.google.gson.JsonParser.parseString(json).getAsJsonObject() : null;
        } catch (Throwable unavailable) { return null; }
    }

    @Override
    public PostResult payIssuerOffer(UUID player, UUID coin, BigDecimal budget, String fingerprint, String reference) {
        Object account = account();
        if (account == null || !supportsReferencedSettlement()) return PostResult.fail("bank_hook_missing");
        try {
            Object result = account.getClass().getMethod("payIssuerOffer", UUID.class, UUID.class, BigDecimal.class, String.class, String.class)
                    .invoke(account, player, coin, budget, fingerprint, reference);
            String code = failureCode(result);
            return code == null ? PostResult.ok(null, null) : PostResult.fail(code);
        } catch (Throwable uncertain) {
            ShopRuntime.LOGGER.error("商店发行人挂单组合付款结果不明：{}", reference, uncertain);
            return PostResult.fail("settlement_unknown");
        }
    }

    @Override
    public PersonalNoteRef findPersonal(String name) {
        try {
            BankBooks books = books();
            if (books == null || name == null || name.isBlank() || PayChoice.hardKey(name)) {
                return null;
            }
            PersonalNote series = books.personalNoteByName(name);
            if (series == null) {
                return null;
            }
            BigDecimal diamonds = books.personalDiamondPrice(series);
            boolean priced = !series.bankrupt() && diamonds != null && diamonds.signum() > 0;
            return new PersonalNoteRef(series.id(), series.name(), priced);
        } catch (Throwable error) {
            logFx(error);
            return null;
        }
    }

    @Override
    public List<String> personalNames() {
        try {
            BankBooks books = books();
            if (books == null) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (PersonalNote series : books.personalNotes()) {
                if (series != null && series.name() != null && !series.name().isBlank() && !series.bankrupt()) {
                    names.add(series.name());
                }
            }
            return List.copyOf(names);
        } catch (Throwable error) {
            logFx(error);
            return List.of();
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

    private static boolean decimalTransferPresent() {
        Boolean cached = decimalTransfer;
        if (cached != null) {
            return cached;
        }
        if (!hookClassPresent()) {
            decimalTransfer = Boolean.FALSE;
            return false;
        }
        try {
            Class<?> type = Class.forName(ACCOUNT);
            type.getMethod("transfer", UUID.class, String.class, BigDecimal.class, boolean.class);
            decimalTransfer = Boolean.TRUE;
            return true;
        } catch (NoSuchMethodException error) {
            decimalTransfer = Boolean.FALSE;
            ShopRuntime.LOGGER.info("商店经营账户还不能按四位小数过户一种选定货币");
            return false;
        } catch (Throwable error) {
            decimalTransfer = Boolean.FALSE;
            ShopRuntime.LOGGER.warn("无法确认商店经营账户的过户方法", error);
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
        } catch (NoSuchMethodException error) {
            return null;
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
