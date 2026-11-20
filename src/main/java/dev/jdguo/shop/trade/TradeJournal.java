package dev.jdguo.shop.trade;

import com.google.gson.JsonObject;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.bank.ShopBank;
import java.math.BigDecimal;
import java.util.UUID;

/** Durable intent plus independent bank/player receipts. Unknown outcomes stay locked,
 * never converted into a guessed refund or another delivery. Server-thread only. */
public final class TradeJournal {
    private TradeJournal() {}
    public record Result(boolean success, String code, String reference) {}

    public static Result begin(ShopSession session, ItemPurse purse, UUID player, String item, int count,
                               boolean buying, PayChoice pay, BigDecimal amount, BigDecimal before, BigDecimal after) {
        if (!session.bank().supportsReferencedSettlement() || !purse.durableDelivery())
            return new Result(false, "bank_hook_missing", "");
        String pending = session.book().saved().pendingTrade(player);
        if (pending != null) {
            Result resumed = resume(session, purse, player, pending);
            return new Result(false, resumed.success() ? "previous_recovered" : resumed.code(), pending);
        }
        String itemPending = session.book().saved().pendingItem(item);
        if (itemPending != null) return new Result(false, "settlement_pending", itemPending);
        if (count <= 0) return new Result(false, "invalid_count", "");
        long available = session.book().saved().stock(item);
        if (buying && available < count) return new Result(false, "stock_insufficient", "");
        if (!buying && available > Long.MAX_VALUE - count) return new Result(false, "stock_overflow", "");
        String ref = "shop_" + UUID.randomUUID();
        session.book().saved().beginTrade(ref, player, item, count, buying, pay.key(), amount, before, after);
        try { session.persist(); }
        catch (RuntimeException uncertain) { return new Result(false, "settlement_pending", ref); }
        return resume(session, purse, player, ref);
    }

    public static Result beginBulkSale(ShopSession session, ItemPurse purse, UUID player, String item, int count,
                                       BigDecimal amount, BigDecimal before, BigDecimal after, JsonObject boxPlan) {
        if (!session.bank().supportsReferencedSettlement() || !purse.durableDelivery())
            return new Result(false, "bank_hook_missing", "");
        var saved = session.book().saved();
        String pending = saved.pendingTrade(player);
        if (pending == null) pending = saved.pendingItem(item);
        if (pending != null) return pending(pending);
        if (count < 1 || count > TradeDesk.BULK_MAX_COUNT || saved.stock(item) > Long.MAX_VALUE - count)
            return new Result(false, "stock_overflow", "");
        String ref = "shop_" + UUID.randomUUID();
        saved.beginBulkSale(ref, player, item, count, amount, before, after, boxPlan);
        try { session.persist(); }
        catch (RuntimeException uncertain) { return pending(ref); }
        return resume(session, purse, player, ref);
    }

    public static Result beginIssuerPayout(ShopSession session, ItemPurse purse, UUID player, PrivatePayoutPlan plan) {
        if (!session.bank().supportsReferencedSettlement() || !purse.durableDelivery())
            return new Result(false, "bank_hook_missing", "");
        String pending = session.book().saved().pendingTrade(player);
        if (pending != null) return new Result(false, "settlement_pending", pending);
        pending = session.book().saved().pendingItem(plan.itemId());
        if (pending != null) return new Result(false, "settlement_pending", pending);
        if (plan.count() <= 0) return new Result(false, "invalid_count", "");
        if (session.book().saved().stock(plan.itemId()) > Long.MAX_VALUE - plan.count())
            return new Result(false, "stock_overflow", "");
        String ref = "shop_" + UUID.randomUUID();
        session.book().saved().beginIssuerPayout(ref, player, plan);
        try { session.persist(); }
        catch (RuntimeException uncertain) { return new Result(false, "settlement_pending", ref); }
        return resume(session, purse, player, ref);
    }

