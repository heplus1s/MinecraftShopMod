package dev.jdguo.shop.trade;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.catalog.*;
import dev.jdguo.shop.price.*;
import dev.jdguo.shop.support.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CatalogSafetyTest {
 @Test void catalogOnlyItemCannotBeBoughtSoldOrQuotedForPayout() {
  var bank=new MemoryBank();bank.shop.put("note",new BigDecimal("1000"));
  var purse=new MapPurse();purse.items.put("minecraft:cut_copper",64);
  var session=new ShopSession(ShopConfig.defaults().withSystemTrading("minecraft:cut_copper",false),new PriceBook(ShopSaved.empty()),bank,()->{});
  var user=UUID.randomUUID();
  assertEquals("catalog_only",TradeDesk.buy(session,purse,user,PayChoice.note(),"minecraft:cut_copper",1).code());
  assertEquals("catalog_only",TradeDesk.sell(session,purse,user,PayChoice.note(),"minecraft:cut_copper",1).code());
  assertNull(TradeDesk.quotePrivatePayout(session,"minecraft:cut_copper",1,PayChoice.personal(UUID.randomUUID(),"测试币")));
  assertEquals(0,bank.referencedCalls);assertEquals(64,purse.plainCount("minecraft:cut_copper"));
 }
 @Test void payoutLabelPreservesFractionWithoutInventingCentralChange() {
  var plan=new PrivatePayoutPlan("minecraft:redstone","红石粉",1,PayChoice.personal(UUID.randomUUID(),"测试币"),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,
   "{\"fingerprint\":\"x\",\"privateAmount\":26.4545,\"centralRemainder\":0,\"unitPrice\":0.00366666,\"issuerProceeds\":1}");
  assertTrue(plan.description().contains("26.4545 测试币"));assertFalse(plan.description().contains("央行券"));assertFalse(plan.description().contains("＋"));
 }
}
