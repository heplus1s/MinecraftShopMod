package dev.jdguo.shop.catalog;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CatalogUpgradeStoreTest {
    @TempDir Path directory;
    private String source(boolean optIn) {
        var defaults=ShopConfig.defaults();
        var old=new ShopConfig(defaults.stepFraction(),defaults.minDiamondPrice(),99,9,
                ShopConfig.legacyDefaultCatalog(),defaults.spreadFraction(),0);
        var root=JsonParser.parseString(ConfigJson.write(old)).getAsJsonObject();
        root.addProperty("refreshDefaultAnchors",optIn); root.addProperty("myUnrelatedSetting","preserve me");
        return root.toString();
    }
    @Test void oldUnoptedConfigurationIsByteForByteUntouched() throws Exception {
        Path file=directory.resolve("shopmod.json");String original=source(false);Files.writeString(file,original);
        assertEquals(0,ConfigStore.readAndUpgrade(file).pricingVersion());
        assertEquals(original,Files.readString(file));
    }
    @Test void explicitUpgradeBacksUpOriginalAndPreservesUnknownSettingsAndIsIdempotent() throws Exception {
        Path file=directory.resolve("shopmod.json");String original=source(true);Files.writeString(file,original);
        var config=ConfigStore.readAndUpgrade(file);
        assertEquals(CatalogPricing.VERSION,config.pricingVersion());
        assertEquals(original,Files.readString(directory.resolve("shopmod.pre-pricing-v2.json")));
        var result=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("preserve me",result.get("myUnrelatedSetting").getAsString());
        assertFalse(result.get("refreshDefaultAnchors").getAsBoolean());
        assertEquals(99,config.maxCount()); assertEquals(9,config.pageSize());
        String once=Files.readString(file);ConfigStore.readAndUpgrade(file);assertEquals(once,Files.readString(file));
    }
    @Test void failedBackupDoesNotApplyNewAnchorsOrOverwriteOriginal() throws Exception {
        Path file=directory.resolve("shopmod.json");String original=source(true);Files.writeString(file,original);
        Files.createDirectory(directory.resolve("shopmod.pre-pricing-v2.json"));
        var config=ConfigStore.readAndUpgrade(file);
        assertEquals(0,config.pricingVersion());
        assertEquals(0,new BigDecimal(".35").compareTo(config.entry("minecraft:sponge").anchorDiamonds()));
        assertEquals(original,Files.readString(file));
    }
}
