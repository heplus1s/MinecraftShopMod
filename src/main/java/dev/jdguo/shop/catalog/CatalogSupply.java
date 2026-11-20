package dev.jdguo.shop.catalog;

import java.math.BigDecimal;
import java.util.Set;

/** Modeled initial supply, not measured world generation or guaranteed free replenishment. */
public final class CatalogSupply {
    private CatalogSupply() {}
    private static final Set<String> EXPLORATION = Set.of("elytra", "enchanted_golden_apple", "heart_of_the_sea", "echo_shard", "music_disc_pigstep", "music_disc_otherside", "music_disc_relic", "music_disc_5");
    private static final Set<String> RARE_DROPS = Set.of("trident", "nether_star", "nautilus_shell", "rabbit_foot", "phantom_membrane");
    private static final Set<String> FARM_OUTPUT = Set.of("iron_ingot", "gold_ingot", "emerald", "redstone", "gunpowder", "bone", "bone_meal", "string", "slime_ball", "ender_pearl", "prismarine_shard", "prismarine_crystals", "bamboo", "sugar_cane", "kelp", "wheat", "carrot", "potato", "beetroot", "cobblestone", "stone", "basalt");
    private static final Set<String> BULK_TERRAIN = Set.of("dirt", "sand", "red_sand", "gravel", "netherrack", "deepslate", "tuff", "andesite", "diorite", "granite", "end_stone", "blackstone");

    public static int baseStock(String itemId, BigDecimal anchor) {
        String id = itemId == null ? "" : itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId;
        if (EXPLORATION.contains(id)) return 8;
        if (id.equals("sponge") || id.equals("wet_sponge")) return 32;
        if (RARE_DROPS.contains(id)) return 32;
        if (id.equals("totem_of_undying") || id.equals("shulker_shell") || id.equals("blaze_rod") || id.equals("ghast_tear")) return 128;
        if (id.equals("diamond")) return 128;
        if (id.equals("ancient_debris") || id.equals("netherite_scrap") || id.equals("netherite_ingot")) return 32;
        if (id.endsWith("_ore")) return 64;
        if (id.endsWith("_smithing_template")) return 16;
        if (FARM_OUTPUT.contains(id)) return 2048;
        if (BULK_TERRAIN.contains(id)) return 4096;
        if (id.endsWith("_log") || id.endsWith("_stem") || id.endsWith("_planks")) return 1024;
        String category = CatalogCategories.id(itemId);
        if (Set.of("armor", "tools", "weapons").contains(category)) return 32;
        if (id.endsWith("_block") && anchor != null && anchor.compareTo(BigDecimal.ONE) >= 0) return 32;
        if ("food".equals(category)) return 512;
        if (Set.of("building", "colored").contains(category)) return 512;
        if ("natural".equals(category)) return 256;
        if (anchor != null && anchor.compareTo(new BigDecimal("4")) >= 0) return 32;
        return 128;
    }
}