    public static Result resume(ShopSession session, ItemPurse purse, UUID player, String ref) {
        var saved = session.book().saved();
        JsonObject row = saved.trade(ref);
        if (row == null || !row.get("player").getAsString().equals(player.toString()))
            return new Result(false, "settlement_pending", ref);
        String recordedState = row.get("state").getAsString();
        if ("completed".equals(recordedState)) return new Result(true, "ok", ref);
        if ("cancelled".equals(recordedState)) return new Result(false, "cancelled", ref);
        String item = row.get("item").getAsString();
        int count = row.get("count").getAsInt();
        boolean buying = row.get("buying").getAsBoolean();
        if (row.has("kind") && "shulker_bulk_sale".equals(row.get("kind").getAsString())) {
            if (buying) return pending(ref);
            try {
                purse = purse.forBulk(row.getAsJsonObject("boxPlan"));
                if (purse == null) return pending(ref);
            } catch (RuntimeException invalidPlan) { return pending(ref); }
        }
        PrivatePayoutPlan payout = row.has("kind") && "issuer_offer_payout".equals(row.get("kind").getAsString())
                ? PrivatePayoutPlan.fromJson(row.getAsJsonObject("payoutPlan")) : null;
        String currency = row.get("currency").getAsString();
        PayChoice pay = currency.startsWith("personal:")
                ? PayChoice.personal(UUID.fromString(currency.substring(9)), currency) : PayChoice.note();
        BigDecimal amount = row.get("amount").getAsBigDecimal();
        String deliveryRef = ref + (buying ? "_give" : "_take");
        String undoRef = ref + "_undo";
        try {
            saved.verifyTradeStock(ref);
            String bankState = session.bank().receiptStatus(ref);
            String delivery = purse.receiptStatus(deliveryRef);
            if ((!"none".equals(bankState) && !"applied".equals(bankState) && !"rolled_back".equals(bankState))
                    || (!"none".equals(delivery) && !"applied".equals(delivery))) return pending(ref);
            if ("rolled_back".equals(bankState)) {
                if (buying && "applied".equals(delivery)) return pending(ref);
                if (!buying && "applied".equals(delivery)
                        && purse.transferReferenced(undoRef, item, count, true) != ItemPurse.Delivery.APPLIED) return pending(ref);
                return cancel(session, purse, ref, deliveryRef, undoRef, "refunded");
            }
            // A rejected sale's item restoration may already have completed before a crash.
            if ("applied".equals(purse.receiptStatus(undoRef))) {
                if (!"none".equals(bankState)) return pending(ref);
                return cancel(session, purse, ref, deliveryRef, undoRef, "bank_rejected");
            }
            if (payout != null && "none".equals(bankState)) {
                JsonObject current = session.bank().quoteIssuerPayout(payout.currency().personalId(), payout.centralBudget());
                if (current == null || !current.has("success") || !current.get("success").getAsBoolean()
                        || !current.has("fingerprint") || !payout.fingerprint().equals(current.get("fingerprint").getAsString())) {
                    if ("applied".equals(delivery)
                            && purse.transferReferenced(undoRef, item, count, true) != ItemPurse.Delivery.APPLIED) return pending(ref);
                    return cancel(session, purse, ref, deliveryRef, undoRef, "payout_quote_changed");
                }
            }
            if (!buying && !"applied".equals(delivery)) {
                state(session, ref, "taking_items");
                var moved = purse.transferReferenced(deliveryRef, item, count, false);
                if (moved == ItemPurse.Delivery.UNKNOWN) return pending(ref);
                if (moved == ItemPurse.Delivery.NO_CHANGE)
                    return cancel(session, purse, ref, deliveryRef, undoRef, "inventory_rejected");
            }
            if (!"applied".equals(bankState)) {
                state(session, ref, "posting_bank");
                ShopBank.PostResult paid = payout == null
                        ? session.bank().postReferenced(player, pay, amount, buying, ref)
                        : session.bank().payIssuerOffer(player, payout.currency().personalId(), payout.centralBudget(), payout.fingerprint(), ref);
                if (!paid.success()) {
                    if ("settlement_unknown".equals(paid.code()) || "bank_pending".equals(paid.code())
                            || "persistence_failed".equals(paid.code()) || "save_failed".equals(paid.code())) return pending(ref);
                    String actual = session.bank().receiptStatus(ref);
                    if (!"none".equals(actual)) return pending(ref);
                    if (!buying && purse.transferReferenced(undoRef, item, count, true) != ItemPurse.Delivery.APPLIED)
                        return pending(ref);
                    return cancel(session, purse, ref, deliveryRef, undoRef, paid.code());
                }
            }
            state(session, ref, "bank_applied");
            if (buying && !"applied".equals(delivery)) {
                state(session, ref, "giving_items");
                var moved = purse.transferReferenced(deliveryRef, item, count, true);
                if (moved == ItemPurse.Delivery.UNKNOWN) return pending(ref);
                if (moved == ItemPurse.Delivery.NO_CHANGE) {
                    if (!session.bank().rollback(ref).success()) return pending(ref);
                    return cancel(session, purse, ref, deliveryRef, undoRef, "refunded");
                }
            }
            BigDecimal before = row.get("priceBefore").getAsBigDecimal();
            BigDecimal after = row.get("priceAfter").getAsBigDecimal();
            boolean stockAware = row.has("stockBefore");
            if (!stockAware) {
                BigDecimal current = session.book().lookup(session.config(), item).diamondEach();
                if (current == null || (current.compareTo(before) != 0 && current.compareTo(after) != 0)) return pending(ref);
            }
            saved.verifyTradeStock(ref);
            state(session, ref, "completing");
            // Goods are durably proved above. Release bank locks idempotently before
            // finalizing the shop journal; a failed acknowledgement must never redeliver.
            if (!session.bank().completePayment(ref).success()) return pending(ref);
            saved.applyTradeStock(ref);
            if (!stockAware) session.book().commit(item, after);
            saved.tradeState(ref, "completed");
            try { session.persist(); }
            catch (RuntimeException uncertain) { saved.tradeState(ref, "completing"); return pending(ref); }
            saved.compactFinalBulkPlan(ref);
            purse.forgetReceipt(deliveryRef); purse.forgetReceipt(undoRef);
            return new Result(true, "ok", ref);
        } catch (RuntimeException uncertain) { return pending(ref); }
    }

    private static void state(ShopSession session, String ref, String state) {
        session.book().saved().tradeState(ref, state); session.persist();
    }
    private static Result cancel(ShopSession session, ItemPurse purse, String ref, String delivery, String undo, String code) {
        session.book().saved().tradeState(ref, "cancelled");
        try { session.persist(); }
        catch (RuntimeException uncertain) { session.book().saved().tradeState(ref, "cancel_pending"); return pending(ref); }
        session.book().saved().compactFinalBulkPlan(ref);
        purse.forgetReceipt(delivery); purse.forgetReceipt(undo);
        return new Result(false, code, ref);
    }
    private static Result pending(String ref) { return new Result(false, "settlement_pending", ref); }
}
