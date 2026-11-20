package dev.jdguo.shop.trade;

/** Plain item stacks only. Stacks that carry extra data are invisible to the shop. */
public interface ItemPurse {
    int plainCount(String itemId);

    int roomFor(String itemId);

    /** True only if give/take failures restore the entire original inventory in memory.
     * This does not promise crash atomicity with another mod's ledger. */
    default boolean failureAtomic() { return false; }

    enum Delivery { APPLIED, NO_CHANGE, UNKNOWN }
    default boolean durableDelivery() { return false; }
    default Delivery transferReferenced(String reference, String itemId, int count, boolean give) { return Delivery.UNKNOWN; }
    default String receiptStatus(String reference) { return "unknown"; }
    default void forgetReceipt(String reference) { }

    /** Restore the exact persisted box delivery plan, never treat it as loose items. */
    default ItemPurse forBulk(com.google.gson.JsonObject plan) { return null; }

    void give(String itemId, int count);

    void takePlain(String itemId, int count);
}
