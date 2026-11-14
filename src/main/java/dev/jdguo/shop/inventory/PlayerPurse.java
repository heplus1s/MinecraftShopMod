package dev.jdguo.shop.inventory;

import dev.jdguo.shop.trade.ItemPurse;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Main inventory and offhand. Armor is left alone. Stacks with extra data are not shop goods. */
public final class PlayerPurse implements ItemPurse {
    private final ServerPlayerEntity player;

    public PlayerPurse(ServerPlayerEntity player) {
        this.player = player;
    }

    public static boolean knownItem(String itemId) {
        return resolve(itemId) != null;
    }

    @Override
    public int plainCount(String itemId) {
        Item item = resolve(itemId);
        if (item == null) {
            return 0;
        }
        int count = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot : slots()) {
            ItemStack stack = inventory.getStack(slot);
            if (plain(stack, item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public int roomFor(String itemId) {
        Item item = resolve(itemId);
        if (item == null) {
            return 0;
        }
        int max = item.getMaxCount();
        int room = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot : slots()) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                room += max;
            } else if (plain(stack, item)) {
                room += Math.max(0, max - stack.getCount());
            }
        }
        return room;
    }

    @Override
    public void give(String itemId, int count) {
        Item item = resolve(itemId);
        if (item == null) {
            throw new IllegalStateException("Unknown item " + itemId);
        }
        int left = count;
        while (left > 0) {
            int piece = Math.min(left, item.getMaxCount());
            ItemStack stack = new ItemStack(item, piece);
            player.getInventory().insertStack(stack);
            if (!stack.isEmpty()) {
                player.dropItem(stack, false);
            }
            left -= piece;
        }
    }

    @Override
    public void takePlain(String itemId, int count) {
        Item item = resolve(itemId);
        if (item == null) {
            throw new IllegalStateException("Unknown item " + itemId);
        }
        int need = count;
        PlayerInventory inventory = player.getInventory();
        for (int slot : slots()) {
            if (need <= 0) {
                return;
            }
            ItemStack stack = inventory.getStack(slot);
            if (!plain(stack, item)) {
                continue;
            }
            int take = Math.min(stack.getCount(), need);
            stack.decrement(take);
            need -= take;
        }
        if (need > 0) {
            throw new IllegalStateException("Not enough plain items");
        }
    }

    private static boolean plain(ItemStack stack, Item item) {
        return stack != null && !stack.isEmpty() && stack.isOf(item) && stack.getNbt() == null;
    }

    private static int[] slots() {
        int[] slots = new int[37];
        for (int i = 0; i < 36; i++) {
            slots[i] = i;
        }
        slots[36] = PlayerInventory.OFF_HAND_SLOT;
        return slots;
    }

    private static Item resolve(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) {
            return null;
        }
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) {
            return null;
        }
        return item;
    }
}
