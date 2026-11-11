package dev.linqfy.bigCasares.modules.casino.pawn;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.CasinoFormat;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoMenu;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.EntityGroup;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The pawn counter: pawn what you hold for quick chips, buy it back within the hold time, or browse the
 * liquidation window where expired and seized goods are sold. The glass case shows the window's items.
 */
public final class PawnShop extends Attraction {

    private static final int CASE_SLOTS = 6;

    private final PawnService pawn;
    private final EntityGroup showcase = new EntityGroup();
    private final List<ItemDisplay> spinning = new ArrayList<>();

    public PawnShop(CasinoContext context, Installation installation, Frame frame, PawnService pawn) {
        super(context, installation, frame);
        this.pawn = pawn;
    }

    @Override
    public void spawn() {
        DisplayKit kit = context.kit();
        kit.block(props, frame.at(0, 0, 0.8), Material.SPRUCE_PLANKS.createBlockData(), 3.0f, 1.0f, 0.8f);
        kit.block(props, frame.at(0, 1.0, 0.8), Material.POLISHED_DEEPSLATE.createBlockData(), 3.1f, 0.06f, 0.9f);
        kit.block(props, frame.at(1.2, 1.06, 0.8), Material.IRON_BARS.createBlockData(), 0.05f, 0.9f, 0.8f);
        kit.block(props, frame.at(-1.2, 1.06, 0.8), Material.IRON_BARS.createBlockData(), 0.05f, 0.9f, 0.8f);
        kit.block(props, frame.at(0, 1.06, 0.4), Material.IRON_BARS.createBlockData(), 2.4f, 0.9f, 0.05f);
        kit.item(props, frame.at(0.6, 1.08, 0.9), new ItemStack(Material.CLOCK), 0.3f, DisplayKit.FLAT);
        kit.item(props, frame.at(0.3, 1.08, 1.0), new ItemStack(Material.SPYGLASS), 0.3f, DisplayKit.FLAT);
        Mannequin broker = kit.npc(props, frame.at(0, 0, -0.2, 0), "§6§lEl Turco", "§7Casa de Empeños · 48 h", "");
        broker.getEquipment().setHelmet(new ItemStack(Material.BROWN_CARPET));
        context.router().bind(broker, this::open);
        Interaction counter = kit.interaction(props, frame.at(0, 0, 0.8), 3.0f, 1.3f);
        context.router().bind(counter, this::open);
        kit.text(props, frame.at(0, 2.7, 0.2), CasinoText.legacy("§6§lCASA DE EMPEÑOS\n§7Compramos lo que sea*\n§8*casi"),
            0.8f, true, 0x99000000);

        // Glass case to the right of the counter (from the customer's view: frame left).
        Frame glass = frame.child(2.6, 0, 1.2, 0);
        kit.block(props, glass.at(0, 0, 0), Material.DARK_OAK_PLANKS.createBlockData(), 1.6f, 0.8f, 1.0f);
        kit.block(props, glass.at(0, 0.8, 0), Material.GLASS.createBlockData(), 1.6f, 0.6f, 1.0f);
        kit.block(props, glass.at(0, 1.4, 0), Material.GOLD_BLOCK.createBlockData(), 1.65f, 0.04f, 1.05f);
        kit.text(props, glass.at(0, 1.75, 0), CasinoText.legacy("§e§lVITRINA DE LIQUIDACIÓN"), 0.5f, true, 0x66000000);
        Interaction caseClick = kit.interaction(props, glass.at(0, 0, 0), 1.6f, 1.5f);
        context.router().bind(caseClick, this::openShowcase);
        refreshShowcase();
    }

