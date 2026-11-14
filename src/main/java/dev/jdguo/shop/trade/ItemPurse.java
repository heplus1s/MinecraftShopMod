package dev.jdguo.shop.trade;

/** Plain item stacks only. Stacks that carry extra data are invisible to the shop. */
public interface ItemPurse {
    int plainCount(String itemId);

    int roomFor(String itemId);

    void give(String itemId, int count);

    void takePlain(String itemId, int count);
}
