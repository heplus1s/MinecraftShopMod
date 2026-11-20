package dev.jdguo.shop.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** Relative effort profiles, not measured drop rates of any particular world. */
final class AcquisitionCost {
    private AcquisitionCost() {}
    private record Route(int access, int attempt, int opportunities, int units) {
        // Work per unit: access cost amortized over repeated attempts; opportunities encode availability.
        BigDecimal cost() {
            return BigDecimal.valueOf(access + attempt * (long) opportunities)
                    .divide(BigDecimal.valueOf(units), 12, RoundingMode.HALF_EVEN);
        }
    }

    private static final Route DIAMOND_MINING = new Route(60, 9, 20, 2);
    private static final Route TERRAIN = new Route(0, 12, 1, 64);
    private static final Route FARM = new Route(36, 12, 1, 32);
    private static final Route COMMON_GATHER = new Route(8, 6, 1, 24);
    private static final Route BASIC_ORE = new Route(12, 4, 4, 12);
    private static final Route RARE_ORE = new Route(72, 14, 20, 1);
    private static final Route MOB = new Route(12, 8, 4, 8);
    private static final Route SPECIAL_MOB = new Route(45, 15, 10, 4);
    private static final Route RARE_DROP = new Route(50, 14, 48, 1);
    private static final Route STRUCTURE_BATCH = new Route(900, 15, 10, 3);
    private static final Route STRUCTURE_SINGLE = new Route(1200, 20, 12, 1);
    private static final Route LONG_EXPLORATION = new Route(2700, 18, 18, 1);
    private static final Route BOSS = new Route(540, 120, 8, 1);
    private static final Route ORDINARY = new Route(12, 8, 2, 12);
    private static final Route PROCESSED_FALLBACK = new Route(16, 12, 4, 16);
    private static final Route EQUIPMENT_FALLBACK = new Route(24, 6, 8, 1);
    private static final Set<String> BULK = Set.of("cobblestone","stone","deepslate","dirt","grass_block","sand","red_sand","gravel","netherrack","end_stone","tuff","andesite","diorite","granite","blackstone","basalt","clay_ball","ice","snowball","kelp");
    private static final Set<String> FARMABLE = Set.of("iron_ingot","gold_ingot","emerald","carrot","potato","beetroot","wheat","bamboo","sugar_cane","gunpowder","slime_ball","bone","string","rotten_flesh","cactus","melon_slice","pumpkin","sweet_berries","cocoa_beans","honeycomb","dried_kelp","phantom_membrane");
    private static final Set<String> BASIC_MINED = Set.of("coal","raw_copper","copper_ingot","raw_iron","raw_gold","lapis_lazuli","redstone","quartz","amethyst_shard","glowstone_dust","obsidian","crying_obsidian");
    private static final Set<String> MOB_DROPS = Set.of("leather","feather","beef","porkchop","chicken","mutton","rabbit","cod","salmon","spider_eye","ink_sac","prismarine_shard","prismarine_crystals","egg");
    private static final Set<String> SPECIAL_DROPS = Set.of("ender_pearl","blaze_rod","ghast_tear","shulker_shell","totem_of_undying","dragon_breath","nautilus_shell");
    private static final Set<String> RARE_DROPS = Set.of("trident","rabbit_foot","wither_skeleton_skull");
    private static final Set<String> LOOT_BATCH = Set.of("sponge","wet_sponge","echo_shard","disc_fragment_5");
    private static final Set<String> LOOT_SINGLE = Set.of("heart_of_the_sea","saddle","music_disc_pigstep","music_disc_otherside","music_disc_relic");
    private static final Set<String> GOLD_GRID = Set.of("gold_ingot", "iron_ingot", "bone");

    static BigDecimal estimate(String id) {
        if ("diamond".equals(id)) return BigDecimal.ONE;
        Route r = route(id);
        BigDecimal cost = relative(r.cost());
        // This resolution keeps 9-to-1 nugget packing exact at the final quote precision.
        if (GOLD_GRID.contains(id)) {
            BigDecimal quantum = new BigDecimal("0.000000009");
            return cost.divide(quantum, 0, RoundingMode.HALF_EVEN).multiply(quantum).stripTrailingZeros();
        }
        return cost;
    }

    static BigDecimal processCost(int workPerBatch) {
        return relative(BigDecimal.valueOf(workPerBatch, 2));
    }
    private static BigDecimal relative(BigDecimal cost) {
        return cost.divide(DIAMOND_MINING.cost(), 12, RoundingMode.HALF_EVEN).stripTrailingZeros();
    }

    private static Route route(String id) {
        if (id.equals("ancient_debris")) return RARE_ORE;
        if (id.equals("nether_star") || id.equals("dragon_egg")) return BOSS;
        if (id.equals("elytra") || id.equals("enchanted_golden_apple")) return LONG_EXPLORATION;
        if (id.equals("netherite_upgrade_smithing_template") || id.endsWith("_armor_trim_smithing_template")) return STRUCTURE_SINGLE;
        if (LOOT_SINGLE.contains(id)) return STRUCTURE_SINGLE;
        if (LOOT_BATCH.contains(id)) return STRUCTURE_BATCH;
        if (RARE_DROPS.contains(id)) return RARE_DROP;
        if (SPECIAL_DROPS.contains(id)) return SPECIAL_MOB;
        if (FARMABLE.contains(id)) return FARM;
        if (BULK.contains(id)) return TERRAIN;
        if (BASIC_MINED.contains(id)) return BASIC_ORE;
        if (MOB_DROPS.contains(id) || id.startsWith("music_disc_")) return MOB;
        if (id.endsWith("_log") || id.endsWith("_stem") || id.endsWith("_leaves") || id.endsWith("_sapling")) return COMMON_GATHER;
        return switch (CatalogCategories.id("minecraft:" + id)) {
            case "natural", "food" -> COMMON_GATHER;
            case "building", "colored", "functional", "redstone", "transport" -> PROCESSED_FALLBACK;
            case "tools", "weapons", "armor" -> EQUIPMENT_FALLBACK;
            default -> ORDINARY;
        };
    }
}
