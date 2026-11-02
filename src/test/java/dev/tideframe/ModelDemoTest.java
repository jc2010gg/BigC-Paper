package dev.tideframe;

import dev.tideframe.core.ModelPlacement;
import dev.tideframe.core.demo.ModelDemo;
import dev.tideframe.edit.ScreenCodec;
import dev.tideframe.pack.HudRaster;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelDemoTest {
    @Test void theDemoIsAValidMenuThatBakes() {
        var demo = ModelDemo.screen();
        assertEquals(demo, ScreenCodec.decode(ScreenCodec.encode(demo)), "schema 9 round trip");
        assertEquals(List.of(), demo.logicProblems());
        assertDoesNotThrow(demo::validateActionAreas);
        assertEquals("close", demo.element("close").orElseThrow().action());
        var prepared = dev.tideframe.core.CloseControl.prepare(demo);
        assertDoesNotThrow(() -> dev.tideframe.pack.HudAssets.create(List.of(prepared), List.of(4, 8),
                dev.tideframe.pack.HudAssets.DEFAULT_CURSOR_SCALE, Map.of(), Map.of()));
    }

    @Test void theMiddleIsTransparentAndTheFrameIsNot() {
        var raster = HudRaster.render(ModelDemo.screen(), null, 8);
        int w = raster.getWidth(), h = raster.getHeight();
        // Inside the hole (just within its outline) nothing is drawn: the world, and the model, show through.
        for (double x = -ModelDemo.HOLE_HALF_WIDTH * .9; x <= ModelDemo.HOLE_HALF_WIDTH * .9; x += .02)
            for (double y = -ModelDemo.HOLE_HALF_HEIGHT * .9; y <= ModelDemo.HOLE_HALF_HEIGHT * .9; y += .02)
                assertEquals(0, raster.getRGB(px(x, w), py(y, h)) >>> 24, "transparent at " + x + "," + y);
        for (double[] p : new double[][] { { -.9, 0 }, { .9, 0 }, { 0, .85 }, { 0, -.85 } })
            assertTrue((raster.getRGB(px(p[0], w), py(p[1], h)) >>> 24) > 200, "the frame is opaque at " + p[0] + "," + p[1]);
    }

    @Test void theModelFitsTheHoleAtTheDesignFov() {
        double share = ModelPlacement.screenShare(ModelPlacement.HEIGHT, ModelPlacement.DISTANCE, ModelPlacement.DESIGN_FOV);
        // The canvas is 2 units tall, so the hole's half height is also its share of the screen height.
        assertTrue(share < ModelDemo.HOLE_HALF_HEIGHT * .8, "at FOV 70 the model leaves a margin in the hole: " + share);
    }

    private static int px(double x, int width) { return (int) Math.round((x + 1) / 2 * (width - 1)); }
    private static int py(double y, int height) { return (int) Math.round((1 - y) / 2 * (height - 1)); }
}
