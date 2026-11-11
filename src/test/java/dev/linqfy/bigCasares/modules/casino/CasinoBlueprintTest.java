package dev.linqfy.bigCasares.modules.casino;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoBlueprintTest {

    @Test
    void everyAttractionFitsInsideTheHall() {
        for (CasinoBlueprint.Spot spot : CasinoBlueprint.spots()) {
            if (spot.type() == AttractionType.SIGN) {
                continue;
            }
            assertTrue(Math.abs(spot.right()) <= CasinoBlueprint.HALF_WIDTH - 1, spot.toString());
            assertTrue(spot.forward() >= 1 && spot.forward() <= CasinoBlueprint.DEPTH - 1, spot.toString());
        }
    }

    @Test
    void theHallHasOneOfEachVenueAndARowOfSlots() {
        Map<AttractionType, Integer> counts = new EnumMap<>(AttractionType.class);
        CasinoBlueprint.spots().forEach(spot -> counts.merge(spot.type(), 1, Integer::sum));
        assertEquals(8, counts.get(AttractionType.SLOT));
        assertEquals(2, counts.get(AttractionType.BLACKJACK));
        for (AttractionType single : List.of(AttractionType.POKER, AttractionType.ROULETTE, AttractionType.FIGHT_PIT,
            AttractionType.BAR, AttractionType.PAWN_SHOP, AttractionType.LOAN_SHARK)) {
            assertEquals(1, counts.get(single), single.name());
        }
    }

    @Test
    void entranceAndOfficeDoorAreOpenAndTheInteriorIsLit() {
        assertEquals(Material.AIR, CasinoBlueprint.material(0, 0, -1, true));
        assertEquals(Material.AIR, CasinoBlueprint.material(0, 1, -1, true));
        assertEquals(Material.GOLD_BLOCK, CasinoBlueprint.material(2, 1, -1, true));
        assertEquals(Material.AIR, CasinoBlueprint.material(0, 1, 46, false));
        assertEquals(Material.POLISHED_BLACKSTONE_BRICKS, CasinoBlueprint.material(3, 1, 46, false));
        assertEquals(Material.SEA_LANTERN, CasinoBlueprint.material(10, CasinoBlueprint.HEIGHT, 20, false));
        assertEquals(Material.BLACK_CONCRETE, CasinoBlueprint.material(0, CasinoBlueprint.HEIGHT, 50, false),
            "the loan shark's office stays dark");
    }
}
