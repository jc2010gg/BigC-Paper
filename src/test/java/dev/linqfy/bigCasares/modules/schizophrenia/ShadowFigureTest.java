package dev.linqfy.bigCasares.modules.schizophrenia;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowFigureTest {

    @Test
    void figureIsTallerThanAnyPlayerAndFitsItsHeight() {
        float top = 0f;
        for (ShadowFigure.Part part : ShadowFigure.PARTS) {
            assertTrue(part.y() >= 0f);
            assertTrue(part.width() > 0f && part.height() > 0f && part.depth() > 0f);
            top = Math.max(top, part.y() + part.height());
        }
        assertTrue(top > 3.0f, "should tower over a 1.8 block player");
        assertTrue(top <= ShadowFigure.HEIGHT);
    }

    @Test
    void onlyTheFaceIsLitAndItSitsOnTheFrontOfTheHead() {
        ShadowFigure.Part head = ShadowFigure.PARTS.stream()
            .filter(part -> !part.lit()).max((a, b) -> Float.compare(a.y(), b.y())).orElseThrow();
        long lit = ShadowFigure.PARTS.stream().filter(ShadowFigure.Part::lit).count();
        assertEquals(3, lit);
        ShadowFigure.PARTS.stream().filter(ShadowFigure.Part::lit).forEach(part -> {
            assertTrue(part.z() - part.depth() / 2f >= head.z() + head.depth() / 2f - 0.01f, "face must be on the front");
            assertTrue(part.y() >= head.y() && part.y() + part.height() <= head.y() + head.height());
        });
    }

    @Test
    void everyPlayerGetsTheirOwnStableSpotInTheVoid() {
        UUID id = UUID.randomUUID();
        assertEquals(VoidRealm.slot(id), VoidRealm.slot(id));
        assertTrue(VoidRealm.slot(id) >= 0 && VoidRealm.slot(id) < 4096);
    }
}
