package dev.jdguo.shop.trade;
import dev.jdguo.shop.bank.PayChoice;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PrivatePayoutLabelTest {
 @Test void customNameChangesOnlyDescription() {
  var plan = new PrivatePayoutPlan("minecraft:stone","石头",1,PayChoice.personal(UUID.randomUUID(),"泉"),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,
   "{\"fingerprint\":\"x\",\"privateAmount\":0,\"centralRemainder\":1,\"unitPrice\":1,\"issuerProceeds\":0}");
  String before=plan.toJson().toString();
  assertTrue(plan.description("华夏券").contains("全部以华夏券支付"));
  assertFalse(plan.description("华夏券").contains("央行券"));
  assertTrue(plan.description().contains("央行券"));
  assertEquals(before,plan.toJson().toString());
 }
}
