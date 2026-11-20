package dev.jdguo.shop.catalog;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CatalogPricingTest {
    private final ShopConfig defaults = ShopConfig.defaults();
    private BigDecimal price(String id) { return defaults.entry("minecraft:"+id).anchorDiamonds(); }
    private void equal(BigDecimal a, BigDecimal b) { assertEquals(0,a.compareTo(b),a+" != "+b); }
    private String legacyJson() {
        return ConfigJson.write(new ShopConfig(defaults.stepFraction(),defaults.minDiamondPrice(),100,8,
                ShopConfig.legacyDefaultCatalog(),defaults.spreadFraction(),0));
    }
    @Test void diamondNumeraireAndScarcityAreDistinctFromFarmability() {
        equal(BigDecimal.ONE,price("diamond"));
        equal(price("sponge"),price("wet_sponge"));
        assertTrue(price("elytra").compareTo(price("sponge"))>0);
        assertTrue(price("sponge").compareTo(price("totem_of_undying"))>0);
        assertTrue(price("heart_of_the_sea").compareTo(price("emerald"))>0);
        assertTrue(price("music_disc_pigstep").compareTo(price("music_disc_cat"))>0);
        assertTrue(price("iron_ingot").compareTo(new BigDecimal(".06"))<0);
    }
    @Test void reversiblePackingPreservesValue() {
        for(String[] pair:new String[][]{{"iron_block","iron_ingot"},{"gold_block","gold_ingot"},{"copper_block","copper_ingot"},{"emerald_block","emerald"},{"diamond_block","diamond"},{"netherite_block","netherite_ingot"},{"lapis_block","lapis_lazuli"},{"slime_block","slime_ball"},{"raw_iron_block","raw_iron"},{"raw_gold_block","raw_gold"},{"raw_copper_block","raw_copper"},{"bone_block","bone_meal"}}) equal(price(pair[0]),price(pair[1]).multiply(BigDecimal.valueOf(9)));
        equal(price("iron_ingot"),price("iron_nugget").multiply(BigDecimal.valueOf(9)));
        equal(price("gold_ingot"),price("gold_nugget").multiply(BigDecimal.valueOf(9)));
        equal(price("blue_ice"),price("ice").multiply(BigDecimal.valueOf(81)));
    }
    @Test void orePricesCoverFortuneOutputRatherThanAllowCertainExtractionProfit() {
        for(String[] ore:new String[][]{{"diamond","diamond","4"},{"lapis","lapis_lazuli","36"},{"copper","raw_copper","20"},{"iron","raw_iron","4"},{"redstone","redstone","8"}}) {
            BigDecimal max=price(ore[1]).multiply(new BigDecimal(ore[2]));
            equal(max,price(ore[0]+"_ore")); equal(max,price("deepslate_"+ore[0]+"_ore"));
        }
    }
    @Test void smithingAndEquipmentUseActualIngredientTotalsNotIndependentPremiums() {
        equal(price("netherite_ingot"),price("netherite_scrap").add(price("gold_ingot")).multiply(BigDecimal.valueOf(4)));
        equal(price("netherite_upgrade_smithing_template"),price("diamond").multiply(BigDecimal.valueOf(7)).add(price("netherrack")));
        for(String kind:List.of("pickaxe","sword","helmet","chestplate","leggings","boots")) equal(price("netherite_"+kind),price("diamond_"+kind).add(price("netherite_ingot")).add(price("netherite_upgrade_smithing_template")));
        equal(price("anvil"),price("iron_ingot").multiply(BigDecimal.valueOf(31)));
        equal(price("golden_apple"),price("gold_ingot").multiply(BigDecimal.valueOf(8)).add(price("apple")));
    }
    @Test void oldConfigurationRequiresExplicitOptInAndRoundTripsWithoutRepricing() {
        var old=ConfigJson.parse(legacyJson());
        equal(new BigDecimal(".35"),old.entry("minecraft:sponge").anchorDiamonds());
        assertEquals(0,old.pricingVersion());
        assertEquals(ConfigJson.write(old),ConfigJson.write(ConfigJson.parse(ConfigJson.write(old))));
        assertEquals(0,old.withMaxCount(99).withAnchor("minecraft:diamond",BigDecimal.ONE).pricingVersion());
    }
    @Test void optedInMigrationKeepsCustomDisabledRemovedAndUnknownRows() {
        var old=ConfigJson.parse(legacyJson()).withAnchor("minecraft:emerald",new BigDecimal("99")).withSystemTrading("minecraft:wet_sponge",false);
        var root=JsonParser.parseString(ConfigJson.write(old)).getAsJsonObject();
        var rows=root.getAsJsonArray("catalog");
        for(int i=rows.size()-1;i>=0;i--)if(rows.get(i).getAsJsonObject().get("item").getAsString().equals("minecraft:diamond"))rows.remove(i);
        root.addProperty("refreshDefaultAnchors",true);
        var upgraded=ConfigJson.parse(root.toString());
        equal(price("sponge"),upgraded.entry("minecraft:sponge").anchorDiamonds());
        equal(new BigDecimal(".35"),upgraded.entry("minecraft:wet_sponge").anchorDiamonds());
        assertFalse(upgraded.entry("minecraft:wet_sponge").systemTradeEnabled());
        equal(new BigDecimal("99"),upgraded.entry("minecraft:emerald").anchorDiamonds());
        assertNull(upgraded.entry("minecraft:diamond"));
        assertEquals(CatalogPricing.VERSION,upgraded.pricingVersion());
        assertEquals(ConfigJson.write(upgraded),ConfigJson.write(ConfigJson.parse(ConfigJson.write(upgraded))));
    }
    @Test void supplyIsModeledByAvailabilityAndNotSimplyPrice() {
        assertEquals(32,CatalogSupply.baseStock("minecraft:sponge",price("sponge")));
        assertEquals(8,CatalogSupply.baseStock("minecraft:elytra",price("elytra")));
        assertEquals(4096,CatalogSupply.baseStock("minecraft:dirt",price("dirt")));
        assertEquals(2048,CatalogSupply.baseStock("minecraft:iron_ingot",price("iron_ingot")));
        assertEquals(128,CatalogSupply.baseStock("minecraft:shulker_shell",price("shulker_shell")));
        for(var entry:defaults.catalog())assertTrue(CatalogSupply.baseStock(entry.itemId(),entry.anchorDiamonds())>0);
    }
    @Test void newDefaultsIgnorePreviousPriceNumbersAndUseKnownIngredients() {
        var rows = ShopConfig.legacyDefaultCatalog().stream().map(e -> new CatalogEntry(
                e.itemId(),e.name(),new BigDecimal("999"),e.systemTradeEnabled())).toList();
        assertEquals(defaults.catalog(), CatalogPricing.apply(rows));
        equal(price("rail"),price("iron_ingot").multiply(BigDecimal.valueOf(6)).add(price("stick"))
                .divide(BigDecimal.valueOf(16),12,java.math.RoundingMode.HALF_EVEN));
        for (var e : defaults.catalog()) assertTrue(e.anchorDiamonds().signum() > 0);
    }
    @Test void versionOneOptInRepricesOnlyItsOwnOriginalDefaults() {
        var legacy = ShopConfig.legacyDefaultCatalog();
        var previous = legacy.stream().map(e -> e.itemId().equals("minecraft:sponge")
                ? new CatalogEntry(e.itemId(),e.name(),new BigDecimal("3"),true) : e)
                .map(e -> e.itemId().equals("minecraft:emerald")
                        ? new CatalogEntry(e.itemId(),e.name(),new BigDecimal("99"),true) : e).toList();
        var rows = CatalogPricing.refreshLegacyDefaults(previous,1);
        equal(price("sponge"),rows.stream().filter(e -> e.itemId().equals("minecraft:sponge")).findFirst().orElseThrow().anchorDiamonds());
        equal(new BigDecimal("99"),rows.stream().filter(e -> e.itemId().equals("minecraft:emerald")).findFirst().orElseThrow().anchorDiamonds());
    }
}
