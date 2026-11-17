package dev.jdguo.shop.support;

import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.price.MoneyScale;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory balances at four decimal places. Each currency is a separate book. */
public final class MemoryBank implements ShopBank {
    public final Map<String, BigDecimal> player = new HashMap<>();
    public final Map<String, BigDecimal> shop = new HashMap<>();
    public final Map<String, BigDecimal> rates = new HashMap<>();
    public final Map<String, PersonalNoteRef> notes = new HashMap<>();
    public boolean loaded = true;
    public boolean ready = true;
    public boolean hook = true;
    public BigDecimal interest = new BigDecimal("0.02");
    public boolean hideShopBalance;
    public boolean rejectReferenced;
    public boolean unknownAfterPostOnce;
    public int referencedCalls;
    public int rollbackCalls;
    public boolean failComplete;
    public int completionCalls;

    public MemoryBank() {
        player.put("note", new BigDecimal("100"));
        shop.put("note", BigDecimal.ZERO);
        rates.put("note", BigDecimal.ONE);
    }

    public BigDecimal player(String key) {
        return MoneyScale.quantize(player.getOrDefault(key, BigDecimal.ZERO));
    }

    public BigDecimal shop(String key) {
        return MoneyScale.quantize(shop.getOrDefault(key, BigDecimal.ZERO));
    }

    @Override public boolean modLoaded() { return loaded; }
    @Override public boolean ledgerReady() { return loaded && ready; }
    @Override public boolean operatingHookInstalled() { return loaded && hook; }
    @Override public boolean operatingAccountReady() { return loaded && ready && hook; }
    @Override public String currencyId() { return "note"; }
    @Override public String currencyName() { return "央行券"; }

    @Override
    public BigDecimal unitsPerDiamond(PayChoice choice) {
        if (!loaded || !ready || choice == null || choice.hard()) {
            return null;
        }
        return rates.get(choice.key());
    }

    @Override public BigInteger copperPerDiamond() { return new BigInteger("1000"); }
    @Override public BigDecimal annualInterestFraction() { return interest; }

    @Override
    public BigInteger savingsPool() {
        return player("note").add(shop("note")).setScale(0, MoneyScale.ROUND).toBigInteger();
    }

    @Override
    public BigDecimal playerBalance(UUID playerId, PayChoice choice) {
        if (!operatingAccountReady() || choice == null) {
            return null;
        }
        return player(choice.key());
    }

    @Override
    public BigDecimal operatingBalance(PayChoice choice) {
        if (!operatingAccountReady() || hideShopBalance || choice == null) {
            return null;
        }
        return shop(choice.key());
    }

    private record Receipt(UUID player, PayChoice choice, BigDecimal amount, boolean buying, boolean rolledBack) {}
    private final Map<String, Receipt> receipts = new HashMap<>();
    @Override public boolean supportsReferencedSettlement() { return true; }
    @Override public String receiptStatus(String reference) {
        Receipt r = receipts.get(reference);
        return r == null ? "none" : r.rolledBack() ? "rolled_back" : "applied";
    }
    @Override public PostResult postReferenced(UUID playerId, PayChoice choice, BigDecimal amount, boolean buying, String reference) {
        referencedCalls++;
        if (rejectReferenced) return PostResult.fail("bank_rejected");
        Receipt old = receipts.get(reference);
        if (old != null) {
            if (!old.player().equals(playerId) || !old.choice().equals(choice) || old.amount().compareTo(amount) != 0 || old.buying() != buying)
                return PostResult.fail("reference_conflict");
            return old.rolledBack() ? PostResult.fail("rolled_back") : PostResult.ok(player(choice.key()), shop(choice.key()));
        }
        PostResult result = post(playerId, choice, amount, buying);
        if (result.success()) receipts.put(reference, new Receipt(playerId, choice, amount, buying, false));
        if (result.success() && unknownAfterPostOnce) {
            unknownAfterPostOnce = false;
            return PostResult.fail("settlement_unknown");
        }
        return result;
    }
    @Override public PostResult completePayment(String reference) {
        completionCalls++;
        return failComplete || !"applied".equals(receiptStatus(reference))
                ? PostResult.fail("save_failed") : PostResult.ok(null, null);
    }
    @Override public PostResult rollback(String reference) {
        rollbackCalls++;
        Receipt r = receipts.get(reference);
        if (r == null) return PostResult.fail("none");
        if (r.rolledBack()) return PostResult.ok(null, null);
        BigDecimal delta = r.buying() ? r.amount() : r.amount().negate();
        player.put(r.choice().key(), player(r.choice().key()).add(delta));
        shop.put(r.choice().key(), shop(r.choice().key()).subtract(delta));
        receipts.put(reference, new Receipt(r.player(), r.choice(), r.amount(), r.buying(), true));
        return PostResult.ok(player(r.choice().key()), shop(r.choice().key()));
    }

    @Override
    public PostResult post(UUID playerId, PayChoice choice, BigDecimal amount, boolean playerPaysShop) {
        if (choice == null || choice.hard()) {
            return PostResult.fail("hard_currency");
        }
        if (!loaded) {
            return PostResult.fail("bank_absent");
        }
        if (!ready) {
            return PostResult.fail("bank_not_ready");
        }
        if (!hook) {
            return PostResult.fail("bank_hook_missing");
        }
        BigDecimal due = MoneyScale.quantize(amount);
        if (!MoneyScale.positive(due)) {
            return PostResult.fail("bank_rejected");
        }
        String key = choice.key();
        BigDecimal payer = player(key);
        BigDecimal till = shop(key);
        if (playerPaysShop) {
            if (payer.compareTo(due) < 0) {
                return PostResult.fail("insufficient_funds", payer, till);
            }
            player.put(key, MoneyScale.quantize(payer.subtract(due)));
            shop.put(key, MoneyScale.quantize(till.add(due)));
        } else {
            if (till.compareTo(due) < 0) {
                return PostResult.fail("insufficient_shop", payer, till);
            }
            shop.put(key, MoneyScale.quantize(till.subtract(due)));
            player.put(key, MoneyScale.quantize(payer.add(due)));
        }
        return PostResult.ok(player(key), shop(key));
    }

    @Override
    public PersonalNoteRef findPersonal(String name) {
        if (name == null) {
            return null;
        }
        return notes.values().stream().filter(note -> note.name().equals(name)).findFirst().orElse(null);
    }

    @Override
    public List<String> personalNames() {
        return notes.values().stream().map(PersonalNoteRef::name).toList();
    }
}
