package dev.jdguo.shop.ui;

import com.google.gson.JsonObject;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.trade.*;
import java.util.*;

/** Server-thread, player-bound, single-use confirmation tokens. Preview never moves money/items. */
public final class ShopPayoutPreviews {
    private static final Map<UUID, Preview> OPEN = new HashMap<>();
    private static final long TTL_MS = 30_000L;
    private ShopPayoutPreviews() {}
    public record Preview(String id, long expiresAt, PrivatePayoutPlan plan) {
        public JsonObject json() {
            JsonObject o=plan.toJson();
            o.addProperty("quoteId",id); o.addProperty("expiresAt",expiresAt);
            o.addProperty("description",plan.description(dev.jdguo.shop.ShopRuntime.bank.currencyName())); return o;
        }
        public String message() { return plan.description(dev.jdguo.shop.ShopRuntime.bank.currencyName())+"\n30 秒内确认：/shop confirm "+id+"；取消：/shop cancel"; }
    }
    public record Confirmation(ShopTradeResult result, Preview replacement) {}
    public static Preview create(ShopSession session, UUID player, String item, int count, PayChoice pay) {
        OPEN.remove(player);
        PrivatePayoutPlan plan=TradeDesk.quotePrivatePayout(session,item,count,pay);
        if(plan==null)return null;
        Preview p=new Preview(UUID.randomUUID().toString(),System.currentTimeMillis()+TTL_MS,plan);
        OPEN.put(player,p);return p;
    }
    public static void cancel(UUID player) { OPEN.remove(player); }
    public static void clear() { OPEN.clear(); }
    public static Confirmation confirm(ShopSession session, ItemPurse purse, UUID player, String quoteId) {
        Preview previous=OPEN.get(player);
        if(previous==null || !previous.id().equals(quoteId))
            return new Confirmation(ShopTradeResult.fail("payout_quote_missing","预览已失效或已使用，请重新预览。"),null);
        OPEN.remove(player); // A repeated click can never execute the same preview twice.
        PrivatePayoutPlan current=TradeDesk.quotePrivatePayout(session,previous.plan().itemId(),previous.plan().count(),previous.plan().currency());
        if(System.currentTimeMillis()>previous.expiresAt() || !previous.plan().same(current)) {
            Preview replacement=create(session,player,previous.plan().itemId(),previous.plan().count(),previous.plan().currency());
            return new Confirmation(ShopTradeResult.fail("payout_quote_changed", replacement==null
                    ? "当前资金或挂单不可用，未收取物品。" : "报价或收款金额已变化，请查看新预览并再次确认。"),replacement);
        }
        ShopTradeResult result=TradeDesk.sellConfirmed(session,purse,player,previous.plan());
        Preview replacement="payout_quote_changed".equals(result.code())
                ? create(session,player,previous.plan().itemId(),previous.plan().count(),previous.plan().currency()) : null;
        return new Confirmation(result,replacement);
    }
}
