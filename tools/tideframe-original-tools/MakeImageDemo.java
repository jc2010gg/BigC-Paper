import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Original geometric artwork; reads no external image/font or installed asset.
 * Generates a new gallery_demo only: the previous image_demo/orbit artwork is preserved.
 */
class MakeImageDemo {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]);
        var pixels = landscape();
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(pixels, "png", bytes)) throw new AssertionError("PNG writer unavailable");
        var image = ImageAsset.fromPng(bytes.toByteArray());
        if (image.width() != 1024 || image.height() != 576) throw new AssertionError("Unexpected landscape dimensions");
        var screen = new Screen("gallery_demo", "ANGULAR COAST", List.of(
            new Screen.Element("title", "ANGULAR COAST", 0, .87, 1.8, .16, "label", "", "rectangle", false, null),
            new Screen.Element("landscape", "", 0, .06, 1.52, 1.52, "button", "message:Coast image selected.",
                "rectangle", true, null, false, image),
            new Screen.Element("caption", "CLICK ART TO SELECT", -.26, -.78, 1.24, .16, "label", "", "rectangle", false, null),
            new Screen.Element("back", "BACK", .70, -.78, .45, .18, "button", "open:menu", "rounded", true, null)));
        String json = ScreenCodec.encode(screen);
        if (!ScreenCodec.decode(json).equals(screen)) throw new AssertionError("Gallery JSON round trip failed");
        Path assets = root.resolve("examples/images");
        Path examples = root.resolve("examples/gallery-demo");
        Path demos = root.resolve("build/demos");
        Files.createDirectories(assets); Files.createDirectories(examples); Files.createDirectories(demos);
        Files.write(assets.resolve("angular-coast.png"), image.png());
        // A matching original JPEG is a reproducible browser-import fixture, not JSON payload.
        var jpeg = new BufferedImage(pixels.getWidth(), pixels.getHeight(), BufferedImage.TYPE_INT_RGB);
        var jpegGraphics = jpeg.createGraphics();
        try { jpegGraphics.drawImage(pixels, 0, 0, null); } finally { jpegGraphics.dispose(); }
        if (!ImageIO.write(jpeg, "jpeg", assets.resolve("angular-coast.jpg").toFile()))
            throw new AssertionError("JPEG writer unavailable");
        Files.writeString(examples.resolve("gallery_demo.json"), json);
        Files.writeString(demos.resolve("gallery_demo.json"), json);
        System.out.println("GALLERY_DEMO_CREATED schema=3 image=" + image.sha256()
            + " width=1024 height=576 pngBytes=" + image.png().length + " elements=4 clickableImages=1");
    }

    private static BufferedImage landscape() {
        var image = new BufferedImage(1024, 576, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(4, 4);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(0x17314d), 0, 108, new Color(0x74b4c3)));
            g.fillRect(0, 0, 256, 144);
            // Angular clouds and a small square of daylight: deliberately no large circles.
            polygon(g, 0xb1d7df, 12, 26, 38, 26, 50, 18, 75, 18, 90, 26, 110, 26, 110, 30, 12, 30);
            polygon(g, 0x93c5d1, 155, 42, 176, 42, 189, 34, 211, 34, 222, 42, 245, 42, 245, 46, 155, 46);
            g.setColor(new Color(0xf5db9a)); g.fillRect(208, 14, 12, 12);
            // Faceted mountain silhouettes, with edges authored as polygons.
            polygon(g, 0x648b9c, 0, 94, 47, 43, 67, 65, 97, 28, 142, 91, 179, 53, 223, 98, 256, 81, 256, 144, 0, 144);
            polygon(g, 0xa5c5cc, 67, 65, 97, 28, 115, 53, 100, 47, 90, 55, 82, 48);
            polygon(g, 0x344e66, 0, 105, 44, 62, 77, 107, 120, 76, 153, 115, 210, 77, 256, 110, 256, 144, 0, 144);
            polygon(g, 0x446b78, 44, 62, 77, 107, 55, 94, 35, 100);
            polygon(g, 0x5f8b91, 210, 77, 230, 91, 215, 96, 190, 105);
            g.setPaint(new GradientPaint(0, 99, new Color(0x4c9eab), 0, 144, new Color(0x163e55)));
            g.fillRect(0, 109, 256, 35);
            polygon(g, 0x2b5965, 0, 96, 27, 104, 44, 114, 85, 115, 65, 124, 0, 132);
            polygon(g, 0x183b4c, 0, 119, 32, 120, 49, 130, 91, 131, 108, 144, 0, 144);
            polygon(g, 0x264c59, 199, 122, 217, 112, 243, 110, 256, 102, 256, 144, 177, 144);
            g.setColor(new Color(0x8ac8ca));
            g.fillRect(112, 116, 35, 1); g.fillRect(143, 122, 43, 1); g.fillRect(88, 128, 37, 1);
            g.setColor(new Color(0x5795a3));
            g.fillRect(126, 133, 31, 1); g.fillRect(160, 139, 28, 1); g.fillRect(57, 111, 24, 1);
        } finally { g.dispose(); }
        return image;
    }

    private static void polygon(Graphics2D graphics, int rgb, int... coordinates) {
        var shape = new Polygon();
        for (int i = 0; i < coordinates.length; i += 2) shape.addPoint(coordinates[i], coordinates[i + 1]);
        graphics.setColor(new Color(rgb)); graphics.fillPolygon(shape);
    }
}
