package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.modules.casino.world.EntityGroup;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A placed piece of the casino. It is rebuilt from its {@link Installation} every time the module enables. */
public abstract class Attraction {

    protected final CasinoContext context;
    protected final Installation installation;
    protected final Frame frame;
    protected final EntityGroup props = new EntityGroup();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private long tick;

    protected Attraction(CasinoContext context, Installation installation, Frame frame) {
        this.context = context;
        this.installation = installation;
        this.frame = frame;
    }

    public Installation installation() {
        return installation;
    }

    public Location origin() {
        return frame.origin();
    }

    /** Spawns the props. Called once, with the chunk loaded. */
    public abstract void spawn();

    /** Called every server tick. */
    public final void tick() {
        tick++;
        Iterator<Scheduled> iterator = scheduled.iterator();
        List<Scheduled> due = new ArrayList<>();
        while (iterator.hasNext()) {
            Scheduled entry = iterator.next();
            if (entry.dueTick <= tick) {
                iterator.remove();
                due.add(entry);
            }
        }
        for (Scheduled entry : due) {
            entry.action.run();
        }
        onTick(tick);
    }

    protected void onTick(long tick) {
    }

    /** Runs {@code action} after {@code delayTicks} ticks, as part of this attraction's own timeline. */
    protected void later(int delayTicks, Runnable action) {
        scheduled.add(new Scheduled(tick + Math.max(1, delayTicks), action));
    }

    protected void cancelScheduled() {
        scheduled.clear();
    }

    protected long ticks() {
        return tick;
    }

    /** Refunds what is in play and removes every entity. */
    public void despawn() {
        cancelScheduled();
        onDespawn();
        context.router().unbindAll(props);
        props.removeAll();
    }

    protected void onDespawn() {
    }

    public void onQuit(Player player) {
    }

    /** A player left a vehicle; return true when it was one of this attraction's seats. */
    public boolean onDismount(Player player, Entity vehicle) {
        return false;
    }

    /** Damage between players inside this attraction; return true to cancel it. */
    public boolean onPlayerDamage(Player victim, Player attacker, double finalDamage) {
        return false;
    }

    private record Scheduled(long dueTick, Runnable action) {
    }
}