    @Override
    protected void onTick(long tick) {
        if (tick % 200 == 0) {
            pawn.expire();
            refreshShowcase();
        }
        if (tick % 4 == 0) {
            float angle = (float) (tick * 0.05);
            for (ItemDisplay display : spinning) {
                if (display.isValid()) {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(4);
                    display.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angle),
                        new Vector3f(0.32f), new Quaternionf()));
                }
            }
        }
    }

    @Override
    protected void onDespawn() {
        showcase.removeAll();
        spinning.clear();
    }

    private void refreshShowcase() {
        showcase.removeAll();
        spinning.clear();
        List<PawnTicket> items = pawn.showcase();
        Frame glass = frame.child(2.6, 0, 1.2, 0);
        for (int index = 0; index < Math.min(CASE_SLOTS, items.size()); index++) {
            double right = -0.5 + (index % 3) * 0.5;
            double forward = index < 3 ? -0.2 : 0.2;
            ItemStack preview;
            try {
                preview = pawn.preview(items.get(index));
            } catch (RuntimeException unreadable) {
                continue;
            }
            spinning.add(context.kit().item(showcase, glass.at(right, 1.05, forward), preview, 0.32f, DisplayKit.NONE));
        }
    }

    private void open(Player player) {
        CasinoMenu menu = new CasinoMenu(3, "§6Casa de Empeños");
        ItemStack hand = player.getInventory().getItemInMainHand();
        long offer = pawn.offerFor(hand);
        if (hand.getType().isAir()) {
            menu.set(11, CasinoItems.button(Material.HOPPER, "§7Empeñar", "§7Agarrá en la mano lo que querés empeñar."), null);
        } else if (offer <= 0) {
            menu.set(11, CasinoItems.button(Material.BARRIER, "§cEso no lo compramos",
                "§7El Turco lo mira y se ríe."), null);
        } else {
            ItemStack shown = hand.clone();
            ItemMeta meta = shown.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.hasLore() && meta.lore() != null
                ? meta.lore() : List.of());
            lore.add(CasinoText.legacy("§6Te doy: §e" + context.chips(offer)));
            lore.add(CasinoText.legacy("§7Lo rescatás por §f" + context.chips(Math.round(offer
                * context.settings().pawn().redeemRatio())) + " §7en §f" + context.settings().pawn().holdHours() + " h"));
            lore.add(CasinoText.legacy("§a§lClick para empeñar"));
            meta.lore(lore);
            shown.setItemMeta(meta);
            menu.set(11, shown, (clicker, click) -> pawnHand(clicker));
        }
        List<PawnTicket> mine = pawn.pawnedBy(player.getUniqueId());
        menu.set(13, CasinoItems.button(Material.PAPER, "§eMis boletas (" + mine.size() + ")",
            "§7Rescatá lo que empeñaste antes de que venza."), (clicker, click) -> openTickets(clicker));
        menu.set(15, CasinoItems.button(Material.GLASS, "§bVitrina de liquidación (" + pawn.showcase().size() + ")",
            "§7Lo que nadie rescató. Precios de remate."), (clicker, click) -> openShowcase(clicker));
        menu.fill(CasinoItems.filler());
        menu.open(player);
    }

    private void pawnHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return;
        }
        ItemStack item = hand.clone();
        try {
            PawnTicket ticket = pawn.pawn(player.getUniqueId(), item);
            player.getInventory().setItemInMainHand(null);
            player.sendMessage("§6[El Turco] §fTomá " + context.chips(ticket.loan()) + ". Boleta §e#" + ticket.id()
                + "§f. Rescatalo por " + context.chips(ticket.redeemPrice()) + " antes de "
                + context.settings().pawn().holdHours() + " h.");
            context.sound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.2f);
        } catch (IllegalStateException refused) {
            player.sendMessage("§6[El Turco] §c" + refused.getMessage());
        }
        player.closeInventory();
    }

    private void openTickets(Player player) {
        CasinoMenu menu = new CasinoMenu(3, "§6Mis boletas");
        int slot = 0;
        for (PawnTicket ticket : pawn.pawnedBy(player.getUniqueId())) {
            if (slot >= 26) {
                break;
            }
            ItemStack shown = decorate(ticket, List.of(
                "§7Rescate: §e" + context.chips(ticket.redeemPrice()),
                "§7Vence en: §f" + CasinoFormat.duration(ticket.expiresAtMillis() - pawn.now()),
                "§a§lClick para rescatar"));
            menu.set(slot++, shown, (clicker, click) -> {
                try {
                    give(clicker, pawn.redeem(clicker.getUniqueId(), ticket.id()));
                    clicker.sendMessage("§6[El Turco] §aRescataste tu " + ticket.itemName() + ".");
                } catch (IllegalStateException refused) {
                    clicker.sendMessage("§6[El Turco] §c" + refused.getMessage());
                }
                openTickets(clicker);
            });
        }
        menu.set(26, CasinoItems.button(Material.ARROW, "§7Volver"), (clicker, click) -> open(clicker));
        menu.fill(CasinoItems.filler());
        menu.open(player);
    }

    private void openShowcase(Player player) {
        pawn.expire();
        CasinoMenu menu = new CasinoMenu(6, "§bVitrina de liquidación");
        int slot = 0;
        for (PawnTicket ticket : pawn.showcase()) {
            if (slot >= 53) {
                break;
            }
            ItemStack shown = decorate(ticket, List.of("§7Precio de remate: §e" + context.chips(ticket.salePrice()),
                ticket.owner() == null ? "§8Embargado por la AFIP" : "§8No lo rescataron a tiempo",
                "§a§lClick para comprar"));
            menu.set(slot++, shown, (clicker, click) -> {
                try {
                    give(clicker, pawn.buy(clicker.getUniqueId(), ticket.id()));
                    clicker.sendMessage("§6[El Turco] §aCompraste " + ticket.itemName() + " a precio de remate.");
                    context.sound(clicker.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);
                } catch (IllegalStateException refused) {
                    clicker.sendMessage("§6[El Turco] §c" + refused.getMessage());
                }
                refreshShowcase();
                openShowcase(clicker);
            });
        }
        menu.set(53, CasinoItems.button(Material.ARROW, "§7Volver"), (clicker, click) -> open(clicker));
        menu.fill(CasinoItems.filler());
        menu.open(player);
    }

    private ItemStack decorate(PawnTicket ticket, List<String> lines) {
        ItemStack shown;
        try {
            shown = pawn.preview(ticket);
        } catch (RuntimeException unreadable) {
            shown = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = shown.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.hasLore() && meta.lore() != null
            ? meta.lore() : List.of());
        lines.forEach(line -> lore.add(CasinoText.legacy(line)));
        meta.lore(lore);
        shown.setItemMeta(meta);
        return shown;
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
            .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }
}
