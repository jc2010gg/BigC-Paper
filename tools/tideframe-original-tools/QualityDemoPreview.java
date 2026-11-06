import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudRaster;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Reproducible software raster preview, explicitly not a Minecraft screenshot. */
class QualityDemoPreview {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args.length==0?".":args[0]);
        var screen=ScreenCodec.decode(Files.readString(root.resolve("examples/gallery-demo/gallery_demo.json")));
        Path target=root.resolve("evidence/gallery-v05-raster.png");
        ImageIO.write(HudRaster.render(screen,null,8),"png",target.toFile());
        ImageIO.write(HudRaster.render(screen,"landscape",8),"png",root.resolve("evidence/gallery-v05-hover.png").toFile());
        System.out.println("SOFTWARE_RASTER_PREVIEW 1024x576 MinecraftScreenshot=false "+target);
    }
}
