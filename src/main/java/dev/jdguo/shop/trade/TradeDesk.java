package dev.jdguo.shop.trade;

import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.catalog.CatalogEntry;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.MoneyScale;
import dev.jdguo.shop.price.PriceBook;
import dev.jdguo.shop.price.PriceMath;
import dev.jdguo.shop.price.PricePath;
import dev.jdguo.shop.text.ShopText;
import dev.jdguo.shop.ui.ShopListingView;
import dev.jdguo.shop.ui.ShopQuoteView;
import dev.jdguo.shop.ui.ShopTradeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server shop desk. Buying raises the diamond-equivalent price; selling lowers it.
 * One order uses one selected currency, at four decimal places, or it does not trade.
 */
public final class TradeDesk {
    private static final Logger LOGGER = LoggerFactory.getLogger("shopmod");

    private TradeDesk() {}

    public static ShopQuoteView quote(ShopSession session, String itemId, int count, boolean buying, PayChoice pay) {
        Prepared prepared = prepare(session, itemId, count, buying, pay, false);
        if (prepared.early != null) {
            return prepared.early;
        }
        return view(prepared);
    }

    public static ShopTradeResult buy(ShopSession session, ItemPurse purse, UUID player, PayChoice pay,
                                       String itemId, int count) {
        Prepared prepared = prepare(session, itemId, count, true, pay, true);
        if (prepared.tradeFailure != null) {
            return prepared.tradeFailure;
        }
        if (purse.roomFor(itemId) < count) {
            return fail(prepared, "no_room", ShopText.noRoom());
        }
        return settle(session, purse, player, prepared, true);
    }

    public static ShopTradeResult sell(ShopSession session, ItemPurse purse, UUID player, PayChoice pay,
                                        String itemId, int count) {
        Prepared prepared = prepare(session, itemId, count, false, pay, true);
        if (prepared.tradeFailure != null) {
            return prepared.tradeFailure;
        }
        int have = purse.plainCount(itemId);
        if (have < count) {
            return fail(prepared, "not_enough_items", ShopText.notEnoughItems(prepared.entry.name(), count, have));
        }
        return settle(session, purse, player, prepared, false);
    }

    public static List<ShopListingView> listings(ShopSession session, int page, PayChoice pay) {
        ShopConfig config = session.config();
        PayChoice choice = pay == null ? PayChoice.note() : pay;
        int pages = Math.max(1, config.pages());
        if (page < 1 || page > pages) {
            return List.of();
        }
        int from = (page - 1) * config.pageSize();
        int to = Math.min(config.catalog().size(), from + config.pageSize());
        List<ShopListingView> rows = new ArrayList<>();
        BigDecimal rate = rate(session.bank(), choice);
        for (int i = from; i < to; i++) {
            CatalogEntry entry = config.catalog().get(i);
            PriceBook.Lookup lookup = session.book() == null
                    ? new PriceBook.Lookup(entry, MoneyScale.positive(entry.anchorDiamonds())
                    ? MoneyScale.quantize(entry.anchorDiamonds()) : null, false)
                    : session.book().lookup(config, entry.itemId());
            BigDecimal each = MoneyScale.positive(lookup.diamondEach()) ? lookup.diamondEach() : null;
            BigDecimal notes = each == null ? null : PriceMath.charge(each, rate);
            rows.add(new ShopListingView(entry.itemId(), entry.name(), each, entry.anchorDiamonds(), notes));
        }
        return rows;
    }

