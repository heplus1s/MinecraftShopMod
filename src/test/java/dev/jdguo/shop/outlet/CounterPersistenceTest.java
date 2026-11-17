package dev.jdguo.shop.outlet;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CounterPersistenceTest {
 @Test void oldWorldNorthAndRotatedChineseNameRoundTrip() throws Exception {
  NbtCompound row=new NbtCompound(); row.putString("name","北京 总店"); row.putString("dimension","minecraft:overworld");
  row.putInt("x",1); row.putInt("y",64); row.putInt("z",2);
  NbtList list=new NbtList();list.add(row);NbtCompound n=new NbtCompound();n.put("outlets",list);
  var read=ShopOutletState.class.getDeclaredMethod("read",NbtCompound.class);read.setAccessible(true);
  var old=(ShopOutletState)read.invoke(null,n);
  var oldRow=old.writeNbt(new NbtCompound()).getList("outlets",10).getCompound(0);
  assertEquals(0,oldRow.getInt("facing")); assertEquals("北京 总店",oldRow.getString("name"));
  row.putInt("facing",3);
  var rotated=(ShopOutletState)read.invoke(null,n);
  var saved=rotated.writeNbt(new NbtCompound()).getList("outlets",10).getCompound(0);
  assertEquals(3,saved.getInt("facing"));assertEquals(1,saved.getInt("x"));assertEquals(64,saved.getInt("y"));
 }
}
