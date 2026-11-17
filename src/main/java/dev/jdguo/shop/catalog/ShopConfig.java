package dev.jdguo.shop.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-wide shop settings. Live prices are not stored here; they live in the world save.
 * Changing an anchor does not move a price that has already traded.
 */
public final class ShopConfig {
    public static final BigDecimal DEFAULT_SPREAD = new BigDecimal("0.04");
    private final BigDecimal spreadFraction;
    private final BigDecimal stepFraction;
    private final BigDecimal minDiamondPrice;
    private final int maxCount;
    private final int pageSize;
    private final List<CatalogEntry> catalog;
    private final Map<String, CatalogEntry> byId;

    public ShopConfig(BigDecimal stepFraction, BigDecimal minDiamondPrice, int maxCount, int pageSize,
                      List<CatalogEntry> catalog) {
        this(stepFraction, minDiamondPrice, maxCount, pageSize, catalog, DEFAULT_SPREAD);
    }

    public ShopConfig(BigDecimal stepFraction, BigDecimal minDiamondPrice, int maxCount, int pageSize,
                      List<CatalogEntry> catalog, BigDecimal spreadFraction) {
        this.spreadFraction = spreadFraction;
        this.stepFraction = stepFraction;
        this.minDiamondPrice = minDiamondPrice;
        this.maxCount = maxCount;
        this.pageSize = pageSize;
        this.catalog = List.copyOf(catalog);
        Map<String, CatalogEntry> index = new LinkedHashMap<>();
        for (CatalogEntry entry : this.catalog) {
            index.put(entry.itemId(), entry);
        }
        this.byId = Map.copyOf(index);
    }

    public static ShopConfig defaults() {
        return new ShopConfig(new BigDecimal("0.002"), new BigDecimal("0.000000001"), 100, 8, defaultCatalog());
    }

    public void validate() {
        if (spreadFraction == null || spreadFraction.compareTo(new BigDecimal("0.0001")) < 0
                || spreadFraction.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("spreadFraction must be from 0.0001 to 1");
        }
        if (stepFraction == null || stepFraction.signum() <= 0 || stepFraction.compareTo(new BigDecimal("0.2")) > 0) {
            throw new IllegalArgumentException("stepFraction must be greater than 0 and at most 0.2");
        }
        if (minDiamondPrice == null || minDiamondPrice.signum() <= 0 || minDiamondPrice.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("minDiamondPrice must be greater than 0 and at most 1");
        }
        if (maxCount < 1 || maxCount > 2304) {
            throw new IllegalArgumentException("maxCount must be from 1 to 2304");
        }
        if (pageSize < 1 || pageSize > 50) {
            throw new IllegalArgumentException("pageSize must be from 1 to 50");
        }
        Map<String, CatalogEntry> seen = new LinkedHashMap<>();
        for (CatalogEntry entry : catalog) {
            if (entry.itemId() == null || !entry.itemId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid item id: " + entry.itemId());
            }
            if (entry.name() == null || entry.name().isBlank() || entry.name().length() > 64
                    || entry.name().chars().anyMatch(ch -> ch < 32)) {
                throw new IllegalArgumentException("Invalid display name for " + entry.itemId());
            }
            if (entry.anchorDiamonds() == null || entry.anchorDiamonds().signum() <= 0
                    || entry.anchorDiamonds().compareTo(new BigDecimal("1000000")) > 0) {
                throw new IllegalArgumentException("Anchor out of range for " + entry.itemId());
            }
            if (seen.put(entry.itemId(), entry) != null) {
                throw new IllegalArgumentException("Duplicate item id: " + entry.itemId());
            }
        }
    }

    public ShopConfig withStep(BigDecimal step) {
        ShopConfig next = new ShopConfig(step, minDiamondPrice, maxCount, pageSize, catalog, spreadFraction);
        next.validate();
        return next;
    }

