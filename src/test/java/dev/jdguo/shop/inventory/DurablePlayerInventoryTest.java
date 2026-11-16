package dev.jdguo.shop.inventory;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DurablePlayerInventoryTest {
    private static final UUID HOST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MARKER = "shopmod_tx_test_give";
    private static NbtCompound host(boolean receipt, int count) {
        NbtCompound nbt = new NbtCompound(); nbt.putUuid("UUID", HOST);
        NbtList inventory = new NbtList(); NbtCompound stack = new NbtCompound();
        stack.putString("id", "minecraft:diamond"); stack.putByte("Count", (byte) count);
        stack.putByte("Slot", (byte) 0); inventory.add(stack); nbt.put("Inventory", inventory);
        NbtList tags = new NbtList(); if(receipt) tags.add(NbtString.of(MARKER)); nbt.put("Tags",tags);
        return nbt;
    }
    @Test void missingDiskReceiptIsUnknownIfMemoryOrHostAlreadyHasProof() {
        assertEquals("none", PlayerPurse.missingDiskReceiptStatus(false,false));
        assertEquals("unknown", PlayerPurse.missingDiskReceiptStatus(true,false));
        assertEquals("unknown", PlayerPurse.missingDiskReceiptStatus(false,true));
        assertEquals("unknown", PlayerPurse.missingDiskReceiptStatus(true,true));
    }
    @Test void confirmsMatchingInventoryAndReceipt() throws IOException {
        NbtCompound expected=host(true,4);
        DurablePlayerInventory.verifyHostCheckpoint(expected,expected.copy());
        assertTrue(DurablePlayerInventory.hostReceiptConfirmed(HOST,expected,MARKER));
    }
    @Test void oldHostInventoryCannotConfirmDelivery() {
        assertThrows(IOException.class,()->DurablePlayerInventory.verifyHostCheckpoint(host(true,4),host(true,3)));
    }
    @Test void missingHostReceiptCannotConfirmDeliveryOrRetry() throws IOException {
        assertThrows(IOException.class,()->DurablePlayerInventory.verifyHostCheckpoint(host(true,4),host(false,4)));
        assertFalse(DurablePlayerInventory.hostReceiptConfirmed(HOST,host(false,4),MARKER));
    }
    @Test void missingHostIdentityIsUnknownNotSuccessful() {
        assertThrows(IOException.class,()->DurablePlayerInventory.hostReceiptConfirmed(HOST,new NbtCompound(),MARKER));
    }
    @Test void lanGuestDoesNotRequireHostsReceipt() throws IOException {
        assertTrue(DurablePlayerInventory.hostReceiptConfirmed(UUID.randomUUID(),host(false,3),MARKER));
    }
}
