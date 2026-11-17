package dev.jdguo.shop.catalog;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ExpandedCatalogTest {
 @Test void expandedCatalogIsUniqueTradableAndExcludesAdminItems() {
  var c=ShopConfig.defaults();c.validate();assertTrue(c.catalog().size()>800);
  assertEquals(c.catalog().size(),c.catalog().stream().map(CatalogEntry::itemId).distinct().count());
  for(var e:ShopConfig.catalogV3Additions())assertTrue(c.entry(e.itemId()).systemTradeEnabled(),e.itemId());
  assertTrue(c.entry("minecraft:diamond").systemTradeEnabled());
  assertTrue(c.entry("minecraft:blue_ice").systemTradeEnabled());
  for(String id:java.util.List.of("command_block","structure_block","barrier","bedrock","spawner","debug_stick"))assertNull(c.entry("minecraft:"+id));
 }
 @Test void allCategoriesExistAndMatchStableIds() {
  var cats=ShopConfig.defaults().catalog().stream().map(e->CatalogCategories.id(e.itemId())).collect(java.util.stream.Collectors.toSet());
  assertTrue(cats.containsAll(java.util.Set.of("natural","building","colored","functional","redstone","transport","food","tools","weapons","armor","materials")));
  assertEquals("transport",CatalogCategories.id("minecraft:powered_rail"));
  assertEquals("护甲",CatalogCategories.label("armor"));
 }
 @Test void migrationPreservesOverrideAndRemovalOfOldItemsAndIsIdempotent() {
  var c=ConfigJson.parse("{\"catalogVersion\":2,\"catalog\":[{\"item\":\"minecraft:blue_ice\",\"name\":\"蓝冰自定义\",\"anchorDiamonds\":\"9\"}]}");
  assertEquals(new BigDecimal("9"),c.entry("minecraft:blue_ice").anchorDiamonds());
  assertTrue(c.entry("minecraft:blue_ice").systemTradeEnabled());
  assertNull(c.entry("minecraft:diamond"));assertNull(c.entry("minecraft:comparator"));
  assertTrue(c.entry("minecraft:oak_planks").systemTradeEnabled());
  var next=ConfigJson.parse(ConfigJson.write(c));assertEquals(ConfigJson.write(c),ConfigJson.write(next));
 }
 @Test void adminToggleAndPriceEditPreserveGate() {
  var c=ShopConfig.defaults().withSystemTrading("minecraft:blue_ice",false).withAnchor("minecraft:blue_ice",new BigDecimal("2"));
  assertFalse(c.entry("minecraft:blue_ice").systemTradeEnabled());
  var enabled=c.withSystemTrading("minecraft:blue_ice",true);
  assertTrue(ConfigJson.parse(ConfigJson.write(enabled)).entry("minecraft:blue_ice").systemTradeEnabled());
 }
}
