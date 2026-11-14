package dev.jdguo.shop.trade;

import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.catalog.CatalogEntry;
import dev.jdguo.shop.catalog.ShopConfig;
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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server shop desk. Buying raises the diamond-equivalent price; selling lowers it.
 * Money moves only through {@link ShopBank#post}. A refused trade does not change the price.
 */
public final class TradeDesk {
    private static final Logger LOGGER = LoggerFactory.getLogger("shopmod");

    private TradeDesk() {}

    public static ShopQuoteView quote(ShopSession session, String itemId, int count, boolean buying) {
        Prepared prepared = prepare(session, itemId, count, buying, false);
        if (prepared.early != null) {
            return prepared.early;
        }
        return view(prepared);
    }

    public static ShopTradeResult buy(ShopSession session, ItemPurse purse, UUID player, String itemId, int count) {
        Prepared prepared = prepare(session, itemId, count, true, true);
        if (prepared.tradeFailure != null) {
            return prepared.tradeFailure;
        }
        if (purse.roomFor(itemId) < count) {
            return fail(prepared, "no_room", ShopText.noRoom());
        }
        return settle(session, purse, player, prepared, true);
    }

    public static ShopTradeResult sell(ShopSession session, ItemPurse purse, UUID player, String itemId, int count) {
        Prepared prepared = prepare(session, itemId, count, false, true);
        if (prepared.tradeFailure != null) {
            return prepared.tradeFailure;
        }
        int have = purse.plainCount(itemId);
        if (have < count) {
            return fail(prepared, "not_enough_items", ShopText.notEnoughItems(prepared.entry.name(), count, have));
        }
        return settle(session, purse, player, prepared, false);
    }

    public static List<ShopListingView> listings(ShopSession session, int page) {
        ShopConfig config = session.config();
        int pages = Math.max(1, config.pages());
        if (page < 1 || page > pages) {
            return List.of();
        }
        int from = (page - 1) * config.pageSize();
        int to = Math.min(config.catalog().size(), from + config.pageSize());
        List<ShopListingView> rows = new ArrayList<>();
        BigDecimal fx = fx(session.bank());
        for (int i = from; i < to; i++) {
            CatalogEntry entry = config.catalog().get(i);
            PriceBook.Lookup lookup = session.book() == null
                    ? new PriceBook.Lookup(entry, entry.anchorDiamonds(), false)
                    : session.book().lookup(config, entry.itemId());
            BigDecimal each = lookup.corrupt() ? null : lookup.diamondEach();
            BigDecimal notes = each == null || fx == null ? null : PriceMath.notesExact(each, fx);
            rows.add(new ShopListingView(entry.itemId(), entry.name(), each, entry.anchorDiamonds(), notes));
        }
        return rows;
    }

    private static ShopTradeResult settle(ShopSession session, ItemPurse purse, UUID player,
                                           Prepared prepared, boolean buying) {
        ShopBank bank = session.bank();
        BigInteger due = prepared.notesDue;
        if (buying) {
            BigInteger balance = bank.playerBalance(player);
            if (balance != null && balance.compareTo(due) < 0) {
                return fail(prepared, "insufficient_funds",
                        ShopText.insufficientFunds(prepared.currency, due, balance));
            }
        } else {
            BigInteger shop = bank.operatingBalance();
            if (shop != null && shop.compareTo(due) < 0) {
                return fail(prepared, "insufficient_shop",
                        ShopText.insufficientShop(prepared.currency, due, shop));
            }
            purse.takePlain(prepared.itemId, prepared.count);
        }
        ShopBank.PostResult post = bank.post(player, due, buying);
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
                ShopBank.PostResult back = bank.post(player, due, false);
                if (!back.success()) {
                    LOGGER.error("货款退回失败，玩家 {} 代码 {}", player, back.code());
                    return fail(prepared, "settle_inconsistent", ShopText.settleInconsistent());
                }
                return fail(prepared, "refunded", ShopText.refunded());
            }
        }
        session.book().commit(prepared.itemId, prepared.path.end());
        session.persist();
        BigInteger balance = post.playerBalance() != null ? post.playerBalance() : bank.playerBalance(player);
        String message = buying
                ? ShopText.bought(prepared.entry.name(), prepared.count, prepared.currency, due,
                prepared.path.end(), prepared.entry.anchorDiamonds(), balance)
                : ShopText.sold(prepared.entry.name(), prepared.count, prepared.currency, due,
                prepared.path.end(), prepared.entry.anchorDiamonds(), balance);
        return new ShopTradeResult(true, "ok", message, prepared.itemId, prepared.entry.name(), prepared.count,
                prepared.lookup.diamondEach(), prepared.path.total(), prepared.path.end(), due, prepared.minimumCount);
    }

    private static Prepared prepare(ShopSession session, String itemId, int count, boolean buying, boolean forTrade) {
        Prepared prepared = new Prepared();
        prepared.itemId = itemId == null ? "" : itemId;
        prepared.count = count;
        prepared.buying = buying;
        ShopConfig config = session.config();
        ShopBank bank = session.bank();
        prepared.currency = ShopText.currency(bank);
        if (session.book() == null) {
            return stop(prepared, "world_not_ready", ShopText.worldNotReady(), forTrade);
        }
        if (count < 1 || count > config.maxCount()) {
            return stop(prepared, "bad_count", ShopText.badCount(config.maxCount()), forTrade);
        }
        CatalogEntry entry = config.entry(prepared.itemId);
        if (entry == null) {
            return stop(prepared, "not_listed", ShopText.notListed(), forTrade);
        }
        prepared.entry = entry;
        PriceBook.Lookup lookup = session.book().lookup(config, prepared.itemId);
        prepared.lookup = lookup;
        if (lookup.corrupt()) {
            return stop(prepared, "bad_price", ShopText.badPrice(prepared.itemId), forTrade);
        }
        String gate = settlementGate(bank);
        prepared.settlementCode = gate == null ? "ready" : gate;
        if (forTrade && gate != null) {
            return stop(prepared, gate, gateMessage(gate), true);
        }
        prepared.path = buying
                ? PriceMath.buy(lookup.diamondEach(), config.stepFraction(), config.minDiamondPrice(), count)
                : PriceMath.sell(lookup.diamondEach(), config.stepFraction(), config.minDiamondPrice(), count);
        BigDecimal rate = fx(bank);
        if (rate != null) {
            prepared.notesExact = PriceMath.notesExact(prepared.path.total(), rate);
            prepared.notesDue = PriceMath.notesDue(prepared.notesExact);
            prepared.minimumCount = PriceMath.minimumCount(lookup.diamondEach(), config.stepFraction(),
                    config.minDiamondPrice(), config.maxCount(), rate, buying);
        }
        if (forTrade && prepared.notesDue == null) {
            if (prepared.minimumCount > 0) {
                return stop(prepared, "below_unit", ShopText.belowUnit(prepared.currency, prepared.minimumCount), true);
            }
            return stop(prepared, "below_unit", ShopText.belowUnitCap(prepared.currency, config.maxCount()), true);
        }
        return prepared;
    }

    private static String settlementGate(ShopBank bank) {
        if (!bank.modLoaded()) {
            return "bank_absent";
        }
        if (!bank.ledgerReady()) {
            return "bank_not_ready";
        }
        if (bank.unitsPerDiamond() == null || bank.unitsPerDiamond().signum() <= 0) {
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
            default -> ShopText.bankRejected();
        };
    }

    /**
     * Quotes still show the diamond price when settlement is unavailable.
     * Trades stop at the first missing bank piece.
     */
    private static Prepared stop(Prepared prepared, String code, String message, boolean forTrade) {
        prepared.settlementCode = code;
        if (forTrade) {
            prepared.tradeFailure = fail(prepared, code, message);
        } else if (prepared.entry == null) {
            prepared.early = new ShopQuoteView(false, code, message, prepared.itemId, prepared.itemId,
                    prepared.count, prepared.buying, null, null, null, null, null, -1);
        } else {
            prepared.early = new ShopQuoteView(prepared.lookup != null && !prepared.lookup.corrupt() && prepared.lookup.diamondEach() != null,
                    code, message, prepared.itemId, prepared.entry.name(), prepared.count, prepared.buying,
                    prepared.lookup == null ? null : prepared.lookup.diamondEach(),
                    prepared.entry.anchorDiamonds(), null, null, null, -1);
        }
        return prepared;
    }

    private static ShopQuoteView view(Prepared prepared) {
        String message = ShopText.quote(new ShopQuoteView(true, prepared.settlementCode, "", prepared.itemId,
                prepared.entry.name(), prepared.count, prepared.buying, prepared.lookup.diamondEach(),
                prepared.entry.anchorDiamonds(), prepared.path.total(), prepared.notesExact, prepared.notesDue,
                prepared.minimumCount), prepared.currency);
        return new ShopQuoteView(true, prepared.settlementCode, message, prepared.itemId, prepared.entry.name(),
                prepared.count, prepared.buying, prepared.lookup.diamondEach(), prepared.entry.anchorDiamonds(),
                prepared.path.total(), prepared.notesExact, prepared.notesDue, prepared.minimumCount);
    }

    private static ShopTradeResult fail(Prepared prepared, String code, String message) {
        return new ShopTradeResult(false, code, message,
                prepared.itemId,
                prepared.entry == null ? "" : prepared.entry.name(),
                prepared.count,
                prepared.lookup == null ? null : prepared.lookup.diamondEach(),
                prepared.path == null ? null : prepared.path.total(),
                null,
                prepared.notesDue,
                prepared.minimumCount);
    }

    private static ShopTradeResult failureFromPost(Prepared prepared, ShopBank.PostResult post) {
        String code = post.code() == null ? "bank_rejected" : post.code();
        String message = switch (code) {
            case "insufficient_funds" -> ShopText.insufficientFunds(prepared.currency, prepared.notesDue,
                    balanceOr(post.playerBalance(), BigInteger.ZERO));
            case "insufficient_shop" -> ShopText.insufficientShop(prepared.currency, prepared.notesDue,
                    balanceOr(post.operatingBalance(), BigInteger.ZERO));
            case "bank_absent" -> ShopText.bankAbsent();
            case "bank_not_ready" -> ShopText.bankNotReady();
            case "bank_hook_missing" -> ShopText.bankHookMissing();
            default -> ShopText.bankRejected();
        };
        if ("insufficient_funds".equals(code) || "insufficient_shop".equals(code)
                || "bank_absent".equals(code) || "bank_not_ready".equals(code) || "bank_hook_missing".equals(code)) {
            return fail(prepared, code, message);
        }
        return fail(prepared, "bank_rejected", ShopText.bankRejected());
    }

    private static BigInteger balanceOr(BigInteger value, BigInteger fallback) {
        return value == null ? fallback : value;
    }

    private static BigDecimal fx(ShopBank bank) {
        if (bank == null || !bank.modLoaded() || !bank.ledgerReady()) {
            return null;
        }
        BigDecimal rate = bank.unitsPerDiamond();
        if (rate == null || rate.signum() <= 0) {
            return null;
        }
        return rate;
    }

    private static final class Prepared {
        private String itemId = "";
        private int count;
        private boolean buying;
        private String currency = "银行券";
        private String settlementCode = "ready";
        private CatalogEntry entry;
        private PriceBook.Lookup lookup;
        private PricePath path;
        private BigDecimal notesExact;
        private BigInteger notesDue;
        private int minimumCount = -1;
        private ShopQuoteView early;
        private ShopTradeResult tradeFailure;
    }
}
