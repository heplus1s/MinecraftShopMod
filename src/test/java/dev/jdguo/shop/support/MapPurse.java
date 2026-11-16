package dev.jdguo.shop.support;

import dev.jdguo.shop.trade.ItemPurse;

import java.util.HashMap;
import java.util.Map;

public final class MapPurse implements ItemPurse {
    public final Map<String, Integer> items = new HashMap<>();
    public int room = 1000;
    public boolean failGive;

    private final java.util.Set<String> receipts = new java.util.HashSet<>();
    public boolean uncertainAfterTransfer;
    public boolean uncertainOnTransfer;
    @Override public boolean failureAtomic() { return true; }
    @Override public boolean durableDelivery() { return true; }
    @Override public String receiptStatus(String reference) {
        return uncertainAfterTransfer ? "unknown" : receipts.contains(reference) ? "applied" : "none";
    }
    @Override public Delivery transferReferenced(String reference, String item, int count, boolean add) {
        if (receipts.contains(reference)) return Delivery.APPLIED;
        try { if (add) give(item, count); else takePlain(item, count); }
        catch (RuntimeException unchanged) { return Delivery.NO_CHANGE; }
        receipts.add(reference);
        if (uncertainOnTransfer) { uncertainAfterTransfer = true; uncertainOnTransfer = false; }
        return uncertainAfterTransfer ? Delivery.UNKNOWN : Delivery.APPLIED;
    }
    @Override public void forgetReceipt(String reference) { receipts.remove(reference); }

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
