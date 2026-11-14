package dev.jdguo.shop.price;

import dev.jdguo.shop.catalog.ShopConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSavedTest {
    @Test
    void roundTripKeepsUnknownFieldsAndASiblingPrice() {
        String raw = """
                {
                  "version": 1,
                  "note": "keep this",
                  "prices": {
                    "minecraft:diamond": {"diamond": "1.5", "later": true},
                    "minecraft:dirt": "0.25"
                  }
                }
                """;
        ShopSaved saved = ShopSaved.parse(raw);
        saved.setPrice("minecraft:diamond", new BigDecimal("1.75"));
        ShopSaved again = ShopSaved.parse(saved.toJson());
        assertEquals(new BigDecimal("1.75"), again.read("minecraft:diamond").value());
        assertEquals(new BigDecimal("0.25"), again.read("minecraft:dirt").value());
        assertTrue(again.toJson().contains("keep this"));
        assertTrue(again.toJson().contains("later"));
    }

    @Test
    void aNewerVersionIsRefused() {
        assertThrows(IllegalStateException.class, () -> ShopSaved.parse("{\"version\":9,\"prices\":{}}"));
    }

    @Test
    void aCorruptRowDoesNotEraseTheDocument() {
        ShopSaved saved = ShopSaved.parse("{\"version\":1,\"prices\":{\"minecraft:diamond\":{\"diamond\":\"nope\"},\"minecraft:dirt\":{\"diamond\":\"0.2\"}}}");
        assertTrue(saved.read("minecraft:diamond").corrupt());
        assertEquals(new BigDecimal("0.2"), saved.read("minecraft:dirt").value());
        saved.clearPrice("minecraft:diamond");
        assertFalse(saved.read("minecraft:diamond").present());
        assertEquals(new BigDecimal("0.2"), saved.read("minecraft:dirt").value());
    }

    @Test
    void aSavedPriceBeatsANewAnchorUntilReset() {
        ShopSaved saved = ShopSaved.empty();
        saved.setPrice("minecraft:diamond", new BigDecimal("4"));
        PriceBook book = new PriceBook(saved);
        ShopConfig config = ShopConfig.defaults().withAnchor("minecraft:diamond", new BigDecimal("2"));
        assertEquals(new BigDecimal("4"), book.lookup(config, "minecraft:diamond").diamondEach());
        book.reset("minecraft:diamond");
        assertEquals(new BigDecimal("2"), book.lookup(config, "minecraft:diamond").diamondEach());
    }
}
