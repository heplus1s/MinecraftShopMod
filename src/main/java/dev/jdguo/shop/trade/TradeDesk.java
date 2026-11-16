package dev.jdguo.shop.trade;

import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.catalog.CatalogEntry;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.MoneyScale;
import dev.jdguo.shop.price.PriceBook;
import dev.jdguo.shop.price.PriceMath;
import dev.jdguo.shop.price.MarketInfluence;
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
        ShopTradeResult recovery = recoverPending(session, purse, player);
        if (recovery != null) return recovery;
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
        ShopTradeResult recovery = recoverPending(session, purse, player);
        if (recovery != null) return recovery;
        if (pay != null && pay.personal()) {
            return ShopTradeResult.fail("payout_confirmation_required", "个人币收款需要先预览并确认私币与央行券的组合，尚未收取物品。");
        }
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

    public static PrivatePayoutPlan quotePrivatePayout(ShopSession session, String itemId, int count, PayChoice pay) {
        if (pay == null || !pay.personal()) return null;
        Prepared base = prepare(session, itemId, count, false, PayChoice.note(), false);
        if (base.early != null || base.charge == null || base.charge.signum() <= 0 || !"ready".equals(base.settlementCode)) return null;
        BigDecimal available = session.bank().operatingBalance(PayChoice.note());
        if (available == null || available.compareTo(base.charge) < 0) return null;
        com.google.gson.JsonObject quote = session.bank().quoteIssuerPayout(pay.personalId(), base.charge);
        if (quote == null || !quote.has("success") || !quote.get("success").getAsBoolean()) return null;
        for (String field : List.of("fingerprint", "privateAmount", "issuerProceeds", "centralRemainder", "unitPrice"))
            if (!quote.has(field) || quote.get(field).isJsonNull()) return null;
        return new PrivatePayoutPlan(itemId, base.entry.name(), count, pay, base.charge, base.basePrice, base.path.end(), quote.toString());
    }

    public static ShopTradeResult sellConfirmed(ShopSession session, ItemPurse purse, UUID player, PrivatePayoutPlan plan) {
        ShopTradeResult recovery = recoverPending(session, purse, player);
        if (recovery != null) return recovery;
        PrivatePayoutPlan current = quotePrivatePayout(session, plan.itemId(), plan.count(), plan.currency());
        if (!plan.same(current)) return ShopTradeResult.fail("payout_quote_changed", "报价或付款组合已经变化，请重新预览确认。");
        if (purse.plainCount(plan.itemId()) < plan.count())
            return ShopTradeResult.fail("not_enough_items", "可出售物品不足，尚未结算。");
        TradeJournal.Result result = TradeJournal.beginIssuerPayout(session, purse, player, plan);
        return new ShopTradeResult(result.success(), result.code(), result.success() ? "出售完成。" + plan.description()
                : "订单未完成：" + result.code() + "；订单：" + result.reference(), plan.itemId(), plan.itemName(), plan.count(),
                plan.priceBefore(), null, result.success() ? plan.priceAfter() : null, plan.centralBudget());
    }

    private static ShopTradeResult recoverPending(ShopSession session, ItemPurse purse, UUID player) {
        if (session.book() == null) return null;
        String ref = session.book().saved().pendingTrade(player);
        if (ref == null) return null;
        TradeJournal.Result result = TradeJournal.resume(session, purse, player, ref);
        boolean resolved = session.book().saved().pendingTrade(player) == null;
        return new ShopTradeResult(false, resolved ? "previous_recovered" : "settlement_pending",
                resolved ? "上一笔订单已核对恢复，请检查物品和余额后重新下单。"
                         : "上一笔订单结果尚待核对，请勿重复付款。订单：" + ref,
                "", "", 0, null, null, null, null);
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
            BigDecimal each = MoneyScale.positive(lookup.diamondEach())
                    ? MarketInfluence.adjusted(entry.itemId(), lookup.diamondEach(), rate(session.bank(), PayChoice.note()), config.spreadFraction()) : null;
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
        }
        TradeJournal.Result settled = TradeJournal.begin(session, purse, player, prepared.itemId, prepared.count,
                buying, prepared.pay, due, prepared.basePrice, prepared.path.end());
        if (!settled.success()) {
            String message = switch (settled.code()) {
                case "refunded" -> ShopText.refunded();
                case "previous_recovered" -> "上一笔订单已恢复，请重新检查物品及余额后下单。";
                case "bank_hook_missing" -> "当前银行或物品接口不支持可靠引用结算，交易未执行。";
                case "settlement_pending" -> "该订单尚待恢复，已暂停相关玩家/商品交易。请勿重复付款；订单：" + settled.reference();
                default -> "订单未完成：" + settled.code() + "；订单：" + settled.reference();
            };
            return fail(prepared, settled.code(), message);
        }
        BigDecimal balance = bank.playerBalance(player, prepared.pay);
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
        prepared.basePrice = lookup.diamondEach();
        prepared.lookup = lookup;
        if (!MoneyScale.positive(lookup.diamondEach())) {
            return stop(prepared, "no_quote", ShopText.noQuote(), forTrade);
        }
        String gate = settlementGate(bank, prepared.pay);
        prepared.settlementCode = gate == null ? "ready" : gate;
        if (forTrade && gate != null) {
            return stop(prepared, gate, gateMessage(gate), true);
        }
        BigDecimal rate = rate(bank, prepared.pay);
        BigDecimal referenceRate = rate(bank, PayChoice.note());
        PriceMath.Execution execution = PriceMath.execution(prepared.basePrice,
                config.stepFraction(), config.minDiamondPrice(), count, buying, config.spreadFraction(),
                base -> MarketInfluence.adjusted(prepared.itemId, base, referenceRate, config.spreadFraction()), rate);
        prepared.path = execution.path();
        prepared.lookup = new PriceBook.Lookup(entry, execution.first(), lookup.corrupt());
        prepared.charge = execution.charge();
        if (prepared.path.total().signum() <= 0) {
            return stop(prepared, "no_quote", ShopText.noQuote(), forTrade);
        }
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
        BigDecimal basePrice;
        private PriceBook.Lookup lookup;
        private PricePath path;
        private BigDecimal charge;
        private ShopQuoteView early;
        private ShopTradeResult tradeFailure;
    }
}
