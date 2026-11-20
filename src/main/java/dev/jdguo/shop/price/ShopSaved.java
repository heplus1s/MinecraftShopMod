package dev.jdguo.shop.price;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.jdguo.shop.bank.PayChoice;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * World price document. Version 5 adds decayable stock flow to the existing replayable inventory ledger.
 * Unknown keys are written back. A newer version is refused so an older build cannot blank the save.
 */
public final class ShopSaved {
    public static final int VERSION = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final JsonObject root;

    private ShopSaved(JsonObject root) {
        this.root = root;
    }

    public static ShopSaved empty() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("prices", new JsonObject());
        return new ShopSaved(root);
    }

    public static ShopSaved parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Shop save is empty");
        }
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalStateException("Shop save is not a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        if (root.has("version") && !root.get("version").isJsonNull()) {
            int version = root.get("version").getAsInt();
            if (version > VERSION) {
                throw new IllegalStateException("Shop save version " + version + " is newer than " + VERSION);
            }
        } else {
            root.addProperty("version", VERSION);
        }
        if (!root.has("prices")) {
            root.add("prices", new JsonObject());
        } else if (!root.get("prices").isJsonObject()) {
            throw new IllegalStateException("Shop save prices is not an object");
        }
        root.addProperty("version", VERSION);
        return new ShopSaved(root);
    }

    public long journalRevision() { return root.has("journalRevision") ? root.get("journalRevision").getAsLong() : 0L; }

    public void beginTrade(String reference, UUID player, String item, int count, boolean buying,
                           String currency, BigDecimal amount, BigDecimal before, BigDecimal after) {
        beginTrade(reference, player, item, count, buying, currency, amount, before, after, System.currentTimeMillis());
    }

    void beginTrade(String reference, UUID player, String item, int count, boolean buying,
                    String currency, BigDecimal amount, BigDecimal before, BigDecimal after, long timeMs) {
        if (count <= 0 || trades().has(reference)) throw new IllegalArgumentException("trade_input");
        if (pendingItem(item) != null) throw new IllegalStateException("settlement_pending");
        long stockBefore = stock(item);
        long stockAfter = buying ? Math.subtractExact(stockBefore, count) : Math.addExact(stockBefore, count);
        if (stockAfter < 0) throw new IllegalArgumentException("stock_insufficient");
        JsonObject row = new JsonObject();
        row.addProperty("stockBefore", stockBefore); row.addProperty("stockAfter", stockAfter);
        row.addProperty("stockRevisionBefore", stockRevision(item));
        row.addProperty("stockRevisionAfter", Math.addExact(stockRevision(item), 1));
        row.addProperty("player", player.toString()); row.addProperty("item", item);
        row.addProperty("count", count); row.addProperty("buying", buying);
        row.addProperty("currency", currency); row.addProperty("amount", amount.toPlainString());
        row.addProperty("priceBefore", before.toPlainString()); row.addProperty("priceAfter", after.toPlainString());
        row.addProperty("createdAt", timeMs);
        long slot = Math.max(StockPricing.slot(timeMs), flowSlot(item));
        row.addProperty("flowSlot", slot);
        row.addProperty("flowUnits", recentFlowAt(item, slot).add(BigDecimal.valueOf(buying ? -(long) count : count)));
        trades().add(reference, row);
        tradeState(reference, "prepared");
    }
    public void beginBulkSale(String reference, UUID player, String item, int count,
                              BigDecimal amount, BigDecimal before, BigDecimal after, JsonObject boxPlan) {
        if (boxPlan == null) throw new IllegalArgumentException("missing shulker plan");
        beginTrade(reference, player, item, count, false, "note", amount, before, after);
        JsonObject row = trades().getAsJsonObject(reference);
        row.addProperty("kind", "shulker_bulk_sale");
        row.add("boxPlan", boxPlan.deepCopy());
        if (boxPlan.has("slots") && boxPlan.get("slots").isJsonArray())
            row.addProperty("bulkBoxCount", boxPlan.getAsJsonArray("slots").size());
    }

    /** Keep recovery snapshots until the terminal journal state has been durably confirmed. */
    public void compactFinalBulkPlan(String reference) {
        JsonObject row = trades().getAsJsonObject(reference);
        if (row != null && row.has("kind") && "shulker_bulk_sale".equals(row.get("kind").getAsString())
                && ("completed".equals(row.get("state").getAsString()) || "cancelled".equals(row.get("state").getAsString()))) {
            row.remove("boxPlan");
        }
    }

    public void beginIssuerPayout(String reference, UUID player, dev.jdguo.shop.trade.PrivatePayoutPlan plan) {
        beginTrade(reference, player, plan.itemId(), plan.count(), false, plan.currency().key(),
                plan.centralBudget(), plan.priceBefore(), plan.priceAfter());
        JsonObject row = trades().getAsJsonObject(reference);
        row.addProperty("kind", "issuer_offer_payout");
        row.add("payoutPlan", plan.toJson());
    }
    public void tradeState(String reference, String state) {
        JsonObject row = trades().getAsJsonObject(reference);
        if (row == null) throw new IllegalArgumentException("Unknown reference");
        row.addProperty("state", state);
        row.addProperty("updatedAt", System.currentTimeMillis());
        bumpRevision();
    }
    public String pendingTrade(UUID player) {
        for (var entry : trades().entrySet()) {
            JsonObject row = entry.getValue().getAsJsonObject();
            String state = row.get("state").getAsString();
            if (player.toString().equals(row.get("player").getAsString())
                    && !state.equals("completed") && !state.equals("cancelled")) return entry.getKey();
        }
        return null;
    }
    public JsonObject trade(String reference) {
        JsonObject row = trades().getAsJsonObject(reference);
        return row == null ? null : row.deepCopy();
    }
    public java.util.List<String> completedTrades(UUID player) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (var entry : trades().entrySet()) {
            JsonObject row = entry.getValue().getAsJsonObject();
            String state = row.get("state").getAsString();
            if (player.toString().equals(row.get("player").getAsString())
                    && (state.equals("completed") || state.equals("cancelled"))) result.add(entry.getKey());
        }
        return result;
    }
    public String pendingItem(String item) {
        String adjustment = pendingStockAdjustment(item);
        if (adjustment != null) return adjustment;
        for (var entry : trades().entrySet()) {
            JsonObject row = entry.getValue().getAsJsonObject();
            String state = row.get("state").getAsString();
            if (item.equals(row.get("item").getAsString())
                    && !state.equals("completed") && !state.equals("cancelled")) return entry.getKey();
        }
        return null;
    }
    public com.google.gson.JsonArray marketTrades() {
        var out = new com.google.gson.JsonArray();
        var rows = trades().entrySet().stream().filter(e -> "completed".equals(e.getValue().getAsJsonObject().get("state").getAsString()))
                .sorted(java.util.Comparator.comparingLong(e -> e.getValue().getAsJsonObject().get("updatedAt").getAsLong())).toList();
        for (var entry : rows.subList(Math.max(0, rows.size() - 80), rows.size())) {
            var row = entry.getValue().getAsJsonObject(); var t = new JsonObject();
            t.addProperty("reference", entry.getKey()); t.addProperty("source", "system_shop");
            t.addProperty("itemId", row.get("item").getAsString()); t.addProperty("variantKey", "default");
            t.addProperty("count", row.get("count").getAsInt()); t.addProperty("amount", row.get("amount").getAsString());
            t.addProperty("currency", row.has("kind") && "issuer_offer_payout".equals(row.get("kind").getAsString()) ? "note" : row.get("currency").getAsString());
            t.addProperty("at", row.get("updatedAt").getAsLong()); t.addProperty("buying", row.get("buying").getAsBoolean());
            if (row.has("bulkBoxCount")) t.addProperty("bulkBoxes", row.get("bulkBoxCount").getAsInt());
            out.add(t);
        }
        return out;
    }

    /** Missing stock is zero. Malformed stock is fatal, never interpreted as free inventory. */
    public long stock(String item) {
        JsonObject stocks = object("stock");
        if (!stocks.has(item)) return 0;
        return nonnegative(stocks.getAsJsonObject(item), "count");
    }
    public long stockRevision(String item) {
        JsonObject stocks = object("stock");
        if (!stocks.has(item)) return 0;
        return nonnegative(stocks.getAsJsonObject(item), "revision");
    }
    public BigDecimal recentFlow(String item) {
        return recentFlowAt(item, StockPricing.slot(System.currentTimeMillis()));
    }

    BigDecimal recentFlowAt(String item, long slot) {
        var row = stockRecord(item);
        return row == null || !row.has("flowUnits") ? BigDecimal.ZERO
                : StockPricing.decay(flowUnits(row), nonnegative(row, "flowSlot"), slot);
    }
    private JsonObject stockRecord(String item) {
        var rows = object("stock");
        return rows.has(item) ? rows.getAsJsonObject(item) : null;
    }
    private long flowSlot(String item) {
        var row = stockRecord(item);
        return row != null && row.has("flowSlot") ? nonnegative(row, "flowSlot") : 0L;
    }
    private static BigDecimal flowUnits(JsonObject row) {
        BigDecimal units = new BigDecimal(row.get("flowUnits").getAsString());
        if (units.precision() > 40 || units.scale() > 9 || units.scale() < -20
                || units.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE).multiply(BigDecimal.valueOf(2))) > 0)
            throw new IllegalStateException("Invalid stock flow units");
        return units;
    }
    private static long nonnegative(JsonObject row, String key) {
        long value = new java.math.BigDecimal(row.get(key).getAsString()).longValueExact();
        if (value < 0) throw new IllegalStateException("Negative stock field " + key);
        return value;
    }
    private JsonObject object(String key) {
        if (!root.has(key)) root.add(key, new JsonObject());
        if (!root.get(key).isJsonObject()) throw new IllegalStateException("Invalid shop " + key);
        return root.getAsJsonObject(key);
    }
    private void bumpRevision() { root.addProperty("journalRevision", Math.addExact(journalRevision(), 1)); }
    /** Verify before external effects, and again immediately before completion. Legacy intents are exempt. */
    public void verifyTradeStock(String reference) {
        JsonObject row = trades().getAsJsonObject(reference);
        if (row == null) throw new IllegalArgumentException("Unknown reference");
        if (!row.has("stockBefore")) {
            if (row.has("stockAfter") || row.has("stockRevisionBefore") || row.has("stockRevisionAfter"))
                throw new IllegalStateException("Incomplete stock intent");
            return; // Pre-upgrade pending trades retain their original unlimited-stock semantics.
        }
        long count = new java.math.BigDecimal(row.get("count").getAsString()).longValueExact();
        if (count <= 0) throw new IllegalStateException("Invalid stock trade count");
        long expected = row.get("buying").getAsBoolean()
                ? Math.subtractExact(nonnegative(row, "stockBefore"), count)
                : Math.addExact(nonnegative(row, "stockBefore"), count);
        if (expected != nonnegative(row, "stockAfter")) throw new IllegalStateException("Invalid stock trade delta");
        verifyStock(row);
    }
    private void verifyStock(JsonObject row) {
        String item = row.get("item").getAsString();
        long before = nonnegative(row, "stockBefore"), after = nonnegative(row, "stockAfter");
        long revisionBefore = nonnegative(row, "stockRevisionBefore"), revisionAfter = nonnegative(row, "stockRevisionAfter");
        if (revisionAfter != Math.addExact(revisionBefore, 1)) throw new IllegalStateException("Stock revision gap");
        if (row.has("flowSlot") != row.has("flowUnits")) throw new IllegalStateException("Incomplete flow intent");
        if (row.has("flowSlot")) { nonnegative(row, "flowSlot"); flowUnits(row); }
        long current = stock(item), revision = stockRevision(item);
        if (!(current == before && revision == revisionBefore) && !(current == after && revision == revisionAfter))
            throw new IllegalStateException("Stock changed while intent pending");
    }
    public void applyTradeStock(String reference) {
        verifyTradeStock(reference);
        JsonObject row = trades().getAsJsonObject(reference);
        if (row.has("stockBefore")) applyStock(row);
    }
    private void applyStock(JsonObject row) {
        verifyStock(row);
        String item = row.get("item").getAsString();
        if (stockRevision(item) == nonnegative(row, "stockRevisionAfter")) return;
        JsonObject value = object("stock").has(item) ? object("stock").getAsJsonObject(item) : new JsonObject();
        value.addProperty("count", nonnegative(row, "stockAfter"));
        value.addProperty("revision", nonnegative(row, "stockRevisionAfter"));
        if (row.has("flowSlot")) {
            value.addProperty("flowSlot", nonnegative(row, "flowSlot"));
            value.addProperty("flowUnits", flowUnits(row));
        }
        object("stock").add(item, value); bumpRevision();
    }
    public String pendingStockAdjustment(String item) {
        for (var entry : object("stockAdjustments").entrySet()) {
            var row = entry.getValue().getAsJsonObject();
            if (item.equals(row.get("item").getAsString()) && !"completed".equals(row.get("state").getAsString()))
                return entry.getKey();
        }
        return null;
    }
    /** Returns an existing identical pending adjustment, or creates an audited provisioning intent. */
    public String beginStockAdjustment(String item, long target, UUID actor, String reason) {
        if (item == null || item.isBlank() || target < 0 || reason == null || reason.isBlank() || reason.length() > 256)
            throw new IllegalArgumentException("stock_adjustment_input");
        String operator = actor == null ? "console" : actor.toString();
        String pending = pendingStockAdjustment(item);
        if (pending != null) {
            var row = object("stockAdjustments").getAsJsonObject(pending);
            if (nonnegative(row, "stockAfter") == target && operator.equals(row.get("actor").getAsString())
                    && reason.equals(row.get("reason").getAsString())) return pending;
            throw new IllegalStateException("stock_adjustment_pending");
        }
        if (pendingItem(item) != null) throw new IllegalStateException("settlement_pending");
        String reference = "stock_" + UUID.randomUUID();
        JsonObject row = new JsonObject();
        row.addProperty("item", item); row.addProperty("actor", operator); row.addProperty("reason", reason);
        row.addProperty("createdAt", System.currentTimeMillis()); row.addProperty("state", "prepared");
        row.addProperty("stockBefore", stock(item)); row.addProperty("stockAfter", target);
        row.addProperty("stockRevisionBefore", stockRevision(item));
        row.addProperty("stockRevisionAfter", Math.addExact(stockRevision(item), 1));
        object("stockAdjustments").add(reference, row); bumpRevision();
        return reference;
    }
    public void applyStockAdjustment(String reference) {
        var row = object("stockAdjustments").getAsJsonObject(reference);
        if (row == null) throw new IllegalArgumentException("Unknown adjustment");
        if ("completed".equals(row.get("state").getAsString())) return;
        applyStock(row); row.addProperty("state", "completed");
        row.addProperty("updatedAt", System.currentTimeMillis()); bumpRevision();
    }
    /** A failed final persistence keeps the item locked and retryable, even if the write reached disk. */
    public void stockAdjustmentPending(String reference) {
        var row = object("stockAdjustments").getAsJsonObject(reference);
        if (row == null) throw new IllegalArgumentException("Unknown adjustment");
        row.addProperty("state", "save_pending"); bumpRevision();
    }

    private JsonObject trades() {
        if (!root.has("trades")) root.add("trades", new JsonObject());
        return root.getAsJsonObject("trades");
    }

    public String toJson() {
        return GSON.toJson(root);
    }

    public int size() {
        return prices().size();
    }

    public Read read(String itemId) {
        JsonObject prices = prices();
        if (itemId == null || !prices.has(itemId) || prices.get(itemId).isJsonNull()) {
            return Read.missing();
        }
        JsonElement element = prices.get(itemId);
        BigDecimal value = decimal(element);
        if (value == null) {
            return Read.broken();
        }
        return Read.value(value);
    }

    public void setPrice(String itemId, BigDecimal price) {
        JsonObject prices = prices();
        JsonElement existing = prices.get(itemId);
        String text = PriceMath.normalize(price).toPlainString();
        if (existing != null && existing.isJsonObject()) {
            existing.getAsJsonObject().addProperty("diamond", text);
            return;
        }
        JsonObject row = new JsonObject();
        row.addProperty("diamond", text);
        prices.add(itemId, row);
    }

    public void clearPrice(String itemId) {
        prices().remove(itemId);
    }

    public PayChoice payer(UUID player) {
        if (player == null) {
            return PayChoice.note();
        }
        JsonObject payers = payers();
        JsonElement element = payers.get(player.toString());
        if (element == null || !element.isJsonObject()) {
            return PayChoice.note();
        }
        JsonObject row = element.getAsJsonObject();
        String kind = row.has("kind") && row.get("kind").isJsonPrimitive() ? row.get("kind").getAsString() : "note";
        if (!"personal".equals(kind) || !row.has("id")) {
            return PayChoice.note();
        }
        try {
            UUID id = UUID.fromString(row.get("id").getAsString());
            String name = row.has("name") && row.get("name").isJsonPrimitive() ? row.get("name").getAsString() : id.toString();
            return PayChoice.personal(id, name);
        } catch (IllegalArgumentException error) {
            return PayChoice.note();
        }
    }

    public void setPayer(UUID player, PayChoice choice) {
        if (player == null || choice == null || choice.hard()) {
            return;
        }
        JsonObject payers = payers();
        JsonObject row;
        JsonElement existing = payers.get(player.toString());
        if (existing != null && existing.isJsonObject()) {
            row = existing.getAsJsonObject();
        } else {
            row = new JsonObject();
        }
        if (choice.personal()) {
            row.addProperty("kind", "personal");
            row.addProperty("id", choice.personalId().toString());
            row.addProperty("name", choice.name());
        } else {
            row.addProperty("kind", "note");
            row.remove("id");
        }
        payers.add(player.toString(), row);
    }

    private JsonObject prices() {
        return root.getAsJsonObject("prices");
    }

    private JsonObject payers() {
        if (!root.has("payers") || !root.get("payers").isJsonObject()) {
            root.add("payers", new JsonObject());
        }
        return root.getAsJsonObject("payers");
    }

    private static BigDecimal decimal(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) {
                return new BigDecimal(element.getAsString());
            }
            if (element.isJsonObject() && element.getAsJsonObject().has("diamond")
                    && element.getAsJsonObject().get("diamond").isJsonPrimitive()) {
                return new BigDecimal(element.getAsJsonObject().get("diamond").getAsString());
            }
        } catch (NumberFormatException | UnsupportedOperationException | ClassCastException ignored) {
            return null;
        }
        return null;
    }

    public record Read(boolean present, boolean corrupt, BigDecimal value) {
        public static Read missing() {
            return new Read(false, false, null);
        }

        public static Read broken() {
            return new Read(true, true, null);
        }

        public static Read value(BigDecimal value) {
            return new Read(true, false, value);
        }
    }
}
