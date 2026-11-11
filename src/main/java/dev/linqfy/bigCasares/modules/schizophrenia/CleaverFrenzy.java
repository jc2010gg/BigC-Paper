package dev.linqfy.bigCasares.modules.schizophrenia;

import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * The last stage. The victim is handed the Cuchilla and stops being in charge: their eyes are
 * dragged to the nearest living thing in sight, their legs carry them to it and their arm swings
 * until it stops moving. Then the next one. Only the Lobotomizador ends it.
 */
final class CleaverFrenzy {

    /** Fraction of the way the camera turns towards the target on every update. */
    static final double TURN_RATE = 0.45;
    /** Horizontal push per update while walking someone to their victim. */
    static final double PULL = 0.3;
    static final double REACH = 3.0;

    private final Plugin plugin;
    private final CleaverItem cleaver;
    private final SchizophreniaSettings.Cleaver settings;
    private final RandomGenerator random;

    CleaverFrenzy(Plugin plugin, CleaverItem cleaver, SchizophreniaSettings.Cleaver settings, RandomGenerator random) {
        this.plugin = plugin;
        this.cleaver = cleaver;
        this.settings = settings;
        this.random = random;
    }

    boolean enabled() {
        return settings.enabled();
    }

    CleaverItem item() {
        return cleaver;
    }

    /** Runs on every runtime update while the episode sits at the last stage. */
    void update(Player player, Episode episode, long now) {
        if (!episode.frenzied()) {
            begin(player, episode);
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        cleaver.forceIntoHand(player);
        LivingEntity target = target(player);
        if (target == null) {
            if (now % 120 == 0) {
                whisper(player, SchizophreniaLines.pick(SchizophreniaLines.FRENZY_SEARCHING, random));
            }
            return;
        }
        hunt(player, target, now);
    }

    /** Ends the frenzy: the cleaver goes back to wherever it came from. */
    void stop(Player player, Episode episode) {
        cleaver.takeAway(player);
        if (episode != null && episode.frenzied()) {
            episode.frenzied(false);
            player.setWorldBorder(null);
        }
    }

    private void begin(Player player, Episode episode) {
        episode.frenzied(true);
        player.stopAllSounds();
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1.5f, 0.5f);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 1f, 0.6f);
        player.showTitle(Title.title(
            Component.text("MATALOS", NamedTextColor.DARK_RED, TextDecoration.BOLD),
            Component.text("a todos", NamedTextColor.RED),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))));
        whisper(player, "ahora sí.");
        // A permanent red edge to the world for as long as it lasts.
        WorldBorder border = plugin.getServer().createWorldBorder();
        border.setCenter(player.getLocation());
        border.setSize(2000);
        border.setWarningDistance(4000);
        player.setWorldBorder(border);
    }

    private void hunt(Player player, LivingEntity target, long now) {
        Location eye = player.getEyeLocation();
        Location aim = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        Vector want = aim.toVector().subtract(eye.toVector());
        double distance = want.length();
        Vector look = turnTowards(eye.getDirection(), want, TURN_RATE);
        player.lookAt(eye.getX() + look.getX(), eye.getY() + look.getY(), eye.getZ() + look.getZ(), LookAnchor.EYES);

        if (distance <= REACH) {
            if (player.getAttackCooldown() >= 0.95f) {
                player.swingMainHand();
                player.attack(target);
                if (random.nextInt(3) == 0) {
                    whisper(player, SchizophreniaLines.pick(SchizophreniaLines.FRENZY_KILLING, random));
                }
            }
            return;
        }
        if (player.isOnGround() && !player.isInsideVehicle() && !player.isFlying()) {
            Vector flat = want.clone().setY(0);
            if (flat.lengthSquared() > 1.0E-4) {
                flat.normalize();
                Vector velocity = flat.clone().multiply(PULL);
                Block ahead = player.getLocation().add(flat.clone().multiply(0.8)).getBlock();
                velocity.setY(ahead.isSolid() && ahead.getRelative(0, 1, 0).isPassable() ? 0.42 : player.getVelocity().getY());
                player.setVelocity(velocity);
            }
        }
        if (now % 30 == 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.4f, 1.2f);
        }
    }

    /** The nearest living thing the victim can see. */
    private LivingEntity target(Player player) {
        double range = settings.sightRange();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : player.getWorld().getNearbyLivingEntities(player.getLocation(), range)) {
            if (!huntable(player, candidate)) {
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(player.getLocation());
            if (distance < bestDistance && player.hasLineOfSight(candidate)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean huntable(Player player, LivingEntity candidate) {
        if (candidate == player || !candidate.isValid() || candidate.isDead() || candidate.isInvisible()
            || candidate instanceof ArmorStand || candidate instanceof Mannequin
            || candidate.getScoreboardTags().contains(HallucinationDirector.PHANTOM_TAG)) {
            return false;
        }
        if (candidate instanceof Player other) {
            return settings.attackPlayers() && player.canSee(other)
                && (other.getGameMode() == GameMode.SURVIVAL || other.getGameMode() == GameMode.ADVENTURE);
        }
        return true;
    }

    /** Moves {@code look} a fraction {@code rate} of the way towards {@code want}; both are directions. */
    static Vector turnTowards(Vector look, Vector want, double rate) {
        if (want.lengthSquared() < 1.0E-8) {
            return look.clone();
        }
        Vector from = look.lengthSquared() < 1.0E-8 ? want.clone().normalize() : look.clone().normalize();
        Vector to = want.clone().normalize();
        if (from.dot(to) < -0.999) {
            // Straight behind: a plain blend would never leave the spot, so start turning sideways.
            Vector side = Math.abs(to.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(-to.getZ(), 0, to.getX());
            from = side.normalize();
        }
        Vector mixed = from.multiply(1.0 - rate).add(to.clone().multiply(rate));
        return mixed.lengthSquared() < 1.0E-8 ? to : mixed.normalize();
    }

    private static void whisper(Player player, String line) {
        player.sendMessage(Component.text(line, NamedTextColor.DARK_RED, TextDecoration.ITALIC));
    }
}
