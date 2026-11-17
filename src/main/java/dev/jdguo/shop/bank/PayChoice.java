package dev.jdguo.shop.bank;

import java.util.Locale;
import java.util.UUID;

/**
 * The one currency a player uses for a shop order.
 * {@code note} is the central bank note. A personal note uses {@code personal:<uuid>}.
 * Copper and diamond are never shop payment currencies.
 */
public record PayChoice(String key, String name, boolean personal, UUID personalId) {
    public static PayChoice note() {
        return new PayChoice("note", "央行券", false, null);
    }

    public static PayChoice note(String displayName) {
        String name = displayName == null || displayName.isBlank() ? "央行券" : displayName;
        return new PayChoice("note", name, false, null);
    }

    public static PayChoice personal(UUID id, String displayName) {
        if (id == null) {
            throw new IllegalArgumentException("personal note");
        }
        String name = displayName == null || displayName.isBlank() ? id.toString() : displayName;
        return new PayChoice("personal:" + id, name, true, id);
    }

    public static boolean hardKey(String raw) {
        if (raw == null) {
            return false;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return id.equals("copper")
                || id.equals("diamond")
                || id.equals("minecraft:copper_ingot")
                || id.equals("minecraft:diamond");
    }

    public boolean hard() {
        return hardKey(key);
    }
}
