package dev.jdguo.bank.ledger;

import dev.jdguo.bank.currency.CurrencyDef;
import dev.jdguo.bank.market.PersonalNote;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.UUID;

/** Compile-only surface of the reads the shop is allowed to make. Not packaged. */
public final class BankBooks {
    public CurrencyDef primaryVirtual() {
        throw new UnsupportedOperationException("stub");
    }

    public BigDecimal unitsPer(String pay, String receive) {
        throw new UnsupportedOperationException("stub");
    }

    public BigInteger copperPerDiamond() {
        throw new UnsupportedOperationException("stub");
    }

    public BigDecimal interestFraction() {
        throw new UnsupportedOperationException("stub");
    }

    public BigInteger savingsPool(String currency) {
        throw new UnsupportedOperationException("stub");
    }

    public BigInteger balance(UUID player, String currency) {
        throw new UnsupportedOperationException("stub");
    }

    public PersonalNote personalNoteByName(String name) {
        throw new UnsupportedOperationException("stub");
    }

    public PersonalNote personalNoteById(UUID id) {
        throw new UnsupportedOperationException("stub");
    }

    public Collection<PersonalNote> personalNotes() {
        throw new UnsupportedOperationException("stub");
    }

    public BigInteger personalHolding(UUID noteId, UUID player) {
        throw new UnsupportedOperationException("stub");
    }

    public BigDecimal personalDiamondPrice(PersonalNote note) {
        throw new UnsupportedOperationException("stub");
    }
}
