package dev.jdguo.shop.outlet;

/** A world-owned outlet; no mutable static world state is retained between servers. */
public record ShopOutlet(String name, String dimension, int x, int y, int z, int facing) {
    public ShopOutlet(String name, String dimension, int x, int y, int z) { this(name, dimension, x, y, z, 0); }
    public ShopOutlet rotated(int facing) { return new ShopOutlet(name, dimension, x, y, z, CounterRotation.normalize(facing)); }

    public ShopOutlet {
        if (name == null || (!name.matches("[\\p{L}\\p{N}_ -]{1,32}") || !name.equals(name.trim()))) throw new IllegalArgumentException("网点名称限 1–32 位中外文字、数字、空格、下划线或短横线");
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("无效维度");
    }
    public boolean at(String dimension, int x, int y, int z) {
        return this.dimension.equals(dimension) && this.x == x && this.y == y && this.z == z;
    }
}