    public ShopConfig withAnchor(String itemId, BigDecimal anchor) {
        List<CatalogEntry> next = new ArrayList<>();
        boolean found = false;
        for (CatalogEntry entry : catalog) {
            if (entry.itemId().equals(itemId)) {
                next.add(new CatalogEntry(entry.itemId(), entry.name(), anchor, entry.systemTradeEnabled()));
                found = true;
            } else {
                next.add(entry);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }
        ShopConfig config = new ShopConfig(stepFraction, minDiamondPrice, maxCount, pageSize, next, spreadFraction);
        config.validate();
        return config;
    }

    public ShopConfig withMaxCount(int count) {
        ShopConfig next = new ShopConfig(stepFraction, minDiamondPrice, count, pageSize, catalog, spreadFraction);
        next.validate(); return next;
    }

    public ShopConfig withSystemTrading(String itemId, boolean enabled) {
        if (entry(itemId) == null) throw new IllegalArgumentException("Unknown item: " + itemId);
        List<CatalogEntry> next = catalog.stream().map(e -> e.itemId().equals(itemId)
                ? new CatalogEntry(e.itemId(), e.name(), e.anchorDiamonds(), enabled) : e).toList();
        ShopConfig config = new ShopConfig(stepFraction, minDiamondPrice, maxCount, pageSize, next, spreadFraction);
        config.validate();
        return config;
    }

    public ShopConfig withSpread(BigDecimal spread) {
        ShopConfig next = new ShopConfig(stepFraction, minDiamondPrice, maxCount, pageSize, catalog, spread);
        next.validate();
        return next;
    }

    public BigDecimal spreadFraction() { return spreadFraction; }
    public BigDecimal stepFraction() { return stepFraction; }
    public BigDecimal minDiamondPrice() { return minDiamondPrice; }
    public int maxCount() { return maxCount; }
    public int pageSize() { return pageSize; }
    public List<CatalogEntry> catalog() { return catalog; }

    public CatalogEntry entry(String itemId) {
        return itemId == null ? null : byId.get(itemId);
    }

    public int pages() {
        if (catalog.isEmpty()) {
            return 1;
        }
        return (catalog.size() + pageSize - 1) / pageSize;
    }

    /**
     * Starting diamond-equivalents. These compare rough in-game gathering difficulty with one diamond.
     * They are not a claim about the right long-run price. After the first trade, the world save wins.
     */
    private static List<CatalogEntry> baseCatalog() {
        List<CatalogEntry> list = new ArrayList<>();
        list.add(item("minecraft:dirt", "泥土", "0.0004"));
        list.add(item("minecraft:sand", "沙子", "0.0005"));
        list.add(item("minecraft:gravel", "沙砾", "0.0006"));
        list.add(item("minecraft:cobblestone", "圆石", "0.0008"));
        list.add(item("minecraft:oak_log", "橡木原木", "0.002"));
        list.add(item("minecraft:spruce_log", "云杉原木", "0.002"));
        list.add(item("minecraft:birch_log", "白桦原木", "0.002"));
        list.add(item("minecraft:jungle_log", "丛林原木", "0.002"));
        list.add(item("minecraft:acacia_log", "金合欢原木", "0.002"));
        list.add(item("minecraft:dark_oak_log", "深色橡木原木", "0.002"));
        list.add(item("minecraft:wheat", "小麦", "0.003"));
        list.add(item("minecraft:string", "线", "0.008"));
        list.add(item("minecraft:bone", "骨头", "0.008"));
        list.add(item("minecraft:amethyst_shard", "紫水晶碎片", "0.008"));
        list.add(item("minecraft:coal", "煤炭", "0.01"));
        list.add(item("minecraft:bread", "面包", "0.01"));
        list.add(item("minecraft:copper_ingot", "铜锭", "0.012"));
        list.add(item("minecraft:arrow", "箭", "0.012"));
        list.add(item("minecraft:leather", "皮革", "0.015"));
        list.add(item("minecraft:cooked_beef", "熟牛肉", "0.02"));
        list.add(item("minecraft:cooked_porkchop", "熟猪排", "0.02"));
        list.add(item("minecraft:redstone", "红石粉", "0.02"));
        list.add(item("minecraft:quartz", "下界石英", "0.02"));
        list.add(item("minecraft:lapis_lazuli", "青金石", "0.025"));
        list.add(item("minecraft:prismarine_shard", "海晶碎片", "0.03"));
        list.add(item("minecraft:gunpowder", "火药", "0.04"));
        list.add(item("minecraft:prismarine_crystals", "海晶砂粒", "0.05"));
        list.add(item("minecraft:iron_ingot", "铁锭", "0.06"));
        list.add(item("minecraft:coal_block", "煤炭块", "0.09"));
        list.add(item("minecraft:obsidian", "黑曜石", "0.1"));
        list.add(item("minecraft:copper_block", "铜块", "0.108"));
        list.add(item("minecraft:gold_ingot", "金锭", "0.15"));
        list.add(item("minecraft:crying_obsidian", "哭泣的黑曜石", "0.18"));
        list.add(item("minecraft:magma_cream", "岩浆膏", "0.2"));
        list.add(item("minecraft:ender_pearl", "末影珍珠", "0.35"));
        list.add(item("minecraft:sponge", "海绵", "0.35"));
        list.add(item("minecraft:blaze_rod", "烈焰棒", "0.4"));
        list.add(item("minecraft:nautilus_shell", "鹦鹉螺壳", "0.4"));
        list.add(item("minecraft:experience_bottle", "附魔之瓶", "0.4"));
        list.add(item("minecraft:iron_block", "铁块", "0.54"));
        list.add(item("minecraft:ender_eye", "末影之眼", "0.55"));
        list.add(item("minecraft:emerald", "绿宝石", "0.6"));
        list.add(item("minecraft:diamond", "钻石", "1"));
        list.add(item("minecraft:golden_apple", "金苹果", "1.2"));
        list.add(item("minecraft:gold_block", "金块", "1.35"));
        list.add(item("minecraft:ghast_tear", "恶魂之泪", "1.5"));
        list.add(item("minecraft:dragon_breath", "龙息", "1.5"));
        list.add(item("minecraft:echo_shard", "回响碎片", "1.5"));
        list.add(item("minecraft:shulker_shell", "潜影壳", "2"));
        list.add(item("minecraft:ancient_debris", "远古残骸", "3"));
        list.add(item("minecraft:netherite_scrap", "下界合金碎片", "3"));
        list.add(item("minecraft:heart_of_the_sea", "海洋之心", "4"));
        list.add(item("minecraft:recovery_compass", "恢复指南针", "4"));
        list.add(item("minecraft:netherite_upgrade_smithing_template", "下界合金升级锻造模板", "5"));
        list.add(item("minecraft:emerald_block", "绿宝石块", "5.4"));
        list.add(item("minecraft:totem_of_undying", "不死图腾", "6"));
        list.add(item("minecraft:diamond_block", "钻石块", "9"));
        list.add(item("minecraft:netherite_ingot", "下界合金锭", "16"));
        list.add(item("minecraft:nether_star", "下界之星", "20"));
        list.add(item("minecraft:elytra", "鞘翅", "24"));
        list.add(item("minecraft:enchanted_golden_apple", "附魔金苹果", "36"));
        list.addAll(equipmentCatalog());
        return list;
    }

    private static List<CatalogEntry> defaultCatalog() {
        List<CatalogEntry> list = new ArrayList<>(baseCatalog());
        list.addAll(catalogV3Additions());
        return list;
    }

    /** Only new IDs are migrated, so an administrator's removed v2 items stay removed. */
    public static List<CatalogEntry> catalogV3Additions() {
        var previous = baseCatalog().stream().map(CatalogEntry::itemId).collect(java.util.stream.Collectors.toSet());
        return ExpandedCatalog.entries().stream().filter(entry -> !previous.contains(entry.itemId())).toList();
    }

    /** New catalog generation. Prices are explicit diamond anchors, never invented market fills. */
    public static List<CatalogEntry> equipmentCatalog() {
        List<CatalogEntry> list = new ArrayList<>();
        String[] tiers = {"wooden", "stone", "iron", "golden", "diamond", "netherite"};
        String[] labels = {"木", "石", "铁", "金", "钻石", "下界合金"};
        String[] kinds = {"sword", "pickaxe", "axe", "shovel", "hoe"};
        String[] names = {"剑", "镐", "斧", "锹", "锄"};
        int[] units = {2, 3, 3, 1, 2};
        BigDecimal[] materials = {new BigDecimal("0.001"), new BigDecimal("0.0008"), new BigDecimal("0.06"), new BigDecimal("0.15"), BigDecimal.ONE};
        for (int t = 0; t < tiers.length; t++) for (int k = 0; k < kinds.length; k++) {
            BigDecimal price = t == 5 ? BigDecimal.valueOf(units[k]).add(new BigDecimal("21.01"))
                    : materials[t].multiply(BigDecimal.valueOf(units[k])).add(new BigDecimal("0.002"));
            list.add(new CatalogEntry("minecraft:" + tiers[t] + "_" + kinds[k], labels[t] + names[k], price));
        }
        list.add(item("minecraft:bow", "弓", "0.028"));
        list.add(item("minecraft:crossbow", "弩", "0.082"));
        list.add(item("minecraft:shield", "盾牌", "0.065"));
        list.add(item("minecraft:trident", "三叉戟", "12"));
        list.add(item("minecraft:fishing_rod", "钓鱼竿", "0.020"));
        list.add(item("minecraft:shears", "剪刀", "0.12"));
        list.add(item("minecraft:flint_and_steel", "打火石", "0.065"));
        list.add(item("minecraft:redstone_block", "红石块", "0.18"));
        list.add(item("minecraft:redstone_torch", "红石火把", "0.021"));
        list.add(item("minecraft:repeater", "红石中继器", "0.065"));
        list.add(item("minecraft:comparator", "红石比较器", "0.086"));
        list.add(item("minecraft:redstone_lamp", "红石灯", "0.20"));
        list.add(item("minecraft:redstone_ore", "红石矿石", "0.12"));
        list.add(item("minecraft:deepslate_redstone_ore", "深层红石矿石", "0.12"));
        return list;
    }

    private static CatalogEntry item(String id, String name, String anchor) {
        return new CatalogEntry(id, name, new BigDecimal(anchor));
    }
}
