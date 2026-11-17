package dev.jdguo.shop.ui;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShopCatalogBatchTest {
    private JsonObject page(String batch, int page, int count, String item, String category) {
        var result = new JsonObject();
        result.addProperty("catalogBatch", batch); result.addProperty("page", page); result.addProperty("pages", count);
        result.addProperty("open", true);
        var rows = new JsonArray(); var row = new JsonObject();
        row.addProperty("item", item); row.addProperty("name", item); row.addProperty("category", category);
        rows.add(row); result.add("listings", rows); return result;
    }
    @Test void exposesEveryPageOnlyWhenComplete() {
        var batches = new ShopCatalogBatch();
        assertNull(batches.accept(page("a",1,2,"stone","自然方块")));
        var all = batches.accept(page("a",2,2,"bread","食物与饮品"));
        assertEquals(2,ShopQuotes.items(all).size());
        assertEquals(2,ShopQuotes.categories(ShopQuotes.items(all)).size());
        assertTrue(ShopQuotes.shouldPresent(all));
    }
    @Test void rejectsMissingOrMixedPagesAndCanRestart() {
        var batches = new ShopCatalogBatch();
        assertNull(batches.accept(page("a",1,3,"stone","自然方块")));
        assertNull(batches.accept(page("a",3,3,"bread","食物与饮品")));
        assertNull(batches.accept(page("b",1,2,"stone","自然方块")));
        assertNull(batches.accept(page("a",2,2,"bread","食物与饮品")));
        assertNotNull(batches.accept(page("c",1,1,"bread","食物与饮品")));
    }
    @Test void acceptsLegacyPacketsAndClearsOnDisconnect() {
        var batches = new ShopCatalogBatch();
        var legacy = new JsonObject(); assertSame(legacy,batches.accept(legacy));
        batches.accept(page("a",1,2,"stone","自然方块")); batches.clear();
        assertNull(batches.accept(page("a",2,2,"bread","食物与饮品")));
    }
}
