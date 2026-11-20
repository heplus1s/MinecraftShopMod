package dev.jdguo.shop.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jdguo.shop.trade.ItemPurse;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;

/** Whole plain, single-SKU shulkers are emptied in place. No loose inventory is ever used. */
public final class BulkShulkerPurse implements ItemPurse {
    private static final int MAIN_SLOTS = 36;
    private final ServerPlayerEntity player;
    private final PlayerPurse receipts;
    private final Plan plan;

    public BulkShulkerPurse(ServerPlayerEntity player, Plan plan) {
        if (!player.getUuid().equals(plan.owner)) throw new IllegalArgumentException("Plan owner mismatch");
        this.player = player;
        this.receipts = new PlayerPurse(player);
        this.plan = plan;
    }

    public static Plan plan(ServerPlayerEntity player, String itemId, int maxBoxes, int countLimit) {
        return select(player.getUuid(), player.getInventory()::getStack, itemId, maxBoxes, countLimit);
    }

    static Plan select(UUID player, IntFunction<ItemStack> inventory, String itemId, int maxBoxes, int countLimit) {
        if (maxBoxes < 1 || maxBoxes > MAIN_SLOTS || countLimit < 1) throw new IllegalArgumentException("Bulk limits");
        List<Slot> slots = new ArrayList<>();
        int count = 0;
        for (int index = 0; index < MAIN_SLOTS && slots.size() < maxBoxes; index++) {
            ItemStack stack = inventory.apply(index);
            int items = eligibleCount(stack, itemId);
            if (items <= 0 || items > countLimit - count) continue;
            slots.add(new Slot(index, stack.copy(), new ItemStack(stack.getItem())));
            count += items;
        }
        return slots.isEmpty() ? null : new Plan(player, itemId, count, slots);
    }

