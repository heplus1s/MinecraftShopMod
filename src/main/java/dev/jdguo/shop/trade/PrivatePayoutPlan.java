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
        JsonObject q=bankQuote();
        String fallback=q.get("privateAmount").getAsBigDecimal().signum()==0
                ? "本次无可成交私币，全部以央行券支付。\n" : "";
        return fallback + "出售 " + itemName + " ×" + count + "；总应付 " + centralBudget.toPlainString() + " 央行券。\n"
                + "你将收到 " + number(q,"privateAmount") + " " + currency.name()
                + " ＋ " + number(q,"centralRemainder") + " 央行券。\n"
                + "发行人挂单成交价：每币 " + number(q,"unitPrice") + " 央行券；发行人收到 "
                + number(q,"issuerProceeds") + " 央行券。原准备金不变。";
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
