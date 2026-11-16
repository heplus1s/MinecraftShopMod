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

    private static final String RECEIPT_PREFIX = "shopmod_tx_";
    @Override public boolean durableDelivery() { return true; }

    @Override
    public Delivery transferReferenced(String reference, String itemId, int count, boolean give) {
        String status = receiptStatus(reference);
        if ("applied".equals(status)) return Delivery.APPLIED;
        if (!"none".equals(status)) return Delivery.UNKNOWN;
        String marker = RECEIPT_PREFIX + reference;
        // Do not repeat an unconfirmed in-memory delivery after an I/O failure.
        if (player.getCommandTags().contains(marker)) return Delivery.UNKNOWN;
        if (player.getCommandTags().size() >= 1024) return Delivery.NO_CHANGE;
        try { if (give) give(itemId, count); else takePlain(itemId, count); }
        catch (RuntimeException unchanged) { return Delivery.NO_CHANGE; }
        if (!player.addCommandTag(marker)) return Delivery.UNKNOWN;
        try {
            DurablePlayerInventory.save(player);
            return "applied".equals(receiptStatus(reference)) ? Delivery.APPLIED : Delivery.UNKNOWN;
        } catch (Exception uncertain) { return Delivery.UNKNOWN; }
    }

    private java.nio.file.Path playerFile() {
        return player.getServer().getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA)
                .resolve(player.getUuidAsString() + ".dat");
    }

    @Override
    public String receiptStatus(String reference) {
        try {
            java.nio.file.Path path = playerFile();
            if (!java.nio.file.Files.exists(path)) {
                // A new player still needs an initial durable inventory baseline.
                player.getServer().getPlayerManager().saveAllPlayerData();
            }
            net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.NbtIo.readCompressed(path.toFile());
            var tags = nbt.getList("Tags", 8);
            for (int i = 0; i < tags.size(); i++)
                if ((RECEIPT_PREFIX + reference).equals(tags.getString(i)))
                    return player.getCommandTags().contains(RECEIPT_PREFIX + reference)
                            && DurablePlayerInventory.integratedReceiptConfirmed(player, RECEIPT_PREFIX + reference)
                            ? "applied" : "unknown";
            // A memory/host receipt without matching playerdata is uncertain, not
            // evidence that delivery never happened. Journal cancellation must not
            // discard an already-taken item or refund an already-given item.
            return missingDiskReceiptStatus(player.getCommandTags().contains(RECEIPT_PREFIX + reference),
                    DurablePlayerInventory.integratedHostReceiptPresent(player, RECEIPT_PREFIX + reference));
        } catch (Exception unreadable) { return "unknown"; }
    }

    static String missingDiskReceiptStatus(boolean memoryReceipt, boolean hostReceipt) {
        return memoryReceipt || hostReceipt ? "unknown" : "none";
    }

    @Override public void forgetReceipt(String reference) {
        player.removeScoreboardTag(RECEIPT_PREFIX + reference);
    }

    @Override
    public boolean failureAtomic() { return true; }

    @Override
    public void give(String itemId, int count) {
        Item item = resolve(itemId);
        if (item == null || count < 0 || roomFor(itemId) < count)
            throw new IllegalStateException("Not enough inventory room or invalid item");
        atomicInventory(() -> {
            int left = count;
            while (left > 0) {
                int piece = Math.min(left, item.getMaxCount());
                ItemStack stack = new ItemStack(item, piece);
                player.getInventory().insertStack(stack);
                // Never drop to the world: a partial drop cannot be safely refunded.
                if (!stack.isEmpty()) throw new IllegalStateException("Inventory changed while giving");
                left -= piece;
            }
        });
    }

    @Override
    public void takePlain(String itemId, int count) {
        Item item = resolve(itemId);
        if (item == null || count < 0 || plainCount(itemId) < count)
            throw new IllegalStateException("Not enough plain items or invalid item");
        atomicInventory(() -> {
            int need = count;
            PlayerInventory inventory = player.getInventory();
            for (int slot : slots()) {
                if (need <= 0) break;
                ItemStack stack = inventory.getStack(slot);
                if (!plain(stack, item)) continue;
                int take = Math.min(stack.getCount(), need);
                stack.decrement(take);
                need -= take;
            }
            if (need > 0) throw new IllegalStateException("Inventory changed while taking");
        });
    }

    private void atomicInventory(Runnable operation) {
        PlayerInventory inventory = player.getInventory();
        java.util.List<ItemStack> before = new java.util.ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) before.add(inventory.getStack(slot).copy());
        try {
            operation.run();
            inventory.markDirty();
        } catch (RuntimeException error) {
            for (int slot = 0; slot < before.size(); slot++) inventory.setStack(slot, before.get(slot));
            inventory.markDirty();
            throw error;
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
