package dev.jdguo.shop.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/** Versioned starting anchors only. Never reads or rewrites world prices, stock or admin settings. */
public final class CatalogPricing {
    public static final int VERSION = 2;
    private CatalogPricing() {}

    public static List<CatalogEntry> apply(List<CatalogEntry> legacy) {
        Routes p = new Routes(legacy);
        // Shelf-stable dry goods are made from their naturally obtained form.
        derive(p, "sponge", "wet_sponge", 1, 1);
        derive(p, "netherite_scrap", "ancient_debris", 1, 1);
        derive(p, "bone_meal", "bone", 1, 3);
        derive(p, "dried_kelp", "kelp", 1, 1);
        process(p, "glass", 1, 6, "sand", 1);
        process(p, "glass", 1, 6, "red_sand", 1);
        process(p, "smooth_stone", 1, 6, "stone", 1);
        defineCommonVariants(p);
        // Choose terminating component values so reversible packing is exact, not rounded per block.
        derive(p, "iron_nugget", "iron_ingot", 1, 9); derive(p, "gold_nugget", "gold_ingot", 1, 9);
        for (String material : List.of("iron", "gold", "copper")) {
            derive(p, "raw_" + material, material + "_ingot", 1, 1);
            derive(p, "raw_" + material + "_block", "raw_" + material, 9, 1);
            derive(p, material + "_block", material + "_ingot", 9, 1);
        }
        for (String[] pair : new String[][]{{"diamond_block","diamond"},{"emerald_block","emerald"},{"coal_block","coal"},{"lapis_block","lapis_lazuli"},{"redstone_block","redstone"},{"slime_block","slime_ball"},{"hay_block","wheat"},{"dried_kelp_block","dried_kelp"}}) derive(p,pair[0],pair[1],9,1);
        derive(p,"bone_block","bone_meal",9,1);
        derive(p,"packed_ice","ice",9,1); derive(p,"blue_ice","packed_ice",9,1);
        derive(p,"clay","clay_ball",4,1); derive(p,"glowstone","glowstone_dust",4,1);
        derive(p,"amethyst_block","amethyst_shard",4,1); derive(p,"honeycomb_block","honeycomb",4,1);
        derive(p,"blaze_powder","blaze_rod",1,2); sum(p,"ender_eye","ender_pearl",1,"blaze_powder",1);
        sum(p,"magma_cream","slime_ball",1,"blaze_powder",1);
        sum(p,"netherite_ingot","netherite_scrap",4,"gold_ingot",4); derive(p,"netherite_block","netherite_ingot",9,1);
        // Template duplication consumes one and produces two: incremental cost is seven diamonds + block.
        sum(p,"netherite_upgrade_smithing_template","diamond",7,"netherrack",1);
        sum(p,"golden_apple","gold_ingot",8,"apple",1); sum(p,"golden_carrot","gold_nugget",8,"carrot",1);
        // Conservative silk-touch ore price covers maximum vanilla Fortune III output (not expected yield).
        for (String[] ore : new String[][]{{"coal","coal","4"},{"iron","raw_iron","4"},{"copper","raw_copper","20"},{"gold","raw_gold","4"},{"lapis","lapis_lazuli","36"},{"redstone","redstone","8"},{"diamond","diamond","4"},{"emerald","emerald","4"}}) {
            derive(p,ore[0]+"_ore",ore[1],Integer.parseInt(ore[2]),1);
            derive(p,"deepslate_"+ore[0]+"_ore",ore[0]+"_ore",1,1);
        }
        derive(p,"nether_gold_ore","gold_nugget",24,1); derive(p,"nether_quartz_ore","quartz",4,1);
        // Fundamental cheap recipes: no crafting premium for arbitrarily repeatable processing.
        derive(p,"stick","oak_planks",1,2); derive(p,"bowl","oak_planks",3,4);
        derive(p,"bread","wheat",3,1); derive(p,"charcoal","oak_log",1,1);
        for (String wood : List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","crimson","warped")) {
            String log = wood + ((wood.equals("crimson") || wood.equals("warped")) ? "_stem" : "_log");
            if (!p.containsKey(log)) continue;
            derive(p,"stripped_"+log,log,1,1);
            derive(p,wood+"_planks",log,1,4);
            String pl=wood+"_planks";
            derive(p,wood+"_slab",pl,1,2); derive(p,wood+"_stairs",pl,3,2);
            derive(p,wood+"_door",pl,2,1); derive(p,wood+"_trapdoor",pl,3,1);
            derive(p,wood+"_button",pl,1,1); derive(p,wood+"_pressure_plate",pl,2,1);
            recipe(p,wood+"_fence",3,pl,4,"stick",2);
            sum(p,wood+"_fence_gate",pl,2,"stick",4);
            recipe(p,wood+"_sign",3,pl,6,"stick",1);
            derive(p,wood+"_boat",pl,5,1);
            sum(p,wood+"_chest_boat",wood+"_boat",1,"chest",1);
        }
        recipe(p,"torch",4,"charcoal",1,"stick",1);
        recipe(p,"soul_torch",4,"charcoal",1,"stick",1,"soul_sand",1);
        derive(p,"flint","gravel",1,1);
        for(String[] pair:new String[][]{{"cooked_beef","beef"},{"cooked_porkchop","porkchop"},{"cooked_chicken","chicken"},{"cooked_mutton","mutton"},{"cooked_rabbit","rabbit"},{"cooked_cod","cod"},{"cooked_salmon","salmon"},{"baked_potato","potato"}}) derive(p,pair[0],pair[1],1,1);
        derive(p,"bucket","iron_ingot",3,1);
        for (String id : List.of("water_bucket","milk_bucket","powder_snow_bucket")) derive(p,id,"bucket",1,1);
        sum(p,"lava_bucket","bucket",1,"obsidian",1);
        derive(p,"anvil","iron_ingot",31,1); derive(p,"cauldron","iron_ingot",7,1);
        derive(p,"iron_door","iron_ingot",2,1); derive(p,"iron_trapdoor","iron_ingot",4,1); derive(p,"iron_bars","iron_ingot",6,16);
        sum(p,"chain","iron_ingot",1,"iron_nugget",2);
        sum(p,"lantern","iron_nugget",8,"torch",1); sum(p,"soul_lantern","iron_nugget",8,"soul_torch",1);
        sum(p,"hopper","iron_ingot",5,"chest",1); sum(p,"compass","iron_ingot",4,"redstone",1);
        sum(p,"clock","gold_ingot",4,"redstone",1); sum(p,"recovery_compass","echo_shard",8,"compass",1);
        sum(p,"spyglass","copper_ingot",2,"amethyst_shard",1);
        recipe(p,"brush",1,"copper_ingot",1,"feather",1,"stick",1);
        derive(p,"lightning_rod","copper_ingot",3,1);
        derive(p,"shears","iron_ingot",2,1); sum(p,"flint_and_steel","iron_ingot",1,"flint",1);
        recipe(p,"bow",1,"string",3,"stick",3); recipe(p,"crossbow",1,"iron_ingot",1,"string",2,"stick",3,"tripwire_hook",1);
        sum(p,"shield","iron_ingot",1,"oak_planks",6);
        recipe(p,"piston",1,"iron_ingot",1,"redstone",1,"cobblestone",4,"oak_planks",3);
        sum(p,"sticky_piston","piston",1,"slime_ball",1);
        recipe(p,"tripwire_hook",2,"iron_ingot",1,"stick",1,"oak_planks",1);
        sum(p,"trapped_chest","chest",1,"tripwire_hook",1);
        recipe(p,"rail",16,"iron_ingot",6,"stick",1);
        recipe(p,"powered_rail",6,"gold_ingot",6,"stick",1,"redstone",1);
        recipe(p,"detector_rail",6,"iron_ingot",6,"stone_pressure_plate",1,"redstone",1);
        recipe(p,"activator_rail",6,"iron_ingot",6,"stick",2,"redstone_torch",1);
        derive(p,"minecart","iron_ingot",5,1);
        for (String part : List.of("chest","hopper","furnace","tnt")) sum(p,part+"_minecart","minecart",1,part,1);
        sum(p,"tnt","gunpowder",5,"sand",4);
        derive(p,"prismarine","prismarine_shard",4,1); derive(p,"prismarine_bricks","prismarine_shard",9,1);
        sum(p,"dark_prismarine","prismarine_shard",8,"black_dye",1);
        sum(p,"sea_lantern","prismarine_shard",4,"prismarine_crystals",5);
        for (String stone : List.of("prismarine","prismarine_bricks","dark_prismarine","quartz_block","smooth_quartz")) {
            String stem=stone.equals("prismarine_bricks")?"prismarine_brick":stone.equals("quartz_block")?"quartz":stone;
            derive(p,stem+"_stairs",stone,1,1); derive(p,stem+"_slab",stone,1,2); derive(p,stem+"_wall",stone,1,1);
        }
        for (String id : List.copyOf(p.keySet())) if (id.contains("copper") && !id.contains("ore") && !id.startsWith("raw_") && !id.endsWith("ingot")) {
            if (id.contains("cut_copper")) derive(p,id,"copper_block",1,id.endsWith("slab")?8:4);
            else if (id.contains("exposed")||id.contains("weathered")||id.contains("oxidized")||id.equals("waxed_copper_block")) derive(p,id,"copper_block",1,1);
        }
        for (String wood : List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","crimson","warped")) {
            String log=wood+((wood.equals("crimson")||wood.equals("warped"))?"_stem":"_log");
            recipe(p,wood+"_hanging_sign",6,log,6,"chain",2);
        }
        String[] kinds={"sword","pickaxe","axe","shovel","hoe"}; int[] units={2,3,3,1,2};
        String[] tiers={"wooden","stone","iron","golden","diamond"}, materials={"oak_planks","cobblestone","iron_ingot","gold_ingot","diamond"};
        for(int t=0;t<tiers.length;t++) for(int k=0;k<kinds.length;k++) recipe(p,tiers[t]+"_"+kinds[k],1,materials[t],units[k],"stick",k==0?1:2);
        String[] armor={"helmet","chestplate","leggings","boots"}; int[] armorUnits={5,8,7,4};
        for(String[] tier:new String[][]{{"leather","leather"},{"iron","iron_ingot"},{"golden","gold_ingot"},{"diamond","diamond"}}) for(int k=0;k<armor.length;k++) derive(p,tier[0]+"_"+armor[k],tier[1],armorUnits[k],1);
        for(String kind:List.of("sword","pickaxe","axe","shovel","hoe","helmet","chestplate","leggings","boots")) recipe(p,"netherite_"+kind,1,"diamond_"+kind,1,"netherite_ingot",1,"netherite_upgrade_smithing_template",1);
        recipe(p,"beacon",1,"nether_star",1,"obsidian",3,"glass",5);
        sum(p,"conduit","heart_of_the_sea",1,"nautilus_shell",8);
        sum(p,"shulker_box","shulker_shell",2,"chest",1);
        sum(p,"ender_chest","ender_eye",1,"obsidian",8);
        recipe(p,"enchanting_table",1,"diamond",2,"obsidian",4,"book",1);
        sum(p,"smithing_table","iron_ingot",2,"oak_planks",4);
        recipe(p,"blast_furnace",1,"iron_ingot",5,"furnace",1,"smooth_stone",3);
        sum(p,"stonecutter","iron_ingot",1,"stone",3);
        for(String id:List.copyOf(p.keySet())) {
            // Colored boxes can be washed. Same shell value regardless of color prevents wash loops.
            if(id.endsWith("_shulker_box")) derive(p,id,"shulker_box",1,1);
            if(id.endsWith("_carpet") && p.containsKey(id.replace("_carpet","_wool"))) derive(p,id,id.replace("_carpet","_wool"),2,3);
            if(id.endsWith("_bed") && p.containsKey(id.replace("_bed","_wool"))) sum(p,id,id.replace("_bed","_wool"),3,"oak_planks",3);
        }
        // Recompute dependents whose ingredient changed later in the recipe order.
        recipe(p,"crossbow",1,"iron_ingot",1,"string",2,"stick",3,"tripwire_hook",1);
        sum(p,"tnt_minecart","minecart",1,"tnt",1);
        sum(p,"jack_o_lantern","carved_pumpkin",1,"torch",1);
        sum(p,"fishing_rod","string",2,"stick",3);
        // The template also has a direct exploration route. Compare it with replication's net yield.
        p.direct.add("netherite_upgrade_smithing_template");
        Map<String, BigDecimal> prices = p.solve();
        return legacy.stream().map(e -> new CatalogEntry(e.itemId(), e.name(),
                prices.get(shortId(e.itemId())), e.systemTradeEnabled())).toList();
    }
    /** Explicit opt-in only: matching old defaults are candidates, not proof of administrator intent. */
    public static List<CatalogEntry> refreshLegacyDefaults(List<CatalogEntry> configured, int fromVersion) {
        Map<String,CatalogEntry> old = new LinkedHashMap<>(), updated = new LinkedHashMap<>();
        for (var entry : ShopConfig.legacyDefaultCatalog()) old.put(entry.itemId(),entry);
        for (var entry : apply(ShopConfig.legacyDefaultCatalog())) updated.put(entry.itemId(),entry);
        Map<String, BigDecimal> previousAnchors = fromVersion == 1 ? loadV1() : Map.of();
        return configured.stream().map(entry -> {
            var baseline = old.get(entry.itemId());
            return baseline != null && entry.systemTradeEnabled() && baseline.systemTradeEnabled()
                    && entry.name().equals(baseline.name())
                    && entry.anchorDiamonds().compareTo(previousAnchors.getOrDefault(entry.itemId(), baseline.anchorDiamonds())) == 0
                    ? updated.get(entry.itemId()) : entry;
        }).toList();
    }
    private static String shortId(String id) { return id.startsWith("minecraft:") ? id.substring(10) : id; }
    private static Map<String, BigDecimal> loadV1() {
        var stream = CatalogPricing.class.getResourceAsStream("/shopmod/defaults-v1.tsv");
        if (stream == null) throw new IllegalStateException("Missing price version 1 baseline");
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                var pair = line.split("\t", -1);
                result.put(pair[0], new BigDecimal(pair[1]));
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read price version 1 baseline", failure);
        }
        return result;
    }

    private static void defineCommonVariants(Routes p) {
        for (String stone : List.of("stone","cobblestone","mossy_cobblestone","stone_bricks","mossy_stone_bricks",
                "andesite","diorite","granite","polished_andesite","polished_diorite","polished_granite",
                "brick","mud_brick","sandstone","red_sandstone","smooth_sandstone","smooth_red_sandstone",
                "deepslate_brick","deepslate_tile","cobbled_deepslate","polished_deepslate","blackstone",
                "polished_blackstone","polished_blackstone_brick","end_stone_brick","purpur","nether_brick","red_nether_brick")) {
            String block = switch (stone) {
                case "stone_bricks" -> "stone_bricks";
                case "mossy_stone_bricks" -> "mossy_stone_bricks";
                case "brick", "mud_brick", "deepslate_brick", "polished_blackstone_brick",
                        "end_stone_brick", "nether_brick", "red_nether_brick" -> stone + "s";
                case "deepslate_tile" -> "deepslate_tiles";
                case "purpur" -> "purpur_block";
                default -> stone;
            };
            String stem = stone.equals("stone_bricks") ? "stone_brick" : stone.equals("mossy_stone_bricks") ? "mossy_stone_brick" : stone;
            derive(p, stem + "_stairs", block, 1, 1);
            derive(p, stem + "_slab", block, 1, 2);
            derive(p, stem + "_wall", block, 1, 1);
        }
        for (String stone : List.of("andesite","diorite","granite","blackstone")) derive(p, "polished_" + stone, stone, 1, 1);
        derive(p,"stone_bricks","stone",1,1);
        derive(p,"sandstone","sand",4,1); derive(p,"red_sandstone","red_sand",4,1);
        process(p,"smooth_sandstone",1,6,"sandstone",1); process(p,"smooth_red_sandstone",1,6,"red_sandstone",1);
        derive(p,"snow_block","snowball",4,1); derive(p,"snow","snow_block",3,8);
        derive(p,"chest","oak_planks",8,1); derive(p,"crafting_table","oak_planks",4,1);
        derive(p,"furnace","cobblestone",8,1);
        derive(p,"paper","sugar_cane",1,1); sum(p,"book","paper",3,"leather",1);
    }

    private static void derive(Routes p,String id,String from,int n,int d) { recipe(p,id,d,from,n); }
    private static void sum(Routes p,String id,String a,int na,String b,int nb) { recipe(p,id,1,a,na,b,nb); }
    private static void recipe(Routes p,String id,int output,Object... ingredients) {
        process(p,id,output,0,ingredients);
    }
    private static void process(Routes p,String id,int output,int work,Object... ingredients) {
        if (!p.containsKey(id)) return;
        Map<String,Integer> materials = new LinkedHashMap<>();
        for (int i=0;i<ingredients.length;i+=2) {
            var input = (String) ingredients[i];
            if (!p.containsKey(input) || id.equals(input)) return;
            materials.merge(input, (Integer) ingredients[i+1], Integer::sum);
        }
        p.recipes.computeIfAbsent(id, k -> new ArrayList<>()).add(new Recipe(output, work, materials));
    }

    private record Recipe(int output, int work, Map<String,Integer> inputs) {
        BigDecimal cost(Map<String, BigDecimal> prices) {
            BigDecimal value = AcquisitionCost.processCost(work);
            for (var input : inputs.entrySet()) {
                var price = prices.get(input.getKey());
                if (price == null) return null;
                value = value.add(price.multiply(BigDecimal.valueOf(input.getValue())));
            }
            return value.divide(BigDecimal.valueOf(output), 12, RoundingMode.HALF_EVEN)
                    .max(new BigDecimal("0.000000001")).stripTrailingZeros();
        }
    }
    private static final class Routes {
        final Set<String> ids = new LinkedHashSet<>();
        final Set<String> direct = new LinkedHashSet<>();
        final Map<String, List<Recipe>> recipes = new LinkedHashMap<>();
        Routes(List<CatalogEntry> catalog) {
            for (var entry : catalog) ids.add(shortId(entry.itemId()));
        }
        boolean containsKey(String id) { return ids.contains(id); }
        Set<String> keySet() { return ids; }
        Map<String, BigDecimal> solve() {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            for (String id : ids) if (id.equals("diamond") || direct.contains(id) || !recipes.containsKey(id))
                values.put(id, AcquisitionCost.estimate(id));
            // Relax only recorded vanilla paths. A recipe's result must not be an ingredient in itself.
            // No prices are seeded for a reversible conversion family without an acquisition source.
            for (int pass = 0; pass < ids.size(); pass++) {
                boolean changed = false;
                for (var row : recipes.entrySet()) {
                    String id = row.getKey();
                    if ("diamond".equals(id)) continue;
                    for (Recipe recipe : row.getValue()) {
                        BigDecimal cost = recipe.cost(values), old = values.get(id);
                        if (cost != null && (old == null || cost.compareTo(old) < 0)) {
                            values.put(id,cost); changed = true;
                        }
                    }
                }
                if (!changed) break;
                if (pass == ids.size() - 1) throw new IllegalStateException("Unstable catalog recipe cost graph");
            }
            // Missing details use a shared acquisition/category profile, never the old per-item price.
            for (String id : ids) values.computeIfAbsent(id, AcquisitionCost::estimate);
            return values;
        }
    }
}
