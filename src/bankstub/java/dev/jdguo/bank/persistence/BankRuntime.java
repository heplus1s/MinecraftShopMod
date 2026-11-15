package dev.jdguo.bank.persistence;

import dev.jdguo.bank.ledger.BankBooks;
import net.minecraft.server.MinecraftServer;

/** Compile-only surface of the bank runtime. Not packaged. */
public final class BankRuntime {
    public static BankWorldState state;
    public static MinecraftServer server;

    private BankRuntime() {}

    public static BankBooks books() {
        throw new UnsupportedOperationException("stub");
    }

    public static void save() {
        throw new UnsupportedOperationException("stub");
    }
}
