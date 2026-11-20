package dev.jdguo.shop.trade;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.catalog.*;
import dev.jdguo.shop.price.*;
import dev.jdguo.shop.support.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class BatchLimitTest {
 @Test void defaultIs100OldExplicitConfigIsPreserved() {
  assertEquals(100,ShopConfig.defaults().maxCount());
  var c=ConfigJson.parse(ConfigJson.write(ShopConfig.defaults().withMaxCount(64)));
  assertEquals(64,c.maxCount());assertEquals(200,c.withMaxCount(200).maxCount());
  assertThrows(IllegalArgumentException.class,()->c.withMaxCount(0));
  assertThrows(IllegalArgumentException.class,()->c.withMaxCount(2305));
 }
 @Test void item101IsRejectedBeforeInventoryOrMoneyMoves() {
  var b=new MemoryBank();b.shop.put("note",new BigDecimal("1000"));var p=new MapPurse();p.items.put("minecraft:cut_copper",101);
  var s=new ShopSession(ShopConfig.defaults(),new PriceBook(ShopSaved.empty()),b,()->{});
  assertEquals("bad_count",TradeDesk.sell(s,p,UUID.randomUUID(),PayChoice.note(),"minecraft:cut_copper",101).code());
  assertEquals(101,p.plainCount("minecraft:cut_copper"));assertEquals(0,b.referencedCalls);
 }
 @Test void batchAndSplitSalesShareCurveIncludingReload() {
  var config=ShopConfig.defaults();var b1=new MemoryBank();var b2=new MemoryBank();
  b1.shop.put("note",new BigDecimal("1000"));b2.shop.put("note",new BigDecimal("1000"));
  var p1=new MapPurse();var p2=new MapPurse();p1.items.put("minecraft:cut_copper",100);p2.items.put("minecraft:cut_copper",100);
  var a=new ShopSession(config,new PriceBook(ShopSaved.empty()),b1,()->{});
  var b=new ShopSession(config,new PriceBook(ShopSaved.empty()),b2,()->{});
  assertTrue(TradeDesk.sell(a,p1,UUID.randomUUID(),PayChoice.note(),"minecraft:cut_copper",100).success());
  for(int i=0;i<100;i++) {
   assertTrue(TradeDesk.sell(b,p2,UUID.randomUUID(),PayChoice.note(),"minecraft:cut_copper",1).success());
   if(i==49)b=new ShopSession(config,new PriceBook(ShopSaved.parse(b.book().saved().toJson())),b2,()->{});
  }
  assertEquals(0,b1.shop("note").compareTo(b2.shop("note")));
  assertEquals(100,a.book().saved().stock("minecraft:cut_copper"));
  assertEquals(a.book().saved().stock("minecraft:cut_copper"),b.book().saved().stock("minecraft:cut_copper"));
  var anchor=config.entry("minecraft:cut_copper").anchorDiamonds();
  int target=TradeDesk.targetStock(config.entry("minecraft:cut_copper"));
  assertTrue(StockPricing.midpoint(anchor,target,100,config).compareTo(StockPricing.midpoint(anchor,target,0,config))<0);
 }
 @Test void emptyOperatingAccountDoesNotAutoFundOrTakeGoods() {
  var b=new MemoryBank();var p=new MapPurse();p.items.put("minecraft:cut_copper",1);
  var s=new ShopSession(ShopConfig.defaults(),new PriceBook(ShopSaved.empty()),b,()->{});
  assertFalse(TradeDesk.sell(s,p,UUID.randomUUID(),PayChoice.note(),"minecraft:cut_copper",1).success());
  assertEquals(1,p.plainCount("minecraft:cut_copper"));assertEquals(0,b.referencedCalls);assertEquals(0,b.shop("note").signum());
 }
}
