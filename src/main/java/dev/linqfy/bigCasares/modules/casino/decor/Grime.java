package dev.linqfy.bigCasares.modules.casino.decor;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

import java.util.Random;

/**
 * Cigarette butts and suspicious green stains scattered on the floor. The layout is seeded from the
 * installation id, so it looks the same after every restart.
 */
public final class Grime extends Attraction {

    private static final double RADIUS = 6;

    public Grime(CasinoContext context, Installation installation, Frame frame) {
        super(context, installation, frame);
    }

    @Override
    public void spawn() {
        Random layout = new Random(installation.id().hashCode());
        for (int butt = 0; butt < 16; butt++) {
            Location at = frame.at((layout.nextDouble() - 0.5) * RADIUS * 2, 0.005, (layout.nextDouble() - 0.5) * RADIUS * 2);
            at.setYaw(layout.nextFloat() * 360);
            Quaternionf lying = new Quaternionf(new AxisAngle4f((float) Math.PI / 2, 1, 0, 0));
            context.kit().block(props, at, Material.WHITE_CONCRETE.createBlockData(), 0.035f, 0.16f, 0.035f, lying);
            Location tip = at.clone().add(at.getDirection().multiply(0.12));
            context.kit().block(props, tip, Material.ORANGE_CONCRETE.createBlockData(), 0.037f, 0.05f, 0.037f, lying);
            if (layout.nextInt(3) == 0) {
                context.kit().block(props, at.clone().add(0.05, 0, 0.05), Material.GRAY_CONCRETE_POWDER.createBlockData(),
                    0.12f, 0.004f, 0.1f);
            }
        }
        for (int stain = 0; stain < 7; stain++) {
            Location at = frame.at((layout.nextDouble() - 0.5) * RADIUS * 2, 0.003, (layout.nextDouble() - 0.5) * RADIUS * 2);
            at.setYaw(layout.nextFloat() * 360);
            float size = 0.4f + layout.nextFloat() * 0.6f;
            context.kit().block(props, at, Material.SLIME_BLOCK.createBlockData(), size, 0.004f, size * (0.6f + layout.nextFloat()));
            context.kit().block(props, at.clone().add(size * 0.3, 0, 0), Material.LIME_CONCRETE.createBlockData(),
                size * 0.4f, 0.005f, size * 0.3f);
        }
    }

    @Override
    protected void onTick(long tick) {
        if (tick % 100 == 0 && !context.playersNear(frame.origin(), 16).isEmpty()) {
            Location at = frame.at((context.random().nextDouble() - 0.5) * RADIUS, 0.1, (context.random().nextDouble() - 0.5) * RADIUS);
            at.getWorld().spawnParticle(Particle.SMOKE, at, 2, 0.02, 0.05, 0.02, 0.005);
        }
    }
}
