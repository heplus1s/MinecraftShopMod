package dev.jdguo.shop.catalog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigJsonTest {
    @Test
    void defaultsRoundTripAndKeepChineseNames() {
        String json = ConfigJson.write(ShopConfig.defaults());
        ShopConfig config = ConfigJson.parse(json);
        assertEquals(new BigDecimal("0.002"), config.stepFraction());
        assertEquals("钻石", config.entry("minecraft:diamond").name());
        assertEquals(new BigDecimal("1"), config.entry("minecraft:diamond").anchorDiamonds());
        assertEquals("下界合金升级锻造模板", config.entry("minecraft:netherite_upgrade_smithing_template").name());
        assertTrue(config.catalog().size() > 40);
    }

    @Test
    void missingOptionalKeysUseDefaultsButACatalogIsRequired() {
        ShopConfig config = ConfigJson.parse("""
                {"catalog":[{"item":"minecraft:diamond","name":"钻石","anchorDiamonds":"1"}]}
                """);
        assertEquals(64, config.maxCount());
        assertEquals(new BigDecimal("0.002"), config.stepFraction());
        assertThrows(IllegalArgumentException.class, () -> ConfigJson.parse("{\"stepFraction\":\"0.002\"}"));
    }

    @Test
    void aLargeStepIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShopConfig.defaults().withStep(new BigDecimal("0.5")));
    }
    @Test void legacyCatalogAddsEquipmentWithoutChangingCustomPrices() {
        ShopConfig config = ConfigJson.parse("{\"catalog\":[{\"item\":\"minecraft:diamond_sword\",\"name\":\"自定义剑\",\"anchorDiamonds\":\"8\"}]}");
        assertEquals(new BigDecimal("8"), config.entry("minecraft:diamond_sword").anchorDiamonds());
        assertEquals("自定义剑", config.entry("minecraft:diamond_sword").name());
        assertTrue(config.entry("minecraft:netherite_pickaxe") != null);
        assertTrue(config.entry("minecraft:comparator") != null);
        config.validate();
    }
    @Test void migratedCatalogHonorsExplicitRemoval() {
        ShopConfig config = ConfigJson.parse("{\"catalogVersion\":2,\"catalog\":[]}");
        assertEquals(0, config.catalog().size());
        assertEquals(0, ConfigJson.parse(ConfigJson.write(config)).catalog().size());
    }
}
