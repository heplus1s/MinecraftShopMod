package dev.jdguo.shop.inventory;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulkShulkerPurseTest {
    private static final String SKU = "minecraft:iron_ingot";
    private static final UUID OWNER = UUID.randomUUID();

    @BeforeAll static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }
    private static NbtCompound entry(int slot, Item item, int count) {
        var row = new ItemStack(item, count).writeNbt(new NbtCompound());
        row.putByte("Slot", (byte)slot);
        return row;
    }
    private static ItemStack filled(Item box, NbtCompound... rows) {
        var contents = new NbtList();
        contents.addAll(Arrays.asList(rows));
        var entity = new NbtCompound(); entity.put("Items", contents);
        var stack = new ItemStack(box); stack.setSubNbt("BlockEntityTag", entity);
        return stack;
    }
    private static ItemStack ironBox(Item color, int count) { return filled(color, entry(0,Items.IRON_INGOT,count)); }
    private static ItemStack[] inventory() {
        var stacks=new ItemStack[36]; Arrays.fill(stacks, ItemStack.EMPTY); return stacks;
    }
    @Test void choosesOnlyWholeMatchingBoxesWithinLimitsAndLeavesEmptyStacksAlone() {
        var inv=inventory();
        inv[0]=new ItemStack(Items.SHULKER_BOX, 64); // Carpet's stacked empty boxes
        inv[1]=ironBox(Items.RED_SHULKER_BOX,16);
        inv[2]=ironBox(Items.GREEN_SHULKER_BOX,8);
        inv[3]=ironBox(Items.BLUE_SHULKER_BOX,12);
        inv[4]=ironBox(Items.ORANGE_SHULKER_BOX,6);
        var plan=BulkShulkerPurse.select(OWNER,i->inv[i],SKU,2,24);
        assertEquals(24,plan.count()); assertEquals(2,plan.boxCount());
        assertEquals(1,plan.toJson().getAsJsonArray("slots").get(0).getAsJsonObject().get("slot").getAsInt());
        assertEquals(2,plan.toJson().getAsJsonArray("slots").get(1).getAsJsonObject().get("slot").getAsInt());
        assertEquals(64,inv[0].getCount());
        assertNull(BulkShulkerPurse.select(OWNER,i->inv[i],SKU,4,5));
    }
    @Test void refusesMixedNestedDuplicateOversizedStagedOrCustomData() {
        var box = Items.SHULKER_BOX;
        var mixed=filled(box,entry(0,Items.IRON_INGOT,16),entry(1,Items.GOLD_INGOT,1));
        var duplicate=filled(box,entry(0,Items.IRON_INGOT,16),entry(0,Items.IRON_INGOT,1));
        var custom=entry(0,Items.IRON_INGOT,1); var customTag=new NbtCompound(); customTag.putInt("Enchantments",1);custom.put("tag",customTag);
        var stacked=ironBox(box,5);stacked.setCount(2);
        var named=ironBox(box,5);named.setCustomName(net.minecraft.text.Text.literal("My shulker"));
        var extra=ironBox(box,5);extra.getNbt().getCompound("BlockEntityTag").putString("LootTable","minecraft:test");
        for (var invalid : new ItemStack[]{mixed,duplicate,filled(box,custom),ironBox(box,65),stacked,named,extra})
            assertEquals(0,BulkShulkerPurse.eligibleCount(invalid,SKU));
        assertEquals(0,BulkShulkerPurse.eligibleCount(filled(box,entry(0,box,1)),"minecraft:shulker_box"));
    }
    @Test void exactSnapshotsRoundTripAndRejectChangedSlotOnTakeOrUndo() {
        var inv=inventory();inv[0]=ironBox(Items.LIME_SHULKER_BOX,32);inv[5]=ironBox(Items.RED_SHULKER_BOX,3);
        var original=BulkShulkerPurse.select(OWNER,i->inv[i],SKU,2,99);
        var plan=BulkShulkerPurse.Plan.fromJson(original.toJson());
        assertEquals(original.fingerprint(),plan.fingerprint());
        assertTrue(plan.matches(i->inv[i],false));
        assertFalse(plan.matches(i->inv[i],true));
        inv[0]=ironBox(Items.LIME_SHULKER_BOX,31);
        assertFalse(plan.matches(i->inv[i],false));
        inv[0]=new ItemStack(Items.LIME_SHULKER_BOX);inv[5]=new ItemStack(Items.RED_SHULKER_BOX);
        assertTrue(plan.matches(i->inv[i],true));
        inv[5]=new ItemStack(Items.RED_SHULKER_BOX,2);
        assertFalse(plan.matches(i->inv[i],true));
        var broken=plan.toJson();broken.getAsJsonArray("slots").get(1).getAsJsonObject().addProperty("slot",0);
        assertThrows(IllegalArgumentException.class,()->BulkShulkerPurse.Plan.fromJson(broken));
        var money=plan.toJson(); money.addProperty("count",77);
        assertThrows(IllegalArgumentException.class,()->BulkShulkerPurse.Plan.fromJson(money));
    }
}
