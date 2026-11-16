package dev.jdguo.shop.outlet;

/** A world-owned outlet; no mutable static world state is retained between servers. */
public record ShopOutlet(String name, String dimension, int x, int y, int z) {
    public ShopOutlet {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("网点名称限 1–32 位字母、数字、下划线或短横线");
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("无效维度");
    }
    public boolean at(String dimension, int x, int y, int z) {
        return this.dimension.equals(dimension) && this.x == x && this.y == y && this.z == z;
    }
}
