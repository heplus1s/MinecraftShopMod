package dev.jdguo.shop.catalog;
import net.minecraft.SharedConstants;
import net.minecraft.Bootstrap;
import net.minecraft.registry.Registries;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Full mapped Minecraft test, not merely checking translated block names. */
class VanillaCatalogRegistryTest {
 @Test void everyDefaultCatalogIdIsAnActualInventoryItem() {
  SharedConstants.createGameVersion();
  Bootstrap.initialize();
  for(var entry:ShopConfig.defaults().catalog()) {
   var id=new Identifier(entry.itemId());
   assertTrue(Registries.ITEM.containsId(id),entry.itemId());
   assertNotSame(Items.AIR,Registries.ITEM.get(id),entry.itemId());
  }
 }
}
