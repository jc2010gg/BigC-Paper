package dev.linqfy.bigCasares.modules.casino.pawn;

import dev.linqfy.bigCasares.modules.casino.CasinoBank;
import dev.linqfy.bigCasares.modules.casino.CasinoFiles;
import dev.linqfy.bigCasares.modules.casino.CasinoServices;
import dev.linqfy.bigCasares.modules.casino.CasinoSettings;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Pawn tickets, the shop window and item appraisal, persisted in {@code casino/pawn.yml}. */
public final class PawnService implements CasinoServices {

    private final CasinoBank bank;
    private final Appraiser appraiser;
    private final PawnLedger ledger;
    private final File file;
    private final Logger logger;
    private final LongSupplier clock;

    public PawnService(CasinoSettings.Pawn settings, CasinoBank bank, File file, Logger logger, LongSupplier clock) {
        this.bank = bank;
        this.appraiser = new Appraiser(settings.prices());
        this.ledger = new PawnLedger(settings.loanRatio(), settings.redeemRatio(), settings.saleRatio(),
            settings.holdHours() * 3_600_000L);
        this.file = file;
        this.logger = logger;
        this.clock = clock;
        load();
    }

    @Override
    public long appraise(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        int damage = meta instanceof Damageable damageable ? damageable.getDamage() : 0;
        int levels = 0;
        if (meta != null) {
            levels += meta.getEnchants().values().stream().mapToInt(Integer::intValue).sum();
            if (meta instanceof EnchantmentStorageMeta stored) {
                levels += stored.getStoredEnchants().values().stream().mapToInt(Integer::intValue).sum();
            }
        }
        return appraiser.appraise(new AppraisalInput(item.getType().name(), item.getAmount(),
            item.getType().getMaxDurability(), damage, levels)).orElse(0);
    }

    @Override
    public void consign(ItemStack item, long appraisal) {
        ledger.consign(encode(item), nameOf(item), appraisal, clock.getAsLong());
        save();
    }

    public long offerFor(ItemStack item) {
        long value = appraise(item);
        return value <= 0 ? 0 : ledger.offerFor(value);
    }

    /** Takes the item, pays the loan and returns the ticket. The caller removes the item from the player. */
    public PawnTicket pawn(UUID owner, ItemStack item) {
        long value = appraise(item);
        if (value <= 0) {
            throw new IllegalStateException("Eso no lo compramos");
        }
        PawnTicket ticket = ledger.pawn(owner, encode(item), nameOf(item), value, clock.getAsLong());
        if (!bank.deposit(owner, ticket.loan())) {
            ledger.redeem(ticket.id(), owner, clock.getAsLong());
            throw new IllegalStateException("La caja está trabada, volvé en un rato");
        }
        save();
        return ticket;
    }

    public ItemStack redeem(UUID owner, String ticketId) {
        PawnTicket ticket = ledger.find(ticketId).orElseThrow(() -> new IllegalStateException("Esa boleta no existe"));
        if (!owner.equals(ticket.owner())) {
            throw new IllegalStateException("Esa boleta no es tuya");
        }
        if (!bank.withdraw(owner, ticket.redeemPrice())) {
            throw new IllegalStateException("Necesitás " + bank.format(ticket.redeemPrice()));
        }
        try {
            ledger.redeem(ticketId, owner, clock.getAsLong());
        } catch (IllegalStateException expired) {
            bank.deposit(owner, ticket.redeemPrice());
            throw expired;
        }
        save();
        return decode(ticket.itemData());
    }

    public ItemStack buy(UUID buyer, String ticketId) {
        PawnTicket ticket = ledger.find(ticketId).orElseThrow(() -> new IllegalStateException("Alguien ya se lo llevó"));
        if (!bank.withdraw(buyer, ticket.salePrice())) {
            throw new IllegalStateException("Necesitás " + bank.format(ticket.salePrice()));
        }
        try {
            ledger.buy(ticketId);
        } catch (IllegalStateException gone) {
            bank.deposit(buyer, ticket.salePrice());
            throw gone;
        }
        save();
        return decode(ticket.itemData());
    }

    /** Moves expired tickets to the window. Returns how many moved. */
    public int expire() {
        int moved = ledger.expire(clock.getAsLong()).size();
        if (moved > 0) {
            save();
        }
        return moved;
    }

    public List<PawnTicket> pawnedBy(UUID owner) {
        return ledger.pawnedBy(owner);
    }

    public List<PawnTicket> showcase() {
        return ledger.showcase();
    }

    public ItemStack preview(PawnTicket ticket) {
        return decode(ticket.itemData());
    }

    public long now() {
        return clock.getAsLong();
    }

    private static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack decode(String data) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
    }

    private static String nameOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        String raw = item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return (item.getAmount() > 1 ? item.getAmount() + "x " : "") + raw;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PawnTicket ticket : ledger.all()) {
            String path = "tickets." + ticket.id();
            yaml.set(path + ".owner", ticket.owner() == null ? null : ticket.owner().toString());
            yaml.set(path + ".item", ticket.itemData());
            yaml.set(path + ".name", ticket.itemName());
            yaml.set(path + ".loan", ticket.loan());
            yaml.set(path + ".redeem", ticket.redeemPrice());
            yaml.set(path + ".sale", ticket.salePrice());
            yaml.set(path + ".created", ticket.createdAtMillis());
            yaml.set(path + ".expires", ticket.expiresAtMillis());
            yaml.set(path + ".state", ticket.state().name());
        }
        try {
            CasinoFiles.atomicSave(yaml, file);
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Casino] Could not save the pawn shop", failure);
        }
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        ConfigurationSection tickets = YamlConfiguration.loadConfiguration(file).getConfigurationSection("tickets");
        if (tickets == null) {
            return;
        }
        for (String id : tickets.getKeys(false)) {
            ConfigurationSection entry = tickets.getConfigurationSection(id);
            try {
                String owner = entry.getString("owner");
                ledger.restore(new PawnTicket(id, owner == null ? null : UUID.fromString(owner),
                    entry.getString("item"), entry.getString("name", "?"), entry.getLong("loan"),
                    entry.getLong("redeem"), entry.getLong("sale"), entry.getLong("created"),
                    entry.getLong("expires"), PawnTicket.State.valueOf(entry.getString("state", "SHOWCASE"))));
            } catch (RuntimeException invalid) {
                logger.warning("[Casino] Ignoring invalid pawn ticket " + id);
            }
        }
    }
}
