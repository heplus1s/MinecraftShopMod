package dev.jdguo.shop.catalog;

/** Shared server-authored categories used by both the shop and optional market. */
public final class CatalogCategories {
    public static String id(String item) {
        String supplied = ExpandedCatalog.category(item);
        if (supplied != null) return supplied;
        String p = item == null ? "" : item.substring(item.indexOf(':') + 1);
        if (p.endsWith("_helmet") || p.endsWith("_chestplate") || p.endsWith("_leggings") || p.endsWith("_boots") || p.endsWith("horse_armor")) return "armor";
        if (p.endsWith("_sword") || p.equals("bow") || p.equals("crossbow") || p.equals("trident") || p.equals("shield") || p.equals("arrow")) return "weapons";
        if (p.endsWith("_pickaxe") || p.endsWith("_axe") || p.endsWith("_shovel") || p.endsWith("_hoe")
                || java.util.Set.of("fishing_rod","flint_and_steel","shears","compass","recovery_compass","clock","spyglass","lead","brush","elytra").contains(p)) return "tools";
        if (java.util.Set.of("bread","cooked_beef","cooked_porkchop","golden_apple","enchanted_golden_apple").contains(p)) return "food";
        if (p.equals("redstone")) return "redstone";
        if (p.endsWith("_log") || java.util.Set.of("dirt","sand","gravel","cobblestone","obsidian","crying_obsidian","ancient_debris","sponge").contains(p)) return "natural";
        if (p.endsWith("_block")) return "building";
        return "materials";
    }
    public static String label(String id) {
        return switch (id) {
            case "natural" -> "自然方块"; case "building" -> "建筑方块"; case "colored" -> "染色方块";
            case "functional" -> "功能方块"; case "redstone" -> "红石与机械"; case "transport" -> "交通运输";
            case "food" -> "食物与饮品"; case "tools" -> "工具"; case "weapons" -> "武器";
            case "armor" -> "护甲"; case "materials" -> "原材料"; default -> "其他";
        };
    }
    private CatalogCategories() {}
}
