package dev.linqfy.bigCasares.modules.schizophrenia;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The tall black figure from the void: a too-thin silhouette with arms down to its knees, two
 * white eyes and a long grin, built from display blocks only one player can see.
 */
final class ShadowFigure {

    /** One box of the body in local space: +Z is the way the figure faces, Y up from its feet. */
    record Part(float x, float y, float z, float width, float height, float depth, boolean lit) {
    }

    static final float HEIGHT = 3.4f;

    static final List<Part> PARTS = List.of(
        new Part(-0.16f, 0.0f, 0f, 0.14f, 1.55f, 0.14f, false),   // left leg
        new Part(0.16f, 0.0f, 0f, 0.14f, 1.55f, 0.14f, false),    // right leg
        new Part(0f, 1.5f, 0f, 0.46f, 1.15f, 0.22f, false),       // torso
        new Part(-0.33f, 1.05f, 0f, 0.1f, 1.55f, 0.1f, false),    // left arm, down to the knees
        new Part(0.33f, 1.05f, 0f, 0.1f, 1.55f, 0.1f, false),     // right arm
        new Part(0f, 2.62f, 0f, 0.1f, 0.3f, 0.1f, false),         // neck, too long
        new Part(0f, 2.9f, 0f, 0.36f, 0.5f, 0.34f, false),        // head
        new Part(-0.08f, 3.18f, 0.172f, 0.06f, 0.05f, 0.01f, true), // left eye
        new Part(0.08f, 3.18f, 0.172f, 0.06f, 0.05f, 0.01f, true),  // right eye
        new Part(0f, 3.0f, 0.172f, 0.26f, 0.025f, 0.01f, true)      // grin
    );

    private final List<BlockDisplay> pieces = new ArrayList<>();

    static ShadowFigure spawn(Plugin plugin, Player viewer, Location feet) {
        ShadowFigure figure = new ShadowFigure();
        for (Part part : PARTS) {
            BlockDisplay piece = feet.getWorld().spawn(feet, BlockDisplay.class, display -> {
                display.setVisibleByDefault(false);
                display.setPersistent(false);
                display.addScoreboardTag(HallucinationDirector.PHANTOM_TAG);
                display.setBlock((part.lit() ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE).createBlockData());
                display.setBrightness(new Display.Brightness(part.lit() ? 15 : 0, part.lit() ? 15 : 0));
                display.setTeleportDuration(0);
                display.setViewRange(2f);
                display.setTransformation(new Transformation(
                    new Vector3f(part.x() - part.width() / 2f, part.y(), part.z() - part.depth() / 2f),
                    new AxisAngle4f(),
                    new Vector3f(part.width(), part.height(), part.depth()),
                    new AxisAngle4f()));
                if (!part.lit()) {
                    // A faint outline is all that separates it from the dark.
                    display.setGlowing(true);
                    display.setGlowColorOverride(Color.fromRGB(28, 28, 32));
                }
            });
            viewer.showEntity(plugin, piece);
            figure.pieces.add(piece);
        }
        return figure;
    }

    /** Jumps the whole figure to new feet, turned to face the given point. */
    void moveTo(Location feet, double lookX, double lookZ) {
        Location target = feet.clone();
        target.setYaw(GazeMath.yawTowards(feet.getX(), feet.getZ(), lookX, lookZ));
        target.setPitch(0f);
        for (BlockDisplay piece : pieces) {
            if (piece.isValid()) {
                piece.teleport(target);
            }
        }
    }

    void remove() {
        pieces.forEach(piece -> {
            if (piece.isValid()) {
                piece.remove();
            }
        });
        pieces.clear();
    }
}
