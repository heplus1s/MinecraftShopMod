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
}
