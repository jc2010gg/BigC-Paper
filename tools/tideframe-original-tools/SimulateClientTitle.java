import dev.tideframe.core.Screen;
import dev.tideframe.core.ViewportFit;
import dev.tideframe.core.VisualMenuCatalog;
import dev.tideframe.core.demo.RuntimeDemoCatalog;
import dev.tideframe.pack.HudAssets;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Reproduces Minecraft 26.2's title renderer on the generated pack, so layout can be checked
 * without a client: Hud.extractTitle translates to (guiWidth/2, guiHeight/2), scales by 4 and
 * draws at x=-width/2, y=-10; GlyphBitmap.getTop() is 7 - ascent; font sheets sample nearest.
 *
 * java -cp build/classes/java/main tools/SimulateClientTitle.java OUT.png SCREEN_ID WINDOW_W WINDOW_H GUI_SCALE [SIZE e.g. 1.75|fit] [CURSOR_X CURSOR_Y] [HOVER]
 */
public final class SimulateClientTitle {
    record Bitmap(BufferedImage image, int ascent, int height) { }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); String id = args[1];
        int windowW = Integer.parseInt(args[2]), windowH = Integer.parseInt(args[3]), guiArg = Integer.parseInt(args[4]);
        Map<String, Screen> screens = new TreeMap<>(Screen.demos());
        RuntimeDemoCatalog.screens().forEach(screens::putIfAbsent);
        screens.putIfAbsent("visual_menu09", VisualMenuCatalog.screen());
        screens.putIfAbsent(dev.tideframe.core.demo.LogicDemo.ID, dev.tideframe.core.demo.LogicDemo.screen());
        screens.putIfAbsent(dev.tideframe.core.demo.ShowcaseDemo.ID, dev.tideframe.core.demo.ShowcaseDemo.screen());
        screens.putIfAbsent(dev.tideframe.core.demo.MediaDemo.ID, dev.tideframe.core.demo.MediaDemo.screen());
        // Optional: preview the boss prompts with a config.yml (TIDEFRAME_CONFIG=path/to/config.yml).
        if (System.getenv("TIDEFRAME_CONFIG") != null)
            screens.putAll(dev.tideframe.core.demo.BossFightScreens.build(dev.tideframe.BossFightConfig.load(Path.of(System.getenv("TIDEFRAME_CONFIG")))));
        Screen screen = dev.tideframe.core.CloseControl.prepare(screens.get(id));
        var viewport = new ViewportFit(windowW, windowH, guiArg);
        int gui = viewport.effectiveGuiScale();
        var assets = HudAssets.create(List.of(screen));
        int multiplier = args.length > 5 && !args[5].equals("fit") ? ViewportFit.parseSize(args[5]) : viewport.multiplier(screen, assets.sizes());
        double cx = args.length > 7 ? Double.parseDouble(args[6]) : 0, cy = args.length > 7 ? Double.parseDouble(args[7]) : 0;
        String hover = args.length > 8 ? args[8] : null;
        var entries = assets.entries();
        // Segments in the order the plugin composes them: HUD layer, then each live label.
        var segments = new ArrayList<String[]>();
        segments.add(new String[]{HudAssets.fontName(multiplier), assets.displayText(assets.frame(screen, hover, 8, multiplier) + (screen.presentation().mode().equals("overlay") ? "" : assets.cursor(screen, cx, cy)), multiplier), "0"});
        var sample = new java.util.HashMap<>(Map.of("{player}", "Steve", "{balance}", "1250", "{health}", "20", "{max_health}", "20", "{world}", "world", "{online}", "3"));
        sample.putAll(Map.of("{key}", "W", "{key_word}", "SPACE", "{feedback}", "PERFECT!", "{score}", "1240", "{combo}", "COMBO x3", "{boss_hp}", "62", "{boss_max}", "100",
                "{best_combo}", "7", "{hits}", "9", "{misses}", "1"));
        sample.putAll(Map.of("{boss_name}", "WARDEN", "{lives}", "4/5", "{balance_short}", "1.25M"));
        var barSample = Map.of("health", "0.7000|0.8500", "boss", "0.6200|0.8000", "hp", "0.6000", "timer", "0.5500", "xp", "0.4500");
        for (var element : screen.elements()) {
            if (dev.tideframe.core.LiveText.isBar(element)) {
                int[] box = dev.tideframe.core.LiveText.barBox(element);
                String v = barSample.getOrDefault(dev.tideframe.core.LiveText.barSource(element), "0.7");
                int s = screen.presentation().scale(), filled = dev.tideframe.core.LiveText.barPixels(v, box[2]), trail = dev.tideframe.core.LiveText.barTrailPixels(v, box[2]), prefix = (box[0] - 128) * s;
                segments.add(new String[]{HudAssets.fontName(multiplier), HudAssets.hudSpace(prefix), "0"});
                segments.add(new String[]{assets.barFont(screen, element, multiplier), HudAssets.barGlyphs(filled, trail, HudAssets.barCellWidth(element)), "#ffffff"});
                segments.add(new String[]{HudAssets.fontName(multiplier), HudAssets.hudSpace(-prefix - trail * s), "0"});
                continue;
            }
            if (!dev.tideframe.core.LiveText.isLive(element)) continue;
            String value = element.text(); for (var e : sample.entrySet()) value = value.replace(e.getKey(), e.getValue());
            String text = dev.tideframe.core.LiveText.sanitize(value, element);
            int s = screen.presentation().scale(), prefix = (dev.tideframe.core.LiveText.lineLeft(element, text.length()) - HudAssets.livePad(element) - 128) * s;
            segments.add(new String[]{HudAssets.fontName(multiplier), HudAssets.hudSpace(prefix), "0"});
            segments.add(new String[]{assets.liveFont(screen, element, multiplier), HudAssets.liveGlyphs(element, text, s, multiplier),
                    HudAssets.liveColorsBaked(element) ? "#ffffff" : element.style().textColor()});
            segments.add(new String[]{HudAssets.fontName(multiplier), HudAssets.hudSpace(-prefix - text.length() * 6 * element.style().textScale() * s), "0"});
        }
        var fonts = new HashMap<String, Map<Character, Double>>(); var bitmaps = new HashMap<String, Map<Character, Bitmap>>();
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().startsWith("assets/tideframe/font/") || !entry.getKey().endsWith(".json")) continue;
            String name = "tideframe:" + entry.getKey().substring(22, entry.getKey().length() - 5);
            String font = new String(entry.getValue(), StandardCharsets.UTF_8);
            var spaces = new HashMap<Character, Double>(); var glyphMap = new HashMap<Character, Bitmap>();
            // Every space provider in the font (the native canvas adds its own block after the bitmaps).
            var block = Pattern.compile("\"type\":\"space\",\"advances\":\\{([^}]*)}").matcher(font);
            while (block.find()) {
                var space = Pattern.compile("\"(\\\\u[0-9a-f]{4}| )\":(-?[0-9.E]+)").matcher(block.group(1));
                while (space.find()) spaces.put(space.group(1).equals(" ") ? ' ' : (char) Integer.parseInt(space.group(1).substring(2), 16), Double.parseDouble(space.group(2)));
            }
            var provider = Pattern.compile("\"file\":\"tideframe:([^\"]+)\",\"ascent\":(-?[0-9]+),\"height\":([0-9]+),\"chars\":\\[((?:\"[^\\]]*\",?)+)\\]").matcher(font);
            while (provider.find()) {
                int ascent = Integer.parseInt(provider.group(2)), height = Integer.parseInt(provider.group(3));
                if (ascent > height) throw new IllegalStateException("Client would reject provider: ascent " + ascent + " > height " + height);
                var image = ImageIO.read(new ByteArrayInputStream(entries.get("assets/tideframe/textures/" + provider.group(1))));
                var rows = provider.group(4).split("\",\"");
                for (int r = 0; r < rows.length; r++) {
                    var cells = new ArrayList<Character>(); var m = Pattern.compile("\\\\u([0-9a-f]{4})").matcher(rows[r]);
                    while (m.find()) cells.add((char) Integer.parseInt(m.group(1), 16));
                    int cw = image.getWidth() / cells.size(), ch = image.getHeight() / rows.length;
                    if (cw > 256 || ch > 256) throw new IllegalStateException("Glyph exceeds 256x256 font sheet");
                    for (int c = 0; c < cells.size(); c++) if (cells.get(c) != 0)
                        glyphMap.put(cells.get(c), new Bitmap(image.getSubimage(c * cw, r * ch, cw, ch), ascent, height));
                }
            }
            fonts.put(name, spaces); bitmaps.put(name, glyphMap);
        }
        int guiW = (windowW + gui - 1) / gui, guiH = (windowH + gui - 1) / gui;
        var frame = new BufferedImage(windowW, windowH, BufferedImage.TYPE_INT_RGB);
        var g = frame.createGraphics();
        // A neutral in-game backdrop with the vanilla hotbar footprint (182x22 GUI px, bottom centre).
        g.setPaint(new GradientPaint(0, 0, new Color(0x7da7d9), 0, windowH * .6f, new Color(0xb9d3ee))); g.fillRect(0, 0, windowW, windowH);
        g.setColor(new Color(0x5d8a3a)); g.fillRect(0, (int) (windowH * .62), windowW, windowH);
        g.setColor(new Color(0, 0, 0, 120)); g.fillRect((windowW - 182 * gui) / 2, windowH - 22 * gui, 182 * gui, 22 * gui);
        double unit = 4.0 * gui; // title scale x GUI scale = physical pixels per font unit
        // TIDEFRAME_FIT=1 applies the auto-fit shader's scaling about the screen centre (use SIZE 2, the reference size).
        double fit = System.getenv("TIDEFRAME_FIT") != null ? dev.tideframe.pack.FitShader.scale(windowW / (double) gui, windowH / (double) gui) : 1;
        double centreX = windowW / 2.0, centreY = windowH / 2.0;
        double originX = guiW / 2 * gui, originY = guiH / 2 * gui;
        double x = 0; // Font.width(text) is zero for these sequences, so x starts at -0/2.
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        for (var segment : segments) {
          var spaces = fonts.get(segment[0]); var glyphMap = bitmaps.get(segment[0]);
          if (spaces == null) throw new IllegalStateException("Missing font " + segment[0]);
          int tint = segment[2].equals("0") ? -1 : Integer.parseInt(segment[2].substring(1), 16);
          for (char c : segment[1].toCharArray()) {
            if (spaces.containsKey(c)) { x += spaces.get(c); continue; }
            var b = glyphMap.get(c);
            if (b == null) throw new IllegalStateException("Missing glyph " + Integer.toHexString(c) + " in " + segment[0]);
            double scale = (double) b.height() / b.image().getHeight();
            double top = -10 + 7 - b.ascent();
            double gx = originX + x * unit, gy = originY + top * unit;
            if (fit != 1) { gx = centreX + (gx - centreX) * fit; gy = centreY + (gy - centreY) * fit; }
            int px0 = (int) Math.round(gx), py0 = (int) Math.round(gy);
            // Round the far edge, not the size: neighbouring tiles then share their edge exactly (as on the GPU).
            int pw = (int) Math.round(gx + b.image().getWidth() * scale * unit * fit) - px0, ph = (int) Math.round(gy + b.height() * unit * fit) - py0;
            for (int py = Math.max(0, py0); py < Math.min(windowH, py0 + ph); py++)
                for (int px = Math.max(0, px0); px < Math.min(windowW, px0 + pw); px++) {
                    int sx = (int) ((px - px0 + .5) * b.image().getWidth() / (double) pw), sy = (int) ((py - py0 + .5) * b.image().getHeight() / (double) ph);
                    int argb = b.image().getRGB(sx, sy), a = argb >>> 24;
                    if (a == 0) continue;
                    if (tint >= 0) argb = (argb & 0xff000000) | ((argb >> 16 & 255) * (tint >> 16 & 255) / 255) << 16
                            | ((argb >> 8 & 255) * (tint >> 8 & 255) / 255) << 8 | (argb & 255) * (tint & 255) / 255; // vanilla multiplies
                    int dst = frame.getRGB(px, py);
                    int r = (((argb >> 16) & 255) * a + ((dst >> 16) & 255) * (255 - a)) / 255;
                    int gg = (((argb >> 8) & 255) * a + ((dst >> 8) & 255) * (255 - a)) / 255;
                    int bb = ((argb & 255) * a + (dst & 255) * (255 - a)) / 255;
                    frame.setRGB(px, py, r << 16 | gg << 8 | bb);
                    if (a > 8) { minX = Math.min(minX, px); maxX = Math.max(maxX, px); minY = Math.min(minY, py); maxY = Math.max(maxY, py); }
                }
            // Vanilla bitmap advance: (int)(0.5 + inkWidth * scale) + 1; zero-advance sequences cancel it with spaces.
            int ink = 0; for (int ix = 0; ix < b.image().getWidth(); ix++) for (int iy = 0; iy < b.image().getHeight(); iy++) if ((b.image().getRGB(ix, iy) >>> 24) != 0) ink = ix + 1;
            x += (int) (0.5 + ink * scale) + 1;
          }
        }
        if (Math.abs(x) > 1e-6) throw new IllegalStateException("Title does not return to its origin: " + x);
        g.dispose();
        ImageIO.write(frame, "png", out.toFile());
        System.out.printf(Locale.ROOT, "screen=%s window=%dx%d gui=%d size=%s canvasPx=[%.0f..%.0f]x[%.0f..%.0f] centreOffsetPx=(%.1f, %.1f) widthFill=%.0f%% cut=%s%n",
                id, windowW, windowH, gui, ViewportFit.label(multiplier), minX, maxX, minY, maxY, (minX + maxX) / 2 - windowW / 2.0, (minY + maxY) / 2 - windowH / 2.0, (maxX - minX + 1) * 100.0 / windowW,
                minX <= 0 || minY <= 0 || maxX >= windowW - 1 || maxY >= windowH - 1);
    }
}
