package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.modules.casino.pit.FightPit;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * "El Gran Casares": a 41 x 57 hall with a dark-wood and gold interior, a black ceiling with lantern grid,
 * a back office for the loan shark and every attraction in its place. Coordinates are local to the entrance:
 * {@code right} across the hall, {@code forward} into it.
 */
public final class CasinoBlueprint {

    public static final int HALF_WIDTH = 20;
    public static final int DEPTH = 56;
    public static final int HEIGHT = 9;

    /** An attraction to install once the shell is built. */
    public record Spot(AttractionType type, double right, double forward, float yawOffset, String variant) {
    }

    /** One block of the shell. */
    public record Piece(int right, int up, int forward, Material material) {
    }

    private CasinoBlueprint() {
    }

    public static List<Spot> spots() {
        List<Spot> spots = new ArrayList<>();
        for (int machine = 0; machine < 8; machine++) {
            spots.add(new Spot(AttractionType.SLOT, 18, 6 + machine * 3, -90, ""));
        }
        spots.add(new Spot(AttractionType.BLACKJACK, -15, 11, 90, ""));
        spots.add(new Spot(AttractionType.BLACKJACK, -15, 20, 90, ""));
        spots.add(new Spot(AttractionType.POKER, -15, 29, 90, ""));
        spots.add(new Spot(AttractionType.ROULETTE, 0, 14, 0, ""));
        spots.add(new Spot(AttractionType.FIGHT_PIT, 7, 34, 0, ""));
        spots.add(new Spot(AttractionType.BAR, -14, 50, 180, ""));
        spots.add(new Spot(AttractionType.PAWN_SHOP, 14, 50, 180, ""));
        spots.add(new Spot(AttractionType.LOAN_SHARK, 0, 52, 180, ""));
        spots.add(new Spot(AttractionType.SIGN, 0, -1.65, 180, "§6§l★ GRAN CASARES ★\\n§fCasino · Bar · Empeños"));
        spots.add(new Spot(AttractionType.SIGN, 0, 45.35, 180, "0"));
        spots.add(new Spot(AttractionType.SIGN, -20.35, 24, 90, "1"));
        spots.add(new Spot(AttractionType.SIGN, -20.35, 38, 90, "2"));
        spots.add(new Spot(AttractionType.SIGN, 20.35, 33, -90, "3"));
        spots.add(new Spot(AttractionType.SIGN, 0, -0.35, 0, "4"));
        spots.add(new Spot(AttractionType.SIGN, 20.35, 45, -90, "5"));
        spots.add(new Spot(AttractionType.GRIME, -9, 42, 0, ""));
        spots.add(new Spot(AttractionType.GRIME, 10, 22, 0, ""));
        spots.add(new Spot(AttractionType.GRIME, 0, 38, 0, ""));
        return spots;
    }

    /** Height above the floor of a spot's origin: signs hang on walls, everything else stands on the floor. */
    public static double heightOf(Spot spot) {
        if (spot.type() != AttractionType.SIGN) {
            return 0;
        }
        return spot.forward() < -1 ? 5.2 : spot.forward() < 1 ? 4.6 : 5.0;
    }

    public static List<Piece> shell() {
        List<Piece> pieces = new ArrayList<>();
        for (int right = -HALF_WIDTH - 1; right <= HALF_WIDTH + 1; right++) {
            for (int forward = -1; forward <= DEPTH + 1; forward++) {
                boolean wallX = Math.abs(right) == HALF_WIDTH + 1;
                boolean wallZ = forward == -1 || forward == DEPTH + 1;
                for (int up = -1; up <= HEIGHT; up++) {
                    pieces.add(new Piece(right, up, forward, material(right, up, forward, wallX || wallZ)));
                }
            }
        }
        return pieces;
    }

    static Material material(int right, int up, int forward, boolean wall) {
        if (up == -1) {
            return floor(right, forward);
        }
        if (up == HEIGHT) {
            boolean lamp = Math.floorMod(right, 5) == 0 && Math.floorMod(forward, 5) == 0 && !office(right, forward);
            return lamp ? Material.SEA_LANTERN : Material.BLACK_CONCRETE;
        }
        if (wall) {
            if (forward == -1 && Math.abs(right) <= 2 && up <= 3) {
                return up == 3 || Math.abs(right) == 2 ? Material.GOLD_BLOCK : Material.AIR;
            }
            boolean pillar = Math.floorMod(right, 6) == 0 && Math.abs(right) != HALF_WIDTH + 1
                || Math.floorMod(forward, 6) == 0 || Math.abs(right) == HALF_WIDTH + 1 && (forward == -1 || forward == DEPTH + 1);
            if (pillar) {
                return Material.STRIPPED_DARK_OAK_LOG;
            }
            if (up == 4) {
                return Material.GOLD_BLOCK;
            }
            return up < 4 ? Material.DARK_OAK_PLANKS : Material.BLACK_CONCRETE;
        }
        if (officeWall(right, forward)) {
            boolean door = forward == 46 && Math.abs(right) <= 0 && up <= 2;
            return door ? Material.AIR : up == 4 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS;
        }
        if (up == 0 && Math.abs(right) <= 1 && forward >= 0 && forward <= 8) {
            return Material.RED_CARPET;
        }
        if (up == 0 && office(right, forward)) {
            return Material.BROWN_CARPET;
        }
        if (up == HEIGHT - 1 && Math.floorMod(right, 10) == 0 && Math.floorMod(forward, 10) == 5 && !office(right, forward)) {
            return Material.IRON_CHAIN;
        }
        if (up == HEIGHT - 2 && Math.floorMod(right, 10) == 0 && Math.floorMod(forward, 10) == 5 && !office(right, forward)) {
            return Material.LANTERN;
        }
        return Material.AIR;
    }

    private static Material floor(int right, int forward) {
        if (office(right, forward)) {
            return Material.DARK_OAK_PLANKS;
        }
        if (Math.floorMod(right, 8) == 4 || Math.floorMod(forward, 8) == 4) {
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        return (right + forward) % 2 == 0 ? Material.BLACK_CONCRETE : Material.POLISHED_DEEPSLATE;
    }

    private static boolean office(int right, int forward) {
        return Math.abs(right) < 6 && forward > 46 && forward <= DEPTH;
    }

    private static boolean officeWall(int right, int forward) {
        return Math.abs(right) == 6 && forward >= 46 && forward <= DEPTH || forward == 46 && Math.abs(right) <= 6;
    }

    public static void place(Frame frame, Piece piece) {
        Location at = frame.at(piece.right(), piece.up(), piece.forward());
        World world = frame.world();
        Block block = world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        if (piece.material() == Material.LANTERN) {
            block.setBlockData(Material.LANTERN.createBlockData("[hanging=true]"), false);
        } else if (block.getType() != piece.material()) {
            block.setType(piece.material(), false);
        }
    }

    public static void buildPit(Frame building) {
        FightPit.build(building.child(7, 0, 34, 0));
    }
}
