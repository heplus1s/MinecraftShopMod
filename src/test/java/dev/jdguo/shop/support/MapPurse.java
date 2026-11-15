package dev.jdguo.shop.support;

import dev.jdguo.shop.trade.ItemPurse;

import java.util.HashMap;
import java.util.Map;

public final class MapPurse implements ItemPurse {
    public final Map<String, Integer> items = new HashMap<>();
    public int room = 1000;
    public boolean failGive;

    @Override
    public int plainCount(String itemId) {
        return items.getOrDefault(itemId, 0);
    }

    @Override
    public int roomFor(String itemId) {
        return room;
    }

    @Override
    public void give(String itemId, int count) {
        if (failGive) {
            throw new IllegalStateException("give failed");
        }
        items.merge(itemId, count, Integer::sum);
    }

    @Override
    public void takePlain(String itemId, int count) {
        int have = plainCount(itemId);
        if (have < count) {
            throw new IllegalStateException("short");
        }
        int left = have - count;
        if (left == 0) {
            items.remove(itemId);
        } else {
            items.put(itemId, left);
        }
    }
}
