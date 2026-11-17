package dev.jdguo.shop.trade;

import com.google.gson.JsonObject;
import dev.jdguo.shop.bank.PayChoice;
import java.math.BigDecimal;

/** Immutable, server-authored payout composition. No quote is accepted from the client. */
public record PrivatePayoutPlan(String itemId, String itemName, int count, PayChoice currency,
                                BigDecimal centralBudget, BigDecimal priceBefore, BigDecimal priceAfter,
                                String bankQuoteJson) {
    public JsonObject bankQuote() { return com.google.gson.JsonParser.parseString(bankQuoteJson).getAsJsonObject(); }
    public String fingerprint() { return bankQuote().get("fingerprint").getAsString(); }
    public boolean same(PrivatePayoutPlan other) {
        return other != null && itemId.equals(other.itemId) && count == other.count
                && currency.key().equals(other.currency.key()) && centralBudget.compareTo(other.centralBudget)==0
                && priceBefore.compareTo(other.priceBefore)==0 && priceAfter.compareTo(other.priceAfter)==0
                && fingerprint().equals(other.fingerprint());
    }
    public String description() {
        return description("央行券");
    }
    public String description(String centralName) {
        if (centralName == null || centralName.isBlank()) centralName = "央行券";
        JsonObject q=bankQuote();
        // Old journal entries may still contain a mixed payout. Describe them truthfully;
        // new quotes are validated as entirely personal currency before entering the journal.
        String legacyRemainder = q.get("centralRemainder").getAsBigDecimal().signum() > 0
                ? " ＋ " + number(q,"centralRemainder") + " " + centralName + "（旧订单尾款）" : "";
        return "出售 " + itemName + " ×" + count + "；实收 " + number(q,"privateAmount") + " "
                + currency.name() + legacyRemainder + "。\n"
                + "收款来自发行人现有挂单，不增发货币、不改变原准备金。";

    }
    private static String number(JsonObject q,String key) { return q.get(key).getAsBigDecimal().stripTrailingZeros().toPlainString(); }
    public JsonObject toJson() {
        JsonObject row=new JsonObject();
        row.addProperty("item",itemId); row.addProperty("itemName",itemName); row.addProperty("count",count);
        row.addProperty("currency",currency.key()); row.addProperty("currencyName",currency.name());
        row.addProperty("budget",centralBudget.toPlainString()); row.addProperty("priceBefore",priceBefore.toPlainString());
        row.addProperty("priceAfter",priceAfter.toPlainString()); row.add("bankQuote",bankQuote());
        return row;
    }
    public static PrivatePayoutPlan fromJson(JsonObject q) {
        return new PrivatePayoutPlan(q.get("item").getAsString(),q.get("itemName").getAsString(),q.get("count").getAsInt(),
                PayChoice.personal(java.util.UUID.fromString(q.get("currency").getAsString().substring(9)),q.get("currencyName").getAsString()),
                q.get("budget").getAsBigDecimal(),q.get("priceBefore").getAsBigDecimal(),q.get("priceAfter").getAsBigDecimal(),q.get("bankQuote").toString());
    }
}
