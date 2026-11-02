import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;

/**
 * Draws the project's own demo GIF (src/main/resources/demo/estrella.gif): an eight-pointed pixel-art star that turns
 * and pulses, 12 frames of 100 ms, transparent background. Mathematical pixels only; no external art is read.
 * Run: java tools/MakeDemoGif.java src/main/resources/demo/estrella.gif
 */
public class MakeDemoGif {
    static final int SIZE = 96, FRAMES = 12;
    public static void main(String[] args) throws Exception {
        // Index 0 is transparent; 1..15 run from deep orange to pale gold; 16 is a dark outline.
        int colors = 17;
        byte[] r = new byte[colors], g = new byte[colors], b = new byte[colors];
        for (int i = 1; i <= 15; i++) {
            double t = (i - 1) / 14.0;
            r[i] = (byte) 255; g[i] = (byte) Math.round(110 + 135 * t); b[i] = (byte) Math.round(20 + 170 * t * t);
        }
        r[16] = (byte) 70; g[16] = (byte) 34; b[16] = (byte) 8;
        var palette = new IndexColorModel(8, colors, r, g, b, 0);
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        var file = new File(args[0]); file.getParentFile().mkdirs();
        try (var out = new FileImageOutputStream(file)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int frame = 0; frame < FRAMES; frame++) {
                var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_INDEXED, palette);
                double turn = frame * Math.PI * 2 / FRAMES / 8; // one point-step per loop: seamless
                double pulse = 1 + .08 * Math.sin(frame * Math.PI * 2 / FRAMES);
                double outer = SIZE * .46 * pulse, inner = SIZE * .2 * pulse, c = SIZE / 2.0;
                for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                    double dx = x + .5 - c, dy = y + .5 - c, distance = Math.hypot(dx, dy);
                    double angle = Math.atan2(dy, dx) - turn;
                    // Star radius at this angle: straight edges between 8 points and 8 notches.
                    double sector = Math.PI / 8, a = ((angle % (2 * sector)) + 2 * sector) % (2 * sector);
                    double u = Math.abs(a - sector) / sector; // 1 at a point, 0 at a notch
                    double edge = inner + (outer - inner) * u;
                    int index = 0;
                    if (distance <= edge) index = distance > edge - 2.5 ? 16 : 1 + (int) Math.round(14 * Math.max(0, 1 - distance / outer));
                    image.getRaster().setSample(x, y, 0, index);
                }
                var meta = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), null);
                var tree = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
                for (var node = tree.getFirstChild(); node != null; node = node.getNextSibling())
                    if (node.getNodeName().equals("GraphicControlExtension")) {
                        var control = (IIOMetadataNode) node;
                        control.setAttribute("disposalMethod", "restoreToBackgroundColor"); control.setAttribute("delayTime", "10");
                        control.setAttribute("transparentColorFlag", "TRUE"); control.setAttribute("transparentColorIndex", "0");
                    }
                if (frame == 0) {
                    // Loop forever (NETSCAPE2.0 application extension).
                    var apps = new IIOMetadataNode("ApplicationExtensions");
                    var app = new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE"); app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[]{1, 0, 0}); apps.appendChild(app); tree.appendChild(apps);
                }
                meta.setFromTree("javax_imageio_gif_image_1.0", tree);
                writer.writeToSequence(new IIOImage(image, null, meta), null);
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
        System.out.println("Wrote " + file + " (" + file.length() + " bytes)");
    }
}