    private static ShopTradeResult settle(ShopSession session, ItemPurse purse, UUID player,
                                           Prepared prepared, boolean buying) {
        ShopBank bank = session.bank();
        BigDecimal due = prepared.charge;
        if (buying) {
            BigDecimal balance = quantize(bank.playerBalance(player, prepared.pay));
            if (balance != null && balance.compareTo(due) < 0) {
                return fail(prepared, "insufficient_funds",
                        ShopText.insufficientFunds(prepared.currency, due, balance));
            }
        } else {
            BigDecimal shop = quantize(bank.operatingBalance(prepared.pay));
            if (shop != null && shop.compareTo(due) < 0) {
                return fail(prepared, "insufficient_shop",
                        ShopText.insufficientShop(prepared.currency, due, shop));
            }
            purse.takePlain(prepared.itemId, prepared.count);
        }
        ShopBank.PostResult post = bank.post(player, prepared.pay, due, buying);
        if (!post.success()) {
            if (!buying) {
                purse.give(prepared.itemId, prepared.count);
            }
            return failureFromPost(prepared, post);
        }
        if (buying) {
            try {
                purse.give(prepared.itemId, prepared.count);
            } catch (RuntimeException error) {
                LOGGER.error("购入已入账，但物品没有交给玩家 {}", player, error);
                ShopBank.PostResult back = bank.post(player, prepared.pay, due, false);
                if (!back.success()) {
                    LOGGER.error("货款退回失败，玩家 {} 代码 {}", player, back.code());
                    return fail(prepared, "settle_inconsistent", ShopText.settleInconsistent());
                }
                return fail(prepared, "refunded", ShopText.refunded());
            }
        }
        session.book().commit(prepared.itemId, prepared.path.end());
        session.persist();
        BigDecimal balance = post.playerBalance() != null
                ? post.playerBalance()
                : bank.playerBalance(player, prepared.pay);
        String message = buying
                ? ShopText.bought(prepared.entry.name(), prepared.count, prepared.currency, due,
                prepared.path.end(), prepared.entry.anchorDiamonds(), balance)
                : ShopText.sold(prepared.entry.name(), prepared.count, prepared.currency, due,
                prepared.path.end(), prepared.entry.anchorDiamonds(), balance);
        return new ShopTradeResult(true, "ok", message, prepared.itemId, prepared.entry.name(), prepared.count,
                prepared.lookup.diamondEach(), prepared.path.total(), prepared.path.end(), due);
    }

    private static Prepared prepare(ShopSession session, String itemId, int count, boolean buying,
                                     PayChoice pay, boolean forTrade) {
        Prepared prepared = new Prepared();
        prepared.itemId = itemId == null ? "" : itemId;
        prepared.count = count;
        prepared.buying = buying;
        prepared.pay = pay == null || pay.hard() ? PayChoice.note() : pay;
        if (pay != null && pay.hard()) {
            prepared.pay = pay;
        }
        ShopConfig config = session.config();
        ShopBank bank = session.bank();
        prepared.currency = prepared.pay.name();
        if (prepared.pay.hard()) {
            return stop(prepared, "hard_currency", ShopText.hardCurrency(), forTrade);
        }
        if (session.book() == null) {
            return stop(prepared, "world_not_ready", ShopText.worldNotReady(), forTrade);
        }
        if (count < 1 || count > config.maxCount()) {
            return stop(prepared, "bad_count", ShopText.badCount(config.maxCount()), forTrade);
        }
        CatalogEntry entry = config.entry(prepared.itemId);
        if (entry == null) {
            return stop(prepared, "no_quote", ShopText.noQuote(), forTrade);
        }
        prepared.entry = entry;
        PriceBook.Lookup lookup = session.book().lookup(config, prepared.itemId);
        prepared.lookup = lookup;
        if (!MoneyScale.positive(lookup.diamondEach())) {
            return stop(prepared, "no_quote", ShopText.noQuote(), forTrade);
        }
        String gate = settlementGate(bank, prepared.pay);
        prepared.settlementCode = gate == null ? "ready" : gate;
        if (forTrade && gate != null) {
            return stop(prepared, gate, gateMessage(gate), true);
        }
        prepared.path = buying
                ? PriceMath.buy(lookup.diamondEach(), config.stepFraction(), config.minDiamondPrice(), count)
                : PriceMath.sell(lookup.diamondEach(), config.stepFraction(), config.minDiamondPrice(), count);
        if (!MoneyScale.positive(prepared.path.total())) {
            return stop(prepared, "no_quote", ShopText.noQuote(), forTrade);
        }
        BigDecimal rate = rate(bank, prepared.pay);
        prepared.charge = PriceMath.charge(prepared.path.total(), rate);
        if (forTrade && !MoneyScale.positive(prepared.charge)) {
            return stop(prepared, "zero_charge", ShopText.zeroCharge(), true);
        }
        return prepared;
    }

    private static String settlementGate(ShopBank bank, PayChoice pay) {
        if (pay != null && pay.hard()) {
            return "hard_currency";
        }
        if (!bank.modLoaded()) {
            return "bank_absent";
        }
        if (!bank.ledgerReady()) {
            return "bank_not_ready";
        }
        BigDecimal rate = bank.unitsPerDiamond(pay);
        if (!MoneyScale.positive(rate) && (rate == null || rate.signum() <= 0)) {
            return "fx_unavailable";
        }
        if (!bank.operatingAccountReady()) {
            return bank.operatingHookInstalled() ? "bank_not_ready" : "bank_hook_missing";
        }
        return null;
    }

