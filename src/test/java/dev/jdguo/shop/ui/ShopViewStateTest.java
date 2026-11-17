package dev.jdguo.shop.ui;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
class ShopViewStateTest {
 @Test void retainedSettingsAreAnImmutableSnapshot() {
  var amounts=new HashMap<String,String>();amounts.put("minecraft:redstone","10");
  var state=new ShopViewState("personal:abc",false,"红石与机械",144,amounts);
  amounts.put("minecraft:redstone","1");
  assertEquals("personal:abc",state.currencyId());assertEquals("10",state.quantities().get("minecraft:redstone"));
  assertEquals(144,state.scroll());assertEquals("红石与机械",state.category());
 }
 @Test void openFromServerUsesSavedCurrencyAndNoStaleQuote() {
  var data=new JsonObject();data.addProperty("currencyKey","personal:abc");
  assertEquals("personal:abc",ShopViewState.fromSnapshot(data).currencyId());
  assertTrue(ShopViewState.fromSnapshot(data).quantities().isEmpty());
 }
}
