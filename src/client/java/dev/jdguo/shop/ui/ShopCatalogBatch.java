package dev.jdguo.shop.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Ordered bounded server pages become one catalog, never a partial category list. */
public final class ShopCatalogBatch {
    private String batch;
    private int nextPage;
    private int totalPages;
    private JsonArray rows = new JsonArray();

    public void clear() {
        batch = null;
        nextPage = 1;
        totalPages = 0;
        rows = new JsonArray();
    }

    public JsonObject accept(JsonObject packet) {
        if (!packet.has("catalogBatch")) { clear(); return packet; }
        try {
            String id = packet.get("catalogBatch").getAsString();
            int page = packet.get("page").getAsInt();
            int pages = packet.get("pages").getAsInt();
            if (page == 1) { clear(); batch = id; totalPages = pages; }
            if (!id.equals(batch) || page != nextPage || pages != totalPages || pages < 1) {
                clear(); return null;
            }
            for (var row : packet.getAsJsonArray("listings")) rows.add(row.deepCopy());
            nextPage++;
            if (page < pages) return null;
            JsonObject complete = packet.deepCopy();
            complete.add("listings", rows);
            complete.addProperty("page", 1);
            complete.addProperty("pages", 1);
            clear();
            return complete;
        } catch (RuntimeException malformed) { clear(); return null; }
    }
}