    static int eligibleCount(ItemStack box, String itemId) {
        if (box.isEmpty() || box.getCount() != 1 || !isShulker(box)) return 0;
        NbtCompound outer = box.getNbt();
        if (outer == null || outer.getSize() != 1 || !outer.contains("BlockEntityTag", NbtElement.COMPOUND_TYPE)) return 0;
        NbtCompound content = outer.getCompound("BlockEntityTag");
        if (!content.contains("Items", NbtElement.LIST_TYPE)
                || content.getSize() != (content.contains("id") ? 2 : 1)
                || content.contains("id") && (!content.contains("id", NbtElement.STRING_TYPE)
                || !"minecraft:shulker_box".equals(content.getString("id")))) return 0;
        var items = content.getList("Items", NbtElement.COMPOUND_TYPE);
        if (items.isEmpty() || items.size() > 27) return 0;
        var id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id) || !id.toString().equals(itemId)) return 0;
        var item = Registries.ITEM.get(id);
        if (item == net.minecraft.item.Items.AIR || item instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock) return 0;
        int total = 0, occupied = 0;
        for (int i = 0; i < items.size(); i++) {
            NbtCompound row = items.getCompound(i);
            if (row.getSize() != (row.contains("tag") ? 4 : 3)
                    || !row.contains("Slot", NbtElement.BYTE_TYPE) || !row.contains("Count", NbtElement.BYTE_TYPE)
                    || !row.contains("id", NbtElement.STRING_TYPE) || !itemId.equals(row.getString("id"))) return 0;
            if (row.contains("tag")) {
                if (!row.contains("tag", NbtElement.COMPOUND_TYPE)) return 0;
                NbtCompound tag = row.getCompound("tag");
                if (!tag.isEmpty() && !(item.getMaxDamage() > 0 && tag.getSize() == 1
                        && tag.contains("Damage", NbtElement.INT_TYPE) && tag.getInt("Damage") == 0)) return 0;
            }
            int position = row.getByte("Slot"), count = row.getByte("Count");
            if (position < 0 || position >= 27 || count < 1 || count > item.getMaxCount()
                    || (occupied & 1 << position) != 0) return 0;
            occupied |= 1 << position;
            total += count;
        }
        return total;
    }

    private static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock;
    }

    private record Slot(int index, ItemStack before, ItemStack after) {}

    /** Immutable server-created selection, persisted in the shop journal before effects. */
    public static final class Plan {
        private final UUID owner;
        private final String itemId;
        private final int count;
        private final List<Slot> slots;

        private Plan(UUID owner, String itemId, int count, List<Slot> slots) {
            this.owner = owner; this.itemId = itemId; this.count = count; this.slots = List.copyOf(slots);
        }

        public int count() { return count; }
        public int boxCount() { return slots.size(); }
        public String itemId() { return itemId; }
        public String fingerprint() {
            try {
                // Deterministic even if compound key insertion order changes on save/reload.
                var digest = MessageDigest.getInstance("SHA-256");
                digest.update((owner + "\n" + itemId + "\n" + count).getBytes(StandardCharsets.UTF_8));
                for (Slot slot : slots) {
                    var box = slot.before;
                    var block = box.getNbt().getCompound("BlockEntityTag");
                    digest.update(("\n" + slot.index + ":" + Registries.ITEM.getId(box.getItem())
                            + ":" + block.contains("id")).getBytes(StandardCharsets.UTF_8));
                    var list = block.getList("Items", NbtElement.COMPOUND_TYPE);
                    for (int i = 0; i < list.size(); i++) {
                        var row = list.getCompound(i);
                        int tag = !row.contains("tag") ? 0 : row.getCompound("tag").isEmpty() ? 1 : 2;
                        digest.update((";" + row.getByte("Slot") + ":" + row.getByte("Count") + ":" + tag)
                                .getBytes(StandardCharsets.UTF_8));
                    }
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }

        public JsonObject toJson() {
            var out = new JsonObject();
            out.addProperty("owner", owner.toString());
            out.addProperty("item", itemId);
            out.addProperty("count", count);
            var rows = new JsonArray();
            for (Slot slot : slots) {
                var row = new JsonObject(); row.addProperty("slot", slot.index);
                row.addProperty("before", slot.before.writeNbt(new NbtCompound()).toString());
                row.addProperty("after", slot.after.writeNbt(new NbtCompound()).toString());
                rows.add(row);
            }
            out.add("slots", rows);
            return out;
        }

        public static Plan fromJson(JsonObject input) {
            var rows = input.getAsJsonArray("slots");
            if (rows.isEmpty() || rows.size() > MAIN_SLOTS) throw new IllegalArgumentException("Invalid bulk slots");
            var owner = UUID.fromString(input.get("owner").getAsString());
            String item = input.get("item").getAsString();
            List<Slot> slots = new ArrayList<>();
            int count = 0, last = -1;
            for (var raw : rows) {
                var row = raw.getAsJsonObject();
                int slot = new java.math.BigDecimal(row.get("slot").getAsString()).intValueExact();
                if (slot <= last || slot >= MAIN_SLOTS) throw new IllegalArgumentException("Invalid bulk slot");
                last = slot;
                var before = parseStack(row.get("before").getAsString());
                var after = parseStack(row.get("after").getAsString());
                int amount = eligibleCount(before, item);
                if (amount <= 0 || !ItemStack.areEqual(after, new ItemStack(before.getItem())))
                    throw new IllegalArgumentException("Invalid bulk snapshot");
                count += amount;
                slots.add(new Slot(slot, before, after));
            }
            if (count != new java.math.BigDecimal(input.get("count").getAsString()).intValueExact())
                throw new IllegalArgumentException("Invalid bulk count");
            return new Plan(owner, item, count, slots);
        }

        boolean matches(IntFunction<ItemStack> inventory, boolean after) {
            return slots.stream().allMatch(s -> ItemStack.areEqual(inventory.apply(s.index), after ? s.after : s.before));
        }

        private static ItemStack parseStack(String snbt) {
            if (snbt.length() > 8192) throw new IllegalArgumentException("Bulk stack too large");
            try {
                var row = StringNbtReader.parse(snbt);
                if (row.getSize() != (row.contains("tag") ? 3 : 2)
                        || !row.contains("id", NbtElement.STRING_TYPE) || !row.contains("Count", NbtElement.BYTE_TYPE)
                        || row.getByte("Count") != 1 || row.contains("tag") && !row.contains("tag", NbtElement.COMPOUND_TYPE))
                    throw new IllegalArgumentException("Invalid bulk stack fields");
                return ItemStack.fromNbt(row);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException malformed) {
                throw new IllegalArgumentException("Invalid bulk stack", malformed);
            }
        }
    }

    @Override public ItemPurse forBulk(JsonObject persistedPlan) { return receipts.forBulk(persistedPlan); }

    @Override public int plainCount(String itemId) {
        return plan.itemId.equals(itemId) && matches(false) ? plan.count : 0;
    }
    @Override public int roomFor(String itemId) { return 0; }
    @Override public boolean durableDelivery() { return true; }
    @Override public String receiptStatus(String reference) { return receipts.receiptStatus(reference); }
    @Override public void forgetReceipt(String reference) { receipts.forgetReceipt(reference); }
    @Override public void give(String itemId, int count) { throw new UnsupportedOperationException("Journal required"); }
    @Override public void takePlain(String itemId, int count) { throw new UnsupportedOperationException("Journal required"); }

    private boolean matches(boolean after) {
        return plan.matches(player.getInventory()::getStack, after);
    }

    @Override public Delivery transferReferenced(String reference, String itemId, int count, boolean give) {
        if (player.getServer() == null || !player.getServer().isOnThread()
                || player.getServer().getPlayerManager().getPlayer(player.getUuid()) != player
                || !plan.itemId.equals(itemId) || plan.count != count) return Delivery.UNKNOWN;
        String suffix = give ? "_undo" : "_take";
        if (!reference.endsWith(suffix)) return Delivery.UNKNOWN;
        String status = receiptStatus(reference);
        if ("applied".equals(status)) return Delivery.APPLIED;
        if (!"none".equals(status)) return Delivery.UNKNOWN;
        if (give && !"applied".equals(receiptStatus(reference.substring(0, reference.length() - suffix.length()) + "_take")))
            return Delivery.UNKNOWN;
        // Undo never overwrites a slot the player moved or changed. Keep it locked for reconciliation.
        if (!matches(give)) return give ? Delivery.UNKNOWN : Delivery.NO_CHANGE;
        var inventory = player.getInventory();
        var replacement = plan.slots.stream().map(s -> (give ? s.before : s.after).copy()).toList();
        var originals = plan.slots.stream().map(s -> inventory.getStack(s.index).copy()).toList();
        if (!receipts.addReceipt(reference)) return give ? Delivery.UNKNOWN : Delivery.NO_CHANGE;
        try {
            for (int i = 0; i < plan.slots.size(); i++) inventory.setStack(plan.slots.get(i).index, replacement.get(i));
            inventory.markDirty();
        } catch (RuntimeException incomplete) {
            for (int i = 0; i < plan.slots.size(); i++) inventory.setStack(plan.slots.get(i).index, originals.get(i));
            forgetReceipt(reference);
            inventory.markDirty();
            return give ? Delivery.UNKNOWN : Delivery.NO_CHANGE;
        }
        try {
            DurablePlayerInventory.save(player);
            return "applied".equals(receiptStatus(reference)) ? Delivery.APPLIED : Delivery.UNKNOWN;
        } catch (RuntimeException uncertain) { return Delivery.UNKNOWN; }
    }
}
