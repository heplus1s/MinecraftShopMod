package dev.jdguo.shop.outlet;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import java.util.*;

public final class ShopOutletState extends PersistentState {
    private final Map<String, ShopOutlet> entries = new LinkedHashMap<>();
    public static ShopOutletState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(ShopOutletState::read, ShopOutletState::new, "shopmod_outlets");
    }
    public Collection<ShopOutlet> all() { return List.copyOf(entries.values()); }
    public ShopOutlet find(String name) { return entries.get(name); }
    public void put(ShopOutlet outlet) {
        for (ShopOutlet other : entries.values()) {
            if (!other.name().equals(outlet.name()) && other.at(outlet.dimension(), outlet.x(), outlet.y(), outlet.z()))
                throw new IllegalArgumentException("此位置已有商店柜台");
        }
        if (!entries.containsKey(outlet.name()) && entries.size() >= 128) throw new IllegalArgumentException("最多 128 个商店网点");
        entries.put(outlet.name(), outlet); markDirty();
    }
    public boolean remove(String name) { boolean removed = entries.remove(name) != null; if (removed) markDirty(); return removed; }
    private static ShopOutletState read(NbtCompound nbt) {
        ShopOutletState state = new ShopOutletState();
        NbtList list = nbt.getList("outlets", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound row = list.getCompound(i);
            ShopOutlet outlet = new ShopOutlet(row.getString("name"), row.getString("dimension"), row.getInt("x"), row.getInt("y"), row.getInt("z"), CounterRotation.normalize(row.getInt("facing")));
            state.put(outlet);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (ShopOutlet outlet : entries.values()) {
            NbtCompound row = new NbtCompound();
            row.putString("name", outlet.name()); row.putString("dimension", outlet.dimension());
            row.putInt("x", outlet.x()); row.putInt("y", outlet.y()); row.putInt("z", outlet.z()); row.putInt("facing", CounterRotation.normalize(outlet.facing())); list.add(row);
        }
        nbt.put("outlets", list); return nbt;
    }
}
