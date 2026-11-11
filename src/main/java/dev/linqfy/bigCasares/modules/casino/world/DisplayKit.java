package dev.linqfy.bigCasares.modules.casino.world;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spawns the casino's display entities. Everything is non-persistent and tagged: nothing is ever written to
 * the world save, so a crash cannot leave duplicated tables behind, and the attractions rebuild on enable.
 */
public final class DisplayKit {

    public static final Quaternionf NONE = new Quaternionf();
    /** Lays an item flat with its face up and its top edge pointing away from the viewer. */
    public static final Quaternionf FLAT = new Quaternionf(new AxisAngle4f((float) -Math.PI / 2, 1, 0, 0));

    private final Plugin plugin;
    private final CasinoKeys keys;

    public DisplayKit(Plugin plugin, CasinoKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public Plugin plugin() {
        return plugin;
    }

    /** A box of {@code data} whose bottom face is centred on {@code base}, turned by the location's yaw. */
    public BlockDisplay block(EntityGroup group, Location base, BlockData data, float sx, float sy, float sz) {
        return block(group, base, data, sx, sy, sz, NONE);
    }

    public BlockDisplay block(EntityGroup group, Location base, BlockData data, float sx, float sy, float sz,
                              Quaternionf rotation) {
        Location spawn = base.clone();
        spawn.setPitch(0);
        return group.add(base.getWorld().spawn(spawn, BlockDisplay.class, display -> {
            prepare(display);
            display.setBlock(data);
            display.setTransformation(boxTransformation(sx, sy, sz, rotation));
        }));
    }

    public static Transformation boxTransformation(float sx, float sy, float sz, Quaternionf rotation) {
        Vector3f offset = new Vector3f(-sx / 2, 0, -sz / 2);
        new Quaternionf(rotation).transform(offset);
        return new Transformation(offset, new Quaternionf(rotation), new Vector3f(sx, sy, sz), new Quaternionf());
    }

    public ItemDisplay item(EntityGroup group, Location at, ItemStack item, float scale, Quaternionf rotation) {
        Location spawn = at.clone();
        spawn.setPitch(0);
        return group.add(at.getWorld().spawn(spawn, ItemDisplay.class, display -> {
            prepare(display);
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(rotation),
                new Vector3f(scale, scale, scale), new Quaternionf()));
        }));
    }

    public TextDisplay text(EntityGroup group, Location at, Component text, float scale, boolean billboard,
                            int backgroundArgb) {
        Location spawn = at.clone();
        spawn.setPitch(0);
        return group.add(at.getWorld().spawn(spawn, TextDisplay.class, display -> {
            prepare(display);
            display.text(text);
            display.setBillboard(billboard ? Display.Billboard.CENTER : Display.Billboard.FIXED);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setLineWidth(240);
            display.setBackgroundColor(Color.fromARGB(backgroundArgb));
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
        }));
    }

    public Interaction interaction(EntityGroup group, Location at, float width, float height) {
        return group.add(at.getWorld().spawn(at, Interaction.class, interaction -> {
            tag(interaction);
            interaction.setPersistent(false);
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setResponsive(true);
        }));
    }

    /**
     * Invisible seat for a cushion whose top is at {@code at}. A rider sits at the stand's height (1.975)
     * minus the player's riding offset (0.6), so the stand goes below the cushion to put the hips on it.
     */
    public ArmorStand seat(EntityGroup group, Location at) {
        return group.add(at.getWorld().spawn(at.clone().subtract(0, 1.9, 0), ArmorStand.class, stand -> {
            tag(stand);
            stand.setPersistent(false);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values());
            stand.setCanTick(false);
        }));
    }

    public Mannequin npc(EntityGroup group, Location at, String name, String description, String skin) {
        return group.add(at.getWorld().spawn(at, Mannequin.class, npc -> {
            tag(npc);
            npc.setPersistent(false);
            npc.setInvulnerable(true);
            npc.setImmovable(true);
            npc.setSilent(true);
            npc.setRemoveWhenFarAway(false);
            npc.customName(CasinoText.legacy(name));
            npc.setCustomNameVisible(true);
            npc.setDescription(CasinoText.legacy(description));
            applySkin(npc, skin);
        }));
    }

    public static void applySkin(Mannequin npc, String skin) {
        if (skin == null || skin.isBlank()) {
            return;
        }
        PlayerProfile profile = Bukkit.createProfile(skin.trim());
        npc.setProfile(ResolvableProfile.resolvableProfile(profile));
    }

    /** Makes {@code entity} visible only to {@code viewer} (it must have been spawned hidden). */
    public void showOnlyTo(Entity entity, Player viewer) {
        viewer.showEntity(plugin, entity);
    }

    public void hideFrom(Entity entity, Player viewer) {
        viewer.hideEntity(plugin, entity);
    }

    public void tag(Entity entity) {
        entity.getPersistentDataContainer().set(keys.entity, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isCasinoEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(keys.entity, PersistentDataType.BYTE);
    }

    private void prepare(Display display) {
        tag(display);
        display.setPersistent(false);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(1.5f);
        display.setShadowRadius(0);
    }
}
