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
        assertEquals(0, again.read("minecraft:diamond").value().compareTo(new BigDecimal("1.75")));
        assertEquals(0, again.read("minecraft:dirt").value().compareTo(new BigDecimal("0.25")));
        assertTrue(again.toJson().contains("keep this"));
        assertTrue(again.toJson().contains("later"));
    }

    @Test
    void emptyCheckpointIsNotANewWorld() {
        assertThrows(IllegalStateException.class, () -> ShopSaved.parse(" "));
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
        assertEquals(0, book.lookup(config, "minecraft:diamond").diamondEach().compareTo(new BigDecimal("4")));
        book.reset("minecraft:diamond");
        assertEquals(0, book.lookup(config, "minecraft:diamond").diamondEach().compareTo(new BigDecimal("2")));
    }
    @Test void onlyCompletedShopTradesAppearOnceAndSurviveReload() {
        var saved=ShopSaved.empty();var player=java.util.UUID.randomUUID();
        dev.jdguo.shop.support.StockFixture.seed(saved, "minecraft:diamond_sword", 1);
        saved.beginTrade("a",player,"minecraft:diamond_sword",1,true,"note",new BigDecimal("4.25"),BigDecimal.ONE,BigDecimal.TEN);
        assertEquals(0,saved.marketTrades().size());
        saved.tradeState("a","completed");
        saved.beginTrade("b",player,"minecraft:diamond_sword",1,false,"note",new BigDecimal("4"),BigDecimal.TEN,BigDecimal.ONE);
        saved.tradeState("b","cancelled");
        var history=ShopSaved.parse(saved.toJson()).marketTrades();
        assertEquals(1,history.size());assertEquals("a",history.get(0).getAsJsonObject().get("reference").getAsString());
        assertEquals("4.25",history.get(0).getAsJsonObject().get("amount").getAsString());
        assertEquals(1,saved.marketTrades().size());
    }
}