    private static String gateMessage(String code) {
        return switch (code) {
            case "bank_absent" -> ShopText.bankAbsent();
            case "bank_not_ready" -> ShopText.bankNotReady();
            case "bank_hook_missing" -> ShopText.bankHookMissing();
            case "fx_unavailable" -> ShopText.fxUnavailable();
            case "hard_currency" -> ShopText.hardCurrency();
            default -> ShopText.bankRejected();
        };
    }

    private static Prepared stop(Prepared prepared, String code, String message, boolean forTrade) {
        prepared.settlementCode = code;
        if (forTrade) {
            prepared.tradeFailure = fail(prepared, code, message);
        } else if (prepared.entry == null || prepared.lookup == null || !MoneyScale.positive(prepared.lookup.diamondEach())) {
            prepared.early = new ShopQuoteView(false, code, message, prepared.itemId,
                    prepared.entry == null ? prepared.itemId : prepared.entry.name(),
                    prepared.count, prepared.buying, null,
                    prepared.entry == null ? null : prepared.entry.anchorDiamonds(),
                    null, null);
        } else {
            prepared.early = new ShopQuoteView(true, code, message, prepared.itemId, prepared.entry.name(),
                    prepared.count, prepared.buying, prepared.lookup.diamondEach(), prepared.entry.anchorDiamonds(),
                    null, null);
        }
        return prepared;
    }

    private static ShopQuoteView view(Prepared prepared) {
        ShopQuoteView draft = new ShopQuoteView(true, prepared.settlementCode, "", prepared.itemId,
                prepared.entry.name(), prepared.count, prepared.buying, prepared.lookup.diamondEach(),
                prepared.entry.anchorDiamonds(), prepared.path.total(), prepared.charge);
        String message = ShopText.quote(draft, prepared.currency);
        return new ShopQuoteView(true, prepared.settlementCode, message, prepared.itemId, prepared.entry.name(),
                prepared.count, prepared.buying, prepared.lookup.diamondEach(), prepared.entry.anchorDiamonds(),
                prepared.path.total(), prepared.charge);
    }

    private static ShopTradeResult fail(Prepared prepared, String code, String message) {
        return new ShopTradeResult(false, code, message,
                prepared.itemId,
                prepared.entry == null ? "" : prepared.entry.name(),
                prepared.count,
                prepared.lookup == null ? null : prepared.lookup.diamondEach(),
                prepared.path == null ? null : prepared.path.total(),
                null,
                prepared.charge);
    }

    private static ShopTradeResult failureFromPost(Prepared prepared, ShopBank.PostResult post) {
        String code = post.code() == null ? "bank_rejected" : post.code();
        String message = switch (code) {
            case "insufficient_funds" -> ShopText.insufficientFunds(prepared.currency, prepared.charge,
                    balanceOr(post.playerBalance()));
            case "insufficient_shop" -> ShopText.insufficientShop(prepared.currency, prepared.charge,
                    balanceOr(post.operatingBalance()));
            case "bank_absent" -> ShopText.bankAbsent();
            case "bank_not_ready" -> ShopText.bankNotReady();
            case "bank_hook_missing" -> ShopText.bankHookMissing();
            case "hard_currency" -> ShopText.hardCurrency();
            default -> ShopText.bankRejected();
        };
        if ("insufficient_funds".equals(code) || "insufficient_shop".equals(code)
                || "bank_absent".equals(code) || "bank_not_ready".equals(code)
                || "bank_hook_missing".equals(code) || "hard_currency".equals(code)) {
            return fail(prepared, code, message);
        }
        return fail(prepared, "bank_rejected", ShopText.bankRejected());
    }

    private static BigDecimal balanceOr(BigDecimal value) {
        return value == null ? MoneyScale.quantize(BigDecimal.ZERO) : MoneyScale.quantize(value);
    }

    private static BigDecimal quantize(BigDecimal value) {
        return value == null ? null : MoneyScale.quantize(value);
    }

    private static BigDecimal rate(ShopBank bank, PayChoice pay) {
        if (bank == null || pay == null || pay.hard() || !bank.modLoaded() || !bank.ledgerReady()) {
            return null;
        }
        BigDecimal rate = bank.unitsPerDiamond(pay);
        if (rate == null || rate.signum() <= 0) {
            return null;
        }
        return rate;
    }

    private static final class Prepared {
        private String itemId = "";
        private int count;
        private boolean buying;
        private PayChoice pay = PayChoice.note();
        private String currency = "银行券";
        private String settlementCode = "ready";
        private CatalogEntry entry;
        private PriceBook.Lookup lookup;
        private PricePath path;
        private BigDecimal charge;
        private ShopQuoteView early;
        private ShopTradeResult tradeFailure;
    }
}
