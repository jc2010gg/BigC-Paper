package dev.tideframe;

import dev.tideframe.core.GifFrames;
import dev.tideframe.core.ViewState;
import dev.tideframe.core.demo.MediaDemo;
import dev.tideframe.edit.ScreenCodec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MediaDemoTest {
    @Test void theDemoGifIsTheProjectsOwnLoopingTwelveFrameStar() throws Exception {
        byte[] gif;
        try (var in = TideframeResources.open(MediaDemo.GIF_RESOURCE)) { assertNotNull(in, "bundled"); gif = in.readAllBytes(); }
        var frames = GifFrames.decode(gif, 0, dev.tideframe.pack.HudAssets.MAX_FRAMES);
        assertEquals(12, frames.frames().size()); assertEquals(12, frames.distinct()); assertEquals(100, frames.intervalMs());
        assertEquals(96, frames.frames().get(0).width());
    }

    @Test void theDemoIsValidBakesWithItsGifAndShowsTheFrostedPanel() throws Exception {
        var demo = MediaDemo.screen();
        assertEquals(demo, ScreenCodec.decode(ScreenCodec.encode(demo)), "schema 9 round trip, blurs included");
        assertEquals(List.of(), demo.logicProblems());
        assertDoesNotThrow(demo::validateActionAreas);
        assertEquals(Set.of("popup", "popup_text", "star"), demo.runtimeIds());
        assertTrue(demo.element("popup").orElseThrow().style().backdropBlur() > 0, "the hidden panel is frosted glass");
        assertNotNull(demo.element("coin").orElseThrow().hoverImage(), "the coin turns into the star on hover");
        assertEquals("slide", demo.logic().animation().close().effect(), "the page slides out when it closes");
        byte[] gif;
        try (var in = TideframeResources.open(MediaDemo.GIF_RESOURCE)) { gif = in.readAllBytes(); }
        var frames = GifFrames.decode(gif, 0, dev.tideframe.pack.HudAssets.MAX_FRAMES);
        var prepared = dev.tideframe.core.CloseControl.prepare(demo);
        var assets = dev.tideframe.pack.HudAssets.create(List.of(prepared), List.of(4, 8), dev.tideframe.pack.HudAssets.DEFAULT_CURSOR_SCALE,
                Map.of(MediaDemo.GIF_FOLDER, frames.frames()), Map.of(MediaDemo.GIF_FOLDER, frames.intervalMs()));
        assertEquals(12, assets.frameCount(prepared, "star"));
        assertEquals(100, assets.frameInterval(prepared.element("star").orElseThrow()));
        for (int quarters : List.of(4, 8)) assertNotNull(assets.layer(prepared, "popup", quarters), "the panel is its own layer at " + quarters);
        var host = new ScreenRuntimeTest.FakeHost(demo);
        var runtime = new ScreenRuntime(demo, new HashMap<>(), host);
        runtime.open();
        assertFalse(host.view.shown("popup"));
        runtime.click("show", UiSession.Click.LEFT, "");
        assertTrue(host.view.shown("popup") && host.view.shown("popup_text"), "the button shows the panel and its text");
    }
}
