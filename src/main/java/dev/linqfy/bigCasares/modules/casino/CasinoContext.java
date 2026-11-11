package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.modules.casino.slots.JackpotPool;
import dev.linqfy.bigCasares.modules.casino.world.CasinoKeys;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.InteractionRouter;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/** Shared services every attraction uses. */
public record CasinoContext(
    Plugin plugin,
    CasinoSettings settings,
    CasinoBank bank,
    CasinoEscrow escrow,
    DisplayKit kit,
    InteractionRouter router,
    CasinoKeys keys,
    RandomGenerator random,
    JackpotPool jackpot,
    Runnable saveState,
    CasinoServices services,
    LongSupplier clock
) {

    public Logger logger() {
        return plugin.getLogger();
    }

    public long now() {
        return clock.getAsLong();
    }

    public List<Player> playersNear(Location center, double radius) {
        if (center.getWorld() == null) {
            return List.of();
        }
        double squared = radius * radius;
        return center.getWorld().getPlayers().stream()
            .filter(player -> player.getLocation().distanceSquared(center) <= squared)
            .toList();
    }

    public void broadcastNear(Location center, double radius, String message) {
        playersNear(center, radius).forEach(player -> player.sendMessage(message));
    }

    public void sound(Location at, Sound sound, float volume, float pitch) {
        if (at.getWorld() != null) {
            at.getWorld().playSound(at, sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }

    public String chips(long amount) {
        return bank.format(amount);
    }
}
