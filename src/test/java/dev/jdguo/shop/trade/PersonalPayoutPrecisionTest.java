package dev.jdguo.shop.trade;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.shop.bank.*;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.*;
import dev.jdguo.shop.support.MemoryBank;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PersonalPayoutPrecisionTest {
 final PayChoice pay=PayChoice.personal(UUID.randomUUID(),"测试币");
 ShopSession session(String json) {
  var bank=new MemoryBank();bank.shop.put("note",new BigDecimal("1000"));
  JsonObject quote=JsonParser.parseString(json).getAsJsonObject();
  ShopBank proxy=(ShopBank)java.lang.reflect.Proxy.newProxyInstance(ShopBank.class.getClassLoader(),new Class<?>[]{ShopBank.class},(p,m,a)->{
   if(m.getName().equals("quoteIssuerPayout"))return quote.deepCopy();
   return m.invoke(bank,a);
  });
  return new ShopSession(ShopConfig.defaults(),new PriceBook(ShopSaved.empty()),proxy,()->{});
 }
 @Test void decimalPersonalPayoutIsPreserved() {
  var s=session("{\"success\":true,\"fingerprint\":\"a\",\"privateAmount\":26.4545,\"centralRemainder\":0,\"issuerProceeds\":0.097,\"unitPrice\":0.00366666}");
  var plan=TradeDesk.quotePrivatePayout(s,"minecraft:redstone",10,pay);
  assertNotNull(plan);assertEquals(new BigDecimal("26.4545"),plan.bankQuote().get("privateAmount").getAsBigDecimal());
 }
 @Test void olderBankMixedPayoutCannotSilentlyExecute() {
  var s=session("{\"success\":true,\"fingerprint\":\"a\",\"privateAmount\":26,\"centralRemainder\":0.0016,\"issuerProceeds\":0.0954,\"unitPrice\":0.00366666}");
  assertNull(TradeDesk.quotePrivatePayout(s,"minecraft:redstone",10,pay));
 }
 @Test void precisionFailureReachesUserFacingReason() {
  var s=session("{\"success\":false,\"code\":\"precision\",\"message\":\"当前金额无法按个人币精度精确结算，请调整数量\"}");
  assertNull(TradeDesk.quotePrivatePayout(s,"minecraft:redstone",10,pay));
  assertEquals("当前金额无法按个人币精度精确结算，请调整数量",TradeDesk.privatePayoutUnavailable(s,"minecraft:redstone",10,pay));
 }
}
